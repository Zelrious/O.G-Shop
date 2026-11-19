import os
import json
from typing import Dict, Any
from google import genai
from google.genai import types

from .config import Config

class GeminiPostProcessor:
    """
    Agent AI sử dụng Google Gemini API để chuẩn hóa dữ liệu bóc tách từ thẻ CCCD:
    - Nắn chỉnh chính tả địa danh hành chính Việt Nam (Tỉnh/TP, Quận/Huyện, Phường/Xã).
    - Chuẩn hóa in hoa Họ và tên đầy đủ có dấu chuẩn tiếng Việt.
    - Xóa bỏ ký tự nhiễu rác từ mô hình OCR thị giác máy tính.
    - Bảo toàn 100% số CCCD và ngày sinh.
    - Chống ảo giác (anti-hallucination) với temperature=0.0.
    """

    def __init__(self, api_key: str = None, model_name: str = None):
        self.api_key = api_key or os.getenv("GEMINI_API_KEY") or getattr(Config, "GEMINI_API_KEY", None)
        if not self.api_key or self.api_key == "your_gemini_api_key_here":
            raise ValueError("Chưa thiết lập GEMINI_API_KEY trong cấu hình môi trường.")
        self.client = genai.Client(api_key=self.api_key)
        self.model_name = model_name or os.getenv("GEMINI_MODEL_NAME") or getattr(Config, "MODEL_NAME", "gemini-2.0-flash")

    def process(self, ocr_response: Dict[str, Any]) -> Dict[str, Any]:
        """
        Nhận kết quả OCR (chứa extracted_data), chuẩn hóa bằng Gemini Agent và trả về kết quả đã tinh chỉnh.
        Nếu API gặp sự cố hoặc timeout, tự động trả về dữ liệu gốc để không gián đoạn luồng nghiệp vụ.
        """
        extracted = ocr_response.get("extracted_data", {})
        if not extracted or not isinstance(extracted, dict):
            return ocr_response

        prompt = f"""
Bạn là chuyên gia ngôn ngữ và địa danh hành chính Việt Nam phục vụ hệ thống eKYC của sàn thương mại O.G Shop.
Dưới đây là thông tin trích xuất từ thẻ Căn cước công dân (đã qua hệ thống OCR nội bộ). Do nhiễu ảnh, quang học hoặc góc chụp, một số trường có thể bị thiếu dấu câu, sai dấu thanh tiếng Việt hoặc dính chữ địa danh.

DỮ LIỆU ĐẦU VÀO:
{json.dumps(extracted, ensure_ascii=False, indent=2)}

QUY TẮC BẮT BUỘC (CRITICAL RULES):
1. SỐ CCCD (`so_cccd`) và NGÀY SINH (`ngay_sinh`): Giữ nguyên 100% từng chữ số, tuyệt đối không thay đổi hay suy đoán.
2. HỌ VÀ TÊN (`ho_va_ten`): Viết hoa toàn bộ (UPPERCASE) có dấu tiếng Việt chuẩn xác (ví dụ: "NGUYỄN VĂN AN", "TRẦN ĐÌNH TRỌNG").
3. GIỚI TÍNH (`gioi_tinh`): Chuẩn hóa thành "Nam" hoặc "Nữ".
4. QUÊ QUÁN (`que_quan`) và NƠI THƯỜNG TRÚ (`noi_thuong_tru`):
   - Nắn chỉnh chính tả địa danh theo chuẩn phân cấp hành chính Việt Nam (Tỉnh/Thành phố, Quận/Huyện/Thị xã, Phường/Xã/Thị trấn).
   - Thêm dấu phẩy ", " giữa các cấp đơn vị hành chính để câu chữ mạch lạc, dễ đọc.
   - Loại bỏ các ký tự rác từ OCR như "|", "_", "~", ">>", double spaces.
5. CHỐNG BỊA ĐẶT (ANTI-HALLUCINATION):
   - Tuyệt đối không tự bịa ra thông tin mới không có dấu hiệu trong dữ liệu đầu vào.
   - Nếu một địa chỉ hoặc từ không rõ nghĩa, giữ nguyên các từ gốc có thể đọc được.
6. ĐỊNH DẠNG ĐẦU RA:
   - Trả về đúng 1 đối tượng JSON duy nhất có các khóa:
     "so_cccd", "ho_va_ten", "ngay_sinh", "gioi_tinh", "quoc_tich", "que_quan", "noi_thuong_tru", "ngay_het_han"
"""

        try:
            response = self.client.models.generate_content(
                model=self.model_name,
                contents=prompt,
                config=types.GenerateContentConfig(
                    response_mime_type="application/json",
                    temperature=0.0  # Tất định, hoàn toàn không sáng tạo tùy tiện
                )
            )

            result_text = response.text
            if not result_text:
                return ocr_response

            refined_extracted = json.loads(result_text)

            # Bảo đảm các trường then chốt không bị rỗng sau xử lý
            final_extracted = dict(extracted)
            for k in ["ho_va_ten", "que_quan", "noi_thuong_tru", "gioi_tinh"]:
                val = refined_extracted.get(k)
                if val and isinstance(val, str) and val.strip():
                    final_extracted[k] = val.strip()

            # Luôn bảo toàn so_cccd và ngay_sinh gốc
            final_extracted["so_cccd"] = extracted.get("so_cccd", "")
            final_extracted["ngay_sinh"] = extracted.get("ngay_sinh", "")
            final_extracted["ai_standardized"] = True

            updated_response = dict(ocr_response)
            updated_response["extracted_data"] = final_extracted
            return updated_response

        except Exception as e:
            print(f"[Gemini Post-Processor] Cảnh báo lỗi API Agent (tự động fallback về dữ liệu gốc): {str(e)}")
            fallback_extracted = dict(extracted)
            fallback_extracted["ai_standardized"] = False
            updated_response = dict(ocr_response)
            updated_response["extracted_data"] = fallback_extracted
            return updated_response
