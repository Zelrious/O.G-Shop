import re
from datetime import datetime
from typing import Dict, Any, Tuple, Optional

class CCCDValidator:
    """
    Lớp cung cấp các phương thức tĩnh để kiểm tra tính hợp lệ của dữ liệu CCCD.
    """

    @staticmethod
    def validate_so_cccd(so_cccd: Optional[str]) -> bool:
        """Kiểm tra độ dài và định dạng số CCCD (12 chữ số)."""
        if not so_cccd or not isinstance(so_cccd, str):
            return False
        return bool(re.fullmatch(r'\d{12}', so_cccd.strip()))

    @staticmethod
    def validate_ngay_sinh(ngay_sinh: Optional[str]) -> bool:
        """Kiểm tra định dạng ngày sinh DD/MM/YYYY."""
        if not ngay_sinh or not isinstance(ngay_sinh, str):
            return False
        try:
            datetime.strptime(ngay_sinh.strip(), "%d/%m/%Y")
            return True
        except ValueError:
            return False

    @staticmethod
    def validate_gioi_tinh(gioi_tinh: Optional[str]) -> bool:
        """Kiểm tra giới tính chỉ là Nam hoặc Nữ."""
        if not gioi_tinh or not isinstance(gioi_tinh, str):
            return False
        return gioi_tinh.strip() in ['Nam', 'Nữ']

    @staticmethod
    def validate_ngay_het_han(ngay_het_han: Optional[str]) -> bool:
        """Kiểm tra ngày hết hạn hợp lệ."""
        if not ngay_het_han or not isinstance(ngay_het_han, str):
            return False
        val = ngay_het_han.strip()
        if val.lower() == "không thời hạn":
            return True
        try:
            datetime.strptime(val, "%d/%m/%Y")
            return True
        except ValueError:
            return False

    @staticmethod
    def check_logic_cccd(so_cccd: str, gioi_tinh: str, nam_sinh: str) -> Tuple[bool, str]:
        """
        Kiểm tra logic giữa Số CCCD, Giới tính và Năm sinh.
        """
        if not CCCDValidator.validate_so_cccd(so_cccd):
            return False, "Số CCCD không đúng định dạng 12 chữ số."
            
        so_cccd = so_cccd.strip()
        gioi_tinh = gioi_tinh.strip()
        nam_sinh = nam_sinh.strip()
        
        gender_code = so_cccd[3]
        year_code = so_cccd[4:6]
        
        try:
            birth_year = int(nam_sinh[-4:])
        except ValueError:
            return False, "Năm sinh không hợp lệ."
            
        century = (birth_year // 100) + 1
        
        expected_gender_code = -1
        if century == 20: # 1900-1999
            expected_gender_code = 0 if gioi_tinh == 'Nam' else 1
        elif century == 21: # 2000-2099
            expected_gender_code = 2 if gioi_tinh == 'Nam' else 3
        elif century == 22: # 2100-2199
            expected_gender_code = 4 if gioi_tinh == 'Nam' else 5
        elif century == 23: # 2200-2299
            expected_gender_code = 6 if gioi_tinh == 'Nam' else 7
        elif century == 24: # 2300-2399
            expected_gender_code = 8 if gioi_tinh == 'Nam' else 9
            
        if str(expected_gender_code) != gender_code:
            return False, f"Mã giới tính/thế kỷ ({gender_code}) trong CCCD không khớp với năm sinh {birth_year} và giới tính {gioi_tinh}."
            
        if year_code != str(birth_year)[-2:]:
            return False, f"Mã năm sinh ({year_code}) trong CCCD không khớp với năm sinh ({str(birth_year)[-2:]})."
            
        return True, "Dữ liệu logic hợp lệ."

    @classmethod
    def validate_extracted_data(cls, data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Kiểm tra toàn bộ dữ liệu trả về từ OCR và trả về bản ghi có chèn thêm trường validation_info.
        """
        validation_results = {
            "so_cccd_valid": cls.validate_so_cccd(data.get("so_cccd")),
            "ngay_sinh_valid": cls.validate_ngay_sinh(data.get("ngay_sinh")),
            "gioi_tinh_valid": cls.validate_gioi_tinh(data.get("gioi_tinh")),
            "ngay_het_han_valid": cls.validate_ngay_het_han(data.get("ngay_het_han"))
        }
        
        if validation_results["so_cccd_valid"] and validation_results["gioi_tinh_valid"] and validation_results["ngay_sinh_valid"]:
            is_logic_valid, msg = cls.check_logic_cccd(
                data.get("so_cccd", ""), 
                data.get("gioi_tinh", ""), 
                data.get("ngay_sinh", "")
            )
            validation_results["logic_valid"] = is_logic_valid
            validation_results["logic_message"] = msg
        else:
            validation_results["logic_valid"] = False
            validation_results["logic_message"] = "Bỏ qua kiểm tra logic do dữ liệu cơ bản (Số CCCD, Giới tính hoặc Ngày sinh) không hợp lệ/đầy đủ."
            
        data["validation_info"] = validation_results
        return data
