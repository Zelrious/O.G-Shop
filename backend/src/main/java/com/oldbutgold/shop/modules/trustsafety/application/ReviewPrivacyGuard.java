package com.oldbutgold.shop.modules.trustsafety.application;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates public counterparty review comments against BR-UC61-03 and BR-UC46-02:
 * public comments must not expose private addresses, bank/account numbers, contact info, or private evidence URLs.
 */
public final class ReviewPrivacyGuard {

    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(?:\\+?84|0)(?:\\d[\\s.-]?){8,9}\\d(?!\\d)");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern BANK_KEYWORD_NUMBER = Pattern.compile(
            "(?i)(?:stk|số tài khoản|so tai khoan|tài khoản ngân hàng|iban|vietcombank|vcb|techcombank|tcb|bidv|vietinbank|mbbank|acb|vpbank|tpbank|agribank|sacombank)[\\s:.-]*\\d{6,19}"
    );
    private static final Pattern PRIVATE_MEDIA_URL = Pattern.compile(
            "(?i)(?:/ekyc/|/evidence/|case_evidence|private-assets|unboxing|/api/v1/media/private)"
    );
    private static final Pattern ADDRESS_PATTERN = Pattern.compile(
            "(?i)(?:số nhà|so nha|địa chỉ riêng|dia chi rieng)\\s*[^\\n]{0,30}\\d+|\\b(?:ngõ|ngách|hẻm|đường)\\s+\\d+[\\w/.-]*"
    );

    private ReviewPrivacyGuard() {
    }

    public static void validatePublicComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return;
        }
        String trimmed = comment.trim();
        if (trimmed.length() > 2000) {
            throw new IllegalArgumentException("Nội dung nhận xét tối đa 2000 ký tự.");
        }
        if (PHONE_PATTERN.matcher(trimmed).find() || EMAIL_PATTERN.matcher(trimmed).find()) {
            throw new IllegalArgumentException("Nhận xét công khai không được chứa số điện thoại hoặc email cá nhân (BR-UC61-03).");
        }
        if (BANK_KEYWORD_NUMBER.matcher(trimmed).find()) {
            throw new IllegalArgumentException("Nhận xét công khai không được chứa thông tin tài khoản ngân hàng (BR-UC61-03).");
        }
        if (ADDRESS_PATTERN.matcher(trimmed).find()) {
            throw new IllegalArgumentException("Nhận xét công khai không được chứa địa chỉ riêng (BR-UC61-03).");
        }
        if (PRIVATE_MEDIA_URL.matcher(trimmed).find()) {
            throw new IllegalArgumentException("Nhận xét công khai không được chứa đường dẫn bằng chứng hoặc tài liệu riêng tư (BR-UC61-03).");
        }
    }

    public static String sanitizeSnippetForNotification(String comment) {
        if (comment == null || comment.isBlank()) {
            return "";
        }
        String clean = comment.trim().replaceAll("\\s+", " ");
        if (clean.length() <= 120) {
            return clean;
        }
        return clean.substring(0, 117) + "...";
    }
}

