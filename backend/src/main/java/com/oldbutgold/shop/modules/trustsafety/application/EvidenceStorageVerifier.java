package com.oldbutgold.shop.modules.trustsafety.application;

import java.io.IOException;

/**
 * Port xác minh và tính toán mã băm SHA-256 từ nội dung tệp thực tế trên hệ thống lưu trữ (Storage).
 * Được sử dụng khi tệp bằng chứng không nằm trên bộ nhớ cục bộ (local disk).
 *
 * <p>Nguyên tắc thiết kế:
 * <ul>
 *   <li>Chỉ chấp nhận bằng chứng khi đọc được nội dung byte thực tế của tệp để tính mã băm.</li>
 *   <li>Fail-closed: Nếu không có verifier hoặc xảy ra lỗi I/O, hệ thống lập tức từ chối submission.</li>
 *   <li>Tuyệt đối không chấp nhận digest chỉ dựa vào định dạng hợp lệ nếu không đọc được nội dung file.</li>
 * </ul>
 */
public interface EvidenceStorageVerifier {

    /**
     * Đọc nội dung tệp thực tế từ hệ thống lưu trữ và tính toán mã băm SHA-256.
     *
     * @param objectKey mã định danh hoặc đường dẫn tệp trên hệ thống lưu trữ
     * @return chuỗi hex 64 ký tự biểu diễn SHA-256 của nội dung tệp thực tế, hoặc null nếu không tìm thấy tệp
     * @throws IOException nếu xảy ra lỗi I/O trong quá trình đọc tệp từ storage
     */
    String computeSha256(String objectKey) throws IOException;
}

