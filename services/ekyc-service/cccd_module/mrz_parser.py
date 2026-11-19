import datetime
import re
import os
import unicodedata
from typing import Dict, Any, Optional, List, Tuple
from pydantic import BaseModel


def remove_vietnamese_accents(text: str) -> str:
    """
    Chuyển đổi văn bản tiếng Việt có dấu sang không dấu in hoa.
    Ví dụ: 'ĐẶNG VĂN KHÁNH' -> 'DANG VAN KHANH'
    """
    if not text:
        return ""
    text = text.replace("Đ", "D").replace("đ", "d")
    text = unicodedata.normalize("NFKD", text)
    text = "".join([c for c in text if not unicodedata.combining(c)])
    # Xóa ký tự đặc biệt, chuẩn hóa khoảng trắng
    text = re.sub(r"[^A-Za-z0-9\s]", "", text)
    return " ".join(text.upper().split())


class MRZData(BaseModel):
    line1: str = ""
    line2: str = ""
    line3: str = ""
    so_cccd: str = ""
    ngay_sinh: str = ""        # DD/MM/YYYY
    ngay_sinh_raw: str = ""    # YYMMDD
    gioi_tinh: str = ""        # Nam / Nữ
    gioi_tinh_raw: str = ""    # M / F
    ngay_het_han: str = ""     # DD/MM/YYYY
    quoc_tich: str = "Việt Nam"
    ho_va_ten: str = ""        # In hoa không dấu: DANG VAN KHANH
    ho: str = ""               # DANG
    ten_dem_va_ten: str = ""   # VAN KHANH


class MRZValidationResult(BaseModel):
    is_valid: bool
    error_message: str = ""
    mrz_data: Optional[MRZData] = None
    cross_check: Dict[str, Any] = {}


class MRZValidator:
    """
    Bộ bóc tách và kiểm tra tính hợp lệ của dải mã MRZ (Machine Readable Zone)
    ở mặt sau thẻ Căn cước công dân gắn chip (chuẩn ICAO Doc 9303 TD1 - 3 dòng x 30 ký tự).
    """

    CENTURY_GENDER_MAP = {
        0: (1900, "M"), 1: (1900, "F"),
        2: (2000, "M"), 3: (2000, "F"),
        4: (2100, "M"), 5: (2100, "F"),
        6: (2200, "M"), 7: (2200, "F"),
        8: (2300, "M"), 9: (2300, "F"),
    }

    @staticmethod
    def clean_mrz_line(line: str) -> str:
        """Chuẩn hóa 1 dòng MRZ: in hoa, loại bỏ khoảng trắng và ký tự không hợp lệ."""
        if not line:
            return ""
        cleaned = "".join(line.upper().split())
        cleaned = re.sub(r"[^A-Z0-9<]", "", cleaned)
        return cleaned

    @classmethod
    def parse_mrz(cls, line1: str, line2: str, line3: str) -> Tuple[bool, Optional[MRZData], str]:
        """
        Phân tích cú pháp 3 dòng MRZ mặt sau thẻ CCCD theo đúng cấu trúc:
        1. Dòng 1: IDVNM + 10 số phụ + 12 số CCCD + ký tự kiểm tra / dãy số khác (<<3).
        2. Dòng 2: YYMMDD (ngày sinh) + số KT + M/F (giới tính) + YYMMDD (hạn) + số KT + VNM + đệm <.
        3. Dòng 3: Tên chủ sở hữu: Họ cách chữ thứ 2 bởi <<, các chữ sau cách nhau 1 dấu <.
        """
        l1 = cls.clean_mrz_line(line1)
        l2 = cls.clean_mrz_line(line2)
        l3 = cls.clean_mrz_line(line3)

        if not l1 or not l2 or not l3:
            return False, None, "Không tìm đủ 3 dòng mã MRZ ở mặt sau thẻ CCCD."

        # 1. Kiểm tra DÒNG 1:
        # Cấu trúc: 5 chữ (IDVNM) + 10 số phụ + 12 số CCCD + dãy số/ký tự khác
        match_l1 = re.search(r"^I[A-Z0-9<]{4}([A-Z0-9<]{10})(\d{12})(.*)$", l1)
        if not match_l1:
            match_l1 = re.search(r"IDVNM[A-Z0-9<]{10}(\d{12})", l1)
            if not match_l1:
                return False, None, f"Dòng 1 của mã MRZ không đúng cấu trúc (cần có mã 5 chữ, 10 số phụ và 12 số CCCD, đọc được: '{l1}')."
            so_cccd = match_l1.group(1)
        else:
            so_cccd = match_l1.group(2)

        if len(so_cccd) != 12 or not so_cccd.isdigit():
            return False, None, f"Số CCCD trích xuất từ dòng 1 MRZ không đủ 12 chữ số (đọc được '{so_cccd}')."

        # 2. Kiểm tra DÒNG 2:
        # Cấu trúc: 6 số ngày sinh (YYMMDD) + 1 số KT + Giới tính (M/F) + 6 số hết hạn (YYMMDD) + 1 số KT + VNM + ...
        match_l2 = re.search(r"^(\d{6})\d([MF<])(\d{6})\d([A-Z]{3})", l2)
        if not match_l2:
            match_l2 = re.search(r"(\d{6})\d([MF])(\d{6})\d?VNM", l2)
            if not match_l2:
                return False, None, f"Dòng 2 của mã MRZ không đúng cấu trúc ngày sinh/giới tính/hết hạn (đọc được: '{l2}')."

        dob_raw = match_l2.group(1)       # YYMMDD
        sex_raw = match_l2.group(2)       # M or F
        expiry_raw = match_l2.group(3)    # YYMMDD

        yy = int(dob_raw[0:2])
        mm = int(dob_raw[2:4])
        dd = int(dob_raw[4:6])

        # Phân giải thế kỷ và giới tính theo quy chuẩn CCCD 12 số (Luật Căn cước / Nghị định 137/2015/NĐ-CP mã 0-9)
        g_code = int(so_cccd[3])
        if g_code in cls.CENTURY_GENDER_MAP:
            century, expected_sex = cls.CENTURY_GENDER_MAP[g_code]
            if sex_raw in ["M", "F"] and sex_raw != expected_sex:
                return False, None, f"Ký tự giới tính trên MRZ ('{sex_raw}') không khớp với giới tính mã hóa trong số CCCD ('{expected_sex}')."
            cccd_yy = int(so_cccd[4:6])
            if cccd_yy != yy:
                return False, None, f"Năm sinh trên MRZ ('{yy:02d}') không khớp với hai chữ số năm sinh trong số CCCD ('{cccd_yy:02d}')."
        else:
            century = 2000 if yy <= 40 else 1900

        # Kiểm tra tính hợp lệ của ngày sinh trên lịch thực tế (Calendar Date Validation)
        try:
            full_dob_year = century + yy
            valid_dob = datetime.date(full_dob_year, mm, dd)
            dob_formatted = valid_dob.strftime("%d/%m/%Y")
        except ValueError as err_dob:
            return False, None, f"Ngày sinh trên mã MRZ không tồn tại trên lịch ({dd:02d}/{mm:02d}/{century + yy}): {err_dob}"

        gender_formatted = "Nam" if sex_raw == "M" else ("Nữ" if sex_raw == "F" else "")

        # Kiểm tra ngày hết hạn trên lịch thực tế
        try:
            eyy = int(expiry_raw[0:2])
            emm = int(expiry_raw[2:4])
            edd = int(expiry_raw[4:6])
            exp_year = 2000 + eyy
            valid_exp = datetime.date(exp_year, emm, edd)
            exp_formatted = valid_exp.strftime("%d/%m/%Y")
        except ValueError as err_exp:
            return False, None, f"Ngày hết hạn trên mã MRZ không tồn tại trên lịch ({expiry_raw}): {err_exp}"

        # 3. Kiểm tra DÒNG 3:
        # Cấu trúc: Tên chủ sở hữu: Họ cách chữ thứ 2 bởi 2 dấu <<, các chữ sau cách nhau 1 dấu <
        l3_trimmed = re.sub(r"<+$", "", l3)
        if "<<" not in l3_trimmed:
            return False, None, f"Dòng 3 của mã MRZ không đúng cấu trúc (Họ và Tên phải cách nhau bằng 2 dấu '<<', đọc được: '{l3}')."

        parts = l3_trimmed.split("<<", 1)
        ho = parts[0].replace("<", "").strip()
        remaining = parts[1]

        given_name_parts = [p for p in remaining.split("<") if p.strip()]
        ten_dem_va_ten = " ".join(given_name_parts)
        ho_va_ten = f"{ho} {ten_dem_va_ten}".strip()

        if not ho or not ten_dem_va_ten:
            return False, None, f"Dòng 3 của mã MRZ không trích xuất được đầy đủ Họ và Tên (đọc được: '{l3}')."

        mrz_data = MRZData(
            line1=l1,
            line2=l2,
            line3=l3,
            so_cccd=so_cccd,
            ngay_sinh=dob_formatted,
            ngay_sinh_raw=dob_raw,
            gioi_tinh=gender_formatted,
            gioi_tinh_raw=sex_raw,
            ngay_het_han=exp_formatted,
            quoc_tich="Việt Nam",
            ho_va_ten=ho_va_ten,
            ho=ho,
            ten_dem_va_ten=ten_dem_va_ten,
        )

        return True, mrz_data, ""

    @classmethod
    def cross_check(cls, mrz_data: MRZData, front_data: Dict[str, Any]) -> Tuple[bool, str, Dict[str, Any]]:
        """
        Đối chiếu chéo dữ liệu mã MRZ mặt sau với dữ liệu bóc tách từ mặt trước CCCD.
        Áp dụng nguyên tắc Fail-Closed:
        1. Tất cả 4 trường cốt lõi ở mặt trước bắt buộc phải có giá trị.
        2. Số CCCD: Khớp chính xác 100%.
        3. Họ và tên (không dấu): Khớp chính xác 100% (không dùng substring match).
        4. Ngày sinh: Khớp chính xác ngày/tháng/năm theo lịch.
        5. Giới tính: Khớp chính xác Nam/Nữ.
        """
        front_cccd = str(front_data.get("so_cccd", "")).strip() if front_data else ""
        front_name = str(front_data.get("ho_va_ten", "")).strip() if front_data else ""
        front_dob = str(front_data.get("ngay_sinh", "")).strip() if front_data else ""
        front_gender = str(front_data.get("gioi_tinh", "")).strip() if front_data else ""

        check_results = {
            "so_cccd_matched": False,
            "ho_va_ten_matched": False,
            "ngay_sinh_matched": False,
            "gioi_tinh_matched": False,
        }

        # Bắt buộc phải có đủ 4 trường cốt lõi từ mặt trước (Fail-Closed)
        if not front_cccd or not front_name or not front_dob or not front_gender:
            missing_fields = []
            if not front_cccd: missing_fields.append("số CCCD")
            if not front_name: missing_fields.append("họ và tên")
            if not front_dob: missing_fields.append("ngày sinh")
            if not front_gender: missing_fields.append("giới tính")
            return (
                False,
                f"Dữ liệu mặt trước thẻ CCCD thiếu thông tin bắt buộc để đối chiếu ({', '.join(missing_fields)}).",
                check_results,
            )

        # 1. So sánh số CCCD (Khớp chính xác 100%)
        if mrz_data.so_cccd == front_cccd:
            check_results["so_cccd_matched"] = True
        else:
            return (
                False,
                f"Số CCCD trên dải mã MRZ mặt sau ({mrz_data.so_cccd}) không trùng khớp với số CCCD mặt trước ({front_cccd}). Vui lòng kiểm tra lại ảnh 2 mặt của cùng 1 thẻ.",
                check_results,
            )

        # 2. So sánh Họ và Tên (chuẩn hóa không dấu, khớp chính xác 100%, cấm substring match)
        front_unaccented = remove_vietnamese_accents(front_name)
        mrz_unaccented = remove_vietnamese_accents(mrz_data.ho_va_ten)
        if front_unaccented == mrz_unaccented:
            check_results["ho_va_ten_matched"] = True
        else:
            return (
                False,
                f"Họ và tên trên mã MRZ mặt sau ('{mrz_data.ho_va_ten}') không khớp với họ tên mặt trước ('{front_name}').",
                check_results,
            )

        # 3. So sánh Ngày sinh (Khớp chính xác ngày/tháng/năm theo lịch)
        dob_digits = re.sub(r"[^\d]", "", front_dob)
        if len(dob_digits) != 8:
            return (
                False,
                f"Định dạng ngày sinh mặt trước không hợp lệ ('{front_dob}', cần đúng 8 chữ số DD/MM/YYYY).",
                check_results,
            )
        try:
            f_dd = int(dob_digits[0:2])
            f_mm = int(dob_digits[2:4])
            f_yyyy = int(dob_digits[4:8])
            front_date_str = datetime.date(f_yyyy, f_mm, f_dd).strftime("%d/%m/%Y")
        except ValueError:
            return (
                False,
                f"Ngày sinh mặt trước không tồn tại trên lịch ('{front_dob}').",
                check_results,
            )

        if mrz_data.ngay_sinh == front_date_str:
            check_results["ngay_sinh_matched"] = True
        else:
            return (
                False,
                f"Ngày sinh trên mã MRZ mặt sau ({mrz_data.ngay_sinh}) không khớp với ngày sinh mặt trước ({front_dob}).",
                check_results,
            )

        # 4. So sánh Giới tính (Khớp chính xác Nam/Nữ)
        front_gender_clean = front_gender.strip().lower()
        front_g_norm = "Nam" if front_gender_clean in ["nam", "m"] else ("Nữ" if front_gender_clean in ["nữ", "nu", "f"] else "")
        if front_g_norm and mrz_data.gioi_tinh == front_g_norm:
            check_results["gioi_tinh_matched"] = True
        else:
            return (
                False,
                f"Giới tính trên mã MRZ mặt sau ({mrz_data.gioi_tinh}) không khớp với mặt trước ({front_gender}).",
                check_results,
            )

        return True, "Dữ liệu mã MRZ mặt sau hoàn toàn trùng khớp với thông tin mặt trước CCCD.", check_results

    @classmethod
    def extract_mrz_from_image(cls, image_path: str, gemini_client=None, model_name: str = "gemini-2.0-flash") -> Tuple[bool, List[str], str]:
        """
        Bóc tách 3 dòng mã MRZ từ ảnh mặt sau thẻ CCCD.
        Sử dụng Gemini Vision nếu khả dụng.
        """
        if not os.path.exists(image_path):
            return False, [], "Không tìm thấy file ảnh mặt sau CCCD."

        if gemini_client is not None:
            try:
                from PIL import Image
                from google.genai import types
                import json

                pil_img = Image.open(image_path)
                prompt = """
Đây là ảnh mặt sau của thẻ Căn cước công dân (CCCD) gắn chip Việt Nam.
Dưới cùng của mặt sau có dải mã ký tự MRZ gồm đúng 3 dòng (chuẩn TD1):
- Dòng 1: bắt đầu bằng "IDVNM...", chứa 10 số và 12 số CCCD
- Dòng 2: bắt đầu bằng ngày sinh (YYMMDD), giới tính (M hoặc F), ngày hết hạn, mã VNM
- Dòng 3: Họ và Tên viết hoa không dấu cách nhau bằng ký tự < (ví dụ: DANG<<VAN<KHANH<<<<<<<<<<<)

Hãy đọc chính xác 3 dòng ký tự này, giữ nguyên các ký tự '<', chữ cái và chữ số.
Trả về định dạng JSON duy nhất:
{
  "line1": "...",
  "line2": "...",
  "line3": "..."
}
"""
                response = gemini_client.models.generate_content(
                    model=model_name,
                    contents=[pil_img, prompt],
                    config=types.GenerateContentConfig(
                        response_mime_type="application/json",
                        temperature=0.0
                    )
                )

                if response.text:
                    data = json.loads(response.text)
                    l1 = data.get("line1", "")
                    l2 = data.get("line2", "")
                    l3 = data.get("line3", "")
                    if l1 and l2 and l3:
                        return True, [l1, l2, l3], ""
            except Exception as e:
                print(f"[MRZ Vision] Lỗi đọc MRZ qua Gemini: {e}")

        return False, [], "Không thể bóc tách 3 dòng mã MRZ từ ảnh mặt sau CCCD. Vui lòng chụp rõ dải ký tự bên dưới."

