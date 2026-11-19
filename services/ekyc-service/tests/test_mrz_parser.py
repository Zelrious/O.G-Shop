import sys
from pathlib import Path
import pytest

SERVICE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SERVICE_ROOT))

from cccd_module.mrz_parser import MRZValidator, remove_vietnamese_accents


def test_remove_vietnamese_accents():
    assert remove_vietnamese_accents("ĐẶNG VĂN KHÁNH") == "DANG VAN KHANH"
    assert remove_vietnamese_accents("Nguyễn Thị Hồng Nhung") == "NGUYEN THI HONG NHUNG"
    assert remove_vietnamese_accents("LÊ THÀNH ĐÔ") == "LE THANH DO"


def test_parse_valid_synthetic_mrz_century_21_female():
    # Thẻ giả lập an toàn: Nữ, sinh 28/05/2006 (thế kỷ 21 -> mã 3)
    l1 = "IDVNM0000000003001306000003<<3"
    l2 = "0605287F3505283VNM<<<<<<<<<<<8"
    l3 = "DANG<<THI<MAI<<<<<<<<<<<<<<<<<"

    success, mrz, err = MRZValidator.parse_mrz(l1, l2, l3)
    assert success is True, f"Parse failed: {err}"
    assert mrz is not None
    assert mrz.so_cccd == "001306000003"
    assert mrz.ho == "DANG"
    assert mrz.ten_dem_va_ten == "THI MAI"
    assert mrz.ho_va_ten == "DANG THI MAI"
    assert mrz.gioi_tinh_raw == "F"
    assert mrz.gioi_tinh == "Nữ"
    assert mrz.ngay_sinh == "28/05/2006"

    # Cross check with matching front data
    matching_front = {
        "so_cccd": "001306000003",
        "ho_va_ten": "ĐẶNG THỊ MAI",
        "ngay_sinh": "28/05/2006",
        "gioi_tinh": "Nữ",
    }
    valid, msg, checks = MRZValidator.cross_check(mrz, matching_front)
    assert valid is True
    assert checks["so_cccd_matched"] is True
    assert checks["ho_va_ten_matched"] is True
    assert checks["ngay_sinh_matched"] is True
    assert checks["gioi_tinh_matched"] is True


def test_parse_valid_synthetic_mrz_century_22_codes_4_and_5():
    # Mã 4: Nam thế kỷ 22 (sinh 15/01/2105)
    l1_m = "IDVNM0000000004001405000004<<3"
    l2_m = "0501157M3501153VNM<<<<<<<<<<<8"
    l3_m = "TRAN<<VAN<NAM<<<<<<<<<<<<<<<<<"
    ok_m, mrz_m, err_m = MRZValidator.parse_mrz(l1_m, l2_m, l3_m)
    assert ok_m is True, err_m
    assert mrz_m.gioi_tinh == "Nam"
    assert mrz_m.ngay_sinh == "15/01/2105"

    # Mã 5: Nữ thế kỷ 22 (sinh 20/08/2105)
    l1_f = "IDVNM0000000005001505000005<<3"
    l2_f = "0508207F3508203VNM<<<<<<<<<<<8"
    l3_f = "TRAN<<THI<LAN<<<<<<<<<<<<<<<<<"
    ok_f, mrz_f, err_f = MRZValidator.parse_mrz(l1_f, l2_f, l3_f)
    assert ok_f is True, err_f
    assert mrz_f.gioi_tinh == "Nữ"
    assert mrz_f.ngay_sinh == "20/08/2105"


def test_parse_rejects_invalid_calendar_date():
    # Ngày 30/02 không tồn tại trên lịch
    l1 = "IDVNM0000000002001206000002<<3"
    l2 = "0602307M3502283VNM<<<<<<<<<<<8"
    l3 = "NGUYEN<<VAN<AN<<<<<<<<<<<<<<<<"

    success, mrz, err = MRZValidator.parse_mrz(l1, l2, l3)
    assert success is False
    assert "không tồn tại trên lịch" in err


def test_parse_rejects_inconsistent_gender_between_cccd_and_mrz():
    # CCCD mã giới tính là 2 (Nam thế kỷ 21), nhưng MRZ ghi 'F' (Nữ)
    l1 = "IDVNM0000000002001206000002<<3"
    l2 = "0605287F3505283VNM<<<<<<<<<<<8"
    l3 = "NGUYEN<<VAN<AN<<<<<<<<<<<<<<<<"

    success, mrz, err = MRZValidator.parse_mrz(l1, l2, l3)
    assert success is False
    assert "không khớp với giới tính mã hóa trong số CCCD" in err


def test_parse_rejects_inconsistent_birth_year_between_cccd_and_mrz():
    # CCCD 2 số năm sinh là '06', nhưng MRZ năm sinh là '05'
    l1 = "IDVNM0000000002001206000002<<3"
    l2 = "0505287M3505283VNM<<<<<<<<<<<8"
    l3 = "NGUYEN<<VAN<AN<<<<<<<<<<<<<<<<"

    success, mrz, err = MRZValidator.parse_mrz(l1, l2, l3)
    assert success is False
    assert "không khớp với hai chữ số năm sinh trong số CCCD" in err


def test_cross_check_mismatch_cccd():
    l1 = "IDVNM0000000003001306000003<<3"
    l2 = "0605287F3505283VNM<<<<<<<<<<<8"
    l3 = "DANG<<THI<MAI<<<<<<<<<<<<<<<<<"
    success, mrz, _ = MRZValidator.parse_mrz(l1, l2, l3)
    assert success is True

    mismatch_front = {
        "so_cccd": "001306000099",
        "ho_va_ten": "ĐẶNG THỊ MAI",
        "ngay_sinh": "28/05/2006",
        "gioi_tinh": "Nữ",
    }
    valid, msg, checks = MRZValidator.cross_check(mrz, mismatch_front)
    assert valid is False
    assert "không trùng khớp với số CCCD mặt trước" in msg
    assert checks["so_cccd_matched"] is False


def test_cross_check_rejects_name_substring():
    # Kiểm tra cấm so sánh chuỗi con: Tên mặt sau "MAI", mặt trước "ĐẶNG THỊ MAI" -> BỊ TỪ CHỐI
    l1 = "IDVNM0000000003001306000003<<3"
    l2 = "0605287F3505283VNM<<<<<<<<<<<8"
    l3 = "DANG<<MAI<<<<<<<<<<<<<<<<<<<<<"
    success, mrz, _ = MRZValidator.parse_mrz(l1, l2, l3)
    assert success is True

    front_data = {
        "so_cccd": "001306000003",
        "ho_va_ten": "ĐẶNG THỊ MAI",
        "ngay_sinh": "28/05/2006",
        "gioi_tinh": "Nữ",
    }
    valid, msg, checks = MRZValidator.cross_check(mrz, front_data)
    assert valid is False
    assert "không khớp với họ tên mặt trước" in msg
    assert checks["ho_va_ten_matched"] is False


def test_cross_check_fail_closed_on_missing_front_fields():
    # Mặt trước thiếu ngày sinh hoặc thiếu CCCD -> FAIL CLOSED
    l1 = "IDVNM0000000003001306000003<<3"
    l2 = "0605287F3505283VNM<<<<<<<<<<<8"
    l3 = "DANG<<THI<MAI<<<<<<<<<<<<<<<<<"
    success, mrz, _ = MRZValidator.parse_mrz(l1, l2, l3)
    assert success is True

    incomplete_front = {
        "so_cccd": "001306000003",
        "ho_va_ten": "ĐẶNG THỊ MAI",
        # Thiếu ngay_sinh và gioi_tinh
    }
    valid, msg, checks = MRZValidator.cross_check(mrz, incomplete_front)
    assert valid is False
    assert "thiếu thông tin bắt buộc" in msg
    assert checks["so_cccd_matched"] is False


def test_cross_check_rejects_invalid_front_dob():
    l1 = "IDVNM0000000003001306000003<<3"
    l2 = "0605287F3505283VNM<<<<<<<<<<<8"
    l3 = "DANG<<THI<MAI<<<<<<<<<<<<<<<<<"
    success, mrz, _ = MRZValidator.parse_mrz(l1, l2, l3)
    assert success is True

    # Ngày sinh mặt trước không đúng 8 số (ví dụ chỉ có năm sinh)
    invalid_dob_front = {
        "so_cccd": "001306000003",
        "ho_va_ten": "ĐẶNG THỊ MAI",
        "ngay_sinh": "2006",
        "gioi_tinh": "Nữ",
    }
    valid, msg, checks = MRZValidator.cross_check(mrz, invalid_dob_front)
    assert valid is False
    assert "cần đúng 8 chữ số DD/MM/YYYY" in msg


def test_invalid_mrz_structure():
    # Dòng 3 không có << phân cách họ và tên
    l1 = "IDVNM0000000003001306000003<<3"
    l2 = "0605287F3505283VNM<<<<<<<<<<<8"
    l3 = "DANG<THI<MAI<<<<<<<<<<<<<<<<<<"

    success, mrz, err = MRZValidator.parse_mrz(l1, l2, l3)
    assert success is False
    assert "Họ và Tên phải cách nhau bằng 2 dấu '<<'" in err
