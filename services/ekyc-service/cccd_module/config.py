import os

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

class Config:
    """Cấu hình hệ thống cho module trích xuất CCCD."""
    
    # Lấy API Key từ biến môi trường
    GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
    
    # Tên mô hình Gemini được sử dụng (mặc định gemini-3.5-flash cho tài khoản miễn phí)
    MODEL_NAME = os.getenv("GEMINI_MODEL_NAME", "gemini-3.5-flash")
    
    # Thời gian chờ tối đa cho mỗi yêu cầu API (giây)
    TIMEOUT = int(os.getenv("API_TIMEOUT", 60))
    
    # Định dạng ảnh được phép xử lý
    ALLOWED_EXTENSIONS = {'.png', '.jpg', '.jpeg', '.webp', '.jfif'}
    
    # Giới hạn dung lượng tối đa mỗi file ảnh (MB)
    MAX_FILE_SIZE_MB = int(os.getenv("MAX_FILE_SIZE_MB", 4))
    
    # Số lần thử lại tối đa khi gặp lỗi kết nối API
    MAX_RETRIES = int(os.getenv("MAX_RETRIES", 3))
    
    # Thời gian chờ giữa các lần thử lại (giây)
    RETRY_DELAY = int(os.getenv("RETRY_DELAY", 2))
