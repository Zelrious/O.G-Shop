package com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Danh mục các lý do trả hàng chuẩn hóa theo Báo cáo đồ án (BR-UC62-05).
 * 
 * Lưu ý về ĐÁNH ĐỔI KỸ THUẬT:
 * Schema og_compat.cases (V19/V22) chỉ có cột description TEXT, không có cột reason_code riêng.
 * Để bảo toàn kiến trúc hợp nhất 48 bảng (tránh sửa INSTEAD OF trigger og70_write_cases rất nhạy cảm của V22),
 * hệ thống gom mã lý do vào tiền tố chuẩn hóa trong description: [CODE] <chi tiết người dùng gõ>.
 * Enum này là nguồn duy nhất định nghĩa các mã lý do và hàm parse/format tiền tố.
 */
public enum ReturnReasonCode {
    CHANGE_MIND("[CHANGE_MIND]", "Người mua đổi ý khi chưa mở và còn nguyên seal"),
    NOT_AS_DESCRIBED("[NOT_AS_DESCRIBED]", "Hàng sai mô tả, sai mẫu mã hoặc thiếu phụ kiện"),
    DAMAGED_OR_DEFECTIVE("[DAMAGED_OR_DEFECTIVE]", "Hàng hư hỏng, vỡ, lỗi kỹ thuật không hoạt động");

    private static final Pattern PREFIX_PATTERN = Pattern.compile("^\\[([A-Z_]+)\\]\\s*(.*)$", Pattern.DOTALL);

    private final String prefix;
    private final String defaultTitle;

    ReturnReasonCode(String prefix, String defaultTitle) {
        this.prefix = prefix;
        this.defaultTitle = defaultTitle;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getDefaultTitle() {
        return defaultTitle;
    }

    public static String format(ReturnReasonCode code, String detail) {
        Objects.requireNonNull(code, "ReturnReasonCode must not be null");
        String trimmed = detail == null ? "" : detail.trim();
        return trimmed.isEmpty() ? code.getPrefix() : code.getPrefix() + " " + trimmed;
    }

    public static ParsedReason parse(String rawDescription) {
        if (rawDescription == null || rawDescription.isBlank()) {
            return new ParsedReason(null, "");
        }
        String trimmed = rawDescription.trim();
        Matcher matcher = PREFIX_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            String tag = matcher.group(1);
            String detail = matcher.group(2).trim();
            for (ReturnReasonCode candidate : values()) {
                if (candidate.name().equalsIgnoreCase(tag)) {
                    return new ParsedReason(candidate, detail);
                }
            }
        }
        return new ParsedReason(null, trimmed);
    }

    public record ParsedReason(ReturnReasonCode reasonCode, String detail) {
        public boolean hasStandardReason() {
            return reasonCode != null;
        }
    }
}
