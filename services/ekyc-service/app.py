import os
import sys
import tempfile
import base64
from pathlib import Path
from typing import Dict, Any, List, Optional, Union

# Đảm bảo đường dẫn import tương đối luôn trỏ đúng vào thư mục ekyc-service
CURRENT_DIR = Path(__file__).resolve().parent
if str(CURRENT_DIR) not in sys.path:
    sys.path.insert(0, str(CURRENT_DIR))

from dotenv import load_dotenv
load_dotenv(CURRENT_DIR / ".env")

import cv2
import numpy as np
from fastapi import FastAPI, UploadFile, File, Form, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from cccd_module.cccd_extractor import CCCDProcessor, CCCDExtractionError
from cccd_module.ai_post_processing import GeminiPostProcessor
from biometric_module.face_matcher import FaceMatcher

app = FastAPI(
    title="O.G Shop eKYC AI Microservice",
    description="Microservice xử lý OCR Căn cước công dân và Xác thực Sinh trắc học khuôn mặt",
    version="1.0.0"
)

# Cấu hình CORS cho phép Frontend và Backend kết nối
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Global models instance (khởi tạo một lần dùng chung)
ocr_processor: Optional[CCCDProcessor] = None
gemini_processor: Optional[GeminiPostProcessor] = None
face_matcher: Optional[FaceMatcher] = None


def get_ocr_processor() -> CCCDProcessor:
    global ocr_processor
    if ocr_processor is None:
        ocr_processor = CCCDProcessor()
    return ocr_processor


def get_gemini_processor() -> Optional[GeminiPostProcessor]:
    global gemini_processor
    if gemini_processor is None:
        try:
            gemini_processor = GeminiPostProcessor()
        except Exception as e:
            print(f"[Warning] Không thể khởi tạo GeminiPostProcessor: {e}")
            gemini_processor = None
    return gemini_processor


def get_face_matcher() -> FaceMatcher:
    global face_matcher
    if face_matcher is None:
        face_matcher = FaceMatcher(model_name="ArcFace", detector_backend="retinaface")
    return face_matcher


# --- Request & Response Models ---

class HealthResponse(BaseModel):
    status: str = "healthy"
    service: str = "ogshop-ekyc-service"
    version: str = "1.0.0"
    algorithms: Dict[str, str] = {
        "ocr_detection": "YOLOv11n (Corner & Text Bounding Boxes)",
        "ocr_recognition": "VietOCR (TransformerOCR 151MB)",
        "post_processing": "Google Gemini Flash (Spellcheck & Standardization)",
        "biometrics_feature": "DeepFace ArcFace (512-dimensional embedding)",
        "biometrics_detector": "RetinaFace (Card) & OpenCV (Live Webcam)",
        "distance_metric": "Cosine Distance (Threshold <= 0.50)"
    }


class ExtractedData(BaseModel):
    so_cccd: str = ""
    ho_va_ten: str = ""
    ngay_sinh: str = ""
    gioi_tinh: str = ""
    quoc_tich: str = "Việt Nam"
    que_quan: str = ""
    noi_thuong_tru: str = ""
    ngay_het_han: Optional[str] = ""


class QualityCheck(BaseModel):
    is_clear: bool = True
    is_full_document_visible: bool = True
    is_glare_free: bool = True
    rejection_reason: str = ""


class OcrResponse(BaseModel):
    status: str
    error_message: str = ""
    image_quality_check: Optional[QualityCheck] = None
    extracted_data: ExtractedData
    card_face_embedding: Optional[List[float]] = None


class MatchFaceRequest(BaseModel):
    card_image_base64: Optional[str] = None
    card_embedding: Optional[List[float]] = None
    live_frame_base64: str
    circle_center: Optional[List[int]] = [320, 240]
    circle_radius: Optional[int] = 140


class MatchFaceResponse(BaseModel):
    match_status: str
    is_match: bool
    distance: float
    confidence_score: float
    threshold_used: float = 0.50
    user_instruction: str
    live_embedding: Optional[List[float]] = None


# --- Helper functions ---

def decode_base64_to_cv2(base64_str: str) -> np.ndarray:
    if "," in base64_str:
        base64_str = base64_str.split(",", 1)[1]
    img_bytes = base64.b64decode(base64_str)
    nparr = np.frombuffer(img_bytes, np.uint8)
    img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    if img is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Không thể giải mã hình ảnh từ chuỗi Base64."
        )
    return img


# --- Endpoints ---

@app.get("/api/v1/ekyc/health", response_model=HealthResponse)
def health_check():
    """Kiểm tra tình trạng hoạt động của service eKYC"""
    return HealthResponse()


@app.post("/api/v1/ekyc/ocr", response_model=OcrResponse)
async def process_cccd_ocr(
    file: Optional[UploadFile] = File(None),
    image_base64: Optional[str] = Form(None)
):
    """
    Bóc tách thông tin thẻ CCCD mặt trước:
    1. Kiểm tra độ mờ bằng phương sai Laplacian (ngưỡng >= 10.0).
    2. Định vị 4 góc thẻ bằng YOLOv11n và nắn thẳng phối cảnh (Perspective Warp).
    3. Nhận dạng các trường văn bản bằng VietOCR Transformer.
    4. Hậu xử lý lỗi dấu tiếng Việt bằng Gemini Flash.
    5. Trích xuất đồng thời vector khuôn mặt 512 chiều trên thẻ CCCD qua ArcFace.
    """
    processor = get_ocr_processor()
    matcher = get_face_matcher()
    gemini = get_gemini_processor()

    temp_path = None
    try:
        # Lấy dữ liệu ảnh từ File upload hoặc Form base64
        if file is not None:
            suffix = Path(file.filename).suffix or ".jpg"
            with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
                content = await file.read()
                tmp.write(content)
                temp_path = tmp.name
        elif image_base64:
            img = decode_base64_to_cv2(image_base64)
            with tempfile.NamedTemporaryFile(delete=False, suffix=".jpg") as tmp:
                temp_path = tmp.name
                cv2.imwrite(temp_path, img)
        else:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Vui lòng cung cấp file ảnh tải lên hoặc image_base64."
            )

        # 1. Chạy bóc tách OCR cốt lõi
        ocr_result = processor.process_cccd(temp_path)

        if ocr_result.get("status") != "SUCCESS":
            return OcrResponse(
                status="ERROR",
                error_message=ocr_result.get("error_message", "Không thể bóc tách CCCD."),
                image_quality_check=ocr_result.get("image_quality_check"),
                extracted_data=ExtractedData()
            )

        # 2. Hậu xử lý bằng Gemini Flash nếu có cấu hình API Key
        if gemini is not None:
            try:
                ocr_result = gemini.process(ocr_result)
            except Exception as e:
                print(f"[Warning] Bỏ qua bước Gemini post-process do lỗi: {e}")

        raw_data = ocr_result.get("extracted_data", {})
        extracted = ExtractedData(
            so_cccd=str(raw_data.get("so_cccd", "")),
            ho_va_ten=str(raw_data.get("ho_va_ten", "")),
            ngay_sinh=str(raw_data.get("ngay_sinh", "")),
            gioi_tinh=str(raw_data.get("gioi_tinh", "")),
            quoc_tich=str(raw_data.get("quoc_tich", "Việt Nam")),
            que_quan=str(raw_data.get("que_quan", "")),
            noi_thuong_tru=str(raw_data.get("noi_thuong_tru", "")),
            ngay_het_han=str(raw_data.get("ngay_het_han", ""))
        )

        # 3. Trích xuất đồng thời Vector khuôn mặt 512 chiều trên thẻ CCCD
        card_embedding = None
        try:
            emb = matcher.extract_embedding(temp_path, detector_backend="retinaface")
            if emb is not None:
                card_embedding = emb.tolist()
        except Exception as e:
            print(f"[Warning] Không thể trích xuất khuôn mặt từ thẻ CCCD: {e}")

        return OcrResponse(
            status="SUCCESS",
            error_message="",
            image_quality_check=ocr_result.get("image_quality_check"),
            extracted_data=extracted,
            card_face_embedding=card_embedding
        )

    except CCCDExtractionError as e:
        return OcrResponse(
            status="ERROR",
            error_message=str(e),
            extracted_data=ExtractedData()
        )
    except Exception as e:
        return OcrResponse(
            status="ERROR",
            error_message=f"Lỗi hệ thống: {str(e)}",
            extracted_data=ExtractedData()
        )
    finally:
        if temp_path and os.path.exists(temp_path):
            try:
                os.remove(temp_path)
            except OSError:
                pass


@app.post("/api/v1/ekyc/extract-card-face")
async def extract_card_face(
    file: Optional[UploadFile] = File(None),
    image_base64: Optional[str] = Form(None)
):
    """
    Trích xuất vector đặc trưng ArcFace 512 chiều từ ảnh thẻ CCCD bằng RetinaFace.
    """
    matcher = get_face_matcher()
    img_data = None
    if file is not None:
        content = await file.read()
        nparr = np.frombuffer(content, np.uint8)
        img_data = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    elif image_base64:
        img_data = decode_base64_to_cv2(image_base64)
    else:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Cần cung cấp file ảnh hoặc chuỗi image_base64."
        )

    embedding = matcher.extract_embedding(img_data, detector_backend="retinaface")
    if embedding is None:
        return {
            "status": "ERROR",
            "message": "Không tìm thấy khuôn mặt rõ ràng trên thẻ căn cước.",
            "embedding": []
        }

    return {
        "status": "SUCCESS",
        "dimensions": len(embedding),
        "embedding": embedding.tolist()
    }


@app.post("/api/v1/ekyc/match-face", response_model=MatchFaceResponse)
def match_live_face(request: MatchFaceRequest):
    """
    So khớp khuôn mặt giữa chân dung CCCD và luồng Camera trực tiếp (Webcam):
    1. Xác định vector chân dung CCCD (từ card_embedding nạp sẵn hoặc card_image_base64).
    2. Quét nhanh khuôn mặt trên khung hình webcam live bằng OpenCV backend.
    3. Trích xuất đặc trưng ArcFace 512 chiều từ khung hình live.
    4. Tính khoảng cách Cosine: d = 1 - (u . v) / (||u|| * ||v||).
    5. So khớp với ngưỡng strict_threshold = 0.50.
    """
    matcher = get_face_matcher()

    # 1. Lấy card embedding
    card_emb = None
    if request.card_embedding and len(request.card_embedding) == 512:
        card_emb = np.array(request.card_embedding, dtype=np.float32)
        matcher.id_embedding = card_emb
    elif request.card_image_base64:
        card_img = decode_base64_to_cv2(request.card_image_base64)
        emb = matcher.extract_embedding(card_img, detector_backend="retinaface")
        if emb is not None:
            card_emb = emb
            matcher.id_embedding = card_emb

    if card_emb is None:
        return MatchFaceResponse(
            match_status="ERROR",
            is_match=False,
            distance=1.0,
            confidence_score=0.0,
            threshold_used=0.50,
            user_instruction="Chưa có thông tin khuôn mặt thẻ CCCD để so khớp."
        )

    # 2. Giải mã ảnh live
    live_frame = decode_base64_to_cv2(request.live_frame_base64)

    # 3. Chạy thuật toán so khớp live frame
    circle_center = tuple(request.circle_center) if request.circle_center else (320, 240)
    circle_radius = request.circle_radius or 140

    result = matcher.process_live_frame(
        frame=live_frame,
        circle_center=circle_center,
        circle_radius=circle_radius
    )

    # Nếu không bắt được mặt trong vòng tròn, dự phòng quét toàn bộ khung hình
    if result.get("match_status") in ["FACE_NOT_FOUND", "DISTANCE_ISSUE", "ERROR"]:
        fallback_res = matcher.verify_live_frame(live_frame)
        if fallback_res.get("match_status") in ["MATCHED", "NOT_MATCHED"]:
            result = fallback_res

    match_status = result.get("match_status", "ERROR")
    distance = float(result.get("distance", 1.0))
    strict_threshold = 0.50
    is_match = (match_status == "MATCHED") and (distance <= strict_threshold)
    confidence = max(0.0, 1.0 - (distance / (strict_threshold * 1.5))) if is_match else 0.0

    return MatchFaceResponse(
        match_status=match_status,
        is_match=is_match,
        distance=round(distance, 4),
        confidence_score=round(confidence, 4),
        threshold_used=strict_threshold,
        user_instruction=result.get("user_instruction", "")
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
