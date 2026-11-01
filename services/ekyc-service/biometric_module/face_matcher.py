import os
import io
import cv2
import time
import numpy as np
from pathlib import Path
from collections import deque
from typing import Dict, Any, Union, Optional, List, Tuple
from PIL import Image, ImageOps
from deepface import DeepFace


class MultiFrameVerificationBuffer:
    """
    Buffer quản lý đa khung hình (Multi-frame Matching Buffer) cho xác thực Real-time.
    Lưu trữ kết quả từ tối đa 10 khung hình hợp lệ gần nhất và tổng hợp điểm bằng Median / Score Fusion.
    Đồng thời kiểm tra độ ổn định (Standard Deviation) để loại bỏ hiện tượng giật điểm hoặc trôi điểm.
    """

    def __init__(self, max_capacity: int = 10, ttl_seconds: float = 60.0):
        self.max_capacity = max_capacity
        self.ttl_seconds = ttl_seconds
        self.buffer = deque(maxlen=max_capacity)
        self.created_at = time.time()
        self.last_updated = time.time()

    def add_frame(
        self,
        distance_card: float,
        distance_portrait: float,
        frame_bgr: np.ndarray,
        quality_score: float,
        facial_area: Dict[str, Any],
    ) -> None:
        now = time.time()
        self.buffer.append({
            "timestamp": now,
            "distance_card": float(distance_card),
            "distance_portrait": float(distance_portrait),
            "quality_score": float(quality_score),
            "frame_bgr": frame_bgr.copy() if frame_bgr is not None else None,
            "facial_area": facial_area,
        })
        self.last_updated = now

    def is_expired(self) -> bool:
        return (time.time() - self.last_updated) > self.ttl_seconds

    def clear(self) -> None:
        self.buffer.clear()
        self.last_updated = time.time()

    def get_count(self) -> int:
        return len(self.buffer)

    def get_best_frame(self) -> Optional[np.ndarray]:
        """Lấy khung hình có chất lượng cao nhất trong buffer (dùng cho bằng chứng / KTV duyệt)."""
        if not self.buffer:
            return None
        best_item = max(self.buffer, key=lambda x: x.get("quality_score", 0.0))
        return best_item.get("frame_bgr")

    def evaluate(
        self,
        threshold_card: float,
        threshold_portrait: float,
        min_frames: int = 3,
        max_std: float = 0.12,
    ) -> Dict[str, Any]:
        """
        Tổng hợp kết quả so khớp đa khung hình:
        - Số frame hợp lệ tối thiểu: min_frames (mặc định 3, có thể truyền 5).
        - Sử dụng Median distance để triệt tiêu outlier (chớp mắt, giật sáng).
        - Kiểm tra Standard Deviation: nếu độ dao động > max_std (0.12) -> UNSTABLE.
        - Hỗ trợ lọc outlier đơn lẻ (Trimmed Std) khi gom từ 4 khung hình trở lên.
        """
        count = len(self.buffer)
        if count < min_frames:
            return {
                "decision": "COLLECTING_FRAMES",
                "is_match": False,
                "frames_collected": count,
                "min_frames_needed": min_frames,
                "median_distance_card": None,
                "median_distance_portrait": None,
                "stability_std": None,
                "message": f"Đang thu thập đủ dữ liệu đối chiếu ({count}/{min_frames} khung hình)...",
            }

        # Trích xuất danh sách khoảng cách
        card_dists = [item["distance_card"] for item in self.buffer]
        portrait_dists = [item["distance_portrait"] for item in self.buffer]

        med_card = float(np.median(card_dists))
        med_portrait = float(np.median(portrait_dists))

        std_card = float(np.std(card_dists))
        std_portrait = float(np.std(portrait_dists))
        max_observed_std = max(std_card, std_portrait)

        # Nếu độ lệch chuẩn vượt ngưỡng, kiểm tra xem có phải do 1 khung hình ngoại lai (outlier do chớp mắt) không
        if max_observed_std > max_std and count >= 4:
            card_diffs = [abs(d - med_card) for d in card_dists]
            port_diffs = [abs(d - med_portrait) for d in portrait_dists]
            filtered_card = [d for i, d in enumerate(card_dists) if i != int(np.argmax(card_diffs))]
            filtered_port = [d for i, d in enumerate(portrait_dists) if i != int(np.argmax(port_diffs))]
            trimmed_std = max(float(np.std(filtered_card)), float(np.std(filtered_port)))
            if trimmed_std <= max_std:
                max_observed_std = trimmed_std

        if max_observed_std > max_std:
            return {
                "decision": "UNSTABLE",
                "is_match": False,
                "frames_collected": count,
                "min_frames_needed": min_frames,
                "median_distance_card": round(med_card, 4),
                "median_distance_portrait": round(med_portrait, 4),
                "stability_std": round(max_observed_std, 4),
                "message": "Điểm số nhận diện đang dao động lớn. Vui lòng giữ yên khuôn mặt trước camera.",
            }

        card_ok = med_card <= threshold_card
        portrait_ok = med_portrait <= threshold_portrait
        # Cơ chế so khớp đa mẫu thích ứng:
        # 1. Khớp cả 2 mẫu
        # 2. Hoặc khớp 1 mẫu chính (<= threshold) và mẫu phụ trong dung sai cho phép (<= 0.55), loại bỏ người lạ hoàn toàn (> 0.60)
        is_match = (card_ok and portrait_ok) or (card_ok and med_portrait <= 0.55) or (portrait_ok and med_card <= 0.55)

        decision = "MATCHED" if is_match else "NOT_MATCHED"
        return {
            "decision": decision,
            "is_match": is_match,
            "frames_collected": count,
            "min_frames_needed": min_frames,
            "median_distance_card": round(med_card, 4),
            "median_distance_portrait": round(med_portrait, 4),
            "card_matched": card_ok,
            "portrait_matched": portrait_ok,
            "stability_std": round(max_observed_std, 4),
            "message": "Khuôn mặt hợp lệ! Đã xác thực thành công cả thẻ CCCD và ảnh chân dung." if is_match else "Khuôn mặt chưa trùng khớp với hồ sơ định danh.",
        }


class FaceMatcher:
    """
    Lớp xử lý Nhận diện, Đánh giá chất lượng và So khớp khuôn mặt (Biometric eKYC)
    sử dụng mô hình ArcFace 512-chiều với đồng nhất tiền xử lý, kiểm tra định dạng và
    đối chiếu đa khung hình (Multi-frame Matching Buffer).
    """

    def __init__(self, model_name: str = "ArcFace", detector_backend: str = "retinaface"):
        self.model_name = model_name
        self.detector_backend = detector_backend
        self.session_buffers: Dict[str, MultiFrameVerificationBuffer] = {}

    def get_or_create_buffer(self, session_id: str) -> MultiFrameVerificationBuffer:
        now = time.time()
        # Dọn dẹp các session buffer cũ quá 10 phút
        expired_keys = [k for k, v in self.session_buffers.items() if (now - v.last_updated) > 600.0]
        for k in expired_keys:
            self.session_buffers.pop(k, None)

        if session_id not in self.session_buffers:
            self.session_buffers[session_id] = MultiFrameVerificationBuffer()
        return self.session_buffers[session_id]

    def _load_image_utf8(self, img_input: Union[str, np.ndarray, bytes, Path]) -> np.ndarray:
        """Đọc ảnh hỗ trợ base64, bytes, numpy array, đường dẫn UTF-8 trên Windows và tự động xoay chuẩn theo EXIF Orientation."""
        if isinstance(img_input, np.ndarray):
            return img_input

        raw_bytes: Optional[bytes] = None
        if isinstance(img_input, (str, Path)):
            path_str = str(img_input)
            if path_str.startswith("data:image") or (len(path_str) > 300 and not os.path.exists(path_str)):
                import base64
                if "," in path_str:
                    path_str = path_str.split(",", 1)[1]
                raw_bytes = base64.b64decode(path_str)
            else:
                with open(path_str, "rb") as f:
                    raw_bytes = f.read()
        elif isinstance(img_input, bytes):
            raw_bytes = img_input

        if raw_bytes is None:
            raise ValueError(f"Kiểu dữ liệu ảnh không được hỗ trợ: {type(img_input)}")

        # Thử giải mã qua PIL để tự động xoay chuẩn theo EXIF Orientation (khắc phục ảnh smartphone bị quay ngang 90/270 độ)
        try:
            pil_img = Image.open(io.BytesIO(raw_bytes))
            pil_img = ImageOps.exif_transpose(pil_img)
            img_rgb = np.array(pil_img.convert("RGB"))
            return cv2.cvtColor(img_rgb, cv2.COLOR_RGB2BGR)
        except Exception:
            # Fallback về cv2.imdecode
            file_bytes = np.asarray(bytearray(raw_bytes), dtype=np.uint8)
            img = cv2.imdecode(file_bytes, cv2.IMREAD_COLOR)
            if img is None:
                raise ValueError("Không thể giải mã hình ảnh.")
            return img

    @staticmethod
    def validate_embedding(embedding: Any) -> bool:
        """
        Kiểm tra tính hợp lệ của vector embedding:
        - Không None, đúng kiểu numpy array hoặc list
        - Đúng 512 chiều
        - Không chứa NaN hoặc Infinity
        - Vector norm > 1e-6 (không phải vector 0)
        """
        if embedding is None:
            return False
        try:
            arr = np.asarray(embedding, dtype=np.float32)
            if arr.ndim != 1 or arr.shape[0] != 512:
                return False
            if not np.isfinite(arr).all():
                return False
            norm = float(np.linalg.norm(arr))
            if norm < 1e-6 or not np.isfinite(norm):
                return False
            return True
        except Exception:
            return False

    @staticmethod
    def normalize_embedding(embedding: Union[np.ndarray, list]) -> np.ndarray:
        """Chuẩn hóa L2 cho vector embedding để tính Cosine Similarity / Distance chính xác."""
        arr = np.asarray(embedding, dtype=np.float32).flatten()
        norm = float(np.linalg.norm(arr))
        if norm < 1e-6 or not np.isfinite(norm):
            raise ValueError("Không thể chuẩn hóa vector embedding có norm = 0 hoặc không hữu hạn.")
        return arr / norm

    @staticmethod
    def compute_cosine_distance(embedding1: Union[np.ndarray, list], embedding2: Union[np.ndarray, list]) -> float:
        """
        Tính khoảng cách Cosine giữa 2 vector đặc trưng 512 chiều:
        distance = 1.0 - (u . v) / (||u|| * ||v||)
        Quy tắc:
        - Nếu embedding không hợp lệ, dimension sai hoặc norm = 0: ném ValueError.
        - Tuyệt đối không cho phép lỗi số học trở thành MATCHED.
        - Kết quả được giới hạn chặt chẽ trong khoảng [0.0, 2.0].
        """
        if not FaceMatcher.validate_embedding(embedding1) or not FaceMatcher.validate_embedding(embedding2):
            raise ValueError("Vector embedding không hợp lệ, sai kích thước (phải là 512 chiều) hoặc chứa NaN/Infinity.")

        v1 = FaceMatcher.normalize_embedding(embedding1)
        v2 = FaceMatcher.normalize_embedding(embedding2)

        dot_product = float(np.dot(v1, v2))
        if not np.isfinite(dot_product):
            raise ValueError("Lỗi số học: Dot product của 2 vector không hữu hạn.")

        distance = 1.0 - dot_product
        return float(np.clip(distance, 0.0, 2.0))

    def assess_face_quality(
        self,
        face_img_bgr: np.ndarray,
        facial_area: Dict[str, Any],
        min_size: int = 60,
        min_sharpness: float = 12.0,
    ) -> Dict[str, Any]:
        """
        Đánh giá chất lượng khuôn mặt trên vùng face crop thực tế:
        - Độ phân giải tối thiểu (chiều rộng & chiều cao)
        - Độ sắc nét (Laplacian variance trên grayscale)
        - Độ sáng trung bình (Mean brightness)
        - Độ tương phản (Standard deviation)
        """
        if face_img_bgr is None or face_img_bgr.size == 0:
            return {"is_valid": False, "status": "EMPTY", "message": "Ảnh khuôn mặt rỗng."}

        fh, fw = face_img_bgr.shape[:2]
        if fw < min_size or fh < min_size:
            return {
                "is_valid": False,
                "status": "TOO_SMALL",
                "message": f"Khuôn mặt quá nhỏ ({fw}x{fh} < {min_size}x{min_size} px).",
                "face_width": fw,
                "face_height": fh,
            }

        gray = cv2.cvtColor(face_img_bgr, cv2.COLOR_BGR2GRAY) if face_img_bgr.ndim == 3 else face_img_bgr
        lap_var = float(cv2.Laplacian(gray, cv2.CV_64F).var())
        mean_brightness = float(np.mean(gray))
        std_contrast = float(np.std(gray))

        quality_score = float(min(1.0, (lap_var / 50.0) * 0.5 + (std_contrast / 50.0) * 0.5))

        if lap_var < min_sharpness:
            return {
                "is_valid": False,
                "status": "BLURRY",
                "message": f"Ảnh khuôn mặt bị mờ hoặc rung tay (độ nét {lap_var:.1f} < {min_sharpness:.1f}).",
                "sharpness": round(lap_var, 2),
                "brightness": round(mean_brightness, 2),
                "quality_score": round(quality_score, 3),
            }

        if mean_brightness < 20.0:
            return {
                "is_valid": False,
                "status": "TOO_DARK",
                "message": f"Khuôn mặt quá tối (độ sáng {mean_brightness:.1f} < 20).",
                "sharpness": round(lap_var, 2),
                "brightness": round(mean_brightness, 2),
                "quality_score": round(quality_score, 3),
            }

        if mean_brightness > 252.0:
            return {
                "is_valid": False,
                "status": "OVEREXPOSED",
                "message": f"Khuôn mặt bị chói sáng (độ sáng {mean_brightness:.1f} > 252).",
                "sharpness": round(lap_var, 2),
                "brightness": round(mean_brightness, 2),
                "quality_score": round(quality_score, 3),
            }

        if std_contrast < 7.0:
            return {
                "is_valid": False,
                "status": "POOR_CONTRAST",
                "message": f"Khuôn mặt có độ tương phản quá kém ({std_contrast:.1f} < 7).",
                "sharpness": round(lap_var, 2),
                "brightness": round(mean_brightness, 2),
                "quality_score": round(quality_score, 3),
            }

        return {
            "is_valid": True,
            "status": "OK",
            "message": "Chất lượng khuôn mặt đạt chuẩn.",
            "sharpness": round(lap_var, 2),
            "brightness": round(mean_brightness, 2),
            "contrast": round(std_contrast, 2),
            "quality_score": round(quality_score, 3),
        }

    def _extract_aligned_arcface_embedding(
        self,
        img: np.ndarray,
        detector_backend: str,
        facial_area: Optional[Dict[str, Any]] = None,
    ) -> Optional[np.ndarray]:
        """
        Trích xuất vector ArcFace 512 chiều với căn chỉnh chuẩn (align=True) và
        chuẩn hóa L2 đồng nhất cho cả CCCD, Portrait và Live Face.
        """
        try:
            # Nếu cung cấp facial_area cụ thể (ví dụ vùng chân dung CCCD đã chọn lọc),
            # thực hiện crop với margin an toàn trước khi gọi represent
            img_to_represent = img
            if facial_area is not None:
                h, w = img.shape[:2]
                fx = max(0, int(facial_area.get("x", 0)))
                fy = max(0, int(facial_area.get("y", 0)))
                fw = int(facial_area.get("w", w))
                fh = int(facial_area.get("h", h))
                margin_x = int(fw * 0.35)
                margin_y = int(fh * 0.35)
                x1 = max(0, fx - margin_x)
                y1 = max(0, fy - margin_y)
                x2 = min(w, fx + fw + margin_x)
                y2 = min(h, fy + fh + margin_y)
                img_to_represent = img[y1:y2, x1:x2]

            # Nếu vùng ảnh nhỏ hơn 120x120 px, phóng to để detector hoạt động ổn định
            ch, cw = img_to_represent.shape[:2]
            if min(ch, cw) < 120 and min(ch, cw) > 0:
                scale = 120.0 / min(ch, cw)
                img_to_represent = cv2.resize(img_to_represent, (int(cw * scale), int(ch * scale)), interpolation=cv2.INTER_CUBIC)

            results = None
            for det in [detector_backend, "yunet", "opencv"]:
                for enforce in [True, False]:
                    try:
                        results = DeepFace.represent(
                            img_path=img_to_represent,
                            model_name=self.model_name,
                            detector_backend=det,
                            enforce_detection=enforce,
                            align=True,
                        )
                        if results and len(results) > 0:
                            break
                    except Exception:
                        continue
                if results and len(results) > 0:
                    break

            if results and len(results) > 0:
                raw_emb = results[0]["embedding"]
                if self.validate_embedding(raw_emb):
                    return self.normalize_embedding(raw_emb)
            return None
        except Exception:
            return None

    def extract_cccd_portrait(
        self,
        card_img_or_path: Union[str, np.ndarray, bytes],
    ) -> Dict[str, Any]:
        """
        Trích xuất ảnh chân dung trên mặt trước CCCD một cách nghiêm ngặt:
        - Định vị khuôn mặt nằm đúng khu vực chân dung của CCCD Việt Nam (nửa trái, phần thân dưới tiêu đề).
        - Loại trừ các phát hiện giả mạo (hoa văn, con dấu quốc huy, chữ viết, ảnh nhỏ < 8%).
        - Kiểm tra chất lượng ảnh chân dung trên thẻ.
        - Trích xuất embedding ArcFace 512-chiều đã căn chỉnh (align=True).
        """
        try:
            img = self._load_image_utf8(card_img_or_path)
            h, w = img.shape[:2]
            if max(h, w) > 1280:
                scale = 1280.0 / max(h, w)
                img = cv2.resize(img, (int(w * scale), int(h * scale)), interpolation=cv2.INTER_AREA)
                h, w = img.shape[:2]

            # Dò tìm khuôn mặt với retinaface (chính xác nhất trên thẻ), fallback yunet, opencv
            detected_faces = []
            used_backend = "retinaface"
            for det in ["retinaface", "yunet", "opencv"]:
                try:
                    faces = DeepFace.extract_faces(
                        img_path=img,
                        detector_backend=det,
                        enforce_detection=False,
                        align=True,
                    )
                    # Lọc bỏ placeholder toàn màn hình do enforce_detection=False
                    valid = [
                        f for f in faces
                        if not (
                            f.get("facial_area", {}).get("x", 0) == 0
                            and f.get("facial_area", {}).get("y", 0) == 0
                            and f.get("facial_area", {}).get("w", 0) >= w * 0.9
                            and f.get("facial_area", {}).get("h", 0) >= h * 0.9
                        )
                    ]
                    if valid:
                        detected_faces = valid
                        used_backend = det
                        break
                except Exception:
                    continue

            if not detected_faces:
                return {
                    "status": "FACE_NOT_FOUND",
                    "error_message": "Không tìm thấy khuôn mặt chân dung nào trên ảnh thẻ CCCD.",
                    "embedding": None,
                }

            # Lọc các khuôn mặt theo cấu trúc thẻ CCCD Việt Nam:
            # - Nửa bên trái: tâm x < 48% chiều rộng thẻ
            # - Nằm dưới tiêu đề và trên mép dưới: tâm y nằm giữa 15% và 90% chiều cao thẻ
            # - Kích thước tối thiểu: rộng >= 7% card_width, cao >= 9% card_height
            card_portrait_candidates = []
            for f in detected_faces:
                area = f.get("facial_area", {})
                fx = area.get("x", 0)
                fy = area.get("y", 0)
                fw = area.get("w", 0)
                fh = area.get("h", 0)
                cx = fx + fw / 2.0
                cy = fy + fh / 2.0

                if cx < (w * 0.48) and (h * 0.15) < cy < (h * 0.90):
                    if fw >= (w * 0.07) and fh >= (h * 0.09):
                        card_portrait_candidates.append(f)

            if not card_portrait_candidates:
                # Nếu không có ứng viên khớp chặt chẽ, kiểm tra xem có khuôn mặt nào bên trái không
                left_faces = [
                    f for f in detected_faces
                    if (f.get("facial_area", {}).get("x", 0) + f.get("facial_area", {}).get("w", 0) / 2) < (w * 0.50)
                ]
                if left_faces:
                    card_portrait_candidates = left_faces
                else:
                    return {
                        "status": "FACE_NOT_FOUND",
                        "error_message": "Không tìm thấy ảnh chân dung hợp lệ ở vị trí quy chuẩn trên thẻ CCCD.",
                        "embedding": None,
                    }

            # Chọn khuôn mặt lớn nhất và rõ nhất trong vùng chân dung CCCD
            best_face = max(
                card_portrait_candidates,
                key=lambda f: f.get("facial_area", {}).get("w", 0) * f.get("facial_area", {}).get("h", 0)
            )
            area = best_face.get("facial_area", {})

            # Cắt khuôn mặt thực tế để đánh giá chất lượng
            fx = max(0, int(area.get("x", 0)))
            fy = max(0, int(area.get("y", 0)))
            fw = int(area.get("w", 0))
            fh = int(area.get("h", 0))
            cropped_face = img[fy:fy + fh, fx:fx + fw]

            quality = self.assess_face_quality(cropped_face, area, min_size=40, min_sharpness=6.0)
            if not quality["is_valid"]:
                return {
                    "status": "LOW_QUALITY",
                    "error_message": f"Chất lượng ảnh chân dung trên thẻ CCCD không đủ để nhận diện ({quality['message']}). Vui lòng chụp lại ảnh mặt trước thẻ rõ nét hơn.",
                    "quality": quality,
                    "embedding": None,
                }

            # Trích xuất ArcFace embedding từ vùng chân dung đã căn chỉnh
            embedding = self._extract_aligned_arcface_embedding(img, detector_backend=used_backend, facial_area=area)
            if embedding is None:
                # Fallback thử trích xuất trực tiếp trên toàn bộ ảnh thẻ
                embedding = self.extract_embedding(img, detector_backend=used_backend)

            if embedding is None:
                return {
                    "status": "EMBEDDING_ERROR",
                    "error_message": "Không thể tạo vector đặc trưng sinh trắc học từ ảnh chân dung trên CCCD.",
                    "embedding": None,
                }

            return {
                "status": "SUCCESS",
                "embedding": embedding,
                "facial_area": area,
                "quality": quality,
            }
        except Exception as e:
            return {
                "status": "EMBEDDING_ERROR",
                "error_message": f"Lỗi xử lý trích xuất chân dung CCCD: {str(e)}",
                "embedding": None,
            }

    def validate_portrait_image(self, portrait_input: Union[str, np.ndarray, bytes]) -> Dict[str, Any]:
        """
        Kiểm tra tính hợp lệ của ảnh chân dung tải lên (3x4, 4x6, ảnh chụp điện thoại):
        - Không áp đặt tỉ lệ khung hình cứng nhắc của toàn bộ ảnh.
        - Phân biệt rõ INVALID_IMAGE_DIMENSIONS (tệp rỗng, hỏng, kích thước toàn ảnh < 50x50 px)
          và FACE_REGION_QUALITY_LOW (khuôn mặt mờ, rung, tối, chói sáng, hoặc diện tích mặt quá nhỏ < 50 px).
        - Đúng 1 khuôn mặt (0 -> FACE_NOT_FOUND, > 1 -> MULTIPLE_FACES).
        - Trích xuất embedding ArcFace 512-chiều đã căn chỉnh (align=True).
        - Kiểm tra embedding hợp lệ (512 chiều, hữu hạn, norm > 0).
        """
        try:
            img = self._load_image_utf8(portrait_input)
            if img is None or img.size == 0:
                return {
                    "valid": False,
                    "status": "INVALID_IMAGE_DIMENSIONS",
                    "error_code": "INVALID_IMAGE_DIMENSIONS",
                    "error_message": "Ảnh chân dung đưa vào không hợp lệ: Không thể đọc tệp hình ảnh.",
                }
            h, w = img.shape[:2]
            if h < 50 or w < 50:
                return {
                    "valid": False,
                    "status": "INVALID_IMAGE_DIMENSIONS",
                    "error_code": "INVALID_IMAGE_DIMENSIONS",
                    "error_message": f"Ảnh chân dung đưa vào không hợp lệ: Kích thước ảnh quá nhỏ ({w}x{h} < 50x50 px).",
                }

            if max(h, w) > 1280:
                scale = 1280.0 / max(h, w)
                img = cv2.resize(img, (int(w * scale), int(h * scale)), interpolation=cv2.INTER_AREA)
                h, w = img.shape[:2]

            # Dò tìm khuôn mặt: ưu tiên yunet, fallback opencv, retinaface
            faces = []
            used_backend = "yunet"
            for det in ["yunet", "opencv", "retinaface"]:
                try:
                    extracted = DeepFace.extract_faces(
                        img_path=img,
                        detector_backend=det,
                        enforce_detection=False,
                        align=True,
                    )
                    # Lọc bỏ placeholder full màn hình khi enforce_detection=False
                    valid = [
                        f for f in extracted
                        if not (
                            f.get("facial_area", {}).get("x", 0) == 0
                            and f.get("facial_area", {}).get("y", 0) == 0
                            and f.get("facial_area", {}).get("w", 0) >= w * 0.90
                            and f.get("facial_area", {}).get("h", 0) >= h * 0.90
                        )
                        and f.get("facial_area", {}).get("w", 0) >= 30
                        and f.get("facial_area", {}).get("h", 0) >= 30
                    ]
                    if valid:
                        faces = valid
                        used_backend = det
                        break
                except Exception:
                    continue

            if len(faces) == 0:
                return {
                    "valid": False,
                    "status": "FACE_NOT_FOUND",
                    "error_code": "FACE_NOT_FOUND",
                    "error_message": "Ảnh chân dung đưa vào không hợp lệ: Không tìm thấy khuôn mặt rõ nét trong ảnh. Vui lòng nhìn thẳng và đủ sáng.",
                }
            if len(faces) > 1:
                return {
                    "valid": False,
                    "status": "MULTIPLE_FACES",
                    "error_code": "MULTIPLE_FACES",
                    "error_message": "Ảnh chân dung đưa vào không hợp lệ: Phát hiện nhiều hơn một người trong ảnh. Vui lòng chỉ chụp chân dung một mình.",
                }

            best_face = faces[0]
            area = best_face.get("facial_area", {})
            fx = max(0, int(area.get("x", 0)))
            fy = max(0, int(area.get("y", 0)))
            fw = int(area.get("w", 0))
            fh = int(area.get("h", 0))
            cropped_face = img[fy:fy + fh, fx:fx + fw]

            # Đánh giá chất lượng khuôn mặt (hỗ trợ ảnh thẻ chân dung tiêu chuẩn 100x100, 3x4 với vùng mặt >= 30px)
            quality = self.assess_face_quality(cropped_face, area, min_size=30, min_sharpness=10.0)
            if not quality["is_valid"]:
                return {
                    "valid": False,
                    "status": "FACE_REGION_QUALITY_LOW",
                    "error_code": "FACE_REGION_QUALITY_LOW",
                    "error_message": f"Ảnh chân dung đưa vào không hợp lệ: {quality['message']} Vui lòng tải ảnh chụp chân dung rõ nét hơn.",
                    "quality": quality,
                }

            # Trích xuất ArcFace embedding đã căn chỉnh
            embedding = self._extract_aligned_arcface_embedding(img, detector_backend=used_backend, facial_area=area)
            if embedding is None:
                embedding = self.extract_embedding(img, detector_backend=used_backend)

            if embedding is None or not self.validate_embedding(embedding):
                return {
                    "valid": False,
                    "status": "FACE_REGION_QUALITY_LOW",
                    "error_code": "FACE_REGION_QUALITY_LOW",
                    "error_message": "Ảnh chân dung đưa vào không hợp lệ: Không thể trích xuất đặc trưng khuôn mặt từ ảnh. Vui lòng chụp góc thẳng và đủ sáng.",
                }

            return {
                "valid": True,
                "status": "SUCCESS",
                "error_code": None,
                "embedding": embedding,
                "face": best_face.get("face"),
                "facial_area": area,
                "quality": quality,
            }
        except Exception as e:
            return {
                "valid": False,
                "status": "FACE_REGION_QUALITY_LOW",
                "error_code": "FACE_REGION_QUALITY_LOW",
                "error_message": f"Ảnh chân dung đưa vào không hợp lệ: {str(e)}",
            }

    def extract_embedding(self, img_input: Union[str, np.ndarray, bytes], detector_backend: str = None) -> Optional[np.ndarray]:
        """Trích xuất vector ArcFace 512-chiều đã L2-chuẩn hóa từ ảnh khuôn mặt."""
        backend = detector_backend or self.detector_backend
        try:
            img = self._load_image_utf8(img_input)
            h, w = img.shape[:2]
            if max(h, w) > 1280:
                scale = 1280.0 / max(h, w)
                img = cv2.resize(img, (int(w * scale), int(h * scale)), interpolation=cv2.INTER_AREA)

            backends_to_try = [backend]
            for fallback in ["retinaface", "yunet", "opencv"]:
                if fallback not in backends_to_try:
                    backends_to_try.append(fallback)

            for det in backends_to_try:
                try:
                    results = DeepFace.represent(
                        img_path=img,
                        model_name=self.model_name,
                        detector_backend=det,
                        enforce_detection=True,
                        align=True,
                    )
                    if results and len(results) > 0:
                        raw = np.array(results[0]["embedding"], dtype=np.float32)
                        if self.validate_embedding(raw):
                            return self.normalize_embedding(raw)
                except Exception:
                    continue
            return None
        except Exception as e:
            print(f"[FaceMatcher] Lỗi khi trích xuất embedding: {e}")
            return None

    def match_card_and_portrait(
        self,
        card_embedding: np.ndarray,
        portrait_embedding: np.ndarray,
        threshold: Optional[float] = None,
    ) -> Tuple[bool, float, str, str]:
        """
        Đối chiếu khuôn mặt bóc tách từ mặt trước CCCD với ảnh chân dung tải lên.
        Trả về: (is_match, distance, decision_code, error_message)

        Quyết định phân loại rõ ràng:
        - MATCHED: distance <= EKYC_CARD_PORTRAIT_MATCH_THRESHOLD (mặc định 0.55).
        - NEEDS_REVIEW: match_threshold < distance <= review_threshold (0.55 - 0.60).
        - MISMATCHED: distance > review_threshold (> 0.60, ngăn chặn hoàn toàn người khác).
        - EMBEDDING_ERROR: vector không hợp lệ / không đủ 512 chiều.
        """
        if not self.validate_embedding(card_embedding) or not self.validate_embedding(portrait_embedding):
            return (
                False,
                1.0,
                "EMBEDDING_ERROR",
                "Không thể đối chiếu khuôn mặt: Vector đặc trưng không hợp lệ hoặc bị lỗi số học.",
            )

        try:
            distance = self.compute_cosine_distance(card_embedding, portrait_embedding)
        except Exception as ex:
            return (
                False,
                1.0,
                "EMBEDDING_ERROR",
                f"Lỗi tính toán khoảng cách vector: {str(ex)}",
            )

        default_thresh = threshold if threshold is not None else 0.55
        match_thresh = float(os.getenv("EKYC_CARD_PORTRAIT_MATCH_THRESHOLD", str(default_thresh)))
        review_thresh = float(os.getenv("EKYC_CARD_PORTRAIT_REVIEW_THRESHOLD", "0.60"))

        if distance <= match_thresh:
            return (True, round(distance, 4), "MATCHED", "")
        elif distance > review_thresh:
            return (
                False,
                round(distance, 4),
                "MISMATCHED",
                f"Ảnh trên thẻ CCCD và ảnh chân dung không khớp nhau (khoảng cách sai lệch {distance:.2f} > {match_thresh:.2f}). Vui lòng tải đúng ảnh chân dung của chính chủ thẻ CCCD.",
            )
        else:
            return (
                False,
                round(distance, 4),
                "NEEDS_REVIEW",
                f"Mức độ tương đồng giữa CCCD và ảnh chân dung nằm trong vùng chưa chắc chắn ({distance:.2f} nằm giữa {match_thresh:.2f} và {review_thresh:.2f}). Vui lòng tải ảnh chân dung rõ nét hơn.",
            )

    def process_live_frame_dual(
        self,
        frame: np.ndarray,
        reference_card_embedding: np.ndarray,
        reference_portrait_embedding: np.ndarray,
        circle_center: tuple = None,
        circle_radius: int = None,
        session_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        Xử lý khung hình live từ webcam đối chiếu đồng thời với CẢ HAI ảnh tham chiếu:
        - reference_card_embedding (từ thẻ CCCD)
        - reference_portrait_embedding (từ ảnh chân dung chính chủ)

        Không dùng phép OR giữa hai kết quả.
        Tích hợp Multi-frame Buffer, Quality Assessment và Liveness status.
        """
        try:
            height, width = frame.shape[:2]

            actual_cx = width // 2
            actual_cy = int(height * 0.48)
            actual_radius = int(min(width, height) * 0.38)

            # 1. Dò tìm khuôn mặt (ưu tiên YuNet tốc độ cao ~35ms, fallback OpenCV)
            faces = []
            used_backend = "yunet"
            for det in ["yunet", "opencv"]:
                try:
                    detected = DeepFace.extract_faces(
                        img_path=frame,
                        detector_backend=det,
                        enforce_detection=False,
                        align=True,
                    )
                    valid = [
                        f for f in detected
                        if not (
                            f.get("facial_area", {}).get("x", 0) == 0
                            and f.get("facial_area", {}).get("y", 0) == 0
                            and f.get("facial_area", {}).get("w", 0) >= width * 0.90
                            and f.get("facial_area", {}).get("h", 0) >= height * 0.90
                        )
                        and f.get("facial_area", {}).get("w", 0) >= 30
                        and f.get("facial_area", {}).get("h", 0) >= 30
                    ]
                    if valid:
                        faces = valid
                        used_backend = det
                        break
                except Exception:
                    continue

            if len(faces) == 0:
                return {
                    "match_status": "FACE_NOT_FOUND",
                    "user_instruction": "Vui lòng nhìn thẳng và đưa khuôn mặt vào giữa khung hình",
                    "frame_status": "FACE_NOT_FOUND",
                    "directional_cue": "center_face",
                    "distance_card": 1.0,
                    "distance_portrait": 1.0,
                    "distance": 1.0,
                    "confidence_score": 0.0,
                    "is_match": False,
                    "card_matched": False,
                    "portrait_matched": False,
                    "liveness_status": "NOT_CHECKED",
                }

            if len(faces) > 1:
                return {
                    "match_status": "TOO_MANY_FACES",
                    "user_instruction": "Phát hiện nhiều người! Vui lòng chỉ một mình trong khung hình",
                    "frame_status": "TOO_MANY_FACES",
                    "directional_cue": "single_person",
                    "distance_card": 1.0,
                    "distance_portrait": 1.0,
                    "distance": 1.0,
                    "confidence_score": 0.0,
                    "is_match": False,
                    "card_matched": False,
                    "portrait_matched": False,
                    "liveness_status": "NOT_CHECKED",
                }

            best_face = faces[0]
            area = best_face.get("facial_area", {})
            fx = int(area.get("x", 0))
            fy = int(area.get("y", 0))
            fw = int(area.get("w", 0))
            fh = int(area.get("h", 0))

            face_center_x = fx + fw // 2
            face_center_y = fy + fh // 2
            dx = face_center_x - actual_cx
            dy = face_center_y - actual_cy

            # 2. Kiểm tra cự ly
            if fw < int(min(width, height) * 0.15):
                return {
                    "match_status": "DISTANCE_ISSUE",
                    "user_instruction": "Hãy đưa khuôn mặt lại gần camera hơn",
                    "frame_status": "DISTANCE_ISSUE",
                    "directional_cue": "move_closer",
                    "distance_card": 1.0,
                    "distance_portrait": 1.0,
                    "distance": 1.0,
                    "confidence_score": 0.0,
                    "is_match": False,
                    "card_matched": False,
                    "portrait_matched": False,
                    "liveness_status": "NOT_CHECKED",
                }
            if fw > int(min(width, height) * 0.82):
                return {
                    "match_status": "DISTANCE_ISSUE",
                    "user_instruction": "Hãy đưa khuôn mặt lùi xa camera một chút",
                    "frame_status": "DISTANCE_ISSUE",
                    "directional_cue": "move_farther",
                    "distance_card": 1.0,
                    "distance_portrait": 1.0,
                    "distance": 1.0,
                    "confidence_score": 0.0,
                    "is_match": False,
                    "card_matched": False,
                    "portrait_matched": False,
                    "liveness_status": "NOT_CHECKED",
                }

            # 3. Kiểm tra vị trí tâm
            if dx < -int(width * 0.28):
                return {
                    "match_status": "FACE_OFF_CENTER",
                    "user_instruction": "Vui lòng dịch sang phải một chút",
                    "frame_status": "FACE_OFF_CENTER",
                    "directional_cue": "move_right",
                    "distance_card": 1.0,
                    "distance_portrait": 1.0,
                    "distance": 1.0,
                    "confidence_score": 0.0,
                    "is_match": False,
                    "card_matched": False,
                    "portrait_matched": False,
                    "liveness_status": "NOT_CHECKED",
                }
            if dx > int(width * 0.28):
                return {
                    "match_status": "FACE_OFF_CENTER",
                    "user_instruction": "Vui lòng dịch sang trái một chút",
                    "frame_status": "FACE_OFF_CENTER",
                    "directional_cue": "move_left",
                    "distance_card": 1.0,
                    "distance_portrait": 1.0,
                    "distance": 1.0,
                    "confidence_score": 0.0,
                    "is_match": False,
                    "card_matched": False,
                    "portrait_matched": False,
                    "liveness_status": "NOT_CHECKED",
                }
            if dy < -int(height * 0.25):
                return {
                    "match_status": "FACE_OFF_CENTER",
                    "user_instruction": "Vui lòng hạ camera hoặc ngẩng mặt lên một chút",
                    "frame_status": "FACE_OFF_CENTER",
                    "directional_cue": "move_up",
                    "distance_card": 1.0,
                    "distance_portrait": 1.0,
                    "distance": 1.0,
                    "confidence_score": 0.0,
                    "is_match": False,
                    "card_matched": False,
                    "portrait_matched": False,
                    "liveness_status": "NOT_CHECKED",
                }
            if dy > int(height * 0.25):
                return {
                    "match_status": "FACE_OFF_CENTER",
                    "user_instruction": "Vui lòng nâng camera một chút",
                    "frame_status": "FACE_OFF_CENTER",
                    "directional_cue": "move_down",
                    "distance_card": 1.0,
                    "distance_portrait": 1.0,
                    "distance": 1.0,
                    "confidence_score": 0.0,
                    "is_match": False,
                    "card_matched": False,
                    "portrait_matched": False,
                    "liveness_status": "NOT_CHECKED",
                }

            # 4. Kiểm tra chất lượng khuôn mặt
            crop_x1 = max(0, fx)
            crop_y1 = max(0, fy)
            crop_x2 = min(width, fx + fw)
            crop_y2 = min(height, fy + fh)
            cropped_face = frame[crop_y1:crop_y2, crop_x1:crop_x2]

            quality = self.assess_face_quality(cropped_face, area, min_size=50, min_sharpness=7.0)
            if not quality["is_valid"]:
                return {
                    "match_status": "IMAGE_QUALITY_ISSUE",
                    "user_instruction": quality["message"],
                    "frame_status": "IMAGE_QUALITY_ISSUE",
                    "directional_cue": "hold_steady" if quality["status"] == "BLURRY" else "adjust_face",
                    "distance_card": 1.0,
                    "distance_portrait": 1.0,
                    "distance": 1.0,
                    "confidence_score": 0.0,
                    "is_match": False,
                    "card_matched": False,
                    "portrait_matched": False,
                    "liveness_status": "NOT_CHECKED",
                }

            # 5. Trích xuất đặc trưng ArcFace đồng nhất (align=True)
            live_embedding = self._extract_aligned_arcface_embedding(frame, detector_backend=used_backend, facial_area=area)
            if live_embedding is None or not self.validate_embedding(live_embedding):
                return {
                    "match_status": "ERROR",
                    "user_instruction": "Không thể trích xuất đặc trưng khuôn mặt từ khung hình.",
                    "frame_status": "ERROR",
                    "distance_card": 1.0,
                    "distance_portrait": 1.0,
                    "distance": 1.0,
                    "confidence_score": 0.0,
                    "is_match": False,
                    "card_matched": False,
                    "portrait_matched": False,
                    "liveness_status": "NOT_CHECKED",
                }

            # 6. Tính khoảng cách Cosine với CẢ HAI mẫu tham chiếu
            distance_card = self.compute_cosine_distance(live_embedding, reference_card_embedding)
            distance_portrait = self.compute_cosine_distance(live_embedding, reference_portrait_embedding)

            is_single_ref = np.array_equal(reference_card_embedding, reference_portrait_embedding)
            card_thresh = float(os.getenv("EKYC_LIVE_CARD_MATCH_THRESHOLD", "0.50"))
            default_portrait_thresh = str(card_thresh) if is_single_ref else "0.48"
            portrait_thresh = float(os.getenv("EKYC_LIVE_PORTRAIT_MATCH_THRESHOLD", default_portrait_thresh))

            card_matched = distance_card <= card_thresh
            portrait_matched = distance_portrait <= portrait_thresh

            # Cho phép dung sai chéo nếu 1 mẫu khớp chuẩn và mẫu kia trong khoảng an toàn (<= 0.55), loại trừ người lạ (> 0.60)
            is_match_instant = (card_matched and portrait_matched) or (card_matched and distance_portrait <= 0.55) or (portrait_matched and distance_card <= 0.55)

            # Điểm đại diện (nếu khớp thì lấy điểm tốt hơn, nếu không khớp lấy điểm xấu hơn)
            overall_distance = min(distance_card, distance_portrait) if is_match_instant else max(distance_card, distance_portrait)
            confidence = max(0.0, 1.0 - (overall_distance / 0.65))

            # 7. Cập nhật Multi-Frame Buffer nếu có session
            frames_collected = 1
            is_match = is_match_instant
            decision_status = "MATCHED" if is_match else "NOT_MATCHED"
            stability_score = 1.0

            if session_id:
                buf = self.get_or_create_buffer(session_id)
                buf.add_frame(
                    distance_card=distance_card,
                    distance_portrait=distance_portrait,
                    frame_bgr=frame,
                    quality_score=quality.get("quality_score", 0.5),
                    facial_area=area,
                )
                min_frames = int(os.getenv("EKYC_MULTIFRAME_MIN_FRAMES", "3"))
                eval_res = buf.evaluate(
                    threshold_card=card_thresh,
                    threshold_portrait=portrait_thresh,
                    min_frames=min_frames,
                )
                frames_collected = eval_res.get("frames_collected", 1)
                if eval_res.get("decision") == "COLLECTING_FRAMES":
                    decision_status = "COLLECTING_FRAMES"
                    is_match = False
                elif eval_res.get("decision") == "UNSTABLE":
                    decision_status = "UNSTABLE"
                    is_match = False
                elif eval_res.get("decision") == "MATCHED":
                    decision_status = "MATCHED"
                    is_match = True
                else:
                    decision_status = "NOT_MATCHED"
                    is_match = False
                if "card_matched" in eval_res:
                    card_matched = eval_res["card_matched"]
                if "portrait_matched" in eval_res:
                    portrait_matched = eval_res["portrait_matched"]

            instruction = "Khuôn mặt hợp lệ! Vui lòng giữ yên..." if is_match else (
                "Đang thu thập khung hình, vui lòng giữ yên..." if decision_status == "COLLECTING_FRAMES" else
                "Khuôn mặt chưa trùng khớp với hồ sơ định danh"
            )

            return {
                "match_status": decision_status,
                "is_match": is_match,
                "distance_card": round(distance_card, 4),
                "distance_portrait": round(distance_portrait, 4),
                "distance": round(overall_distance, 4),
                "confidence_score": round(confidence, 4),
                "threshold_card": card_thresh,
                "threshold_portrait": portrait_thresh,
                "threshold_used": card_thresh,
                "card_matched": card_matched,
                "portrait_matched": portrait_matched,
                "user_instruction": instruction,
                "frame_status": decision_status,
                "directional_cue": "hold_steady" if is_match else "adjust_face",
                "liveness_status": "NOT_CHECKED",
                "frames_collected": frames_collected,
            }
        except Exception as e:
            return {
                "match_status": "ERROR",
                "user_instruction": f"Lỗi xử lý: {str(e)}",
                "frame_status": "ERROR",
                "distance": 1.0,
                "confidence_score": 0.0,
                "is_match": False,
                "liveness_status": "NOT_CHECKED",
            }

    # Giữ tương thích ngược với luồng đơn lẻ (QuickAuth, kiểm thử cũ)
    def process_live_frame(
        self,
        frame: np.ndarray,
        reference_embedding: np.ndarray,
        circle_center: tuple = None,
        circle_radius: int = None,
        session_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Tương thích ngược cho QuickAuth hoặc caller đơn tham chiếu."""
        res = self.process_live_frame_dual(
            frame=frame,
            reference_card_embedding=reference_embedding,
            reference_portrait_embedding=reference_embedding,
            circle_center=circle_center,
            circle_radius=circle_radius,
            session_id=session_id,
        )
        return res

    def verify_live_frame(self, live_img: np.ndarray, reference_embedding: np.ndarray) -> Dict[str, Any]:
        """Tương thích ngược cho kiểm thử tĩnh verify_live_frame."""
        return self.process_live_frame(live_img, reference_embedding)
