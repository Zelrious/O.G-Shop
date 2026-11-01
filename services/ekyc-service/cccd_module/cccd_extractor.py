import os
import cv2
import json
import traceback
from typing import Dict, Any, Optional

from .config import Config
from .validator import CCCDValidator
from .ocr_core.merged_model import CompletedModel

class CCCDExtractionError(Exception):
    """Ngoại lệ tùy chỉnh cho các lỗi trong quá trình bóc tách CCCD."""
    pass


class CCCDProcessor:
    """
    Lớp chịu trách nhiệm xử lý hình ảnh CCCD và trích xuất thông tin sử dụng thuật toán OCR nội bộ (YOLO + VietOCR).
    Đã loại bỏ sự phụ thuộc vào Gemini API.
    """
    
    def __init__(self):
        # Khởi tạo mô hình OCR từ ocr_core
        try:
            self.model = CompletedModel()
        except Exception as e:
            raise CCCDExtractionError(f"Lỗi khởi tạo mô hình OCR: {str(e)}")

    def _validate_image(self, img_path: str) -> None:
        if not os.path.exists(img_path):
            raise CCCDExtractionError(f"Không tìm thấy file ảnh: {img_path}")
        
        ext = os.path.splitext(img_path)[1].lower()
        if ext not in Config.ALLOWED_EXTENSIONS:
            raise CCCDExtractionError(f"Định dạng ảnh {ext} của {img_path} không được hỗ trợ. Cho phép: {', '.join(Config.ALLOWED_EXTENSIONS)}")
        
        file_size_mb = os.path.getsize(img_path) / (1024 * 1024)
        if file_size_mb > Config.MAX_FILE_SIZE_MB:
            raise CCCDExtractionError(f"Dung lượng file ảnh ({file_size_mb:.2f}MB) vượt quá {Config.MAX_FILE_SIZE_MB}MB.")

    def _check_image_quality(self, image) -> Dict[str, Any]:
        """
        Kiểm tra chất lượng ảnh sử dụng phương sai Laplacian để phát hiện ảnh mờ
        và độ sáng trung bình (Mean Brightness) để phát hiện ảnh quá tối hoặc chói lóa.
        """
        if image is None:
            raise CCCDExtractionError("Không thể đọc dữ liệu ảnh.")
            
        import numpy as np
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        laplacian_var = float(cv2.Laplacian(gray, cv2.CV_64F).var())
        mean_brightness = float(np.mean(gray))
        
        # Ngưỡng sắc nét cho văn bản thẻ CCCD
        blur_threshold = 40.0
        is_clear = laplacian_var >= blur_threshold
        is_too_dark = mean_brightness < 45.0
        is_too_bright = mean_brightness > 225.0
        
        rejection_reasons = []
        if not is_clear:
            rejection_reasons.append(f"Ảnh chụp bị mờ hoặc rung tay (độ nét {laplacian_var:.1f} < {blur_threshold}). Vui lòng giữ chắc camera và chụp lại.")
        if is_too_dark:
            rejection_reasons.append(f"Ảnh chụp quá tối (độ sáng {mean_brightness:.1f} < 45). Vui lòng chụp ở nơi đủ ánh sáng.")
        if is_too_bright:
            rejection_reasons.append(f"Ảnh chụp bị lóa sáng hoặc chói đèn flash (độ sáng {mean_brightness:.1f} > 225). Vui lòng tránh ánh đèn phản chiếu trực tiếp vào thẻ.")
            
        quality = {
            "is_clear": is_clear and not is_too_dark and not is_too_bright,
            "is_full_document_visible": True, # YOLO sẽ kiểm tra sau
            "is_glare_free": not is_too_bright, 
            "laplacian_var": round(laplacian_var, 2),
            "brightness": round(mean_brightness, 2),
            "rejection_reason": " ".join(rejection_reasons)
        }
        return quality

    def _infer_gender(self, so_cccd: str) -> str:
        if not so_cccd or len(so_cccd) != 12 or not so_cccd.isdigit():
            return ""
        gender_code = so_cccd[3]
        if gender_code in ['0', '2', '4', '6', '8']:
            return "Nam"
        elif gender_code in ['1', '3', '5', '7', '9']:
            return "Nữ"
        return ""

    def process_cccd(self, front_img_path: str) -> Dict[str, Any]:
        self._validate_image(front_img_path)
        
        try:
            # Đọc ảnh hỗ trợ đường dẫn tiếng Việt
            import numpy as np
            image = cv2.imdecode(np.fromfile(front_img_path, dtype=np.uint8), cv2.IMREAD_COLOR)
            if image is None:
                raise CCCDExtractionError("Không thể đọc dữ liệu ảnh.")
            
            # 1. Kiểm tra chất lượng ảnh trước
            quality_check = self._check_image_quality(image)
            if not quality_check["is_clear"]:
                # Nếu ảnh quá mờ, từ chối xử lý
                return {
                    "status": "ERROR",
                    "error_message": quality_check["rejection_reason"],
                    "image_quality_check": quality_check,
                    "extracted_data": {}
                }
                
            # 2. Xử lý OCR
            try:
                raw_extracted = self.model.predict(image)
            except Exception as e:
                # Nếu YOLO crop bị lỗi index do không tìm thấy góc -> Ảnh không đủ thông tin
                traceback.print_exc()
                return {
                    "status": "ERROR",
                    "error_message": "Không nhận diện được khung CCCD hoặc góc chụp quá khó quét. Vui lòng chụp rõ mặt trước CCCD.",
                    "image_quality_check": {
                        "is_clear": True,
                        "is_full_document_visible": False,
                        "is_glare_free": True,
                        "rejection_reason": "Không tìm thấy đủ thông tin CCCD."
                    },
                    "extracted_data": {}
                }

            # Map fields from the local OCR output to the standard schema
            # Output of predict(): {'id', 'name', 'birth', 'home', 'add'}
            so_cccd = raw_extracted.get('id', '')
            gioi_tinh = self._infer_gender(so_cccd)
            
            extracted_data = {
                "so_cccd": so_cccd,
                "ho_va_ten": raw_extracted.get('name', ''),
                "ngay_sinh": raw_extracted.get('birth', ''),
                "gioi_tinh": gioi_tinh,
                "quoc_tich": "Việt Nam", # Mặc định
                "que_quan": raw_extracted.get('home', ''),
                "noi_thuong_tru": raw_extracted.get('add', ''),
                "ngay_het_han": "" # Model tham khảo không bóc trường này
            }
            
            # Kiểm tra xem có lấy được những trường bắt buộc không (Nếu một trường bị thiếu do che khuất)
            required_fields = {
                "so_cccd": "Số CCCD", 
                "ho_va_ten": "Họ và tên", 
                "ngay_sinh": "Ngày sinh", 
                "que_quan": "Quê quán", 
                "noi_thuong_tru": "Nơi thường trú"
            }
            missing_fields = [name for key, name in required_fields.items() if not str(extracted_data.get(key, '')).strip()]
            
            if missing_fields:
                return {
                    "status": "ERROR",
                    "error_code": "MISSING_REQUIRED_FIELDS",
                    "error_message": f"Ảnh không nhận diện được đủ thông tin do bị mờ hoặc che khuất phần: {', '.join(missing_fields)}. Vui lòng chụp lại toàn bộ thẻ rõ nét, không che các góc hoặc nội dung.",
                    "image_quality_check": quality_check,
                    "extracted_data": extracted_data
                }
            
            if not CCCDValidator.validate_so_cccd(so_cccd):
                return {
                    "status": "ERROR",
                    "error_code": "INVALID_CCCD_NUMBER",
                    "error_message": f"Số CCCD không đủ 12 chữ số hoặc bị che khuất (đọc được '{so_cccd}'). Vui lòng chụp lại ảnh rõ nét phần số CCCD.",
                    "image_quality_check": quality_check,
                    "extracted_data": extracted_data
                }

            if not CCCDValidator.validate_ho_va_ten(extracted_data.get("ho_va_ten")):
                raw_name = extracted_data.get("ho_va_ten", "")
                return {
                    "status": "ERROR",
                    "error_code": "INVALID_FULL_NAME",
                    "error_message": f"Họ và tên trên thẻ không hợp lệ hoặc bị che khuất (đọc được '{raw_name}'). Vui lòng không che thẻ và chụp lại ảnh rõ nét.",
                    "image_quality_check": quality_check,
                    "extracted_data": extracted_data
                }

            if not CCCDValidator.validate_ngay_sinh(extracted_data.get("ngay_sinh")):
                raw_dob = extracted_data.get("ngay_sinh", "")
                return {
                    "status": "ERROR",
                    "error_code": "INVALID_DOB",
                    "error_message": f"Ngày sinh trên thẻ không đúng định dạng DD/MM/YYYY hoặc bị che khuất (đọc được '{raw_dob}'). Vui lòng chụp lại ảnh rõ nét.",
                    "image_quality_check": quality_check,
                    "extracted_data": extracted_data
                }

            if not CCCDValidator.validate_noi_thuong_tru(extracted_data.get("noi_thuong_tru")):
                return {
                    "status": "ERROR",
                    "error_code": "INVALID_ADDRESS",
                    "error_message": "Nơi thường trú trên thẻ không rõ ràng hoặc bị che khuất. Vui lòng chụp lại ảnh rõ nét.",
                    "image_quality_check": quality_check,
                    "extracted_data": extracted_data
                }
            
            # Chạy logic validator chuẩn
            validated_extracted = CCCDValidator.validate_extracted_data(extracted_data)
            
            return {
                "status": "SUCCESS",
                "error_message": "",
                "image_quality_check": quality_check,
                "extracted_data": validated_extracted
            }

        except Exception as e:
            if not isinstance(e, CCCDExtractionError):
                raise CCCDExtractionError(f"Lỗi không xác định trong quá trình bóc tách: {str(e)}")
            raise e
