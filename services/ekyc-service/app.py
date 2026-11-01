import base64
import binascii
import hashlib
import os
import sys
import tempfile
from pathlib import Path
from typing import Any, Dict, List, Optional

CURRENT_DIR = Path(__file__).resolve().parent
if str(CURRENT_DIR) not in sys.path:
    sys.path.insert(0, str(CURRENT_DIR))

from dotenv import load_dotenv

load_dotenv(CURRENT_DIR / ".env")

import cv2
import numpy as np
from fastapi import Depends, FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from biometric_module.face_matcher import FaceMatcher
from cccd_module.ai_post_processing import GeminiPostProcessor
from cccd_module.cccd_extractor import CCCDExtractionError, CCCDProcessor
from cccd_module.mrz_parser import MRZValidator
from cccd_module.back_card_processor import BackCardProcessor
from internal_auth import require_internal_token

MAX_FILE_SIZE = int(os.getenv("MAX_FILE_SIZE_MB", "4")) * 1024 * 1024
ALLOWED_CONTENT_TYPES = {"image/jpeg", "image/png", "image/webp"}
GEMINI_ENABLED = os.getenv("EKYC_GEMINI_POSTPROCESSING_ENABLED", "false").lower() == "true"
SYNTHETIC_ONLY = os.getenv("EKYC_ALLOW_SYNTHETIC_DATA_ONLY", "true").lower() == "true"
ALLOWED_ORIGINS = [
    value.strip()
    for value in os.getenv("EKYC_ALLOWED_ORIGINS", "http://localhost:8080").split(",")
    if value.strip()
]

app = FastAPI(
    title="O.G Shop eKYC AI Microservice",
    description="Internal-only simulated eKYC OCR and face matching service",
    version="2.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=False,
    allow_methods=["GET", "POST"],
    allow_headers=["Content-Type", "X-Internal-Token"],
)

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
        except Exception:
            gemini_processor = None
    return gemini_processor


def get_face_matcher() -> FaceMatcher:
    global face_matcher
    if face_matcher is None:
        face_matcher = FaceMatcher(model_name="ArcFace", detector_backend="yunet")
    return face_matcher


@app.on_event("startup")
def preload_models():
    try:
        get_ocr_processor()
    except Exception as e:
        print(f"[Startup] Preload OCR processor error: {e}")
    try:
        matcher = get_face_matcher()
        dummy = np.zeros((112, 112, 3), dtype=np.uint8)
        DeepFace.represent(dummy, model_name=matcher.model_name, detector_backend="skip", enforce_detection=False, align=False)
        print("[Startup] eKYC AI models preloaded successfully.")
    except Exception as e:
        print(f"[Startup] Preload FaceMatcher error: {e}")


class HealthResponse(BaseModel):
    status: str = "healthy"
    service: str = "ogshop-ekyc-service"
    version: str = "2.0.0"
    gemini_post_processing_enabled: bool = GEMINI_ENABLED


class ExtractedData(BaseModel):
    so_cccd: str = ""
    ho_va_ten: str = ""
    ngay_sinh: str = ""
    gioi_tinh: str = ""
    quoc_tich: str = "Việt Nam"
    que_quan: str = ""
    noi_thuong_tru: str = ""
    ngay_het_han: str = ""
    ai_standardized: bool = False
    mrz_verified: bool = False
    mrz_details: Optional[Dict[str, Any]] = None


class QualityCheck(BaseModel):
    is_clear: bool = True
    is_full_document_visible: bool = True
    is_glare_free: bool = True
    rejection_reason: str = ""


class OcrResponse(BaseModel):
    status: str
    error_code: Optional[str] = None
    error_message: str = ""
    image_quality_check: Optional[QualityCheck] = None
    extracted_data: ExtractedData


class MatchFaceRequest(BaseModel):
    card_image_base64: str = Field(min_length=16, max_length=8 * 1024 * 1024)
    live_frame_base64: str = Field(min_length=16, max_length=8 * 1024 * 1024)
    circle_center: List[int] = Field(default_factory=lambda: [320, 240], min_length=2, max_length=2)
    circle_radius: int = Field(default=140, ge=50, le=1000)
    portrait_image_base64: Optional[str] = Field(default=None, max_length=8 * 1024 * 1024)
    session_id: Optional[str] = Field(default=None, max_length=128)
    attempt_id: Optional[str] = Field(default=None, max_length=128)


class MatchFaceResponse(BaseModel):
    match_status: str
    is_match: bool
    distance_card: float = 1.0
    distance_portrait: float = 1.0
    distance: float = 1.0
    confidence_score: float = 0.0
    threshold_used: float = 0.50
    threshold_card: float = 0.50
    threshold_portrait: float = 0.45
    card_matched: bool = False
    portrait_matched: bool = False
    liveness_status: str = "NOT_CHECKED"
    frames_collected: int = 1
    user_instruction: str = ""
    model_name: str = "ArcFace"
    model_version: str = "deepface-runtime"
    is_simulated: bool = True
    frame_status: str = ""
    directional_cue: str = ""


def decode_base64_to_cv2(value: str) -> np.ndarray:
    encoded = value.split(",", 1)[1] if "," in value else value
    encoded = "".join(encoded.split())
    try:
        image_bytes = base64.b64decode(encoded)
    except (binascii.Error, ValueError) as exception:
        raise HTTPException(status_code=400, detail="Invalid base64 image.") from exception
    if not image_bytes or len(image_bytes) > MAX_FILE_SIZE:
        raise HTTPException(status_code=413, detail="Image is empty or exceeds the configured limit.")
    image = cv2.imdecode(np.frombuffer(image_bytes, np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        raise HTTPException(status_code=400, detail="Image cannot be decoded.")
    return image


async def read_upload(file: UploadFile) -> bytes:
    if file.content_type not in ALLOWED_CONTENT_TYPES:
        raise HTTPException(status_code=415, detail="Unsupported image content type.")
    content = await file.read(MAX_FILE_SIZE + 1)
    if not content or len(content) > MAX_FILE_SIZE:
        raise HTTPException(status_code=413, detail="Image is empty or exceeds the configured limit.")
    return content


def extracted_data(raw_data: Dict[str, Any]) -> ExtractedData:
    return ExtractedData(
        so_cccd=str(raw_data.get("so_cccd", "")),
        ho_va_ten=str(raw_data.get("ho_va_ten", "")),
        ngay_sinh=str(raw_data.get("ngay_sinh", "")),
        gioi_tinh=str(raw_data.get("gioi_tinh", "")),
        quoc_tich=str(raw_data.get("quoc_tich", "Việt Nam")),
        que_quan=str(raw_data.get("que_quan", "")),
        noi_thuong_tru=str(raw_data.get("noi_thuong_tru", "")),
        ngay_het_han=str(raw_data.get("ngay_het_han", "")),
        ai_standardized=bool(raw_data.get("ai_standardized", False)),
        mrz_verified=bool(raw_data.get("mrz_verified", False)),
        mrz_details=raw_data.get("mrz_details"),
    )


@app.get("/api/v1/ekyc/health", response_model=HealthResponse)
def health_check() -> HealthResponse:
    return HealthResponse()


@app.post("/api/v1/ekyc/ocr", response_model=OcrResponse, dependencies=[Depends(require_internal_token)])
async def process_cccd_ocr(
    file: UploadFile = File(...),
    back_file: Optional[UploadFile] = File(None),
    portrait_file: Optional[UploadFile] = File(None),
    is_synthetic: bool = Form(False),
) -> OcrResponse:
    content = await read_upload(file)
    suffix = Path(file.filename or "identity-card.jpg").suffix.lower()
    if suffix not in {".jpg", ".jpeg", ".png", ".webp"}:
        suffix = ".jpg"
    temp_path: Optional[str] = None
    back_temp_path: Optional[str] = None
    portrait_temp_path: Optional[str] = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temporary:
            temporary.write(content)
            temp_path = temporary.name

        # -------------------------------------------------------------
        # 1. ƯU TIÊN 1: Kiểm tra ảnh CCCD mặt trước hợp lệ
        # -------------------------------------------------------------
        result = get_ocr_processor().process_cccd(temp_path)
        if result.get("status") != "SUCCESS":
            err_msg = result.get("error_message") or "Ảnh CCCD mặt trước không hợp lệ. Vui lòng chụp rõ nét 4 góc, đủ thông tin và không bị chói sáng hoặc che khuất."
            err_code = "FRONT_CCCD_BLURRY" if "mờ" in err_msg.lower() or "blurry" in err_msg.lower() else "FRONT_CCCD_NOT_RECOGNIZED"
            return OcrResponse(
                status="ERROR",
                error_code=err_code,
                error_message=err_msg,
                image_quality_check=result.get("image_quality_check"),
                extracted_data=ExtractedData(),
            )
        gemini = get_gemini_processor()
        if gemini is not None:
            try:
                result = gemini.process(result)
            except Exception:
                pass

        # -------------------------------------------------------------
        # 2. ƯU TIÊN 2: Kiểm tra ảnh CCCD mặt sau hợp lệ
        # (Không có các đặc điểm của mặt sau CCCD, bỏ qua kiểm tra khớp dữ liệu với mặt trước)
        # -------------------------------------------------------------
        if back_file is None or not back_file.filename:
            return OcrResponse(
                status="ERROR",
                error_code="BACK_CCCD_MISSING",
                error_message="Ảnh CCCD mặt sau không hợp lệ. Vui lòng tải lên ảnh mặt sau của thẻ CCCD gắn chip.",
                image_quality_check=result.get("image_quality_check"),
                extracted_data=ExtractedData(),
            )

        back_content = await read_upload(back_file)
        if not back_content or len(back_content) == 0:
            return OcrResponse(
                status="ERROR",
                error_code="BACK_CCCD_MISSING",
                error_message="Ảnh CCCD mặt sau không hợp lệ. Tệp rỗng hoặc không đọc được.",
                image_quality_check=result.get("image_quality_check"),
                extracted_data=ExtractedData(),
            )

        back_suffix = Path(back_file.filename).suffix.lower()
        if back_suffix not in {".jpg", ".jpeg", ".png", ".webp"}:
            back_suffix = ".jpg"
        with tempfile.NamedTemporaryFile(delete=False, suffix=back_suffix) as back_temp:
            back_temp.write(back_content)
            back_temp_path = back_temp.name

        back_res = BackCardProcessor.process_back_card(
            image_path=back_temp_path,
            front_extracted_data=result.get("extracted_data", {}),
            gemini_processor=gemini,
            allow_synthetic=False,
            skip_cross_check=True,
        )

        if not back_res.get("success"):
            err_code = back_res.get("error_code") or "BACK_CCCD_INVALID"
            err_msg = back_res.get("error_message") or "Ảnh CCCD mặt sau không hợp lệ (không phát hiện được chip điện tử, con dấu hoặc đặc điểm mặt sau thẻ CCCD)."
            print(f"[app.py] Back card verification rejected: {err_code} - {err_msg}")
            return OcrResponse(
                status="ERROR",
                error_code=err_code,
                error_message=err_msg,
                image_quality_check=result.get("image_quality_check"),
                extracted_data=ExtractedData(),
            )

        extracted_dict = result.get("extracted_data", {})
        extracted_dict["card_verified"] = True
        extracted_dict["mrz_verified"] = bool(back_res.get("mrz_verified"))
        if back_res.get("mrz_data"):
            extracted_dict["mrz_details"] = back_res.get("mrz_data")
        result["extracted_data"] = extracted_dict

        # -------------------------------------------------------------
        # 3. ƯU TIÊN 3: Kiểm tra ảnh chân dung đưa vào hợp lệ
        # (Ảnh đưa vào không phải ảnh chân dung hợp lệ để xác thực khuôn mặt)
        # -------------------------------------------------------------
        if portrait_file is None or not portrait_file.filename:
            return OcrResponse(
                status="ERROR",
                error_code="PORTRAIT_MISSING",
                error_message="Ảnh chân dung đưa vào không hợp lệ. Vui lòng tải lên ảnh thẻ chân dung (3x4 hoặc 4x6).",
                image_quality_check=result.get("image_quality_check"),
                extracted_data=ExtractedData(),
            )

        portrait_content = await read_upload(portrait_file)
        if not portrait_content or len(portrait_content) == 0:
            return OcrResponse(
                status="ERROR",
                error_code="PORTRAIT_MISSING",
                error_message="Ảnh chân dung đưa vào không hợp lệ. Tệp rỗng hoặc không đọc được.",
                image_quality_check=result.get("image_quality_check"),
                extracted_data=ExtractedData(),
            )

        portrait_suffix = Path(portrait_file.filename).suffix.lower()
        if portrait_suffix not in {".jpg", ".jpeg", ".png", ".webp"}:
            portrait_suffix = ".jpg"
        with tempfile.NamedTemporaryFile(delete=False, suffix=portrait_suffix) as portrait_temp:
            portrait_temp.write(portrait_content)
            portrait_temp_path = portrait_temp.name

        matcher = get_face_matcher()
        portrait_val = matcher.validate_portrait_image(portrait_temp_path)
        if not portrait_val.get("valid"):
            err_code = portrait_val.get("error_code") or portrait_val.get("status") or "FACE_REGION_QUALITY_LOW"
            err_msg = portrait_val.get("error_message") or "Ảnh chân dung đưa vào không hợp lệ để xác thực khuôn mặt."
            print(f"[app.py] Portrait validation rejected: {err_code} - {err_msg}")
            return OcrResponse(
                status="ERROR",
                error_code=err_code,
                error_message=err_msg,
                image_quality_check=result.get("image_quality_check"),
                extracted_data=ExtractedData(),
            )

        # -------------------------------------------------------------
        # 4. ƯU TIÊN 4: Ảnh trên CCCD và ảnh chân dung không khớp nhau
        # -------------------------------------------------------------
        card_portrait_res = matcher.extract_cccd_portrait(temp_path)
        if card_portrait_res.get("status") != "SUCCESS" or card_portrait_res.get("embedding") is None:
            err_msg = card_portrait_res.get("error_message") or "Ảnh CCCD mặt trước không hợp lệ: Không tìm thấy ảnh chân dung rõ ràng trên thẻ CCCD để đối chiếu."
            print(f"[app.py] CCCD portrait extraction failed: {err_msg}")
            return OcrResponse(
                status="ERROR",
                error_code="FRONT_CCCD_NOT_RECOGNIZED",
                error_message=err_msg,
                image_quality_check=result.get("image_quality_check"),
                extracted_data=ExtractedData(),
            )

        card_embedding = card_portrait_res["embedding"]
        portrait_embedding = portrait_val["embedding"]
        match_ok, match_dist, match_code, match_err = matcher.match_card_and_portrait(card_embedding, portrait_embedding)
        if not match_ok:
            print(f"[app.py] Card vs Portrait mismatch: code={match_code}, distance={match_dist}, error={match_err}")
            return OcrResponse(
                status="ERROR",
                error_code="PORTRAIT_MISMATCH",
                error_message=match_err or "Ảnh trên thẻ CCCD và ảnh chân dung không khớp nhau. Vui lòng tải đúng ảnh chân dung chính chủ của người trên thẻ CCCD.",
                image_quality_check=result.get("image_quality_check"),
                extracted_data=ExtractedData(),
            )

        return OcrResponse(
            status="SUCCESS",
            image_quality_check=result.get("image_quality_check"),
            extracted_data=extracted_data(result.get("extracted_data", {})),
        )
    except CCCDExtractionError:
        return OcrResponse(status="ERROR", error_message="Không thể bóc tách thẻ căn cước.", extracted_data=ExtractedData())
    except HTTPException:
        raise
    except Exception as exception:
        raise HTTPException(status_code=422, detail="Image processing failed.") from exception
    finally:
        if temp_path and os.path.exists(temp_path):
            try:
                os.remove(temp_path)
            except OSError:
                pass
        if back_temp_path and os.path.exists(back_temp_path):
            try:
                os.remove(back_temp_path)
            except OSError:
                pass
        if portrait_temp_path and os.path.exists(portrait_temp_path):
            try:
                os.remove(portrait_temp_path)
            except OSError:
                pass


card_embedding_cache: Dict[str, np.ndarray] = {}


@app.post("/api/v1/ekyc/match-face", response_model=MatchFaceResponse, dependencies=[Depends(require_internal_token)])
def match_live_face(request: MatchFaceRequest) -> MatchFaceResponse:
    try:
        matcher = get_face_matcher()
        attempt_id = request.attempt_id or ""
        card_hash = hashlib.sha256(request.card_image_base64.encode("ascii", errors="ignore")).hexdigest()
        card_cache_key = f"{attempt_id}:{card_hash}" if attempt_id else card_hash
        card_embedding = card_embedding_cache.get(card_cache_key)

        if card_embedding is None:
            card_image = decode_base64_to_cv2(request.card_image_base64)
            card_portrait_res = matcher.extract_cccd_portrait(card_image)
            if card_portrait_res.get("status") == "SUCCESS" and card_portrait_res.get("embedding") is not None:
                card_embedding = card_portrait_res["embedding"]
            else:
                card_embedding = matcher.extract_embedding(card_image, detector_backend="retinaface")
                if card_embedding is None:
                    card_embedding = matcher.extract_embedding(card_image, detector_backend="opencv")

            if card_embedding is not None:
                if len(card_embedding_cache) > 200:
                    card_embedding_cache.clear()
                card_embedding_cache[card_cache_key] = card_embedding

        if card_embedding is None:
            return MatchFaceResponse(
                match_status="ERROR",
                is_match=False,
                distance=1.0,
                confidence_score=0.0,
                user_instruction="Không tìm thấy khuôn mặt rõ ràng trên ảnh thẻ CCCD. Vui lòng chụp lại ảnh mặt trước thẻ rõ nét hơn.",
            )

        portrait_embedding = None
        portrait_hash = ""
        if request.portrait_image_base64 and len(request.portrait_image_base64) > 100:
            portrait_hash = hashlib.sha256(request.portrait_image_base64.encode("ascii", errors="ignore")).hexdigest()
            portrait_cache_key = f"{attempt_id}:{portrait_hash}" if attempt_id else portrait_hash
            portrait_embedding = card_embedding_cache.get(portrait_cache_key)
            if portrait_embedding is None:
                portrait_image = decode_base64_to_cv2(request.portrait_image_base64)
                portrait_val = matcher.validate_portrait_image(portrait_image)
                if portrait_val.get("valid") and portrait_val.get("embedding") is not None:
                    portrait_embedding = portrait_val["embedding"]
                else:
                    portrait_embedding = matcher.extract_embedding(portrait_image, detector_backend="yunet")
                    if portrait_embedding is None:
                        portrait_embedding = matcher.extract_embedding(portrait_image, detector_backend="retinaface")
                if portrait_embedding is not None:
                    card_embedding_cache[portrait_cache_key] = portrait_embedding

        if request.portrait_image_base64 and len(request.portrait_image_base64) > 100 and portrait_embedding is None:
            return MatchFaceResponse(
                match_status="ERROR",
                is_match=False,
                distance=1.0,
                confidence_score=0.0,
                user_instruction="Không thể trích xuất đặc trưng khuôn mặt từ ảnh chân dung tham chiếu. Vui lòng chụp lại ảnh chân dung rõ nét.",
            )

        live_frame = decode_base64_to_cv2(request.live_frame_base64)

        # Định danh session buffer để theo dõi nhiều frame liên tiếp
        session_id = request.session_id or f"{card_hash[:16]}_{portrait_hash[:16] if portrait_embedding is not None else 'card'}"

        if portrait_embedding is not None:
            result = matcher.process_live_frame_dual(
                frame=live_frame,
                reference_card_embedding=card_embedding,
                reference_portrait_embedding=portrait_embedding,
                circle_center=tuple(request.circle_center),
                circle_radius=request.circle_radius,
                session_id=session_id,
            )
        else:
            result = matcher.process_live_frame(
                frame=live_frame,
                reference_embedding=card_embedding,
                circle_center=tuple(request.circle_center),
                circle_radius=request.circle_radius,
                session_id=session_id,
            )

        match_status = result.get("match_status", "ERROR")
        is_match = bool(result.get("is_match", False))
        instruction = result.get("user_instruction", "")
        distance = float(result.get("distance", 1.0))
        distance_card = float(result.get("distance_card", distance))
        distance_portrait = float(result.get("distance_portrait", distance))
        confidence = float(result.get("confidence_score", 0.0))
        frame_status = result.get("frame_status", "")
        directional_cue = result.get("directional_cue", "")
        threshold_card = float(result.get("threshold_card", os.getenv("EKYC_LIVE_CARD_MATCH_THRESHOLD", "0.50")))
        threshold_portrait = float(result.get("threshold_portrait", os.getenv("EKYC_LIVE_PORTRAIT_MATCH_THRESHOLD", "0.45")))
        card_matched = bool(result.get("card_matched", False))
        portrait_matched = bool(result.get("portrait_matched", False))
        liveness_status = result.get("liveness_status", "NOT_CHECKED")
        frames_collected = int(result.get("frames_collected", 1))

        return MatchFaceResponse(
            match_status=match_status,
            is_match=is_match,
            distance_card=round(distance_card, 4),
            distance_portrait=round(distance_portrait, 4),
            distance=round(distance, 4),
            confidence_score=round(confidence, 4),
            threshold_used=threshold_card,
            threshold_card=threshold_card,
            threshold_portrait=threshold_portrait,
            card_matched=card_matched,
            portrait_matched=portrait_matched,
            liveness_status=liveness_status,
            frames_collected=frames_collected,
            user_instruction=instruction,
            frame_status=frame_status,
            directional_cue=directional_cue,
        )
    except HTTPException:
        raise
    except Exception as e:
        print(f"[match_live_face] Unexpected error: {e}")
        return MatchFaceResponse(
            match_status="ERROR",
            is_match=False,
            distance=1.0,
            confidence_score=0.0,
            threshold_used=float(os.getenv("EKYC_FACE_MATCH_THRESHOLD", "0.50")),
            user_instruction=f"Không thể xử lý đối chiếu khuôn mặt: {str(e)}",
        )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=8001)
