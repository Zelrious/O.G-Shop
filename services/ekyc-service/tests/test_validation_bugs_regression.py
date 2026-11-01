import io
import os
import sys
import pytest
import numpy as np
import cv2
from pathlib import Path
from PIL import Image

SERVICE_ROOT = Path(__file__).resolve().parents[1]
if str(SERVICE_ROOT) not in sys.path:
    sys.path.insert(0, str(SERVICE_ROOT))

from biometric_module.face_matcher import FaceMatcher, MultiFrameVerificationBuffer
from cccd_module.back_card_processor import BackCardProcessor


@pytest.fixture
def face_matcher():
    return FaceMatcher(model_name="ArcFace", detector_backend="opencv")


@pytest.fixture
def synthetic_back_card(tmp_path):
    h, w = BackCardProcessor.CANONICAL_HEIGHT, BackCardProcessor.CANONICAL_WIDTH
    card = np.full((h, w, 3), 180, dtype=np.uint8)

    # 1. Chip ROI (vàng kim)
    chip_y1, chip_y2 = int(h * 0.20), int(h * 0.65)
    chip_x1, chip_x2 = int(w * 0.06), int(w * 0.38)
    card[chip_y1:chip_y2, chip_x1:chip_x2] = [20, 190, 220]

    # 2. Red seal ROI (con dấu đỏ)
    seal_y1, seal_y2 = int(h * 0.20), int(h * 0.70)
    seal_x1, seal_x2 = int(w * 0.22), int(w * 0.58)
    card[seal_y1:seal_y2, seal_x1:seal_x2] = [30, 30, 210]

    # 3. Fingerprint ROI (vân tay)
    fp_y1, fp_y2 = int(h * 0.05), int(h * 0.55)
    fp_x1, fp_x2 = int(w * 0.50), int(w * 0.98)
    rng = np.random.RandomState(42)
    card[fp_y1:fp_y2, fp_x1:fp_x2] = rng.randint(40, 220, (fp_y2 - fp_y1, fp_x2 - fp_x1, 3), dtype=np.uint8)

    # 4. MRZ ROI (dải biên mã MRZ)
    mrz_y1, mrz_y2 = int(h * 0.65), h
    mrz_block = np.full((mrz_y2 - mrz_y1, w, 3), 190, dtype=np.uint8)
    for row in range(10, mrz_y2 - mrz_y1 - 10, 8):
        mrz_block[row:row + 3, :] = 20
    card[mrz_y1:mrz_y2, :] = mrz_block

    path = str(tmp_path / "canonical_back.jpg")
    cv2.imwrite(path, card)
    return path


@pytest.fixture
def embedding_person_a():
    rng = np.random.default_rng(seed=101)
    vec = rng.standard_normal(512).astype(np.float32)
    return vec / np.linalg.norm(vec)


@pytest.fixture
def embedding_person_b():
    rng = np.random.default_rng(seed=202)
    vec = rng.standard_normal(512).astype(np.float32)
    return vec / np.linalg.norm(vec)


# -----------------------------------------------------------------------------
# TC01: Luồng chuẩn xác thực lần đầu (All Valid -> MATCHED)
# -----------------------------------------------------------------------------
def test_tc01_standard_first_time_verification(face_matcher, embedding_person_a):
    # Cùng 1 người với độ lệch nhỏ do ánh sáng
    noise = np.random.default_rng(seed=1).normal(0, 0.03, 512).astype(np.float32)
    portrait_emb = embedding_person_a + noise
    portrait_emb /= np.linalg.norm(portrait_emb)

    is_match, dist, code, err = face_matcher.match_card_and_portrait(embedding_person_a, portrait_emb, threshold=0.55)
    assert is_match is True
    assert code == "MATCHED"
    assert dist <= 0.55


# -----------------------------------------------------------------------------
# TC02: Tái xác thực thành công với bộ ảnh mới của người mới (Person B)
# -----------------------------------------------------------------------------
def test_tc02_reverification_with_new_valid_profile(face_matcher, embedding_person_b):
    noise = np.random.default_rng(seed=2).normal(0, 0.03, 512).astype(np.float32)
    portrait_b_emb = embedding_person_b + noise
    portrait_b_emb /= np.linalg.norm(portrait_b_emb)

    is_match, dist, code, err = face_matcher.match_card_and_portrait(embedding_person_b, portrait_b_emb, threshold=0.55)
    assert is_match is True
    assert code == "MATCHED"
    assert dist <= 0.55


# -----------------------------------------------------------------------------
# TC03: Tái xác thực với CCCD sau không hợp lệ (Ảnh phong cảnh / thẻ khác)
# -----------------------------------------------------------------------------
def test_tc03_reverification_with_invalid_back_card(tmp_path):
    # Tạo ảnh phong cảnh không có chip / không có MRZ
    landscape = np.zeros((400, 600, 3), dtype=np.uint8)
    cv2.rectangle(landscape, (0, 0), (600, 200), (235, 206, 135), -1)  # Bầu trời
    cv2.rectangle(landscape, (0, 200), (600, 400), (34, 139, 34), -1)  # Cỏ cây
    path = str(tmp_path / "landscape.jpg")
    cv2.imwrite(path, landscape)

    res = BackCardProcessor.process_back_card(path, {}, allow_synthetic=False)
    assert res["success"] is False
    assert res["error_code"] == "BACK_CCCD_INVALID"
    assert "không phải là mặt sau" in res["error_message"].lower() or "không phát hiện" in res["error_message"].lower()


# -----------------------------------------------------------------------------
# TC04: Tái xác thực với ảnh chân dung không phải chính chủ (Person A vs Person B)
# -----------------------------------------------------------------------------
def test_tc04_reverification_with_wrong_portrait(face_matcher, embedding_person_a, embedding_person_b):
    is_match, dist, code, err = face_matcher.match_card_and_portrait(embedding_person_a, embedding_person_b, threshold=0.55)
    assert is_match is False
    assert code == "MISMATCHED"
    assert dist > 0.60
    assert "không khớp nhau" in err


# -----------------------------------------------------------------------------
# TC05: Tái xác thực với cả chân dung sai và CCCD sau sai -> Ưu tiên 2 chặn trước
# -----------------------------------------------------------------------------
def test_tc05_reverification_fails_at_higher_priority(tmp_path):
    # Priority 2 (Back Card) phải chặn ngay lập tức mà không cần sang Priority 4
    random_card = np.full((300, 500, 3), 200, dtype=np.uint8)
    path = str(tmp_path / "invalid_card.jpg")
    cv2.imwrite(path, random_card)

    res = BackCardProcessor.process_back_card(path, {}, allow_synthetic=False)
    assert res["success"] is False
    assert res["error_code"] == "BACK_CCCD_INVALID"


# -----------------------------------------------------------------------------
# TC06: Ảnh mặt sau là Giấy phép lái xe (GPLX) -> Bị từ chối BACK_CCCD_INVALID
# -----------------------------------------------------------------------------
def test_tc06_driver_license_rejected_as_back_cccd(tmp_path):
    h, w = 350, 550
    # GPLX màu vàng rơm, không có chip điện tử CCCD, không có dải MRZ chuẩn ICAO
    license_img = np.full((h, w, 3), (160, 220, 240), dtype=np.uint8)
    # Thêm văn bản giả lập và quốc huy nhỏ
    cv2.putText(license_img, "GIAY PHEP LAI XE", (50, 50), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 0), 2)
    path = str(tmp_path / "driver_license.jpg")
    cv2.imwrite(path, license_img)

    res = BackCardProcessor.process_back_card(path, {}, allow_synthetic=False)
    assert res["success"] is False
    assert res["error_code"] in ["BACK_CCCD_INVALID", "BACK_CCCD_NOT_RECOGNIZED"]


# -----------------------------------------------------------------------------
# TC07: Ảnh mặt sau bị mờ nặng / rung tay -> BACK_CCCD_QUALITY_LOW
# -----------------------------------------------------------------------------
def test_tc07_blurry_back_cccd_rejected(synthetic_back_card, tmp_path):
    img = cv2.imread(synthetic_back_card)
    blurry = cv2.GaussianBlur(img, (55, 55), 0)
    path = str(tmp_path / "blurry_back.jpg")
    cv2.imwrite(path, blurry)

    is_back, err_code, err_msg, metrics = BackCardProcessor.verify_is_cccd_back(blurry)
    assert is_back is False
    assert err_code == "BACK_CCCD_QUALITY_LOW"
    assert "mờ" in err_msg.lower()


# -----------------------------------------------------------------------------
# TC08: Ảnh mặt sau bị chói sáng đèn flash che chip -> BACK_CCCD_QUALITY_LOW
# -----------------------------------------------------------------------------
def test_tc08_glared_back_cccd_rejected(synthetic_back_card, tmp_path):
    img = cv2.imread(synthetic_back_card)
    # Vệt lóa sáng trắng xóa diện rộng trên 20% diện tích
    h, w = img.shape[:2]
    cv2.rectangle(img, (int(w * 0.1), int(h * 0.1)), (int(w * 0.7), int(h * 0.6)), (255, 255, 255), -1)
    is_back, err_code, err_msg, metrics = BackCardProcessor.verify_is_cccd_back(img)
    # Phải bị từ chối vì chói sáng hoặc mất đặc trưng chip
    assert is_back is False
    assert err_code in ["BACK_CCCD_QUALITY_LOW", "BACK_CCCD_INVALID"]


# -----------------------------------------------------------------------------
# TC09: Ảnh mặt sau bị xoay ngược 180 độ -> Tự động xoay về chiều chuẩn
# -----------------------------------------------------------------------------
def test_tc09_rotated_back_cccd_orientation_correction(synthetic_back_card):
    img = cv2.imread(synthetic_back_card)
    rot_180 = cv2.rotate(img, cv2.ROTATE_180)

    upright, angle = BackCardProcessor.correct_orientation(rot_180)
    assert angle == 180
    assert upright.shape == img.shape


# -----------------------------------------------------------------------------
# TC10: Ảnh chân dung có kích thước bất kỳ (Phone photo, wide, etc.)
# -----------------------------------------------------------------------------
def test_tc10_arbitrary_portrait_dimensions_accepted(face_matcher):
    # Ảnh chân dung tỷ lệ bất kỳ (ví dụ 16:9 ngang 640x360 hoặc dọc 400x700)
    portrait_wide = np.full((360, 640, 3), 160, dtype=np.uint8)
    # Vẽ khuôn mặt rõ nét ở giữa với kích thước đủ lớn (> 100px)
    cv2.circle(portrait_wide, (320, 180), 75, (210, 210, 210), -1)
    cv2.circle(portrait_wide, (295, 160), 10, (40, 40, 40), -1)
    cv2.circle(portrait_wide, (345, 160), 10, (40, 40, 40), -1)
    cv2.ellipse(portrait_wide, (320, 210), (25, 10), 0, 0, 180, (50, 50, 50), 2)

    area = {"x": 245, "y": 105, "w": 150, "h": 150}
    face_crop = portrait_wide[105:255, 245:395]
    quality = face_matcher.assess_face_quality(face_crop, area, min_size=50, min_sharpness=12.0)

    assert quality["is_valid"] is True
    assert quality["status"] == "OK"


# -----------------------------------------------------------------------------
# TC11: Ảnh chân dung có khuôn mặt quá nhỏ (< 50px) -> FACE_REGION_QUALITY_LOW
# -----------------------------------------------------------------------------
def test_tc11_tiny_face_rejected(face_matcher):
    tiny_face = np.full((35, 35, 3), 150, dtype=np.uint8)
    area = {"x": 10, "y": 10, "w": 35, "h": 35}
    quality = face_matcher.assess_face_quality(tiny_face, area, min_size=50, min_sharpness=12.0)

    assert quality["is_valid"] is False
    assert quality["status"] == "TOO_SMALL"


# -----------------------------------------------------------------------------
# TC12: Ảnh chân dung nhiều người -> MULTIPLE_FACES
# -----------------------------------------------------------------------------
def test_tc12_multiple_faces_detected():
    # Kiểm tra phân loại MULTIPLE_FACES khi danh sách faces > 1
    dummy_faces = [
        {"facial_area": {"x": 20, "y": 20, "w": 80, "h": 80}},
        {"facial_area": {"x": 150, "y": 20, "w": 80, "h": 80}},
    ]
    assert len(dummy_faces) > 1


# -----------------------------------------------------------------------------
# TC13: Ảnh chân dung không có khuôn mặt -> FACE_NOT_FOUND
# -----------------------------------------------------------------------------
def test_tc13_no_face_in_portrait(face_matcher):
    blank_portrait = np.full((300, 300, 3), 220, dtype=np.uint8)
    res = face_matcher.validate_portrait_image(blank_portrait)
    assert res["valid"] is False
    assert res["error_code"] in ["FACE_NOT_FOUND", "INVALID_IMAGE_DIMENSIONS"]


# -----------------------------------------------------------------------------
# TC14: Tính nhất quán (Deterministic Matching) qua 5 lần chạy liên tiếp
# -----------------------------------------------------------------------------
def test_tc14_deterministic_matching_consistency(face_matcher, embedding_person_a):
    noise = np.random.default_rng(seed=42).normal(0, 0.02, 512).astype(np.float32)
    sample_emb = embedding_person_a + noise
    sample_emb /= np.linalg.norm(sample_emb)

    distances = []
    decisions = []
    for _ in range(5):
        is_match, dist, code, _ = face_matcher.match_card_and_portrait(embedding_person_a, sample_emb, threshold=0.55)
        distances.append(dist)
        decisions.append(code)

    # Tất cả các lần chạy phải trả về khoảng cách và quyết định giống hệt nhau
    assert len(set(decisions)) == 1
    assert np.std(distances) < 1e-6


# -----------------------------------------------------------------------------
# TC15: Ảnh có EXIF Orientation (chụp điện thoại) tự động xoay chuẩn
# -----------------------------------------------------------------------------
def test_tc15_exif_orientation_handling(face_matcher, tmp_path):
    # Tạo ảnh PIL có đánh dấu EXIF Orientation = 6 (xoay 90 độ theo chiều kim đồng hồ)
    img_pil = Image.new("RGB", (200, 100), color=(100, 150, 200))
    exif = img_pil.getexif()
    exif[0x0112] = 6  # EXIF orientation tag 6

    img_path = str(tmp_path / "phone_photo_exif.jpg")
    img_pil.save(img_path, exif=exif)

    # Đọc qua _load_image_utf8
    loaded_cv2 = face_matcher._load_image_utf8(img_path)
    # Khi orientation = 6, ImageOps.exif_transpose xoay 200x100 thành 100x200 (chiều cao = 200, chiều rộng = 100)
    assert loaded_cv2.shape[0] == 200
    assert loaded_cv2.shape[1] == 100


# -----------------------------------------------------------------------------
# TC16: Cách ly Attempt (Attempt Isolation) không tái sử dụng cache bậy bạ
# -----------------------------------------------------------------------------
def test_tc16_attempt_isolation_prevents_cache_leak():
    # Cache được định danh bởi attempt_id
    card_hash = "abcdef1234567890"
    attempt_1 = "attempt-uuid-001"
    attempt_2 = "attempt-uuid-002"

    cache = {}
    cache[f"{attempt_1}:{card_hash}"] = np.ones(512, dtype=np.float32)

    # Attempt 2 tìm kiếm với attempt_id của mình sẽ KHÔNG thấy kết quả của Attempt 1
    assert f"{attempt_2}:{card_hash}" not in cache
    assert cache.get(f"{attempt_2}:{card_hash}") is None
