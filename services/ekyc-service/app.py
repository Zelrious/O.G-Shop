import base64
import binascii
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
    if not GEMINI_ENABLED:
        return None
    if gemini_processor is None:
        try:
            gemini_processor = GeminiPostProcessor()
        except Exception:
            gemini_processor = None
    return gemini_processor


def get_face_matcher() -> FaceMatcher:
    global face_matcher
    if face_matcher is None:
        face_matcher = FaceMatcher(model_name="ArcFace", detector_backend="retinaface")
    return face_matcher


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


class MatchFaceRequest(BaseModel):
    card_image_base64: str = Field(min_length=16, max_length=8 * 1024 * 1024)
    live_frame_base64: str = Field(min_length=16, max_length=8 * 1024 * 1024)
    circle_center: List[int] = Field(default_factory=lambda: [320, 240], min_length=2, max_length=2)
    circle_radius: int = Field(default=140, ge=50, le=1000)


class MatchFaceResponse(BaseModel):
    match_status: str
    is_match: bool
    distance: float
    confidence_score: float
    threshold_used: float = 0.50
    user_instruction: str
    model_name: str = "ArcFace"
    model_version: str = "deepface-runtime"
    is_simulated: bool = True


def decode_base64_to_cv2(value: str) -> np.ndarray:
    encoded = value.split(",", 1)[1] if "," in value else value
    try:
        image_bytes = base64.b64decode(encoded, validate=True)
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
    )


@app.get("/api/v1/ekyc/health", response_model=HealthResponse)
def health_check() -> HealthResponse:
    return HealthResponse()


@app.post("/api/v1/ekyc/ocr", response_model=OcrResponse, dependencies=[Depends(require_internal_token)])
async def process_cccd_ocr(
    file: UploadFile = File(...),
    is_synthetic: bool = Form(False),
) -> OcrResponse:
    content = await read_upload(file)
    suffix = Path(file.filename or "identity-card.jpg").suffix.lower()
    if suffix not in {".jpg", ".jpeg", ".png", ".webp"}:
        suffix = ".jpg"
    temp_path: Optional[str] = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temporary:
            temporary.write(content)
            temp_path = temporary.name
        result = get_ocr_processor().process_cccd(temp_path)
        if result.get("status") != "SUCCESS":
            return OcrResponse(
                status="ERROR",
                error_message="Không thể bóc tách thẻ căn cước.",
                image_quality_check=result.get("image_quality_check"),
                extracted_data=ExtractedData(),
            )
        gemini = get_gemini_processor()
        if gemini is not None and is_synthetic and SYNTHETIC_ONLY:
            try:
                result = gemini.process(result)
            except Exception:
                pass
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


@app.post("/api/v1/ekyc/match-face", response_model=MatchFaceResponse, dependencies=[Depends(require_internal_token)])
def match_live_face(request: MatchFaceRequest) -> MatchFaceResponse:
    matcher = get_face_matcher()
    card_image = decode_base64_to_cv2(request.card_image_base64)
    live_frame = decode_base64_to_cv2(request.live_frame_base64)
    card_embedding = matcher.extract_embedding(card_image, detector_backend="retinaface")
    if card_embedding is None:
        return MatchFaceResponse(
            match_status="ERROR", is_match=False, distance=1.0, confidence_score=0.0,
            user_instruction="Không tìm thấy khuôn mặt rõ ràng trên thẻ.",
        )
    result = matcher.process_live_frame(
        frame=live_frame,
        reference_embedding=card_embedding,
        circle_center=tuple(request.circle_center),
        circle_radius=request.circle_radius,
    )
    if result.get("match_status") in {"FACE_NOT_FOUND", "DISTANCE_ISSUE", "ERROR"}:
        fallback = matcher.verify_live_frame(live_frame, card_embedding)
        if fallback.get("match_status") in {"MATCHED", "NOT_MATCHED"}:
            result = fallback
    match_status = result.get("match_status", "ERROR")
    distance = float(result.get("distance", 1.0))
    threshold = 0.50
    is_match = match_status == "MATCHED" and distance <= threshold
    confidence = max(0.0, 1.0 - (distance / (threshold * 1.5))) if is_match else 0.0
    return MatchFaceResponse(
        match_status=match_status,
        is_match=is_match,
        distance=round(distance, 4),
        confidence_score=round(confidence, 4),
        threshold_used=threshold,
        user_instruction=result.get("user_instruction", ""),
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=8001)
