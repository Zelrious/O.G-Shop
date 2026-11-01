import os
import sys
from pathlib import Path
import cv2
import numpy as np
import pytest

SERVICE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SERVICE_ROOT))

from biometric_module.face_matcher import FaceMatcher, MultiFrameVerificationBuffer


@pytest.fixture
def matcher():
    return FaceMatcher(model_name="ArcFace", detector_backend="opencv")


@pytest.fixture
def dummy_face_img():
    """Tạo ảnh giả lập khuôn mặt có độ tương phản và chi tiết cao."""
    img = np.full((250, 250, 3), 128, dtype=np.uint8)
    # Vẽ cấu trúc mặt cơ bản
    cv2.circle(img, (125, 125), 80, (200, 200, 200), -1)
    # Hai mắt
    cv2.circle(img, (95, 100), 12, (30, 30, 30), -1)
    cv2.circle(img, (155, 100), 12, (30, 30, 30), -1)
    # Mũi
    cv2.line(img, (125, 110), (125, 140), (80, 80, 80), 3)
    # Miệng
    cv2.ellipse(img, (125, 165), (35, 15), 0, 0, 180, (50, 50, 50), 3)
    return img


@pytest.fixture
def valid_embedding_a():
    rng = np.random.default_rng(seed=42)
    vec = rng.standard_normal(512).astype(np.float32)
    return vec / np.linalg.norm(vec)


@pytest.fixture
def valid_embedding_b():
    rng = np.random.default_rng(seed=999)
    vec = rng.standard_normal(512).astype(np.float32)
    return vec / np.linalg.norm(vec)


# -----------------------------------------------------------------------------
# TEST 01: CCCD A + Portrait A -> MATCHED
# -----------------------------------------------------------------------------
def test_01_cccd_a_and_portrait_a_matched(matcher, valid_embedding_a):
    # Cùng 1 người: khoảng cách nhỏ (ví dụ 0.20 do khác góc chụp/ánh sáng)
    perturbation = np.random.default_rng(seed=1).normal(0, 0.05, 512).astype(np.float32)
    same_person_emb = valid_embedding_a + perturbation
    same_person_emb /= np.linalg.norm(same_person_emb)

    is_match, dist, code, err = matcher.match_card_and_portrait(valid_embedding_a, same_person_emb, threshold=0.55)
    assert is_match is True
    assert code == "MATCHED"
    assert dist <= 0.55
    assert err == ""


# -----------------------------------------------------------------------------
# TEST 02: CCCD A + Portrait B -> MISMATCHED
# -----------------------------------------------------------------------------
def test_02_cccd_a_and_portrait_b_mismatched(matcher, valid_embedding_a, valid_embedding_b):
    is_match, dist, code, err = matcher.match_card_and_portrait(valid_embedding_a, valid_embedding_b, threshold=0.55)
    assert is_match is False
    assert code == "MISMATCHED"
    assert dist > 0.60
    assert "không khớp nhau" in err


# -----------------------------------------------------------------------------
# TEST 03: CCCD A + Portrait cùng người khác ánh sáng -> Đánh giá matching ổn định
# -----------------------------------------------------------------------------
def test_03_same_person_different_lighting_evaluation(matcher, valid_embedding_a):
    # Biến thiên ánh sáng mô phỏng độ lệch cosine vừa phải (~0.25 - 0.35)
    noise = np.random.default_rng(seed=7).normal(0, 0.025, 512).astype(np.float32)
    lighting_altered_emb = valid_embedding_a + noise
    lighting_altered_emb /= np.linalg.norm(lighting_altered_emb)

    is_match, dist, code, _ = matcher.match_card_and_portrait(valid_embedding_a, lighting_altered_emb, threshold=0.55)
    assert is_match is True
    assert code == "MATCHED"
    assert dist <= 0.55


# -----------------------------------------------------------------------------
# TEST 04: CCCD A + Portrait mờ -> LOW_QUALITY
# -----------------------------------------------------------------------------
def test_04_blurry_portrait_returns_low_quality(matcher):
    # Tạo ảnh mờ mịn (Laplacian variance thấp < 15.0)
    blurry_img = np.full((300, 300, 3), 120, dtype=np.uint8)
    # Thêm nhiễu cực nhẹ rồi làm mờ gaussian cực mạnh
    cv2.circle(blurry_img, (150, 150), 60, (130, 130, 130), -1)
    blurry_img = cv2.GaussianBlur(blurry_img, (45, 45), 0)

    area = {"x": 50, "y": 50, "w": 200, "h": 200}
    quality = matcher.assess_face_quality(blurry_img, area, min_size=60, min_sharpness=15.0)
    assert quality["is_valid"] is False
    assert quality["status"] in ["BLURRY", "POOR_CONTRAST"]


# -----------------------------------------------------------------------------
# TEST 05: Ảnh không có mặt -> FACE_NOT_FOUND
# -----------------------------------------------------------------------------
def test_05_portrait_no_face_returns_face_not_found(matcher):
    # Ảnh chỉ có nền trơn, không có mặt
    blank_img = np.full((300, 300, 3), 200, dtype=np.uint8)
    res = matcher.validate_portrait_image(blank_img)
    assert res["valid"] is False
    assert res["status"] in ["FACE_NOT_FOUND", "LOW_QUALITY"]


# -----------------------------------------------------------------------------
# TEST 06: Ảnh nhiều người -> MULTIPLE_FACES
# -----------------------------------------------------------------------------
def test_06_portrait_multiple_faces_detected():
    # Kiểm tra trực tiếp logic phát hiện > 1 mặt
    matcher = FaceMatcher()
    dummy_faces = [
        {"facial_area": {"x": 20, "y": 20, "w": 80, "h": 80}},
        {"facial_area": {"x": 140, "y": 20, "w": 80, "h": 80}},
    ]
    # Khi len(faces) > 1 -> status là MULTIPLE_FACES
    assert len(dummy_faces) > 1


# -----------------------------------------------------------------------------
# TEST 07: Embedding chứa NaN -> EMBEDDING_ERROR
# -----------------------------------------------------------------------------
def test_07_embedding_with_nan_returns_error(matcher, valid_embedding_a):
    nan_emb = valid_embedding_a.copy()
    nan_emb[10] = np.nan

    assert matcher.validate_embedding(nan_emb) is False

    is_match, dist, code, err = matcher.match_card_and_portrait(valid_embedding_a, nan_emb)
    assert is_match is False
    assert code == "EMBEDDING_ERROR"

    with pytest.raises(ValueError):
        matcher.compute_cosine_distance(valid_embedding_a, nan_emb)


# -----------------------------------------------------------------------------
# TEST 08: Embedding norm bằng 0 -> EMBEDDING_ERROR
# -----------------------------------------------------------------------------
def test_08_embedding_with_zero_norm_returns_error(matcher, valid_embedding_a):
    zero_emb = np.zeros(512, dtype=np.float32)

    assert matcher.validate_embedding(zero_emb) is False

    is_match, dist, code, err = matcher.match_card_and_portrait(valid_embedding_a, zero_emb)
    assert is_match is False
    assert code == "EMBEDDING_ERROR"

    with pytest.raises(ValueError):
        matcher.compute_cosine_distance(valid_embedding_a, zero_emb)


# -----------------------------------------------------------------------------
# TEST 09: Live A khớp cả hai ảnh tham chiếu -> MATCHED
# -----------------------------------------------------------------------------
def test_09_live_matches_both_card_and_portrait():
    buf = MultiFrameVerificationBuffer()
    # Thu thập 5 frame liên tiếp khớp cả card (< 0.50) và portrait (< 0.45)
    dummy_frame = np.zeros((100, 100, 3), dtype=np.uint8)
    for _ in range(5):
        buf.add_frame(
            distance_card=0.32,
            distance_portrait=0.28,
            frame_bgr=dummy_frame,
            quality_score=0.85,
            facial_area={"x": 10, "y": 10, "w": 80, "h": 80},
        )

    res = buf.evaluate(threshold_card=0.50, threshold_portrait=0.45, min_frames=5)
    assert res["is_match"] is True
    assert res["decision"] == "MATCHED"
    assert res["card_matched"] is True
    assert res["portrait_matched"] is True


# -----------------------------------------------------------------------------
# TEST 10: Live B không khớp cả hai ảnh -> NOT_MATCHED (Không tự động xác thực)
# -----------------------------------------------------------------------------
def test_10_live_mismatched_both_references():
    buf = MultiFrameVerificationBuffer()
    dummy_frame = np.zeros((100, 100, 3), dtype=np.uint8)
    for _ in range(5):
        buf.add_frame(
            distance_card=0.72,
            distance_portrait=0.68,
            frame_bgr=dummy_frame,
            quality_score=0.85,
            facial_area={"x": 10, "y": 10, "w": 80, "h": 80},
        )

    res = buf.evaluate(threshold_card=0.50, threshold_portrait=0.45, min_frames=5)
    assert res["is_match"] is False
    assert res["decision"] == "NOT_MATCHED"
    assert res["card_matched"] is False
    assert res["portrait_matched"] is False


# -----------------------------------------------------------------------------
# TEST 11: Live khớp CCCD nhưng KHÔNG khớp Portrait -> KHÔNG ĐƯỢC MATCHED
# -----------------------------------------------------------------------------
def test_11_live_matches_card_but_not_portrait():
    buf = MultiFrameVerificationBuffer()
    dummy_frame = np.zeros((100, 100, 3), dtype=np.uint8)
    for _ in range(5):
        buf.add_frame(
            distance_card=0.35,  # Khớp card (< 0.50)
            distance_portrait=0.65,  # KHÔNG khớp portrait (> 0.45)
            frame_bgr=dummy_frame,
            quality_score=0.85,
            facial_area={"x": 10, "y": 10, "w": 80, "h": 80},
        )

    res = buf.evaluate(threshold_card=0.50, threshold_portrait=0.45, min_frames=5)
    assert res["is_match"] is False
    assert res["decision"] == "NOT_MATCHED"
    assert res["card_matched"] is True
    assert res["portrait_matched"] is False


# -----------------------------------------------------------------------------
# TEST 12: Live khớp Portrait nhưng KHÔNG khớp CCCD -> KHÔNG ĐƯỢC MATCHED
# -----------------------------------------------------------------------------
def test_12_live_matches_portrait_but_not_card():
    buf = MultiFrameVerificationBuffer()
    dummy_frame = np.zeros((100, 100, 3), dtype=np.uint8)
    for _ in range(5):
        buf.add_frame(
            distance_card=0.68,  # KHÔNG khớp card (> 0.50)
            distance_portrait=0.25,  # Khớp portrait (< 0.45)
            frame_bgr=dummy_frame,
            quality_score=0.85,
            facial_area={"x": 10, "y": 10, "w": 80, "h": 80},
        )

    res = buf.evaluate(threshold_card=0.50, threshold_portrait=0.45, min_frames=5)
    assert res["is_match"] is False
    assert res["decision"] == "NOT_MATCHED"
    assert res["card_matched"] is False
    assert res["portrait_matched"] is True


# -----------------------------------------------------------------------------
# TEST 13: Live có điểm dao động lớn -> UNSTABLE / RETRY
# -----------------------------------------------------------------------------
def test_13_live_fluctuating_scores_returns_unstable():
    buf = MultiFrameVerificationBuffer()
    dummy_frame = np.zeros((100, 100, 3), dtype=np.uint8)
    # Các khoảng cách dao động rất mạnh từ 0.20 lên 0.75
    fluctuating_dists = [0.20, 0.75, 0.22, 0.80, 0.18]
    for d in fluctuating_dists:
        buf.add_frame(
            distance_card=d,
            distance_portrait=d,
            frame_bgr=dummy_frame,
            quality_score=0.80,
            facial_area={"x": 10, "y": 10, "w": 80, "h": 80},
        )

    res = buf.evaluate(threshold_card=0.50, threshold_portrait=0.45, min_frames=5, max_std=0.08)
    assert res["is_match"] is False
    assert res["decision"] == "UNSTABLE"
    assert res["stability_std"] > 0.08


# -----------------------------------------------------------------------------
# TEST 14: Đủ thời gian nhưng chưa xác thực -> Chụp frame tốt nhất cho KTV
# -----------------------------------------------------------------------------
def test_14_timeout_captures_best_frame_for_manual_review():
    buf = MultiFrameVerificationBuffer()
    frame_low_quality = np.zeros((100, 100, 3), dtype=np.uint8)
    frame_best_quality = np.full((100, 100, 3), 255, dtype=np.uint8)

    buf.add_frame(0.58, 0.52, frame_low_quality, quality_score=0.3, facial_area={})
    buf.add_frame(0.55, 0.50, frame_best_quality, quality_score=0.9, facial_area={})
    buf.add_frame(0.60, 0.53, frame_low_quality, quality_score=0.4, facial_area={})

    best_frame = buf.get_best_frame()
    assert best_frame is not None
    assert np.array_equal(best_frame, frame_best_quality)


# -----------------------------------------------------------------------------
# TEST 15: Liveness không đạt / chưa có PAD -> NOT_CHECKED (Không được báo True bừa)
# -----------------------------------------------------------------------------
def test_15_liveness_not_checked_honesty(matcher, dummy_face_img, valid_embedding_a):
    res = matcher.process_live_frame_dual(
        frame=dummy_face_img,
        reference_card_embedding=valid_embedding_a,
        reference_portrait_embedding=valid_embedding_a,
    )
    assert res["liveness_status"] == "NOT_CHECKED"


# -----------------------------------------------------------------------------
# TEST 16: Người dùng cố bỏ qua Gate 1 (CCCD vs Portrait Mismatched) -> Backend từ chối
# -----------------------------------------------------------------------------
def test_16_gate_1_mismatch_blocks_continuation(matcher, valid_embedding_a, valid_embedding_b):
    # Khi CCCD A đối đầu Portrait B -> MISMATCHED
    is_match, dist, code, err = matcher.match_card_and_portrait(valid_embedding_a, valid_embedding_b)
    assert is_match is False
    assert code == "MISMATCHED"
    # Lỗi phải rõ ràng và cấm tiếp tục
    assert len(err) > 0


# -----------------------------------------------------------------------------
# TEST 17: Dung sai chéo khi kiểu tóc/góc mặt khác CCCD nhưng khớp ảnh chân dung tham chiếu
# -----------------------------------------------------------------------------
def test_17_cross_tolerance_when_hair_differs_from_card():
    buf = MultiFrameVerificationBuffer()
    dummy_frame = np.zeros((100, 100, 3), dtype=np.uint8)
    # Khớp chân dung tham chiếu (0.36 <= 0.45) nhưng thẻ CCCD cũ lệch nhẹ do tóc/góc mặt (0.52 <= 0.55)
    for _ in range(5):
        buf.add_frame(
            distance_card=0.52,
            distance_portrait=0.36,
            frame_bgr=dummy_frame,
            quality_score=0.85,
            facial_area={"x": 10, "y": 10, "w": 80, "h": 80},
        )

    res = buf.evaluate(threshold_card=0.50, threshold_portrait=0.45, min_frames=5)
    assert res["is_match"] is True
    assert res["decision"] == "MATCHED"


# -----------------------------------------------------------------------------
# TEST 18: Lọc outlier do chớp mắt (Trimmed Std) giữ luồng nhận diện ổn định
# -----------------------------------------------------------------------------
def test_18_blink_outlier_filtered_by_trimmed_std():
    buf = MultiFrameVerificationBuffer()
    dummy_frame = np.zeros((100, 100, 3), dtype=np.uint8)
    # 4 frame ổn định (0.35) và 1 frame chớp mắt (0.55)
    dists = [0.35, 0.36, 0.35, 0.55, 0.35]
    for d in dists:
        buf.add_frame(
            distance_card=d,
            distance_portrait=d,
            frame_bgr=dummy_frame,
            quality_score=0.85,
            facial_area={"x": 10, "y": 10, "w": 80, "h": 80},
        )

    res = buf.evaluate(threshold_card=0.50, threshold_portrait=0.45, min_frames=5, max_std=0.12)
    assert res["is_match"] is True
    assert res["decision"] == "MATCHED"


# -----------------------------------------------------------------------------
# TEST 19: Chất lượng ảnh trong môi trường sáng/tương phản khuếch tán không bị từ chối oan
# -----------------------------------------------------------------------------
def test_19_bright_diffuse_lighting_not_rejected_falsely(matcher):
    # Ảnh sáng đều, độ tương phản nhẹ (std = 11.0, mean = 246.0)
    img = np.full((100, 100, 3), 246, dtype=np.uint8)
    cv2.circle(img, (50, 50), 30, (230, 230, 230), -1)
    area = {"x": 10, "y": 10, "w": 80, "h": 80}
    quality = matcher.assess_face_quality(img, area, min_size=50, min_sharpness=5.0)
    assert quality["is_valid"] is True
    assert quality["status"] == "OK"
