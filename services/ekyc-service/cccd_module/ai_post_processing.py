import json
from typing import Dict, Any
from google import genai
from google.genai import types

from .config import Config

class GeminiPostProcessor:
    def __init__(self):
        if not Config.GEMINI_API_KEY:
            raise ValueError("Chưa thiết lập GEMINI_API_KEY trong cấu hình.")
        self.client = genai.Client(api_key=Config.GEMINI_API_KEY)
        self.model_name = Config.MODEL_NAME

    def process(self, ocr_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Sử dụng Gemini để hậu xử lý văn bản, sửa lỗi chính tả nhỏ giọt.
        Không thay đổi nội dung nếu không chắc chắn.
        """
        prompt = f"""
Bạn là một trợ lý AI chuyên sửa lỗi chính tả tiếng Việt.
Dưới đây là thông tin trích xuất từ thẻ Căn cước công dân (đã qua hệ thống OCR nội bộ). Do nhiễu ảnh, một số trường có thể bị thiếu dấu phẩy hoặc sai chính tả nhẹ ở tên địa danh (Ví dụ: "Lê Hồng Phong TP Quy Nhơn" thay vì "Lê Hồng Phong, TP. Quy Nhơn").

Nhiệm vụ của bạn:
1. Giữ nguyên toàn bộ cấu trúc JSON.
2. Tuyệt đối KHÔNG TỰ BỊA (hallucinate) ra thông tin mới. Chỉ thêm dấu phẩy, dấu chấm, hoặc nắn lại dấu tiếng Việt cho chuẩn xác.
3. Số CCCD và Ngày sinh phải giữ nguyên y hệt.
4. Trả về đúng định dạng JSON chuẩn.

Dữ liệu đầu vào:
{json.dumps(ocr_data, ensure_ascii=False, indent=2)}
"""
        try:
            response = self.client.models.generate_content(
                model=self.model_name,
                contents=prompt,
                config=types.GenerateContentConfig(
                    response_mime_type="application/json",
                    temperature=0.1 # Rất thấp để tránh bịa data
                )
            )
            
            # Phân tích kết quả JSON
            result_text = response.text
            if not result_text:
                return ocr_data
                
            refined_data = json.loads(result_text)
            
            # Đảm bảo trả về đúng cấu trúc chuẩn của hệ thống
            if "status" not in refined_data:
                refined_data = {
                    "status": "SUCCESS",
                    "error_message": "",
                    "image_quality_check": ocr_data.get("image_quality_check", {}),
                    "extracted_data": refined_data.get("extracted_data", refined_data)
                }
            return refined_data
            
        except Exception as e:
            print(f"[Gemini Post-Processor] Lỗi khi gọi API: {str(e)}")
            # Nếu API lỗi, trả về data gốc an toàn
            return ocr_data
