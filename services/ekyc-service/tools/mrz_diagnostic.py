"""Offline-only, single-image diagnostic for back-side MRZ OCR."""

from __future__ import annotations

import contextlib
import io
import sys
from pathlib import Path
from typing import Any
import re
import unicodedata

SERVICE_ROOT = Path(__file__).resolve().parents[1]
if str(SERVICE_ROOT) not in sys.path:
    sys.path.insert(0, str(SERVICE_ROOT))


def _result(
    status: str,
    *,
    input_read: bool = False,
    back_side_check: str = "NOT_RUN",
    mrz_lines: list[str] | None = None,
    parse: str = "NOT_RUN",
    line_checks: list[dict[str, str]] | None = None,
) -> dict[str, Any]:
    return {
        "status": status,
        "input_read": input_read,
        "back_side_check": back_side_check,
        "mrz_lines": mrz_lines or ["NOT_RUN", "NOT_RUN", "NOT_RUN"],
        "line_checks": line_checks or [
            {"status": "NOT_RUN"},
            {"status": "NOT_RUN"},
            {"status": "NOT_RUN"},
        ],
        "parse": parse,
    }


def _normalize(line: str) -> str:
    value = unicodedata.normalize("NFKD", line.upper())
    value = "".join(char for char in value if not unicodedata.combining(char))
    return re.sub(r"[^A-Z0-9<]", "", value)


def _check_line(index: int, line: str) -> dict[str, str]:
    normalized = _normalize(line)
    if not normalized:
        return {"status": "EMPTY"}

    if index == 0:
        prefix_ok = normalized.startswith("IDVNM")
        digit_runs = re.findall(r"\d+", normalized[5:]) if prefix_ok else []
        cccd_shape = any(len(run) >= 12 for run in digit_runs)
        if not prefix_ok:
            return {"status": "PREFIX_NOT_RECOGNIZED"}
        return {
            "status": "STRUCTURE_CANDIDATE" if cccd_shape else "ID_FIELD_NOT_FOUND",
            "prefix": "RECOGNIZED",
            "id_field": "CANDIDATE" if cccd_shape else "NOT_FOUND",
        }

    if index == 1:
        date_shape = re.search(r"\d{6}", normalized) is not None
        gender_shape = re.search(r"\d{6}\d[MF]", normalized) is not None
        country_shape = "VNM" in normalized
        return {
            "status": "STRUCTURE_CANDIDATE" if date_shape and gender_shape and country_shape else "STRUCTURE_NOT_RECOGNIZED",
            "date_field": "CANDIDATE" if date_shape else "NOT_FOUND",
            "gender_field": "CANDIDATE" if gender_shape else "NOT_FOUND",
            "country_marker": "RECOGNIZED" if country_shape else "NOT_FOUND",
        }

    trimmed = normalized.rstrip("<")
    name_separator = "<<" in trimmed
    name_parts = [part for part in trimmed.split("<<", 1)[1].split("<") if part] if name_separator else []
    return {
        "status": "STRUCTURE_CANDIDATE" if name_separator and len(name_parts) >= 2 else "NAME_STRUCTURE_NOT_RECOGNIZED",
        "name_separator": "RECOGNIZED" if name_separator else "NOT_FOUND",
        "name_parts": "MULTIPLE" if len(name_parts) >= 2 else ("ONE" if name_parts else "NONE"),
    }


def diagnose_back_image(image_path: str | Path) -> dict[str, Any]:
    """Run local back-card preparation and MRZ OCR, returning no OCR content."""
    import cv2
    from cccd_module.back_card_processor import BackCardProcessor
    from cccd_module.mrz_parser import MRZValidator

    source = Path(image_path)
    source_before = None
    try:
        source_before = source.read_bytes()
    except OSError:
        return _result("IMAGE_READ_FAILED")

    with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
        try:
            image = BackCardProcessor.read_image_safe(str(source))
        except Exception:
            return _result("IMAGE_READ_FAILED")

        if image is None:
            return _result("IMAGE_READ_FAILED")

        try:
            aligned, _ = BackCardProcessor.detect_and_align_card(image)
            upright, _ = BackCardProcessor.correct_orientation(aligned)
            is_back, _, _ = BackCardProcessor.verify_is_cccd_back(upright)
        except Exception:
            return _result("IMAGE_PREPARATION_FAILED", input_read=True)

        if not is_back:
            return _result("NOT_BACK_CARD", input_read=True, back_side_check="FAIL")

        model = BackCardProcessor.get_vietocr_model()
        if model is None:
            return _result("MODEL_UNAVAILABLE", input_read=True, back_side_check="PASS")

        h, _ = upright.shape[:2]
        crops = [
            upright[int(h * 0.68):int(h * 0.78), :],
            upright[int(h * 0.78):int(h * 0.88), :],
            upright[int(h * 0.88):int(h * 0.98), :],
        ]
        line_status = []
        lines = []
        try:
            for crop in crops:
                text = str(model.predict(crop) or "")
                lines.append(text)
                line_status.append("READ" if text.strip() else "UNREADABLE")
        except Exception:
            completed_lines = lines + [""] * (3 - len(lines))
            return _result(
                "OCR_FAILED",
                input_read=True,
                back_side_check="PASS",
                mrz_lines=line_status + ["NOT_RUN"] * (3 - len(line_status)),
                line_checks=[_check_line(index, line) if line else {"status": "NOT_RUN"} for index, line in enumerate(completed_lines)],
            )

        try:
            parsed, data, _ = MRZValidator.parse_mrz(*lines)
        except Exception:
            parsed, data = False, None

        try:
            source_unchanged = source.read_bytes() == source_before
        except OSError:
            source_unchanged = False

        if not source_unchanged:
            return _result(
                "SOURCE_CHANGED",
                input_read=True,
                back_side_check="PASS",
                mrz_lines=line_status,
                parse="NOT_VERIFIED",
            )

        line_checks = [_check_line(index, line) for index, line in enumerate(lines)]
        if not parsed or data is None:
            return _result(
                "MRZ_UNREADABLE_OR_INVALID",
                input_read=True,
                back_side_check="PASS",
                mrz_lines=line_status,
                parse="FAIL",
                line_checks=line_checks,
            )

        return _result(
            "PARSE_OK",
            input_read=True,
            back_side_check="PASS",
            mrz_lines=line_status,
            parse="PASS",
            line_checks=line_checks,
        )


def main(argv: list[str] | None = None) -> int:
    import argparse
    import json

    parser = argparse.ArgumentParser(description="Offline back-side MRZ diagnostic; emits sanitized status only.")
    parser.add_argument("image", type=Path, help="Path to one local back-side image")
    args = parser.parse_args(argv)
    print(json.dumps(diagnose_back_image(args.image), ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
