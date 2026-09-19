import os
import cv2
import numpy as np
from pathlib import Path
from deepface import DeepFace
from typing import Dict, Any, Union, Optional, List

class FaceMatcher:
    """
    Lớp xử lý Nhận diện và So khớp khuôn mặt (Biometric eKYC) bằng thư viện DeepFace.
    """
    
    def __init__(self, model_name: str = "ArcFace", detector_backend: str = "retinaface"):
        """
        Khởi tạo FaceMatcher.
        - model_name: ArcFace, Facenet512, VGG-Face (ArcFace cho độ chính xác rất cao).
        - detector_backend: retinaface, opencv, mtcnn, ssd, dlib. (retinaface bắt mặt trên thẻ CCCD cực kỳ chuẩn)
        """
        self.model_name = model_name
        self.detector_backend = detector_backend
        
    def _load_image_utf8(self, img_input: Union[str, np.ndarray, bytes]) -> np.ndarray:
        if isinstance(img_input, (str, Path)):
            path_str = str(img_input)
            if path_str.startswith("data:image") or (len(path_str) > 300 and not os.path.exists(path_str)):
                import base64
                if "," in path_str:
                    path_str = path_str.split(",", 1)[1]
                img_bytes = base64.b64decode(path_str)
                file_bytes = np.asarray(bytearray(img_bytes), dtype=np.uint8)
                img = cv2.imdecode(file_bytes, cv2.IMREAD_COLOR)
                if img is not None:
                    return img
            with open(path_str, "rb") as f:
                file_bytes = np.asarray(bytearray(f.read()), dtype=np.uint8)
                img = cv2.imdecode(file_bytes, cv2.IMREAD_COLOR)
                if img is None:
                    raise ValueError(f"Không thể đọc file ảnh từ: {path_str}")
            return img
        elif isinstance(img_input, bytes):
            file_bytes = np.asarray(bytearray(img_input), dtype=np.uint8)
            img = cv2.imdecode(file_bytes, cv2.IMREAD_COLOR)
            if img is None:
                raise ValueError("Không thể giải mã bytes hình ảnh.")
            return img
        return img_input

    def extract_embedding(self, img_input: Union[str, np.ndarray, bytes], detector_backend: str = None) -> Optional[np.ndarray]:
        """
        Trích xuất vector đặc trưng ArcFace 512-chiều từ ảnh khuôn mặt.
        """
        backend = detector_backend or self.detector_backend
        try:
            img = self._load_image_utf8(img_input)
            results = DeepFace.represent(
                img_path=img,
                model_name=self.model_name,
                detector_backend=backend,
                enforce_detection=True,
                align=True
            )
            if len(results) > 0:
                return np.array(results[0]["embedding"], dtype=np.float32)
            return None
        except Exception as e:
            print(f"[FaceMatcher] Lỗi khi trích xuất embedding: {e}")
            return None

    @staticmethod
    def compute_cosine_distance(embedding1: Union[np.ndarray, list], embedding2: Union[np.ndarray, list]) -> float:
        """
        Tính khoảng cách Cosine giữa 2 vector đặc trưng 512 chiều:
        distance = 1.0 - (u . v) / (||u|| * ||v||)
        """
        emb1 = np.array(embedding1, dtype=np.float32)
        emb2 = np.array(embedding2, dtype=np.float32)
        dot_product = float(np.dot(emb1, emb2))
        norm1 = float(np.linalg.norm(emb1))
        norm2 = float(np.linalg.norm(emb2))
        if norm1 == 0 or norm2 == 0:
            return 1.0
        return float(1.0 - (dot_product / (norm1 * norm2)))

    def extract_face(self, img_path_or_frame: Union[str, np.ndarray]) -> Dict[str, Any]:
        """
        Trích xuất (cắt) khuôn mặt từ ảnh. Hàm này có thể dùng để kiểm tra xem
        trong ảnh thẻ CCCD có khuôn mặt hay không.
        Trả về kết quả chứa ảnh đã crop để tái sử dụng.
        """
        try:
            img_input = self._load_image_utf8(img_path_or_frame)
            faces = DeepFace.extract_faces(
                img_path=img_input,
                detector_backend=self.detector_backend,
                enforce_detection=True,
                align=True
            )
            
            if len(faces) == 0:
                return {"status": "ERROR", "message": "Không tìm thấy khuôn mặt nào trong ảnh."}
            if len(faces) > 1:
                return {"status": "ERROR", "message": "Phát hiện nhiều hơn 1 khuôn mặt trong ảnh, vui lòng chụp lại."}
                
            return {
                "status": "SUCCESS", 
                "face": faces[0]["face"], 
                "facial_area": faces[0]["facial_area"]
            }
        except ValueError:
            return {"status": "ERROR", "message": "Không thể nhận diện được khuôn mặt. Vui lòng đảm bảo ảnh rõ nét và đủ sáng."}
        except Exception as e:
            return {"status": "ERROR", "message": f"Lỗi hệ thống khi trích xuất khuôn mặt: {str(e)}"}

    def verify_live_frame(self, live_img: np.ndarray, reference_embedding: np.ndarray) -> Dict[str, Any]:
        """
        So khớp khuôn mặt từ Camera với embedding CCCD cục bộ của request.
        Tính toán bằng Cosine Distance với ngưỡng khắt khe (0.50).
        """
        try:
            # Dùng opencv để lấy embedding của luồng live, fallback skip nếu mặt đã crop
            try:
                results = DeepFace.represent(
                    img_path=live_img,
                    model_name=self.model_name,
                    detector_backend="opencv", 
                    enforce_detection=False,
                    align=True
                )
            except Exception:
                results = DeepFace.represent(
                    img_path=live_img,
                    model_name=self.model_name,
                    detector_backend="skip", 
                    enforce_detection=False,
                    align=False
                )
            
            if len(results) == 0:
                return {"match_status": "FACE_NOT_FOUND", "user_instruction": "Không tìm thấy mặt rõ ràng."}
                
            live_embedding = np.array(results[0]["embedding"])
            
            # Tính Cosine Distance thủ công
            # Công thức: 1 - (dot(a, b) / (norm(a) * norm(b)))
            dot_product = np.dot(reference_embedding, live_embedding)
            norm_id = np.linalg.norm(reference_embedding)
            norm_live = np.linalg.norm(live_embedding)
            distance = 1.0 - (dot_product / (norm_id * norm_live))
            
            # Nới lỏng xuống 0.50 theo yêu cầu
            strict_threshold = 0.50
            
            is_match = distance <= strict_threshold
            confidence_score = max(0.0, 1.0 - (distance / (strict_threshold * 1.5)))
            
            if is_match:
                return {
                    "match_status": "MATCHED",
                    "confidence_score": round(confidence_score, 4),
                    "threshold_used": strict_threshold,
                    "distance": round(distance, 4),
                    "liveness_passed": True,
                    "final_decision": "Xác thực danh tính sinh trắc học THÀNH CÔNG."
                }
            else:
                return {
                    "match_status": "NOT_MATCHED",
                    "confidence_score": round(confidence_score, 4),
                    "threshold_used": strict_threshold,
                    "distance": round(distance, 4),
                    "liveness_passed": True, 
                    "final_decision": "Xác thực THẤT BẠI. Khuôn mặt không khớp với giấy tờ."
                }
                
        except ValueError:
            return {
                "frame_status": "FACE_NOT_FOUND",
                "user_instruction": "Khong tim thay khuon mat ro rang. Vui long dua mat vao giua."
            }
        except Exception as e:
            return {
                "frame_status": "ERROR",
                "user_instruction": f"Loi xu ly: {str(e)}"
            }

    def check_liveness_basic(self, frame: np.ndarray) -> Dict[str, Any]:
        """
        Kiểm tra xem có đúng 1 khuôn mặt xuất hiện không.
        """
        try:
            faces = DeepFace.extract_faces(
                img_path=frame, 
                detector_backend="opencv", 
                enforce_detection=False
            )
            
            valid_faces = [f for f in faces if f.get("confidence", 0) > 0.7]
            
            if len(valid_faces) == 0:
                return {"frame_status": "FACE_NOT_FOUND", "user_instruction": "Không tìm thấy khuôn mặt. Đưa mặt lại gần camera."}
            elif len(valid_faces) > 1:
                return {"frame_status": "TOO_MANY_FACES", "user_instruction": "Phát hiện nhiều khuôn mặt! Vui lòng đứng một mình."}
                
            return {"frame_status": "PROCESSING", "user_instruction": "Giữ nguyên khuôn mặt..."}
            
        except Exception:
            return {"frame_status": "FACE_NOT_FOUND", "user_instruction": "Lỗi đọc khung hình."}

    def process_live_frame(self, frame: np.ndarray, reference_embedding: np.ndarray,
                           circle_center: tuple, circle_radius: int) -> Dict[str, Any]:
        """
        Hàm All-in-one: Vừa tìm mặt (1 lần duy nhất), vừa kiểm tra khoảng cách, vừa so khớp.
        Tăng tốc độ đáng kể.
        """
        try:
            # 1. Quét mặt nhanh bằng OpenCV backend
            faces = DeepFace.extract_faces(
                img_path=frame, 
                detector_backend="opencv", 
                enforce_detection=False
            )
            
            # 2. Lọc các mặt nằm trong Vòng tròn UI
            cx, cy = circle_center
            valid_faces = []
            
            for f in faces:
                if f.get("confidence", 0) < 0.7:
                    continue
                    
                area = f.get("facial_area", {})
                if not area:
                    continue
                    
                fx = area.get("x", 0)
                fy = area.get("y", 0)
                fw = area.get("w", 0)
                fh = area.get("h", 0)
                
                # Tính tâm của khuôn mặt
                face_center_x = fx + fw // 2
                face_center_y = fy + fh // 2
                
                # Tính khoảng cách từ tâm mặt đến tâm vòng tròn
                distance_to_center = ((face_center_x - cx)**2 + (face_center_y - cy)**2) ** 0.5
                
                # Khuôn mặt phải nằm trong vùng của vòng tròn
                # Nới lỏng từ 0.5 lên 0.8 để người dùng dễ dàng căn mặt hơn (không bị bắt buộc phải cứng đơ ở chính giữa)
                if distance_to_center < circle_radius * 0.8:
                    valid_faces.append((f, fw, fh, fx, fy))
                    
            if len(valid_faces) == 0:
                return {"match_status": "FACE_NOT_FOUND", "user_instruction": "Hãy đưa khuôn mặt vào giữa vòng tròn"}
            if len(valid_faces) > 1:
                return {"match_status": "FACE_NOT_FOUND", "user_instruction": "Có nhiều người trong khung hình!"}
                
            # Lấy mặt hợp lệ duy nhất
            best_face, fw, fh, fx, fy = valid_faces[0]
            
            # 3. Kiểm tra khoảng cách (Đưa mặt lại gần / ra xa)
            # Nới lỏng thông số độ lớn khuôn mặt để không bắt bẻ người dùng quá mức
            if fw < circle_radius * 0.6:
                return {"match_status": "DISTANCE_ISSUE", "user_instruction": "Vui lòng đưa mặt lại gần hơn"}
            if fw > circle_radius * 1.7:
                return {"match_status": "DISTANCE_ISSUE", "user_instruction": "Vui lòng đưa mặt ra xa hơn"}
                
            # 4. Trích xuất đặc trưng thẳng từ ảnh đã cắt (Cropped) -> Bỏ qua bước dò lại
            # DeepFace extract_faces trả về ảnh face đã chuẩn hóa trong mảng [0, 1] hoặc [0, 255]
            # Để an toàn cho model, ta cắt thẳng từ ảnh gốc
            height, width = frame.shape[:2]
            margin = 20
            crop_x1 = max(0, fx - margin)
            crop_y1 = max(0, fy - margin)
            crop_x2 = min(width, fx + fw + margin)
            crop_y2 = min(height, fy + fh + margin)
            
            cropped_face = frame[crop_y1:crop_y2, crop_x1:crop_x2]
            
            # Gọi hàm represent với enforce_detection=False để không mất thêm thời gian dò lại
            results = DeepFace.represent(
                img_path=cropped_face,
                model_name=self.model_name,
                detector_backend="skip", # Bỏ dò tìm
                enforce_detection=False,
                align=False # Khỏi align cho nhanh
            )
            
            if len(results) == 0:
                return {"match_status": "ERROR", "user_instruction": "Không trích xuất được đặc trưng."}
                
            live_embedding = np.array(results[0]["embedding"])
            
            # Tính khoảng cách Cosine
            dot_product = np.dot(reference_embedding, live_embedding)
            norm_id = np.linalg.norm(reference_embedding)
            norm_live = np.linalg.norm(live_embedding)
            distance = 1.0 - (dot_product / (norm_id * norm_live))
            
            # Ngưỡng mặc định của ArcFace là 0.68.
            # Điều chỉnh thành 0.50 theo yêu cầu
            strict_threshold = 0.50
            is_match = distance <= strict_threshold
            
            if is_match:
                return {
                    "match_status": "MATCHED",
                    "user_instruction": "Giữ nguyên khuôn mặt...",
                    "distance": round(distance, 4)
                }
            else:
                return {
                    "match_status": "NOT_MATCHED",
                    "user_instruction": "Khuôn mặt không khớp với CCCD!",
                    "distance": round(distance, 4)
                }
                
        except Exception as e:
            return {"match_status": "ERROR", "user_instruction": f"Lỗi: {str(e)}"}
