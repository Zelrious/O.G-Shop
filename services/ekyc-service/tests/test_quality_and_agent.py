import sys
from pathlib import Path
import numpy as np
import pytest
from unittest.mock import MagicMock, patch

SERVICE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SERVICE_ROOT))

from cccd_module.cccd_extractor import CCCDProcessor
from cccd_module.ai_post_processing import GeminiPostProcessor
from biometric_module.face_matcher import FaceMatcher

def test_check_image_quality_detects_blurry_image():
    # Tạo ảnh xám đều không có cạnh (Laplacian variance gần 0)
    processor = CCCDProcessor.__new__(CCCDProcessor)
    flat_image = np.ones((400, 600, 3), dtype=np.uint8) * 128
    
    quality = processor._check_image_quality(flat_image)
    assert quality["is_clear"] is False
    assert "bị mờ hoặc rung tay" in quality["rejection_reason"]

def test_check_image_quality_detects_dark_image():
    # Tạo ảnh gần như đen hoàn toàn (Mean brightness ~ 10 < 45)
    processor = CCCDProcessor.__new__(CCCDProcessor)
    dark_image = np.ones((400, 600, 3), dtype=np.uint8) * 10
    
    quality = processor._check_image_quality(dark_image)
    assert quality["is_clear"] is False
    assert "quá tối" in quality["rejection_reason"]

def test_gemini_post_processor_fallback_when_api_fails():
    with patch("cccd_module.ai_post_processing.genai.Client") as mock_client:
        mock_instance = MagicMock()
        mock_instance.models.generate_content.side_effect = RuntimeError("Network error")
        mock_client.return_value = mock_instance
        
        post_processor = GeminiPostProcessor(api_key="test-key")
        sample_ocr = {
            "status": "SUCCESS",
            "extracted_data": {
                "so_cccd": "001095012345",
                "ho_va_ten": "nguyen van an",
                "ngay_sinh": "15/08/1995",
                "gioi_tinh": "Nam",
                "que_quan": "Ha Noi",
                "noi_thuong_tru": "Quy Nhon Binh Dinh"
            }
        }
        
        result = post_processor.process(sample_ocr)
        assert result["status"] == "SUCCESS"
        assert result["extracted_data"]["so_cccd"] == "001095012345"
        assert result["extracted_data"]["ai_standardized"] is False

def test_face_matcher_detects_blurry_webcam_face():
    matcher = FaceMatcher()
    blurry_frame = np.ones((480, 640, 3), dtype=np.uint8) * 100
    ref_embedding = np.zeros(512, dtype=np.float32)
    
    mock_face = {
        "confidence": 0.95,
        "facial_area": {"x": 200, "y": 150, "w": 200, "h": 200}
    }
    with patch("deepface.DeepFace.extract_faces", return_value=[mock_face]):
        result = matcher.process_live_frame(
            frame=blurry_frame,
            reference_embedding=ref_embedding,
            circle_center=(300, 250),
            circle_radius=150
        )
        assert result["match_status"] == "IMAGE_QUALITY_ISSUE"
        assert "bị mờ hoặc rung tay" in result["user_instruction"]
