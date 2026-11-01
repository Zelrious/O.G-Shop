import sys
import os
from pathlib import Path
import cv2
import numpy as np
import pytest

SERVICE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SERVICE_ROOT))

from cccd_module.back_card_processor import BackCardProcessor
from cccd_module.mrz_parser import MRZValidator, MRZData


@pytest.fixture
def synthetic_back_card_path(tmp_path):
    """
    Tạo fixture ảnh mặt sau CCCD tổng hợp an toàn trong bộ nhớ (100% synthetic, zero PII),
    có đầy đủ đặc trưng màu sắc/vật lý để vượt qua verify_is_cccd_back.
    """
    h, w = BackCardProcessor.CANONICAL_HEIGHT, BackCardProcessor.CANONICAL_WIDTH
    card = np.full((h, w, 3), 180, dtype=np.uint8)

    # 1. Chip ROI (y: 20-65%, x: 6-38%) - vàng kim
    chip_y1, chip_y2 = int(h * 0.20), int(h * 0.65)
    chip_x1, chip_x2 = int(w * 0.06), int(w * 0.38)
    card[chip_y1:chip_y2, chip_x1:chip_x2] = [20, 190, 220]

    # 2. Red seal ROI (y: 20-70%, x: 22-58%) - con dấu đỏ
    seal_y1, seal_y2 = int(h * 0.20), int(h * 0.70)
    seal_x1, seal_x2 = int(w * 0.22), int(w * 0.58)
    card[seal_y1:seal_y2, seal_x1:seal_x2] = [30, 30, 210]

    # 3. Fingerprint ROI (y: 5-55%, x: 50-98%) - nhiễu vân tay
    fp_y1, fp_y2 = int(h * 0.05), int(h * 0.55)
    fp_x1, fp_x2 = int(w * 0.50), int(w * 0.98)
    rng = np.random.RandomState(42)
    card[fp_y1:fp_y2, fp_x1:fp_x2] = rng.randint(40, 220, (fp_y2 - fp_y1, fp_x2 - fp_x1, 3), dtype=np.uint8)

    # 4. MRZ ROI (y: 65-100%, x: 0-100%) - dải biên cạnh ký tự
    mrz_y1, mrz_y2 = int(h * 0.65), h
    mrz_block = np.full((mrz_y2 - mrz_y1, w, 3), 190, dtype=np.uint8)
    for row in range(10, mrz_y2 - mrz_y1 - 10, 8):
        mrz_block[row:row + 3, :] = 20
    card[mrz_y1:mrz_y2, :] = mrz_block

    img_path = str(tmp_path / "synthetic_cccd_back.jpg")
    cv2.imwrite(img_path, card)
    return img_path


def test_detect_and_align_card(synthetic_back_card_path):
    img = BackCardProcessor.read_image_safe(synthetic_back_card_path)
    assert img is not None
    aligned, _ = BackCardProcessor.detect_and_align_card(img)
    assert aligned.shape[1] == BackCardProcessor.CANONICAL_WIDTH
    assert aligned.shape[0] == BackCardProcessor.CANONICAL_HEIGHT


def test_orientation_correction_on_rotations(synthetic_back_card_path):
    img = BackCardProcessor.read_image_safe(synthetic_back_card_path)
    assert img is not None

    # Test với ảnh quay 90 độ
    rot_90 = cv2.rotate(img, cv2.ROTATE_90_CLOCKWISE)
    upright, angle = BackCardProcessor.correct_orientation(rot_90)
    assert upright.shape[1] == BackCardProcessor.CANONICAL_WIDTH
    assert upright.shape[0] == BackCardProcessor.CANONICAL_HEIGHT

    # Test với ảnh quay 180 độ
    rot_180 = cv2.rotate(img, cv2.ROTATE_180)
    upright_180, angle_180 = BackCardProcessor.correct_orientation(rot_180)
    assert upright_180.shape[1] == BackCardProcessor.CANONICAL_WIDTH
    assert upright_180.shape[0] == BackCardProcessor.CANONICAL_HEIGHT


def test_reject_random_landscape_image(tmp_path):
    landscape = np.zeros((630, 1000, 3), dtype=np.uint8)
    landscape[:315, :] = [235, 180, 100]  # Bầu trời
    landscape[315:, :] = [34, 139, 34]    # Đồng cỏ

    temp_path = str(tmp_path / "test_temp_landscape.jpg")
    cv2.imwrite(temp_path, landscape)

    front_data = {
        "so_cccd": "001306000003",
        "ho_va_ten": "NGUYEN VAN A",
        "ngay_sinh": "15/01/2006",
        "gioi_tinh": "Nam",
    }
    res = BackCardProcessor.process_back_card(temp_path, front_data)
    assert res["success"] is False
    assert "không phải là mặt sau thẻ Căn cước công dân" in res["error_message"] or "không phát hiện được chip" in res["error_message"]


def test_reject_front_cccd_with_face_portrait(tmp_path):
    img = np.full((630, 1000, 3), 200, dtype=np.uint8)
    temp_path = str(tmp_path / "test_temp_blank.jpg")
    cv2.imwrite(temp_path, img)

    front_data = {"so_cccd": "001306000003"}
    res = BackCardProcessor.process_back_card(temp_path, front_data)
    assert res["success"] is False


def test_mrz_valid_matching_front_gemini_success(synthetic_back_card_path, monkeypatch):
    """Kịch bản 1: OCR trả MRZ đúng, front khớp -> Thành công (success: True)."""
    mock_lines = [
        "IDVNM0000000003001306000003<<3",
        "0605287F3505283VNM<<<<<<<<<<<8",
        "DANG<<THI<MAI<<<<<<<<<<<<<<<<<",
    ]
    monkeypatch.setattr(MRZValidator, "extract_mrz_from_image", lambda *args, **kwargs: (True, mock_lines, ""))
    mock_processor = type("MockGemini", (), {"client": object(), "model_name": "mock"})()

    matching_front_data = {
        "so_cccd": "001306000003",
        "ho_va_ten": "ĐẶNG THỊ MAI",
        "ngay_sinh": "28/05/2006",
        "gioi_tinh": "Nữ",
    }
    res = BackCardProcessor.process_back_card(synthetic_back_card_path, matching_front_data, gemini_processor=mock_processor)
    assert res["success"] is True
    assert "mrz_data" in res
    assert res["mrz_data"]["so_cccd"] == "001306000003"
    assert res["cross_check"]["so_cccd_matched"] is True
    assert res["cross_check"]["ho_va_ten_matched"] is True
    assert res["cross_check"]["ngay_sinh_matched"] is True
    assert res["cross_check"]["gioi_tinh_matched"] is True


def test_mrz_cccd_mismatch_rejected_gemini(synthetic_back_card_path, monkeypatch):
    """Kịch bản 2: OCR trả MRZ khác số front -> Bị từ chối vì mismatch số CCCD."""
    mock_lines = [
        "IDVNM0000000003001306000003<<3",
        "0605287F3505283VNM<<<<<<<<<<<8",
        "DANG<<THI<MAI<<<<<<<<<<<<<<<<<",
    ]
    monkeypatch.setattr(MRZValidator, "extract_mrz_from_image", lambda *args, **kwargs: (True, mock_lines, ""))
    mock_processor = type("MockGemini", (), {"client": object(), "model_name": "mock"})()

    mismatched_front_data = {
        "so_cccd": "001206000002",
        "ho_va_ten": "NGUYEN VAN A",
        "ngay_sinh": "21/02/2006",
        "gioi_tinh": "Nam",
    }
    res = BackCardProcessor.process_back_card(synthetic_back_card_path, mismatched_front_data, gemini_processor=mock_processor)
    assert res["success"] is False
    assert "không trùng khớp với số CCCD mặt trước" in res["error_message"]
    assert "001306000003" in res["error_message"]
    assert "001206000002" in res["error_message"]


def test_process_back_card_does_not_modify_source_image(synthetic_back_card_path, monkeypatch):
    mock_lines = iter([
        "IDVNM0000000003001306000003<<3",
        "0605287F3505283VNM<<<<<<<<<<<8",
        "DANG<<THI<MAI<<<<<<<<<<<<<<<<<",
    ])
    mock_vietocr = type("MockVietOcr", (), {"predict": lambda self, img: next(mock_lines)})()
    monkeypatch.setattr(BackCardProcessor, "get_vietocr_model", classmethod(lambda cls: mock_vietocr))
    monkeypatch.setattr(BackCardProcessor, "verify_is_cccd_back", classmethod(lambda cls, image: (True, "", {})))
    monkeypatch.setattr(BackCardProcessor, "correct_orientation", classmethod(lambda cls, image: (image, 0)))
    source_before = Path(synthetic_back_card_path).read_bytes()
    front_data = {
        "so_cccd": "001306000003",
        "ho_va_ten": "ĐẶNG THỊ MAI",
        "ngay_sinh": "28/05/2006",
        "gioi_tinh": "Nữ",
    }

    result = BackCardProcessor.process_back_card(synthetic_back_card_path, front_data)

    assert result["success"] is True
    assert Path(synthetic_back_card_path).read_bytes() == source_before


def test_mrz_cccd_mismatch_offline_rejected(synthetic_back_card_path, monkeypatch):
    """Offline OCR parses complete MRZ then rejects a different front ID."""
    mock_lines = iter([
        "IDVNM0000000003001306000003<<3",
        "0605287F3505283VNM<<<<<<<<<<<8",
        "DANG<<THI<MAI<<<<<<<<<<<<<<<<<",
    ])
    mock_vietocr = type("MockVietOcr", (), {"predict": lambda self, img: next(mock_lines)})()
    monkeypatch.setattr(BackCardProcessor, "get_vietocr_model", classmethod(lambda cls: mock_vietocr))

    mismatched_front = {
        "so_cccd": "001206000002",
        "ho_va_ten": "NGUYEN VAN A",
        "ngay_sinh": "21/02/2006",
        "gioi_tinh": "Nam",
    }
    res = BackCardProcessor.process_back_card(synthetic_back_card_path, mismatched_front, allow_synthetic=False)
    assert res["success"] is False
    assert "không trùng khớp với số CCCD mặt trước" in res["error_message"]
    assert "001306000003" in res["error_message"]
    assert "001206000002" in res["error_message"]


def test_offline_mrz_reads_and_cross_checks_all_three_lines(synthetic_back_card_path, monkeypatch):
    """Offline VietOCR must read and validate all MRZ lines before success."""
    mock_lines = iter([
        "IDVNM0000000003001306000003<<3",
        "0605287F3505283VNM<<<<<<<<<<<8",
        "DANG<<THI<MAI<<<<<<<<<<<<<<<<<",
    ])
    mock_vietocr = type("MockVietOcr", (), {"predict": lambda self, img: next(mock_lines)})()
    monkeypatch.setattr(BackCardProcessor, "get_vietocr_model", classmethod(lambda cls: mock_vietocr))

    front_data = {
        "so_cccd": "001306000003",
        "ho_va_ten": "ĐẶNG THỊ MAI",
        "ngay_sinh": "28/05/2006",
        "gioi_tinh": "Nữ",
    }
    monkeypatch.setattr(BackCardProcessor, "verify_is_cccd_back", classmethod(lambda cls, image: (True, "", {})))
    monkeypatch.setattr(BackCardProcessor, "correct_orientation", classmethod(lambda cls, image: (image, 0)))
    res = BackCardProcessor.process_back_card(synthetic_back_card_path, front_data, allow_synthetic=False)
    assert res["success"] is True
    assert res["mrz_data"]["so_cccd"] == "001306000003"
    assert res["mrz_data"]["ngay_sinh"] == "28/05/2006"
    assert res["mrz_data"]["gioi_tinh"] == "Nữ"
    assert res["mrz_data"]["ho_va_ten"] == "DANG THI MAI"
    assert res["cross_check"] == {
        "so_cccd_matched": True,
        "ho_va_ten_matched": True,
        "ngay_sinh_matched": True,
        "gioi_tinh_matched": True,
    }


def test_offline_mrz_unreadable_still_verifies_card_back_features(synthetic_back_card_path, monkeypatch):
    """Khi dải mã MRZ offline chưa đọc được, hệ thống vẫn công nhận ảnh mặt sau hợp lệ do đạt chip/dấu/vân tay."""
    mock_lines = iter([
        "IDVNM0000000003001306000003<<3",
        "0605287F3505283VNM<<<<<<<<<<<8",
        "",
    ])
    mock_vietocr = type("MockVietOcr", (), {"predict": lambda self, img: next(mock_lines)})()
    monkeypatch.setattr(BackCardProcessor, "get_vietocr_model", classmethod(lambda cls: mock_vietocr))
    front_data = {
        "so_cccd": "001306000003",
        "ho_va_ten": "ĐẶNG THỊ MAI",
        "ngay_sinh": "28/05/2006",
        "gioi_tinh": "Nữ",
    }

    res = BackCardProcessor.process_back_card(synthetic_back_card_path, front_data)

    assert res["success"] is True
    assert res["card_verified"] is True
    assert res["mrz_verified"] is False


def test_gemini_invalid_structured_response_fallback_to_offline(synthetic_back_card_path, monkeypatch):
    """
    Kịch bản 5: Gemini trả về 3 dòng nhưng sai cấu trúc:
    Cơ chế điều phối phát hiện dòng sai cấu trúc, chuyển sang offline fallback và xác nhận đặc trưng thẻ mặt sau.
    """
    mock_corrupted_lines = [
        "INVALID_LINE_1_SAMPLE",
        "INVALID_LINE_2_SAMPLE",
        "INVALID_LINE_3_SAMPLE",
    ]
    monkeypatch.setattr(MRZValidator, "extract_mrz_from_image", lambda *args, **kwargs: (True, mock_corrupted_lines, ""))
    mock_processor = type("MockGemini", (), {"client": object(), "model_name": "mock"})()

    # Offline VietOCR cũng không đọc được
    mock_vietocr = type("MockVietOcr", (), {"predict": lambda self, img: ""})()
    monkeypatch.setattr(BackCardProcessor, "get_vietocr_model", classmethod(lambda cls: mock_vietocr))

    front_data = {
        "so_cccd": "001306000003",
        "ho_va_ten": "ĐẶNG THỊ MAI",
        "ngay_sinh": "28/05/2006",
        "gioi_tinh": "Nữ",
    }
    res = BackCardProcessor.process_back_card(synthetic_back_card_path, front_data, gemini_processor=mock_processor)
    assert res["success"] is True
    assert res["card_verified"] is True
    assert res["mrz_verified"] is False


def test_mrz_optical_unrecognized_characters_error(synthetic_back_card_path, monkeypatch):
    """Kịch bản 6: OCR không nạp được nhưng thẻ đạt kiểm định chip/dấu/vân tay -> Thành công xác thực mặt sau."""
    monkeypatch.setattr(BackCardProcessor, "get_vietocr_model", classmethod(lambda cls: None))
    front_data = {"so_cccd": "001306000003"}
    res = BackCardProcessor.process_back_card(synthetic_back_card_path, front_data, allow_synthetic=False)
    assert res["success"] is True
    assert res["card_verified"] is True
    assert res["mrz_verified"] is False


def test_mrz_optical_blurry_error():
    h, w = 630, 1000
    card = np.full((h, w, 3), 180, dtype=np.uint8)
    card[int(h * 0.65):h, :] = cv2.GaussianBlur(
        np.random.randint(80, 140, (h - int(h * 0.65), w, 3), dtype=np.uint8),
        (35, 35),
        10,
    )
    ok, lines, err = BackCardProcessor.extract_mrz_lines(card, "dummy.jpg")
    assert ok is False
    assert err == "Ảnh chụp dải mã MRZ ở đáy thẻ bị mờ hoặc rung tay. Vui lòng giữ chắc camera và chụp lại rõ nét phần đáy thẻ."


def test_mrz_optical_glare_error():
    h, w = 630, 1000
    card = np.full((h, w, 3), 180, dtype=np.uint8)
    mrz_noise = np.random.randint(50, 180, (h - int(h * 0.65), w, 3), dtype=np.uint8)
    mrz_noise[:int(mrz_noise.shape[0] * 0.4), :int(w * 0.5)] = [255, 255, 255]
    card[int(h * 0.65):h, :] = mrz_noise
    ok, lines, err = BackCardProcessor.extract_mrz_lines(card, "dummy.jpg")
    assert ok is False
    assert err == "Dải mã MRZ ở đáy thẻ bị lóa sáng hoặc chói đèn flash. Vui lòng chụp ở nơi không bị ánh đèn phản chiếu trực tiếp."


def test_mrz_optical_dark_error():
    h, w = 630, 1000
    card = np.full((h, w, 3), 180, dtype=np.uint8)
    card[int(h * 0.65):h, :] = np.random.randint(10, 50, (h - int(h * 0.65), w, 3), dtype=np.uint8)
    ok, lines, err = BackCardProcessor.extract_mrz_lines(card, "dummy.jpg")
    assert ok is False
    assert err == "Dải mã MRZ ở đáy thẻ quá tối, không thể đọc rõ ký tự. Vui lòng chụp ở nơi đủ ánh sáng."


def test_mrz_optical_missing_edge_error():
    h, w = 630, 1000
    card = np.full((h, w, 3), 180, dtype=np.uint8)
    mrz_stripes = np.full((h - int(h * 0.65), w, 3), 120, dtype=np.uint8)
    for c in range(0, w, 6):
        mrz_stripes[:, c:c + 3] = 160
    card[int(h * 0.65):h, :] = mrz_stripes
    ok, lines, err = BackCardProcessor.extract_mrz_lines(card, "dummy.jpg")
    assert ok is False
    assert err == "Không phát hiện được dải ký tự mã vạch MRZ ở đáy thẻ. Vui lòng chụp trọn vẹn mặt sau thẻ, không bị che khuất hoặc cắt góc."
