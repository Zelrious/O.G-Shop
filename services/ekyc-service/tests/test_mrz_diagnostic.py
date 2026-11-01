import contextlib
import io
import sys
from pathlib import Path

import cv2
import numpy as np

SERVICE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SERVICE_ROOT))

from cccd_module.back_card_processor import BackCardProcessor
from cccd_module.mrz_parser import MRZValidator
from tools.mrz_diagnostic import diagnose_back_image


def _synthetic_back_card_path(tmp_path):
    h, w = BackCardProcessor.CANONICAL_HEIGHT, BackCardProcessor.CANONICAL_WIDTH
    card = np.full((h, w, 3), 180, dtype=np.uint8)

    card[int(h * 0.20):int(h * 0.65), int(w * 0.06):int(w * 0.38)] = [20, 190, 220]
    card[int(h * 0.20):int(h * 0.70), int(w * 0.22):int(w * 0.58)] = [30, 30, 210]

    rng = np.random.RandomState(42)
    card[int(h * 0.05):int(h * 0.55), int(w * 0.50):int(w * 0.98)] = rng.randint(
        40, 220, (int(h * 0.50), int(w * 0.48), 3), dtype=np.uint8
    )

    for row in range(int(h * 0.65) + 10, h - 10, 8):
        card[row:row + 3, :] = 20

    image_path = tmp_path / "synthetic-back.jpg"
    assert cv2.imwrite(str(image_path), card)
    return image_path


def test_diagnostic_reports_structural_status_without_raw_mrz(tmp_path, monkeypatch):
    image_path = _synthetic_back_card_path(tmp_path)
    before = image_path.read_bytes()
    mrz_lines = [
        "IDVNM0000000003001306000003<<3",
        "0605287F3505283VNM<<<<<<<<<<<8",
        "DANG<<THI<MAI<<<<<<<<<<<<<<<<<",
    ]
    predictions = iter(mrz_lines)
    mock_model = type("MockVietOcr", (), {"predict": lambda self, crop: next(predictions)})()
    monkeypatch.setattr(BackCardProcessor, "get_vietocr_model", classmethod(lambda cls: mock_model))
    monkeypatch.setattr(BackCardProcessor, "detect_and_align_card", classmethod(lambda cls, img: (img, False)))
    monkeypatch.setattr(BackCardProcessor, "correct_orientation", classmethod(lambda cls, img: (img, 0)))
    monkeypatch.setattr(BackCardProcessor, "verify_is_cccd_back", classmethod(lambda cls, img: (True, "", {"ok": True})))
    monkeypatch.setattr(MRZValidator, "parse_mrz", classmethod(lambda cls, *lines: (True, object(), "")))

    result = diagnose_back_image(image_path)

    assert result == {
        "status": "PARSE_OK",
        "input_read": True,
        "back_side_check": "PASS",
        "mrz_lines": ["READ", "READ", "READ"],
        "line_checks": [
            {"status": "STRUCTURE_CANDIDATE", "prefix": "RECOGNIZED", "id_field": "CANDIDATE"},
            {"status": "STRUCTURE_CANDIDATE", "date_field": "CANDIDATE", "gender_field": "CANDIDATE", "country_marker": "RECOGNIZED"},
            {"status": "STRUCTURE_CANDIDATE", "name_separator": "RECOGNIZED", "name_parts": "MULTIPLE"},
        ],
        "parse": "PASS",
    }
    assert image_path.read_bytes() == before


def test_diagnostic_redacts_processor_logs_and_exceptions(tmp_path, monkeypatch):
    image_path = _synthetic_back_card_path(tmp_path)
    sensitive = "SYNTHETIC-MRZ-SECRET"

    def failing_read(cls, path):
        print(f"debug {path} {sensitive}")
        raise RuntimeError(f"failure {path} {sensitive}")

    monkeypatch.setattr(BackCardProcessor, "read_image_safe", classmethod(failing_read))
    captured = io.StringIO()
    with contextlib.redirect_stdout(captured), contextlib.redirect_stderr(captured):
        result = diagnose_back_image(image_path)

    assert result == {
        "status": "IMAGE_READ_FAILED",
        "input_read": False,
        "back_side_check": "NOT_RUN",
        "mrz_lines": ["NOT_RUN", "NOT_RUN", "NOT_RUN"],
        "line_checks": [
            {"status": "NOT_RUN"},
            {"status": "NOT_RUN"},
            {"status": "NOT_RUN"},
        ],
        "parse": "NOT_RUN",
    }
    assert str(image_path) not in captured.getvalue()
    assert sensitive not in captured.getvalue()


def test_diagnostic_rejects_missing_input_without_echoing_path(tmp_path):
    missing = tmp_path / "missing-private-file.jpg"
    result = diagnose_back_image(missing)

    assert result["status"] == "IMAGE_READ_FAILED"
    assert result["input_read"] is False
    assert str(missing) not in repr(result)


def test_diagnostic_reports_model_unavailable_generically(tmp_path, monkeypatch):
    image_path = _synthetic_back_card_path(tmp_path)
    monkeypatch.setattr(BackCardProcessor, "detect_and_align_card", classmethod(lambda cls, img: (img, False)))
    monkeypatch.setattr(BackCardProcessor, "correct_orientation", classmethod(lambda cls, img: (img, 0)))
    monkeypatch.setattr(BackCardProcessor, "verify_is_cccd_back", classmethod(lambda cls, img: (True, "", {})))
    monkeypatch.setattr(BackCardProcessor, "get_vietocr_model", classmethod(lambda cls: None))

    result = diagnose_back_image(image_path)

    assert result["status"] == "MODEL_UNAVAILABLE"
    assert result["mrz_lines"] == ["NOT_RUN", "NOT_RUN", "NOT_RUN"]
    assert all(check["status"] == "NOT_RUN" for check in result["line_checks"])
