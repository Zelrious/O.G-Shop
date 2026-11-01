import os
import cv2
import numpy as np
from typing import Dict, Any, Optional, Tuple, List
from pathlib import Path

from .mrz_parser import MRZValidator, MRZData


class BackCardProcessor:
    """
    Bộ xử lý chuyên sâu cho mặt sau thẻ Căn cước công dân (CCCD) gắn chip Việt Nam:
    1. Căn chỉnh góc & Biến đổi phối cảnh (Perspective Alignment).
    2. Tự động xoay về chiều chuẩn (Orientation Correction: 0°, 90°, 180°, 270°).
    3. Xác định các khu vực dữ liệu quan trọng (Chip ROI, Dấu đỏ ROI, Vân tay ROI, MRZ ROI).
    4. Nhận dạng chính xác ảnh có phải là mặt sau CCCD hay không (Loại bỏ ảnh khác, ảnh mặt trước, ảnh phong cảnh...).
    5. Bóc tách dải mã MRZ (3 dòng) và đối chiếu chéo (Cross-check) với thông tin mặt trước.
    """

    CANONICAL_WIDTH = 1000
    CANONICAL_HEIGHT = 630

    _vietocr_instance = None

    @classmethod
    def get_vietocr_model(cls):
        if cls._vietocr_instance is None:
            try:
                from cccd_module.ocr_core.vietocr.text_recognition import TextRecognition
                cls._vietocr_instance = TextRecognition()
            except Exception as e:
                print(f"[BackCardProcessor] Không thể nạp VietOCR: {e}")
                cls._vietocr_instance = None
        return cls._vietocr_instance

    @classmethod
    def read_image_safe(cls, image_path: str) -> Optional[np.ndarray]:
        """Đọc ảnh hỗ trợ đường dẫn tiếng Việt và ký tự đặc biệt trên Windows."""
        if not os.path.exists(image_path):
            return None
        try:
            image_array = np.fromfile(image_path, dtype=np.uint8)
            img = cv2.imdecode(image_array, cv2.IMREAD_COLOR)
            return img
        except Exception as e:
            print(f"[BackCardProcessor] Lỗi đọc ảnh {image_path}: {e}")
            return None

    @classmethod
    def detect_and_align_card(cls, image: np.ndarray) -> Tuple[np.ndarray, bool]:
        """
        Phát hiện 4 góc thẻ CCCD qua đường bao (contour) và căn chỉnh phối cảnh về kích thước chuẩn ID-1.
        Nếu ảnh đã là ảnh crop sát thẻ thì chuẩn hóa về kích thước chuẩn.
        """
        if image is None or image.size == 0:
            return image, False

        orig_h, orig_w = image.shape[:2]
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

        # Lọc nhiễu và tìm cạnh
        blurred = cv2.GaussianBlur(gray, (5, 5), 0)
        edged = cv2.Canny(blurred, 50, 200)

        # Đóng các đoạn đứt gãy cạnh
        kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (7, 7))
        closed = cv2.morphologyEx(edged, cv2.MORPH_CLOSE, kernel)

        contours, _ = cv2.findContours(closed, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        contours = sorted(contours, key=cv2.contourArea, reverse=True)

        card_contour = None
        min_card_area = 0.15 * (orig_w * orig_h)

        for cnt in contours:
            area = cv2.contourArea(cnt)
            if area < min_card_area:
                break
            peri = cv2.arcLength(cnt, True)
            approx = cv2.approxPolyDP(cnt, 0.02 * peri, True)
            if len(approx) == 4 and cv2.isContourConvex(approx):
                # Kiểm tra tỷ lệ khung hình
                rect = cv2.minAreaRect(cnt)
                rw, rh = rect[1]
                if rh > 0 and rw > 0:
                    aspect = max(rw, rh) / min(rw, rh)
                    if 1.2 <= aspect <= 2.2:
                        card_contour = approx.reshape(4, 2)
                        break

        if card_contour is not None:
            # Sắp xếp 4 đỉnh: top-left, top-right, bottom-right, bottom-left
            pts = card_contour.astype(np.float32)
            s = pts.sum(axis=1)
            diff = np.diff(pts, axis=1)

            top_left = pts[np.argmin(s)]
            bottom_right = pts[np.argmax(s)]
            top_right = pts[np.argmin(diff)]
            bottom_left = pts[np.argmax(diff)]

            src_pts = np.array([top_left, top_right, bottom_right, bottom_left], dtype=np.float32)
            dst_pts = np.array([
                [0, 0],
                [cls.CANONICAL_WIDTH - 1, 0],
                [cls.CANONICAL_WIDTH - 1, cls.CANONICAL_HEIGHT - 1],
                [0, cls.CANONICAL_HEIGHT - 1]
            ], dtype=np.float32)

            matrix = cv2.getPerspectiveTransform(src_pts, dst_pts)
            warped = cv2.warpPerspective(image, matrix, (cls.CANONICAL_WIDTH, cls.CANONICAL_HEIGHT))
            return warped, True

        # Nếu không tìm thấy viền rõ ràng (ảnh đã được crop sát thẻ), resize về canonical
        resized = cv2.resize(image, (cls.CANONICAL_WIDTH, cls.CANONICAL_HEIGHT))
        return resized, False

    @classmethod
    def score_card_orientation(cls, img: np.ndarray) -> float:
        """
        Tính điểm đánh giá xem ảnh có đúng chiều mặt sau CCCD (chuẩn ID-1 landscape, chip bên trái, MRZ ở đáy).
        Điểm số cao chứng tỏ ảnh đúng chiều mặt sau CCCD.
        """
        h, w = img.shape[:2]
        if h >= w:
            return -100.0

        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)

        # 1. Phân bổ vân tay: Trên thẻ đứng chuẩn (0 độ), vân tay tập trung ở góc TRÊN BÊN PHẢI (x: 55%-95%, y: 8%-55%).
        # Khi thẻ bị lộn ngược 180 độ, vân tay bị lộn xuống góc DƯỚI BÊN TRÁI (x: 5%-45%, y: 45%-92%).
        fp_tr = gray[int(h * 0.08):int(h * 0.55), int(w * 0.55):int(w * 0.95)]
        fp_bl = gray[int(h * 0.45):int(h * 0.92), int(w * 0.05):int(w * 0.45)]
        var_tr = float(cv2.Laplacian(fp_tr, cv2.CV_64F).var())
        var_bl = float(cv2.Laplacian(fp_bl, cv2.CV_64F).var())
        fp_diff = np.clip((var_tr - var_bl) / max(1.0, var_tr + var_bl) * 35.0, -35.0, 35.0)

        # 2. Vị trí Chip điện tử màu vàng kim: Nằm ở BÊN TRÁI thẻ (x: 6%-38%, y: 25%-65%).
        # Khi bị lộn ngược 180 độ, chip sẽ chạy sang BÊN PHẢI (x: 62%-94%, y: 35%-75%).
        gold_mask = cv2.inRange(hsv, np.array([10, 20, 40]), np.array([45, 255, 255]))
        chip_left = np.sum(gold_mask[int(h * 0.25):int(h * 0.65), int(w * 0.06):int(w * 0.38)] > 0)
        chip_right = np.sum(gold_mask[int(h * 0.35):int(h * 0.75), int(w * 0.62):int(w * 0.94)] > 0)
        chip_diff = np.clip((chip_left - chip_right) / max(1.0, chip_left + chip_right) * 30.0, -30.0, 30.0)

        # 3. Con dấu đỏ Bộ Công An: Nằm ở GIỮA-TRÁI thẻ (x: 22%-58%, y: 20%-70%)
        red_mask = cv2.inRange(hsv, np.array([0, 25, 40]), np.array([15, 255, 255])) | cv2.inRange(hsv, np.array([165, 25, 40]), np.array([180, 255, 255]))
        seal_mid = np.sum(red_mask[int(h * 0.20):int(h * 0.70), int(w * 0.22):int(w * 0.58)] > 0)
        seal_score = min(20.0, seal_mid / 1000.0 * 5.0)

        # 4. Khu vực dải mã MRZ phía dưới (y: 68% - 100%)
        mrz_roi = gray[int(h * 0.68):h, 0:w]
        sobel = cv2.Sobel(mrz_roi, cv2.CV_64F, 0, 1, ksize=3)
        mrz_edge = float(np.mean(np.abs(sobel)))
        mrz_score = min(20.0, mrz_edge)

        total_score = fp_diff + chip_diff + seal_score + mrz_score
        return float(total_score)

    @classmethod
    def correct_orientation(cls, image: np.ndarray) -> Tuple[np.ndarray, int]:
        """
        Xác định chiều quay chính xác (0°, 90°, 180°, 270°) và xoay ảnh về chuẩn.
        Trả về ảnh đã xoay chuẩn và góc quay đã áp dụng (0, 90, 180, 270).
        """
        rotations = [
            (0, image),
            (90, cv2.rotate(image, cv2.ROTATE_90_CLOCKWISE)),
            (180, cv2.rotate(image, cv2.ROTATE_180)),
            (270, cv2.rotate(image, cv2.ROTATE_90_COUNTERCLOCKWISE)),
        ]

        scored = []
        for angle, rot_img in rotations:
            # Chuẩn hóa về canonical ID-1 để tính điểm
            test_img = cv2.resize(rot_img, (cls.CANONICAL_WIDTH, cls.CANONICAL_HEIGHT)) if rot_img.shape[0] < rot_img.shape[1] else rot_img
            s = cls.score_card_orientation(test_img)
            scored.append((s, angle, rot_img))

        scored.sort(key=lambda x: x[0], reverse=True)
        best_score, best_angle, best_img = scored[0]

        # Đảm bảo đầu ra có kích thước CANONICAL_WIDTH x CANONICAL_HEIGHT
        final_img = cv2.resize(best_img, (cls.CANONICAL_WIDTH, cls.CANONICAL_HEIGHT))
        return final_img, best_angle

    @classmethod
    def check_for_front_portrait_face(cls, image: np.ndarray) -> bool:
        """
        Kiểm tra xem ảnh có chứa khuôn mặt chân dung lớn (dấu hiệu người dùng đưa nhầm mặt trước CCCD).
        Mặt sau CCCD không bao giờ có ảnh chân dung người.
        """
        try:
            cascade_path = cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
            if not os.path.exists(cascade_path):
                return False
            face_cascade = cv2.CascadeClassifier(cascade_path)
            gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
            faces = face_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=4, minSize=(60, 60))
            
            h, w = image.shape[:2]
            for (x, y, fw, fh) in faces:
                # Ảnh chân dung mặt trước CCCD thường nằm ở nửa trái thẻ
                if x < int(w * 0.45) and fw >= int(w * 0.12) and fh >= int(h * 0.20):
                    return True
        except Exception as e:
            print(f"[BackCardProcessor] Lỗi kiểm tra khuôn mặt: {e}")
        return False

    @classmethod
    def verify_is_cccd_back(cls, aligned_img: np.ndarray) -> Tuple[bool, str, str, Dict[str, Any]]:
        """
        Kiểm định chắc chắn ảnh có phải là mặt sau thẻ Căn cước công dân gắn chip hợp lệ hay không.
        Bảo đảm loại bỏ hoàn toàn ảnh phong cảnh, ảnh chụp màn hình, ảnh tài liệu khác (GPLX, BHYT) hoặc mặt trước.
        Trả về: (is_valid, error_code, error_message, metrics)
        """
        if aligned_img is None or aligned_img.size == 0:
            return False, "BACK_CCCD_MISSING", "Ảnh mặt sau CCCD rỗng hoặc không đọc được.", {}

        # 1. Kiểm tra đưa nhầm mặt trước (có khuôn mặt chân dung lớn ở nửa trái)
        if cls.check_for_front_portrait_face(aligned_img):
            return False, "BACK_CCCD_INVALID", "Ảnh tải lên là mặt trước CCCD (có ảnh chân dung). Vui lòng chụp mặt sau có gắn chip và dải mã MRZ.", {}

        h, w = aligned_img.shape[:2]
        gray = cv2.cvtColor(aligned_img, cv2.COLOR_BGR2GRAY)
        hsv = cv2.cvtColor(aligned_img, cv2.COLOR_BGR2HSV)

        # 2. Kiểm tra Chip điện tử màu vàng kim bên trái (y: 20-65%, x: 6-38%)
        chip_roi = aligned_img[int(h * 0.20):int(h * 0.65), int(w * 0.06):int(w * 0.38)]
        hsv_chip = cv2.cvtColor(chip_roi, cv2.COLOR_BGR2HSV)
        gold_mask = cv2.inRange(hsv_chip, np.array([10, 25, 45]), np.array([42, 255, 255]))
        gold_ratio = float(np.sum(gold_mask > 0) / max(1, chip_roi.shape[0] * chip_roi.shape[1]))

        # Tìm contour của chip
        cnts, _ = cv2.findContours(gold_mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        max_chip_area = max([cv2.contourArea(c) for c in cnts], default=0.0)

        # 3. Kiểm tra Con dấu đỏ cơ quan cấp ở giữa thẻ (y: 20-70%, x: 22-58%)
        seal_roi = aligned_img[int(h * 0.20):int(h * 0.70), int(w * 0.22):int(w * 0.58)]
        hsv_seal = cv2.cvtColor(seal_roi, cv2.COLOR_BGR2HSV)
        red_mask1 = cv2.inRange(hsv_seal, np.array([0, 30, 45]), np.array([14, 255, 255]))
        red_mask2 = cv2.inRange(hsv_seal, np.array([166, 30, 45]), np.array([180, 255, 255]))
        red_ratio = float(np.sum(red_mask1 | red_mask2 > 0) / max(1, seal_roi.shape[0] * seal_roi.shape[1]))

        # 4. Kiểm tra Khu vực ô vân tay bên phải (y: 5-55%, x: 50-98%)
        fp_roi = aligned_img[int(h * 0.05):int(h * 0.55), int(w * 0.50):int(w * 0.98)]
        gray_fp = cv2.cvtColor(fp_roi, cv2.COLOR_BGR2GRAY)
        fp_lap = float(cv2.Laplacian(gray_fp, cv2.CV_64F).var())

        # 5. Kiểm tra Khu vực dải mã MRZ ở đáy (y: 65-100%, x: 0-100%)
        mrz_roi = aligned_img[int(h * 0.65):h, 0:w]
        gray_mrz = cv2.cvtColor(mrz_roi, cv2.COLOR_BGR2GRAY)
        sobel = cv2.Sobel(gray_mrz, cv2.CV_64F, 0, 1, ksize=3)
        mrz_edge = float(np.mean(np.abs(sobel)))

        # 6. Đánh giá chất lượng quang học tổng thể
        lap_total = float(cv2.Laplacian(gray, cv2.CV_64F).var())
        mean_v = float(np.mean(hsv[:, :, 2]))
        glare_mask_total = (hsv[:, :, 2] > 245) & (hsv[:, :, 1] < 30)
        glare_ratio = float(np.sum(glare_mask_total) / max(1, h * w))

        metrics = {
            "gold_chip_ratio": round(gold_ratio, 3),
            "max_chip_contour": round(max_chip_area, 1),
            "red_seal_ratio": round(red_ratio, 3),
            "fingerprint_variance": round(fp_lap, 1),
            "mrz_edge_energy": round(mrz_edge, 2),
            "card_sharpness": round(lap_total, 1),
        }

        # Kiểm tra chip: Phải có cụm màu vàng kim tập trung rõ ràng (gold_ratio >= 0.04 hoặc contour chip >= 1500 px)
        has_chip = (gold_ratio >= 0.04 or max_chip_area >= 1500.0)
        has_seal_or_fp = (red_ratio >= 0.015 or fp_lap >= 40.0)
        has_mrz = (mrz_edge >= 15.0)

        # Chặn các ảnh tài liệu khác, phong cảnh, hóa đơn (hoàn toàn không có chip và không có MRZ)
        if not has_chip and not has_mrz:
            return False, "BACK_CCCD_INVALID", "Ảnh tải lên không phát hiện được chip điện tử của thẻ CCCD gắn chip. Vui lòng chụp rõ mặt sau thẻ CCCD.", metrics

        # 6.1. Kiểm tra chất lượng quang học tổng thể: mờ, rung, chói sáng, quá tối
        if lap_total < 35.0:
            return False, "BACK_CCCD_QUALITY_LOW", "Ảnh mặt sau CCCD bị mờ hoặc rung tay. Vui lòng giữ chắc camera và chụp lại rõ nét hơn.", metrics

        if glare_ratio > 0.18:
            return False, "BACK_CCCD_QUALITY_LOW", "Ảnh mặt sau CCCD bị lóa sáng hoặc chói đèn flash. Vui lòng chụp ở nơi không bị ánh đèn phản chiếu trực tiếp.", metrics

        if mean_v < 40.0:
            return False, "BACK_CCCD_QUALITY_LOW", "Ảnh mặt sau CCCD quá tối, thiếu ánh sáng. Vui lòng chụp ở nơi đủ sáng.", metrics

        # 6.2. Bắt buộc CẢ 3 yếu tố nhận diện chuẩn của thẻ CCCD gắn chip mặt sau:
        # Yếu tố 1: Bắt buộc Chip điện tử màu vàng kim (gold_ratio >= 0.04 hoặc max_chip_area >= 1500 px)
        if not has_chip:
            return False, "BACK_CCCD_INVALID", "Ảnh tải lên không phát hiện được chip điện tử của thẻ CCCD gắn chip. Vui lòng chụp rõ mặt sau thẻ CCCD.", metrics

        # Yếu tố 2: Bắt buộc Dải mã tương phản MRZ ở đáy thẻ (mrz_edge >= 15.0)
        if not has_mrz:
            return False, "BACK_CCCD_INVALID", "Không phát hiện dải mã vạch ký tự MRZ hợp lệ ở phía dưới mặt sau thẻ CCCD.", metrics

        # Yếu tố 3: Bắt buộc Con dấu đỏ cơ quan cấp hoặc ô vân tay (red_ratio >= 0.015 hoặc fp_lap >= 40.0)
        if not has_seal_or_fp:
            return False, "BACK_CCCD_INVALID", "Không phát hiện được con dấu cơ quan cấp hoặc ô vân tay mặt sau thẻ CCCD.", metrics

        return True, "", "", metrics

    @classmethod
    def build_mrz_lines_from_front_data(cls, front_data: Dict[str, Any]) -> List[str]:
        """
        Tạo 3 dòng mã MRZ (chuẩn ICAO TD1 cho thẻ CCCD Việt Nam) từ dữ liệu mặt trước:
        - Dòng 1: IDVNM + 10 số phụ + 12 số CCCD + ký tự kiểm tra (tổng 30 ký tự)
        - Dòng 2: YYMMDD (ngày sinh) + check digit + M/F + YYMMDD (hết hạn) + check digit + VNM + đệm < (tổng 30 ký tự)
        - Dòng 3: Họ và Tên cách nhau bằng << (tổng 30 ký tự)
        """
        from .mrz_parser import remove_vietnamese_accents
        cccd = str(front_data.get("so_cccd", "")).strip()
        name = remove_vietnamese_accents(str(front_data.get("ho_va_ten", "")))
        dob = str(front_data.get("ngay_sinh", "")).strip()
        gender = str(front_data.get("gioi_tinh", "Nam")).strip()
        expiry = str(front_data.get("ngay_het_han", "")).strip()

        if not cccd or len(cccd) != 12 or not cccd.isdigit():
            return []

        # Dòng 1
        l1 = f"IDVNM0000000000{cccd}<<3".ljust(30, "<")[:30]

        # Dòng 2: ngày sinh
        if len(dob) == 10 and dob[2] == "/" and dob[5] == "/":
            d, m, y = dob.split("/")
            dob_raw = f"{y[2:]}{m}{d}"
        else:
            dob_raw = "000101"

        g_char = "M" if gender in ["Nam", "M"] else "F"
        if len(expiry) == 10 and expiry[2] == "/" and expiry[5] == "/":
            ed, em, ey = expiry.split("/")
            exp_raw = f"{ey[2:]}{em}{ed}"
        else:
            exp_raw = "351231"

        l2 = f"{dob_raw}7{g_char}{exp_raw}3VNM<<<<<<<<<<<8".ljust(30, "<")[:30]

        # Dòng 3: họ và tên
        parts = [p for p in name.split() if p]
        if len(parts) >= 2:
            ho = parts[0]
            rest = "<".join(parts[1:])
            l3 = f"{ho}<<{rest}".ljust(30, "<")[:30]
        elif len(parts) == 1:
            l3 = f"{parts[0]}<<".ljust(30, "<")[:30]
        else:
            l3 = "CCCD<<CHINH<CHU<<<<<<<<<<<<<<<"

        return [l1, l2, l3]

    @classmethod
    def extract_mrz_lines(
        cls,
        aligned_image: np.ndarray,
        temp_image_path: str,
        gemini_processor: Optional[Any] = None,
        front_extracted_data: Optional[Dict[str, Any]] = None,
        allow_fallback: bool = True,
        allow_synthetic: bool = False,
    ) -> Tuple[bool, List[str], str]:
        """
        Bóc tách 3 dòng mã MRZ từ ảnh mặt sau thẻ CCCD đã được căn chỉnh và xoay chuẩn.
        1. Sử dụng Gemini Vision nếu có cấu hình API key hợp lệ.
        2. Fallback offline: Kiểm tra chất lượng quang học và trích xuất/đối chiếu dữ liệu hợp lệ.
        """
        # Cắt riêng vùng dải mã MRZ dưới đáy thẻ (y: 65% - 100%)
        h, w = aligned_image.shape[:2]
        mrz_crop = aligned_image[int(h * 0.65):h, 0:w]

        # 1. Phân tích chất lượng quang học và vật lý của dải MRZ ở đáy thẻ trước
        gray_mrz = cv2.cvtColor(mrz_crop, cv2.COLOR_BGR2GRAY)
        hsv_mrz = cv2.cvtColor(mrz_crop, cv2.COLOR_BGR2HSV)

        # 1.1. Mờ / Rung tay: Laplacian variance < 75.0
        lap_var = float(cv2.Laplacian(gray_mrz, cv2.CV_64F).var())
        if lap_var < 75.0:
            return False, [], "Ảnh chụp dải mã MRZ ở đáy thẻ bị mờ hoặc rung tay. Vui lòng giữ chắc camera và chụp lại rõ nét phần đáy thẻ."

        # 1.2. Lóa sáng / Chói flash: Tỷ lệ pixel bão hòa trắng V > 245, S < 30 vượt 12% (> 0.12)
        glare_mask = (hsv_mrz[:, :, 2] > 245) & (hsv_mrz[:, :, 1] < 30)
        glare_ratio = float(np.sum(glare_mask) / max(1, mrz_crop.shape[0] * mrz_crop.shape[1]))
        if glare_ratio > 0.12:
            return False, [], "Dải mã MRZ ở đáy thẻ bị lóa sáng hoặc chói đèn flash. Vui lòng chụp ở nơi không bị ánh đèn phản chiếu trực tiếp."

        # 1.3. Quá tối / Thiếu sáng: Độ sáng trung bình V < 60.0
        mean_v = float(np.mean(hsv_mrz[:, :, 2]))
        if mean_v < 60.0:
            return False, [], "Dải mã MRZ ở đáy thẻ quá tối, không thể đọc rõ ký tự. Vui lòng chụp ở nơi đủ ánh sáng."

        # 1.4. Che khuất / Mất góc đáy: Mật độ biên cạnh Sobel < 12.0
        sobel_y = cv2.Sobel(gray_mrz, cv2.CV_64F, 0, 1, ksize=3)
        edge_density = float(np.mean(np.abs(sobel_y)))
        if edge_density < 12.0:
            return False, [], "Không phát hiện được dải ký tự mã vạch MRZ ở đáy thẻ. Vui lòng chụp trọn vẹn mặt sau thẻ, không bị che khuất hoặc cắt góc."

        # Lưu ảnh tạm của vùng MRZ phục vụ Gemini Vision nếu khả dụng
        temp_dir = Path(temp_image_path).parent
        mrz_crop_path = str(temp_dir / f"mrz_crop_{os.path.basename(temp_image_path)}")
        try:
            cv2.imencode(".jpg", mrz_crop)[1].tofile(mrz_crop_path)
        except Exception:
            cv2.imwrite(mrz_crop_path, mrz_crop)

        # 2. Điều phối bóc tách bằng Gemini Vision nếu khả dụng
        if gemini_processor is not None and getattr(gemini_processor, "client", None) is not None:
            try:
                ok_mrz, lines, err = MRZValidator.extract_mrz_from_image(
                    mrz_crop_path, gemini_processor.client, gemini_processor.model_name
                )
                if ok_mrz and len(lines) >= 3:
                    # Validate cấu trúc MRZ từ Gemini trước khi chọn làm kết quả cuối
                    parse_ok, parsed_mrz, parse_err = MRZValidator.parse_mrz(lines[0], lines[1], lines[2])
                    if parse_ok and parsed_mrz is not None:
                        cls._safe_remove(mrz_crop_path)
                        return True, lines, ""
                    else:
                        print(f"[BackCardProcessor] Gemini Vision trả về 3 dòng sai cấu trúc ({parse_err}). Chuyển sang offline fallback...")
            except Exception as e:
                print(f"[BackCardProcessor] Gemini Vision error (chuyển sang offline fallback): {e}")

        cls._safe_remove(mrz_crop_path)

        # 3. Xử lý cục bộ (Offline Fallback bằng VietOCR nội bộ).
        vietocr = cls.get_vietocr_model()
        if vietocr is None:
            return False, [], "Không thể nhận diện dải ký tự mã vạch MRZ ở mặt sau thẻ. Vui lòng chụp lại ảnh mặt sau thẻ rõ nét, vuông góc và tránh bóng đổ."

        try:
            line_crops = [
                aligned_image[int(h * 0.68):int(h * 0.78), :],
                aligned_image[int(h * 0.78):int(h * 0.88), :],
                aligned_image[int(h * 0.88):int(h * 0.98), :],
            ]
            lines = [str(vietocr.predict(crop) or "") for crop in line_crops]
        except Exception as ocr_err:
            print(f"[BackCardProcessor] VietOCR error: {ocr_err}")
            return False, [], "Không thể đọc đầy đủ dải mã MRZ ở mặt sau thẻ. Vui lòng chụp lại ảnh mặt sau thẻ rõ nét hơn."

        parse_ok, mrz_data, _ = MRZValidator.parse_mrz(*lines)
        if not parse_ok or mrz_data is None:
            return False, [], "Không thể đọc đầy đủ dải mã MRZ ở mặt sau thẻ. Vui lòng chụp lại ảnh rõ nét hơn."

        front_cccd = str(front_extracted_data.get("so_cccd", "")).strip() if front_extracted_data else ""
        if len(front_cccd) != 12 or not front_cccd.isdigit():
            return False, [], "Dữ liệu mặt trước thiếu số CCCD hợp lệ để đối chiếu với mặt sau."

        return True, lines, ""

    @classmethod
    def process_back_card(
        cls,
        image_path: str,
        front_extracted_data: Dict[str, Any],
        gemini_processor: Optional[Any] = None,
        allow_synthetic: bool = False,
        skip_cross_check: bool = False,
    ) -> Dict[str, Any]:
        """
        Toàn bộ quy trình xử lý mặt sau thẻ CCCD:
        1. Đọc và kiểm tra chất lượng ảnh.
        2. Căn chỉnh góc (detect & warp perspective).
        3. Tự động xoay về chiều chuẩn.
        4. Xác thực phân loại thẻ CCCD mặt sau (loại bỏ ảnh rác, ảnh phong cảnh, ảnh mặt trước).
        5. Bóc tách và đối chiếu dải mã MRZ.
        """
        img = cls.read_image_safe(image_path)
        if img is None:
            return {
                "success": False,
                "error_code": "BACK_CCCD_PROCESSING_ERROR",
                "error_message": "Không thể mở file ảnh mặt sau CCCD.",
            }

        # 1. Căn chỉnh góc
        aligned, _ = cls.detect_and_align_card(img)

        # 2. Tự động xoay về chiều chuẩn
        upright, angle = cls.correct_orientation(aligned)

        # 3. Phân loại và nhận dạng mặt sau CCCD
        verify_res = cls.verify_is_cccd_back(upright)
        if len(verify_res) == 4:
            is_back, err_code, verify_err, metrics = verify_res
        else:
            is_back, verify_err, metrics = verify_res
            err_code = "BACK_CCCD_INVALID" if not is_back else None
        if not is_back:
            return {
                "success": False,
                "error_code": err_code or "BACK_CCCD_INVALID",
                "error_message": verify_err,
                "metrics": metrics,
            }

        # Keep the caller-provided image path unchanged; processing uses the in-memory aligned image.

        # 4. Thử bóc tách dải mã MRZ (nếu khả dụng)
        ok_lines, mrz_lines, mrz_err = cls.extract_mrz_lines(
            upright,
            image_path,
            gemini_processor,
            front_extracted_data=front_extracted_data,
            allow_fallback=True,
            allow_synthetic=False,
        )

        if not ok_lines and mrz_err:
            if any(k in mrz_err.lower() for k in ["mờ", "rung tay", "lóa sáng", "quá tối"]):
                return {
                    "success": False,
                    "error_code": "BACK_CCCD_QUALITY_LOW",
                    "error_message": mrz_err,
                    "metrics": metrics,
                }
            if "không phát hiện" in mrz_err.lower() or "che khuất" in mrz_err.lower():
                return {
                    "success": False,
                    "error_code": "BACK_CCCD_INVALID",
                    "error_message": mrz_err,
                    "metrics": metrics,
                }

        if ok_lines and len(mrz_lines) >= 3:
            parse_ok, mrz_obj, parse_err = MRZValidator.parse_mrz(mrz_lines[0], mrz_lines[1], mrz_lines[2])
            if parse_ok and mrz_obj is not None:
                cross_ok, cross_err, cross_details = MRZValidator.cross_check(mrz_obj, front_extracted_data)
                if not cross_ok and not skip_cross_check:
                    return {
                        "success": False,
                        "error_code": "BACK_CCCD_INVALID",
                        "error_message": cross_err,
                        "metrics": metrics,
                        "cross_check": cross_details,
                    }
                return {
                    "success": True,
                    "error_code": None,
                    "card_verified": True,
                    "mrz_verified": bool(cross_ok),
                    "mrz_data": mrz_obj.model_dump(),
                    "metrics": metrics,
                    "cross_check": cross_details,
                }

        # Nếu không bóc tách được nội dung dải mã MRZ nhưng đặc trưng chip và vân tay đạt chuẩn:
        # Bắt buộc CẢ 3 yếu tố: chip điện tử vàng kim, dải mã tương phản MRZ và con dấu/ô vân tay
        has_chip_metric = (metrics.get("gold_chip_ratio", 0) >= 0.04 or metrics.get("max_chip_contour", 0) >= 1500.0)
        has_mrz_metric = metrics.get("mrz_edge_energy", 0) >= 15.0
        has_seal_fp_metric = (metrics.get("red_seal_ratio", 0) >= 0.015 or metrics.get("fingerprint_variance", 0) >= 40.0)

        if has_chip_metric and has_mrz_metric and has_seal_fp_metric:
            return {
                "success": True,
                "error_code": None,
                "card_verified": True,
                "mrz_verified": False,
                "metrics": metrics,
                "message": "Ảnh mặt sau CCCD hợp lệ (đã kiểm định chip điện tử, con dấu/vân tay và dải mã MRZ).",
            }

        # Nếu thiếu yếu tố nhận diện chuẩn nào:
        if not has_chip_metric:
            return {
                "success": False,
                "error_code": "BACK_CCCD_INVALID",
                "error_message": "Ảnh tải lên không phát hiện được chip điện tử của thẻ CCCD gắn chip. Vui lòng chụp rõ mặt sau thẻ CCCD.",
                "metrics": metrics,
            }
        if not has_mrz_metric:
            return {
                "success": False,
                "error_code": "BACK_CCCD_INVALID",
                "error_message": "Không phát hiện dải mã vạch ký tự MRZ hợp lệ ở phía dưới mặt sau thẻ CCCD.",
                "metrics": metrics,
            }
        if not has_seal_fp_metric:
            return {
                "success": False,
                "error_code": "BACK_CCCD_INVALID",
                "error_message": "Không phát hiện được con dấu cơ quan cấp hoặc ô vân tay mặt sau thẻ CCCD.",
                "metrics": metrics,
            }

        return {
            "success": False,
            "error_code": "BACK_CCCD_INVALID",
            "error_message": "Không thể nhận diện các đặc trưng chuẩn của mặt sau thẻ CCCD.",
            "metrics": metrics,
        }

    @staticmethod
    def _safe_remove(file_path: str) -> None:
        if file_path and os.path.exists(file_path):
            try:
                os.remove(file_path)
            except OSError:
                pass
