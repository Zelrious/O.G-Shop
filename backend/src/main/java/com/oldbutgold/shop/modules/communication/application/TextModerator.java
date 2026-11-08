package com.oldbutgold.shop.modules.communication.application;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TextModerator: High-performance, zero-AI-quota chat text moderation
 * and anti-disintermediation engine (< 2ms on laptop CPU).
 */
@Component
public class TextModerator {

    public record TextModerationResult(
        boolean isViolation,
        String violationType,
        String maskedText,
        String reason,
        String severityLevel
    ) {
        public TextModerationResult(boolean isViolation, String violationType, String maskedText, String reason) {
            this(isViolation, violationType, maskedText, reason, isViolation ? "MEDIUM" : null);
        }

        public static TextModerationResult clean(String text) {
            return new TextModerationResult(false, null, text, null, null);
        }

        public static TextModerationResult violation(String violationType, String maskedText, String reason) {
            return new TextModerationResult(true, violationType, maskedText, reason, "MEDIUM");
        }

        public static TextModerationResult violation(String violationType, String maskedText, String reason, String severityLevel) {
            return new TextModerationResult(true, violationType, maskedText, reason, severityLevel);
        }
    }

    public record MessageSnippet(long senderId, String digits, Instant timestamp) {}

    // In-memory sliding window buffer: conversationId -> Deque of up to 50 snippets with 30-minute TTL
    public static final int SLIDING_WINDOW_CAPACITY = 50;
    public static final Duration SLIDING_WINDOW_TTL = Duration.ofMinutes(30);
    private final ConcurrentHashMap<Long, Deque<MessageSnippet>> slidingWindow = new ConcurrentHashMap<>();

    // Patterns for anti-false-positive filtering (prices, sizes, weights, counts)
    private static final Pattern MONEY_UNITS = Pattern.compile(
        "(?i)(?<=^|[\\s\\p{Punct}])\\d+(?:[.,]\\d+)?\\s*(?:k|tr|triệu|trieu|củ|cu|lít|lit|cành|canh|đ|d|dong|đồng|vnd|vnđ)(?=[\\s\\p{Punct}]|$)"
    );
    private static final Pattern ATTACHED_K = Pattern.compile("(?i)(?<=^|[\\s\\p{Punct}])\\d+k(?=[\\s\\p{Punct}]|$)");
    private static final Pattern THOUSAND_FORMAT = Pattern.compile(
        "(?<=^|[\\s\\p{Punct}])[1-9]\\d{0,2}(?:\\.\\d{3})+\\s*(?:đ|dong|đồng|vnd|vnđ)\\b|(?<=^|[\\s\\p{Punct}])[1-9]\\d{0,2}(?:\\.\\d{3}){2,}(?=[\\s\\p{Punct}]|$)"
    );
    private static final Pattern DIMENSION_UNITS = Pattern.compile(
        "(?i)(?<=^|[\\s\\p{Punct}])\\d+(?:[.,]\\d+)?\\s*(?:cm|mm|m|km|dm|kg|g|gram|gam|inch|in|px|w|watt|v|volt|hz)(?=[\\s\\p{Punct}]|$)"
    );
    private static final Pattern MULTI_DIMENSIONS = Pattern.compile(
        "(?i)\\b\\d+\\s*[xX]\\s*\\d+(?:\\s*[xX]\\s*\\d+)?(?:\\s*(?:cm|mm|m|in))?\\b|(?i)\\b\\d+\\s*[*]\\s*\\d+(?:\\s*[*]\\s*\\d+)?\\s*(?:cm|mm|m|in)\\b"
    );
    private static final Pattern COUNT_UNITS = Pattern.compile(
        "(?i)(?<=^|[\\s\\p{Punct}])\\d+\\s*(?:cái|cai|chiếc|chiec|bộ|bo|đôi|doi|cặp|cap|món|mon|cuốn|cuon|quyển|quyen|tập|tap|hộp|hop)(?=[\\s\\p{Punct}]|$)"
    );
    private static final Pattern TIME_UNITS = Pattern.compile(
        "(?i)(?<=^|[\\s\\p{Punct}])\\d+\\s*(?:tháng|thang|ngày|ngay|giờ|gio|phút|phut|tuần|tuan|giây|giay)(?=[\\s\\p{Punct}]|$)"
    );
    private static final Pattern PERCENTAGE_UNITS = Pattern.compile(
        "(?<=^|[\\s\\p{Punct}])\\d+(?:[.,]\\d+)?\\s*%(?=[\\s\\p{Punct}]|$)"
    );

    // Invisible / zero-width and control characters
    private static final Pattern INVISIBLE_CHARS = Pattern.compile(
        "[\\u200B-\\u200D\\uFEFF\\u200E\\u200F\\u202A-\\u202E\\u2060-\\u2069]"
    );

    // Emoji and symbols (including keycap sequences 0️⃣-9️⃣)
    private static final Pattern KEYCAP_COMBINER = Pattern.compile("[\\uFE0F\\u20E3]");
    private static final Pattern EMOJI_AND_SYMBOLS = Pattern.compile(
        "[\\uD83C-\\uD83E][\\uDC00-\\uDFFF]|[\\u2600-\\u27BF]|[\\u2300-\\u23FF]|[\\u2B50-\\u2B55]|[\\u203C\\u2049\\u2122\\u2139\\u2194-\\u2199\\u21A9-\\u21AA\\u25AA-\\u25AB\\u25B6\\u25C0\\u25FB-\\u25FE]"
    );

    // 1. Political & Subversive (Severity: CRITICAL)
    private static final Pattern POLITICAL_SUBVERSIVE_PATTERN = Pattern.compile(
        "(?iu)(?<=^|[\\s\\p{Punct}])(?:" +
        "(?:đả\\s*đảo|đà\\s*đào|da\\s*dao)(?:\\s+(?:đcs|dcs|đảng|dang|chế\\s*độ|che\\s*do|nhà\\s*nước|nha\\s*nuoc|cộng\\s*sản|cong\\s*san|chính\\s*quyền|chinh\\s*quyen))?|" +
        "đcs|" +
        "việt\\s*tân|viet\\s*tan|" +
        "đào\\s*minh\\s*quân|dao\\s*minh\\s*quan|" +
        "triều\\s*đại\\s*việt|trieu\\s*dai\\s*viet|" +
        "chính\\s*phủ\\s*(?:quốc\\s*gia\\s*)?(?:việt\\s*nam\\s*)?lâm\\s*thời|chinh\\s*phu\\s*(?:quoc\\s*gia\\s*)?(?:viet\\s*nam\\s*)?lam\\s*thoi|" +
        "lật\\s*đổ\\s*(?:đcs|dcs|chế\\s*độ|đảng|chính\\s*quyền|nhà\\s*nước)|lat\\s*do\\s*(?:dcs|che\\s*do|dang|chinh\\s*quyen|nha\\s*nuoc)|" +
        "chống\\s*phá\\s*(?:đcs|dcs|chế\\s*độ|đảng|chính\\s*quyền|nhà\\s*nước)|chong\\s*pha\\s*(?:dcs|che\\s*do|dang|chinh\\s*quyen|nha\\s*nuoc)|" +
        "tiêu\\s*diệt\\s*(?:đcs|dcs|đảng|cộng\\s*sản)|tieu\\s*diet\\s*(?:dcs|dang|cong\\s*san)|" +
        "xóa\\s*bỏ\\s*điều\\s*4|xoa\\s*bo\\s*dieu\\s*4|" +
        "cách\\s*mạng\\s*(?:màu|trắng)|cach\\s*mang\\s*(?:mau|trang)|" +
        "đa\\s*đảng|da\\s*dang|" +
        "tam\\s*quyền\\s*phân\\s*lập|tam\\s*quyen\\s*phan\\s*lap|" +
        "đảng\\s*cộng\\s*sản\\s*bán\\s*nước|dang\\s*cong\\s*san\\s*ban\\s*nuoc|" +
        "tà\\s*quyền|ta\\s*quyen|" +
        "việt\\s*gian\\s*bán\\s*nước|viet\\s*gian\\s*ban\\s*nuoc|" +
        "bò\\s*đỏ|bo\\s*do|" +
        "dư\\s*luận\\s*viên|du\\s*luan\\s*vien|" +
        "ngụy\\s*quyền|nguy\\s*quyen|" +
        "cờ\\s*vàng\\s*(?:ba\\s*sọc)?|co\\s*vang\\s*(?:ba\\s*soc)?|cờ\\s*ba\\s*sọc|co\\s*ba\\s*soc|" +
        "quân\\s*lực\\s*vnch|quan\\s*luc\\s*vnch|" +
        "phục\\s*quốc|phuc\\s*quoc|" +
        "xuống\\s*đường\\s*biểu\\s*tình|xuong\\s*duong\\s*bieu\\s*tinh|" +
        "kêu\\s*gọi\\s*tổng\\s*đình\\s*công|keu\\s*goi\\s*tong\\s*dinh\\s*cong|" +
        "bao\\s*vây\\s*ủy\\s*ban|bao\\s*vay\\s*uy\\s*ban|" +
        "bạo\\s*động\\s*vũ\\s*trang|bao\\s*dong\\s*vu\\s*trang" +
        ")(?=[\\s\\p{Punct}]|$)"
    );

    // 2. Hate speech & Regional discrimination (Severity: HIGH)
    private static final Pattern HATE_SPEECH_REGIONAL_PATTERN = Pattern.compile(
        "(?iu)(?<=^|[\\s\\p{Punct}])(?:" +
        "bắc\\s*kỳ|bac\\s*ky|bắc\\s*cầy|bac\\s*cay|parky|bucky|bắc\\s*cụ|bac\\s*cu|pake|bake|" +
        "nam\\s*kỳ|nam\\s*ky|nam\\s*cầy|nam\\s*cay|nammay|dân\\s*nam\\s*kỳ|dan\\s*nam\\s*ky|" +
        "trọ\\s*trẹ|tro\\s*tre|dân\\s*trọ\\s*trẹ|dan\\s*tro\\s*tre|" +
        "hoa\\s*thanh\\s*quế|hoa\\s*thanh\\s*que|" +
        "(?:bọn|lũ|dân)\\s*(?:36|37)|" +
        "(?:không\\s*bán\\s*cho\\s*(?:dân\\s*)?(?:bắc|nam|trung|trọ\\s*trẹ))|" +
        "mọi\\s*(?:miên|rợ|da\\s*đen)|moi\\s*(?:mien|ro|da\\s*den)|" +
        "khỉ\\s*rừng|khi\\s*rung|" +
        "nigger|nigga|chink|gook|negro" +
        ")(?=[\\s\\p{Punct}]|$)"
    );

    // 3. Harassment & Threat (Severity: HIGH)
    private static final Pattern HARASSMENT_THREAT_PATTERN = Pattern.compile(
        "(?iu)(?<=^|[\\s\\p{Punct}])(?:" +
        "giết|giet|kill|murder|" +
        "(?:chém(?!\\s*gió)|chem(?!\\s*gio))(?:\\s+(?:chết|chet|mày|may|nó|no|tao|người|nguoi|nhau|đứt))?|" +
        "đâm\\s*chết|dam\\s*chet|bắn\\s*chết|ban\\s*chet|đánh\\s*chết|danh\\s*chet|" +
        "đâm\\s+(?:mày|may|nó|no|người|nguoi)|" +
        "bắn\\s+(?:mày|may|nó|no|người|nguoi|chết|chet)|" +
        "thủ\\s*tiêu|thu\\s*tieu|" +
        "xử\\s*(?:mày|may|đẹp|dep|chết|chet)|" +
        "tao\\s*(?:sẽ\\s+|se\\s+)?(?:chém|chem|giết|giet|đập|dap|đánh|danh|bắn|ban)\\s*(?:chết|chet|mày|may|nát|cho|mất\\s*xác)|" +
        "đập\\s*(?:nát|cho|vỡ)\\s*(?:mặt|mồm|alo|mõm)|dap\\s*(?:nat|cho|vo)\\s*(?:mat|mom|alo|moom)|" +
        "tìm\\s*(?:tận|toi)\\s*nhà|tim\\s*(?:tan|toi)\\s*nha|" +
        "giết\\s*(?:cả\\s*)?(?:nhà|gia\\s*đình|họ)|giet\\s*(?:ca\\s*)?(?:nha|gia\\s*dinh|ho)|" +
        "chờ\\s*(?:đấy|day)\\s*tao\\s*(?:qua|đến|tới)\\s*tận\\s*nơi|" +
        "mày\\s*coi\\s*chừng\\s*tao|may\\s*coi\\s*chung\\s*tao|" +
        "bố\\s*láo\\s*tao\\s*đập|bo\\s*lao\\s*tao\\s*dap|" +
        "cho\\s*ăn\\s*(?:đòn|chém|đập|kẹo\\s*đồng)|cho\\s*an\\s*(?:don|chem|dap|keo\\s*dong)|" +
        "đốt\\s*(?:nhà|quán|tiệm)|dot\\s*(?:nha|quan|tiem)|" +
        "(?:i\\s+will|i'll|imma|gonna)\\s+(?:kill|find|beat|break|shoot)\\s+(?:you|your)|" +
        "beat\\s+your\\s+ass|kick\\s+your\\s+ass|break\\s+your\\s+face|" +
        "óc\\s*chó|oc\\s*cho|" +
        "đồ\\s*súc\\s*vật|do\\s*suc\\s*vat|súc\\s*sinh|suc\\s*sinh|" +
        "não\\s*tàn|nao\\s*tan|" +
        "đồ\\s*ngu\\s*học|do\\s*ngu\\s*hoc|" +
        "ngu\\s*như\\s*(?:bò|chó|heo|lợn)|ngu\\s*nhu\\s*(?:bo|cho|heo|lon)|" +
        "chó\\s*tha|đồ\\s*chó|do\\s*cho|" +
        "retard|moron|stupid\\s*animal|scum|" +
        "thằng\\s*lừa\\s*đảo|thang\\s*lua\\s*dao|" +
        "con\\s*trộm\\s*cắp|con\\s*trom\\s*cap|" +
        "ăn\\s*cướp\\s*giữa\\s*ban\\s*ngày|an\\s*cuop\\s*giua\\s*ban\\s*ngay|" +
        "scammer|thief" +
        ")(?=[\\s\\p{Punct}]|$)"
    );

    // 4. Offensive Profanity (Severity: LOW)
    private static final Pattern OFFENSIVE_PROFANITY_PATTERN = Pattern.compile(
        "(?iu)(?<=^|[\\s\\p{Punct}])(?:" +
        "đm|dmm|đcm|dcm|đcl|vcl|vkl|dkm|vcc|vlon|" +
        "đ\\.m|d\\.m|đ_m|v\\.c\\.l|c\\.ặ\\.c|l\\.ồ\\.n|đ-é-o|đ3o|loz|l0n|c4c|" +
        "đéo|deo|đếch|dech|" +
        "cặc|cak|buồi|buoi|lồn|l0n|loz|" +
        "chó\\s*đẻ|cho\\s*de|" +
        "đĩ|mẹ\\s*kiếp|me\\s*kiep|khốn\\s*nạn|khon\\s*nan|chết\\s*tiệt|chet\\s*tiet|" +
        "đậu\\s*má|đụ\\s*má|đụ\\s*mẹ|dau\\s*ma|du\\s*ma|du\\s*me|" +
        "địt\\s*mẹ|dit\\s*me|địt|dit|" +
        "fuck|fucking|fucked|fucker|fck|f\\*ck|f\\.u\\.c\\.k|" +
        "shit|bitch|bastard|asshole|dick|pussy|cunt|cock|motherfucker|stfu|gtfo|bullshit" +
        ")(?=[\\s\\p{Punct}]|$)"
    );

    // Platform redirect slang patterns
    private static final Pattern ZALO_SLANG = Pattern.compile(
        "(?i)(?<=^|[\\s\\p{Punct}])(?:kết\\s+bạn\\s+)?(?:da\\s*lô|da\\s*lo|dép\\s*lào|dep\\s*lao|z\\.?l|zalo|za\\s+lo)(?=[\\s\\p{Punct}]|$)|(?i)zalo\\.me"
    );
    private static final Pattern FACEBOOK_SLANG = Pattern.compile(
        "(?i)(?<=^|[\\s\\p{Punct}])(?:phây\\s*búc|phay\\s*buc|phây|phay|f\\.?b|facebook|face\\s*book)(?=[\\s\\p{Punct}]|$)|(?i)fb\\.com"
    );
    private static final Pattern TELEGRAM_SLANG = Pattern.compile(
        "(?i)(?<=^|[\\s\\p{Punct}])(?:tê\\s*lê|te\\s*le|tele|telegram)(?=[\\s\\p{Punct}]|$)|(?i)t\\.me"
    );
    private static final Pattern EXTERNAL_URL = Pattern.compile(
        "(?i)https?://\\S+|\\b[a-z0-9-]+(?:\\[\\.\\]|\\(\\.\\)|\\.)(?:com|vn|net|org|me|io|info)\\b"
    );

    // Off-platform payment slang patterns
    private static final Pattern OFF_PLATFORM_PAYMENT_PATTERN = Pattern.compile(
        "(?i)(?<=^|[\\s\\p{Punct}])(?:cọc\\s*trước|coc\\s*truoc|đặt\\s*cọc\\s*trước|dat\\s*coc\\s*truoc|chuyển\\s*(?:khoản\\s*)?cọc|chuyen\\s*(?:khoan\\s*)?coc|chuyển\\s*lúa|chuyen\\s*lua|chuyển\\s*khoản\\s*riêng|ck\\s*riêng|ck\\s*rieng|ck\\s*ngoài|ck\\s*ngoai|chuyển\\s*khoản\\s*ngoài|bắn\\s*tiền\\s*ngoài|ban\\s*tien\\s*ngoai|giao\\s*dịch\\s*ngoài|gd\\s*ngoai|gd\\s*ngoài)(?=[\\s\\p{Punct}]|$)"
    );

    // Direct phone regex matching 10 digits starting with 0 or +84 (allows any second digit to catch 0123456789, test numbers, old 012/016 prefixes, landlines)
    // OR 9 digits starting with [35789] (omitted leading 0, e.g. 987654321, 912345678)
    private static final Pattern VIETNAMESE_PHONE_WITH_SEPARATORS = Pattern.compile(
        "(?i)(?<!\\d)(?:(?:\\+?84|0)[\\s.*_()/\\-]*\\d(?:[\\s.*_()/\\-]*\\d){8}|[35789](?:[\\s.*_()/\\-]*\\d){8})(?!\\d)"
    );

    // Advanced phone regex supporting homoglyphs (O/0, l/1, S/5, B/8), zero-width chars, and separators
    private static final Pattern VIETNAMESE_PHONE_WITH_HOMOGLYPHS = Pattern.compile(
        "(?i)(?<!\\d)(?:(?:\\+?84|[0Oo])[\\s.*_()/\\-\\u200B-\\u200D\\uFEFF]*[0-9OoLlI|SsBb](?:[\\s.*_()/\\-\\u200B-\\u200D\\uFEFF]*[0-9OoLlI|SsBb]){8}|[35Ss78Bb9](?:[\\s.*_()/\\-\\u200B-\\u200D\\uFEFF]*[0-9OoLlI|SsBb]){8})(?!\\d)"
    );

    // Padded digit sequences (10 to 20 continuous or separator-spaced digits e.g. 123450912345678999 "lấy 10 số ở giữa")
    private static final Pattern PADDED_DIGITS_PATTERN = Pattern.compile(
        "(?<!\\d)\\d(?:[\\s.*_()/\\-]*\\d){9,20}(?!\\d)"
    );

    // Single alphanumeric token containing interleaved letters and digits (e.g. 012345abkcsja6789, 12345abkcsja6789)
    private static final Pattern INTERLEAVED_TOKEN_PATTERN = Pattern.compile(
        "(?i)(?<![0-9a-zA-Z])([0-9a-zA-Z]*[0-9OoLlI|SsBb][0-9a-zA-Z]*)(?![0-9a-zA-Z])"
    );

    // Sliding window & general phone patterns
    private static final Pattern STRICT_OR_GENERAL_10_PHONE = Pattern.compile("(?:\\+?84|0)\\d{9}");
    private static final Pattern NINE_DIGIT_PHONE = Pattern.compile("[35789]\\d{8}");
    private static final Pattern SLIDING_PHONE_PATTERN = Pattern.compile("(?:\\+?84|0)\\d{9}|[35789]\\d{8}|\\d{10,}");

    // CCCD (Căn cước công dân): 12 digits, starts with 0 (valid Vietnam province codes 001-096),
    // supports separators (e.g. 079199012345, 079 199 012 345, 001.201.012.345, 001-201-012345)
    private static final Pattern CCCD_PATTERN = Pattern.compile(
        "(?<!\\d)(?:0\\d{2}[.\\s_-]?[0-3]\\d{2}[.\\s_-]?\\d{6}|0\\d{11}|0\\d{2}[.\\s_-]?\\d{3}[.\\s_-]?\\d{3}[.\\s_-]?\\d{3})(?!\\d)"
    );

    // Identity card (CCCD / CMND) with keyword context
    private static final Pattern PII_ID_CARD_KEYWORD = Pattern.compile(
        "(?i)(?:cccd|căn\\s*cước(?:\\s*công\\s*dân)?|can\\s*cuoc|cmnd|chứng\\s*minh(?:\\s*nhân\\s*dân)?|chung\\s*minh)[^0-9\\r\\n]{0,35}?([0-9][0-9.\\s_-]{7,15})"
    );

    // Sequence of 8 to 15 Vietnamese number words (and digits) for surgical masking of spelled-out numbers
    private static final String VN_NUM_WORD_TOKEN =
        "(?:không|khong|ko|k|dzo|zero|một|mot|mốt|nhất|hai|nhị|ba|tam|bốn|bon|tư|tu|tứ|năm|nam|lăm|lam|ngũ|sáu|sau|lục|bảy|bay|bẩy|thất|tám|bát|chín|chin|cửu|\\d+)";

    private static final Pattern VN_WORD_NUMBER_SEQUENCE = Pattern.compile(
        "(?i)(?:\\b" + VN_NUM_WORD_TOKEN + "[\\s,.*_\\-/]+\\s*){7,15}\\b" + VN_NUM_WORD_TOKEN + "\\b"
    );

    private static final Pattern ANY_WORD_NUMBER_TOKEN = Pattern.compile(
        "(?i)(?<=^|[\\s\\p{Punct}])(?:không|khong|ko|zero|dzo|một|mot|mốt|nhất|hai|nhị|ba|tam|bốn|bon|tư|tu|tứ|năm|nam|lăm|lam|ngũ|sáu|sau|lục|bảy|bay|bẩy|thất|tám|bát|chín|chin|cửu)(?=[\\s\\p{Punct}]|$)"
    );

    // Pure 10-digit Vietnamese phone regex
    private static final Pattern STRICT_VN_PHONE = Pattern.compile(
        "(?:\\+?84|0)[35789]\\d{8}"
    );

    private static int severityRank(String level) {
        if (level == null) return 0;
        return switch (level) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private static boolean isHigherSeverity(String newLevel, String currentLevel) {
        return severityRank(newLevel) > severityRank(currentLevel);
    }

    public void clearBuffer(long conversationId) {
        slidingWindow.remove(conversationId);
    }

    public void clearAllBuffers() {
        slidingWindow.clear();
    }

    /**
     * Main moderation method called before persisting a message.
     * Evaluates all violation types comprehensively across the message
     * to ensure multiple leaks (phones, links, payment keywords, CCCD, profanity, hate speech, threats, politics)
     * are all masked in a single pass.
     */
    public TextModerationResult moderate(long conversationId, long senderId, String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return TextModerationResult.clean(rawText);
        }

        String currentText = rawText;
        boolean isViolation = false;
        String primaryViolationType = null;
        String primaryReason = null;
        String severityLevel = null;

        // 0a. Check & mask Political & Subversive (Severity: CRITICAL)
        Matcher politicalMatcher = POLITICAL_SUBVERSIVE_PATTERN.matcher(currentText);
        if (politicalMatcher.find()) {
            currentText = politicalMatcher.replaceAll("***");
            isViolation = true;
            primaryViolationType = "POLITICAL_SUBVERSIVE";
            primaryReason = "POLITICAL_SUBVERSIVE_DETECTED";
            severityLevel = "CRITICAL";
        }

        // 0b. Check & mask Hate Speech & Regional Discrimination (Severity: HIGH)
        Matcher hateMatcher = HATE_SPEECH_REGIONAL_PATTERN.matcher(currentText);
        if (hateMatcher.find()) {
            currentText = hateMatcher.replaceAll("***");
            isViolation = true;
            if (severityLevel == null || isHigherSeverity("HIGH", severityLevel)) {
                primaryViolationType = "HATE_SPEECH_REGIONAL";
                primaryReason = "HATE_SPEECH_REGIONAL_DETECTED";
                severityLevel = "HIGH";
            }
        }

        // 0c. Check & mask Harassment & Threats (Severity: HIGH)
        Matcher threatMatcher = HARASSMENT_THREAT_PATTERN.matcher(currentText);
        if (threatMatcher.find()) {
            currentText = threatMatcher.replaceAll("***");
            isViolation = true;
            if (severityLevel == null || isHigherSeverity("HIGH", severityLevel)) {
                primaryViolationType = "HARASSMENT_THREAT";
                primaryReason = "HARASSMENT_THREAT_DETECTED";
                severityLevel = "HIGH";
            }
        }

        // 0d. Check & mask Offensive Profanity (Severity: LOW)
        Matcher profanityMatcher = OFFENSIVE_PROFANITY_PATTERN.matcher(currentText);
        if (profanityMatcher.find()) {
            currentText = profanityMatcher.replaceAll("***");
            isViolation = true;
            if (severityLevel == null) {
                primaryViolationType = "OFFENSIVE_PROFANITY";
                primaryReason = "OFFENSIVE_PROFANITY_DETECTED";
                severityLevel = "LOW";
            }
        }

        // 1. Check & mask Off-Platform Payment keywords
        Matcher paymentMatcher = OFF_PLATFORM_PAYMENT_PATTERN.matcher(currentText);
        if (paymentMatcher.find()) {
            currentText = paymentMatcher.replaceAll("***");
            isViolation = true;
            if (severityLevel == null || isHigherSeverity("MEDIUM", severityLevel)) {
                primaryViolationType = "OFF_PLATFORM_PAYMENT";
                primaryReason = "OFF_PLATFORM_PAYMENT_DETECTED";
                severityLevel = "MEDIUM";
            }
        }

        // 2. Check & mask Platform Redirects (Zalo, FB, Tele)
        Matcher zaloMatcher = ZALO_SLANG.matcher(currentText);
        if (zaloMatcher.find()) {
            currentText = zaloMatcher.replaceAll("***");
            isViolation = true;
            if (severityLevel == null || isHigherSeverity("MEDIUM", severityLevel)) {
                primaryViolationType = "PLATFORM_REDIRECT";
                primaryReason = "SLANG_ZALO_REDIRECT";
                severityLevel = "MEDIUM";
            }
        }

        Matcher fbMatcher = FACEBOOK_SLANG.matcher(currentText);
        if (fbMatcher.find()) {
            currentText = fbMatcher.replaceAll("***");
            isViolation = true;
            if (severityLevel == null || isHigherSeverity("MEDIUM", severityLevel)) {
                primaryViolationType = "PLATFORM_REDIRECT";
                primaryReason = "SLANG_FACEBOOK_REDIRECT";
                severityLevel = "MEDIUM";
            }
        }

        Matcher teleMatcher = TELEGRAM_SLANG.matcher(currentText);
        if (teleMatcher.find()) {
            currentText = teleMatcher.replaceAll("***");
            isViolation = true;
            if (severityLevel == null || isHigherSeverity("MEDIUM", severityLevel)) {
                primaryViolationType = "PLATFORM_REDIRECT";
                primaryReason = "SLANG_TELEGRAM_REDIRECT";
                severityLevel = "MEDIUM";
            }
        }

        // 3. Check & mask External URLs and defanged links
        Matcher urlMatcher = EXTERNAL_URL.matcher(currentText);
        if (urlMatcher.find()) {
            currentText = urlMatcher.replaceAll("***");
            isViolation = true;
            if (severityLevel == null || isHigherSeverity("MEDIUM", severityLevel)) {
                primaryViolationType = "PLATFORM_REDIRECT";
                primaryReason = "EXTERNAL_LINK_REDIRECT";
                severityLevel = "MEDIUM";
            }
        }

        // 4. Check & mask Citizen Identity Cards (CCCD / CMND - PII Protection)
        // 4a. With explicit keyword (e.g. cccd: 079199012345 or cmnd số 025678912)
        Matcher piiKeywordMatcher = PII_ID_CARD_KEYWORD.matcher(currentText);
        if (piiKeywordMatcher.find()) {
            currentText = maskPiiKeyword(currentText);
            isViolation = true;
            if (severityLevel == null || isHigherSeverity("MEDIUM", severityLevel)) {
                primaryViolationType = "PII_LEAK";
                primaryReason = "CCCD_CMND_KEYWORD_DETECTED";
                severityLevel = "MEDIUM";
            }
        }

        // 4. Compute anti-false text once for PII & Phone evaluation
        String antiFalseText = stripAntiFalsePositive(currentText);

        // 4b. Standalone 12-digit CCCD
        String antiFalseForCccd = stripAntiFalsePositive(currentText);
        Matcher cccdMatcher = CCCD_PATTERN.matcher(antiFalseForCccd);
        if (cccdMatcher.find()) {
            currentText = CCCD_PATTERN.matcher(currentText).replaceAll("***");
            isViolation = true;
            if (severityLevel == null || isHigherSeverity("MEDIUM", severityLevel)) {
                primaryViolationType = "PII_LEAK";
                primaryReason = "CCCD_NUMBER_DETECTED";
                severityLevel = "MEDIUM";
            }
        }

        // 5. Check & mask Phone Numbers (Separators, Homoglyphs, Emojis, Zero-width)
        // Recompute on currentText so already-masked CCCDs won't trigger phone regex
        String antiFalseForPhone = stripAntiFalsePositive(currentText);
        Matcher phoneMatcher = VIETNAMESE_PHONE_WITH_HOMOGLYPHS.matcher(antiFalseForPhone);
        if (phoneMatcher.find()) {
            currentText = VIETNAMESE_PHONE_WITH_HOMOGLYPHS.matcher(currentText).replaceAll("***");
            isViolation = true;
            if (severityLevel == null || isHigherSeverity("MEDIUM", severityLevel)) {
                primaryViolationType = "PHONE_LEAK";
                primaryReason = "DIRECT_PHONE_DETECTED";
                severityLevel = "MEDIUM";
            }
        }

        // Check if normalized text has direct phone matching (fallback for edge cases)
        Matcher sepMatcher = VIETNAMESE_PHONE_WITH_SEPARATORS.matcher(currentText);
        if (sepMatcher.find()) {
            currentText = sepMatcher.replaceAll("***");
            isViolation = true;
            if (severityLevel == null || isHigherSeverity("MEDIUM", severityLevel)) {
                primaryViolationType = "PHONE_LEAK";
                primaryReason = "DIRECT_PHONE_DETECTED";
                severityLevel = "MEDIUM";
            }
        }

        // Check Vietnamese word numbers ONLY if word number tokens exist in text
        if (ANY_WORD_NUMBER_TOKEN.matcher(antiFalseForPhone).find()) {
            String wordNormalized = convertVietnameseWordNumbers(normalizeObfuscations(antiFalseForPhone));
            String allDigits = extractAllDigits(wordNormalized);

            boolean isWordCccd = allDigits.length() >= 12 && CCCD_PATTERN.matcher(allDigits).find();
            boolean isWordPhone = STRICT_OR_GENERAL_10_PHONE.matcher(allDigits).find() || NINE_DIGIT_PHONE.matcher(allDigits).find();

            if (isWordCccd || isWordPhone) {
                isViolation = true;
                if (severityLevel == null || isHigherSeverity("MEDIUM", severityLevel)) {
                    severityLevel = "MEDIUM";
                    if (isWordCccd) {
                        primaryViolationType = "PII_LEAK";
                        primaryReason = "VIETNAMESE_WORD_CCCD_LEAK";
                    } else {
                        primaryViolationType = "PHONE_LEAK";
                        primaryReason = "VIETNAMESE_WORD_PHONE_LEAK";
                    }
                }
                currentText = maskWordNumberSequence(currentText);
            }
        }

        // 5c. Check & mask Padded digit sequences (e.g. 123450912345678999 "lấy 10 số ở giữa", 9990901234567888)
        String antiFalseCluster = stripAntiFalsePositive(currentText);
        Matcher paddedMatcher = PADDED_DIGITS_PATTERN.matcher(antiFalseCluster);
        while (paddedMatcher.find()) {
            String padded = paddedMatcher.group();
            String digits = extractAllDigits(padded);
            if (digits.length() >= 10) {
                isViolation = true;
                boolean isCccd = digits.length() == 12 || (digits.length() >= 12 && CCCD_PATTERN.matcher(digits).find());
                if (severityLevel == null || isHigherSeverity("MEDIUM", severityLevel)) {
                    severityLevel = "MEDIUM";
                    if (isCccd) {
                        primaryViolationType = "PII_LEAK";
                        primaryReason = "CCCD_NUMBER_DETECTED";
                    } else {
                        primaryViolationType = "PHONE_LEAK";
                        primaryReason = "PADDED_DIGITS_PHONE_LEAK";
                    }
                }
                currentText = currentText.replace(padded, "***");
            }
        }

        // 5d. Check & mask Interleaved alphanumeric tokens (e.g. 012345abkcsja6789, 12345abkcsja6789)
        Matcher interleavedMatcher = INTERLEAVED_TOKEN_PATTERN.matcher(antiFalseCluster);
        while (interleavedMatcher.find()) {
            String token = interleavedMatcher.group(1);
            String tokenDigits = extractAllDigits(normalizeObfuscations(token));
            if (tokenDigits.length() >= 9) {
                isViolation = true;
                if (severityLevel == null || isHigherSeverity("MEDIUM", severityLevel)) {
                    severityLevel = "MEDIUM";
                    primaryViolationType = "PHONE_LEAK";
                    primaryReason = "OBFUSCATED_PHONE_DETECTED";
                }
                currentText = currentText.replace(token, "***");
            }
        }

        // 6. Stateful Sliding Window buffer check (for fragmented numbers across messages)
        if (!isViolation) {
            TextModerationResult slidingResult = checkSlidingWindow(conversationId, senderId, currentText);
            if (slidingResult.isViolation()) {
                return slidingResult;
            }
        }

        if (isViolation) {
            return TextModerationResult.violation(primaryViolationType, currentText, primaryReason, severityLevel != null ? severityLevel : "MEDIUM");
        }

        return TextModerationResult.clean(rawText);
    }

    private String maskPiiKeyword(String text) {
        Matcher m = PII_ID_CARD_KEYWORD.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String fullMatch = m.group(0);
            String numberPart = m.group(1);
            String replacement = fullMatch.replace(numberPart, "***");
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Detects single-message phone leak with obfuscation handling.
     */
    private TextModerationResult checkSingleMessagePhone(String rawText) {
        // Strip anti-false-positive patterns (money, sizes, etc.) first for candidate inspection
        String antiFalseText = stripAntiFalsePositive(rawText);

        // Pre-normalize text for obfuscation detection
        String normalized = normalizeObfuscations(antiFalseText);

        // Check if normalized text has direct phone matching
        Matcher directMatcher = VIETNAMESE_PHONE_WITH_SEPARATORS.matcher(normalized);
        if (directMatcher.find()) {
            String masked = maskPhoneInOriginal(rawText, directMatcher.group());
            return TextModerationResult.violation("PHONE_LEAK", masked, "DIRECT_PHONE_DETECTED");
        }

        // Also check if translating Vietnamese word numbers yields a phone number
        String wordNormalized = convertVietnameseWordNumbers(normalized);
        String allDigits = extractAllDigits(wordNormalized);
        if (STRICT_OR_GENERAL_10_PHONE.matcher(allDigits).find() || NINE_DIGIT_PHONE.matcher(allDigits).find()) {
            String masked = rawText;
            if (rawText.length() <= 80 || containsWordNumberSequence(rawText)) {
                masked = "***";
            } else {
                masked = VIETNAMESE_PHONE_WITH_SEPARATORS.matcher(rawText).replaceAll("***");
                if (masked.equals(rawText)) {
                    masked = "***";
                }
            }
            return TextModerationResult.violation("PHONE_LEAK", masked, "VIETNAMESE_WORD_PHONE_LEAK");
        }

        return TextModerationResult.clean(rawText);
    }

    /**
     * Checks whether fragmented digits across recent messages of the same sender form a phone number.
     */
    private TextModerationResult checkSlidingWindow(long conversationId, long senderId, String rawText) {
        String cleaned = stripAntiFalsePositive(rawText);
        String normalized = normalizeObfuscations(cleaned);
        String currentDigits = extractAllDigits(convertVietnameseWordNumbers(normalized));

        Deque<MessageSnippet> queue = slidingWindow.computeIfAbsent(conversationId, k -> new ArrayDeque<>());

        synchronized (queue) {
            // Evict expired snippets older than TTL (30 minutes)
            Instant cutoff = Instant.now().minus(SLIDING_WINDOW_TTL);
            queue.removeIf(s -> s.timestamp().isBefore(cutoff));

            StringBuilder joined = new StringBuilder();
            for (MessageSnippet s : queue) {
                if (s.senderId() == senderId && s.digits() != null && !s.digits().isEmpty()) {
                    joined.append(s.digits());
                }
            }
            joined.append(currentDigits);

            String combined = joined.toString();
            Matcher m = SLIDING_PHONE_PATTERN.matcher(combined);
            if (m.find()) {
                // Violation detected: Fragmented phone number sequence completed.
                // We intentionally do NOT clear the preceding snippets from the queue, nor add the current
                // masked digits. Because earlier messages (1..N-1) are already rendered on the recipient's screen,
                // keeping them prevents the sender from bypassing by immediately re-sending the blocked final digit
                // using alternative formats (words, emojis, interleaved text, or extra padding).
                String masked = rawText;
                if (rawText.length() <= 40 || containsWordNumberSequence(rawText)) {
                    masked = "***";
                } else {
                    masked = rawText.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])(?:không|khong|ko|zero|dzo|một|mot|mốt|nhất|hai|nhị|ba|tam|bốn|bon|tư|tu|tứ|năm|nam|lăm|lam|ngũ|sáu|sau|lục|bảy|bay|bẩy|thất|tám|bát|chín|chin|cửu|\\d+)(?=[\\s\\p{Punct}]|$)", "***");
                    if (masked.equals(rawText)) {
                        masked = "***";
                    }
                }
                return TextModerationResult.violation("PHONE_LEAK", masked, "SLIDING_WINDOW_PHONE_SPLIT");
            }

            queue.addLast(new MessageSnippet(senderId, currentDigits, Instant.now()));
            while (queue.size() > SLIDING_WINDOW_CAPACITY) {
                queue.pollFirst();
            }
        }

        return TextModerationResult.clean(rawText);
    }

    /**
     * Strips prices, sizes, dimensions, and item counts to avoid false positives.
     */
    public String stripAntiFalsePositive(String text) {
        if (text == null) return "";
        String s = MULTI_DIMENSIONS.matcher(text).replaceAll(" ");
        s = MONEY_UNITS.matcher(s).replaceAll(" ");
        s = ATTACHED_K.matcher(s).replaceAll(" ");
        s = THOUSAND_FORMAT.matcher(s).replaceAll(" ");
        s = DIMENSION_UNITS.matcher(s).replaceAll(" ");
        s = COUNT_UNITS.matcher(s).replaceAll(" ");
        s = TIME_UNITS.matcher(s).replaceAll(" ");
        s = PERCENTAGE_UNITS.matcher(s).replaceAll(" ");
        return s;
    }

    /**
     * Normalizes zero-width characters, emojis, NFKC, and homoglyphs.
     */
    public String normalizeObfuscations(String text) {
        if (text == null) return "";

        // 1. Remove keycap combiners (turning 0️⃣ -> 0)
        String s = KEYCAP_COMBINER.matcher(text).replaceAll("");

        // 2. Remove invisible characters (Zero-width space, ZWNJ, ZWJ, BOM, RTL override)
        s = INVISIBLE_CHARS.matcher(s).replaceAll("");

        // 3. Remove general emojis
        s = EMOJI_AND_SYMBOLS.matcher(s).replaceAll("");

        // 4. Unicode NFKC normalization (turns fullwidth ０-９ into 0-9)
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);

        // 5. Cyrillic homoglyphs
        s = s.replace('\u041E', '0').replace('\u043E', '0'); // Cyrillic О, о

        // 6. Confusable homoglyphs adjacent to digits or separator sequences
        s = normalizeHomoglyphs(s);

        return s;
    }

    /**
     * Canonicalizes homoglyphs (O/o->0, l/I/|->1, S/s->5, B/b->8) when within or adjacent to digits.
     */
    private String normalizeHomoglyphs(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == 'O' || c == 'o' || c == 'l' || c == 'I' || c == '|' || c == 'S' || c == 's' || c == 'B') {
                if (isDigitContext(chars, i)) {
                    if (c == 'O' || c == 'o') chars[i] = '0';
                    else if (c == 'l' || c == 'I' || c == '|') chars[i] = '1';
                    else if (c == 'S' || c == 's') chars[i] = '5';
                    else if (c == 'B') chars[i] = '8';
                }
            }
        }
        return new String(chars);
    }

    private boolean isDigitContext(char[] chars, int idx) {
        boolean hasDigitBefore = false;
        for (int i = idx - 1; i >= 0 && i >= idx - 4; i--) {
            if (Character.isDigit(chars[i])) { hasDigitBefore = true; break; }
            if (!isSeparator(chars[i])) break;
        }
        boolean hasDigitAfter = false;
        for (int i = idx + 1; i < chars.length && i <= idx + 4; i++) {
            if (Character.isDigit(chars[i])) { hasDigitAfter = true; break; }
            if (!isSeparator(chars[i])) break;
        }
        if (chars[idx] == 'O' || chars[idx] == 'o') {
            for (int i = idx + 1; i < chars.length && i <= idx + 3; i++) {
                if (chars[i] == '9' || chars[i] == '3' || chars[i] == '5' || chars[i] == '7' || chars[i] == '8') {
                    return true;
                }
                if (!isSeparator(chars[i])) break;
            }
        }
        return hasDigitBefore || hasDigitAfter;
    }

    private boolean isSeparator(char c) {
        return c == ' ' || c == '.' || c == '-' || c == '*' || c == '_' || c == '/' || c == '(' || c == ')';
    }

    /**
     * Converts Vietnamese word numbers to digits.
     */
    public String convertVietnameseWordNumbers(String text) {
        if (text == null) return "";
        String s = text.toLowerCase(Locale.ROOT);

        // Handle 'k' as 'không' when followed by phone prefixes
        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])k(?=[\\s\\p{Punct}]*(?:[035789]|ba|năm|nam|bảy|bay|tám|tam|chín|chin))", "0 ");

        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])(?:không|khong|ko|zero|dzo)(?=[\\s\\p{Punct}]|$)", "0");
        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])(?:một|mot|mốt|nhất)(?=[\\s\\p{Punct}]|$)", "1");
        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])(?:hai|nhị)(?=[\\s\\p{Punct}]|$)", "2");
        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])(?:ba|tam)(?=[\\s\\p{Punct}]|$)", "3");
        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])(?:bốn|bon|tư|tu|tứ)(?=[\\s\\p{Punct}]|$)", "4");
        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])(?:năm|nam|lăm|lam|ngũ)(?=[\\s\\p{Punct}]|$)", "5");
        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])(?:sáu|sau|lục)(?=[\\s\\p{Punct}]|$)", "6");
        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])(?:bảy|bay|bẩy|thất)(?=[\\s\\p{Punct}]|$)", "7");
        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])(?:tám|bát)(?=[\\s\\p{Punct}]|$)", "8");
        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])(?:chín|chin|cửu)(?=[\\s\\p{Punct}]|$)", "9");
        return s;
    }

    private String maskWordNumberSequence(String text) {
        Matcher m = VN_WORD_NUMBER_SEQUENCE.matcher(text);
        if (m.find()) {
            return m.replaceAll("***");
        }
        if (text.length() <= 80 || containsWordNumberSequence(text)) {
            return "***";
        }
        return text;
    }

    private boolean containsWordNumberSequence(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("không") || lower.contains("khong") || lower.contains("chín") || lower.contains("chin");
    }

    private String extractAllDigits(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String maskPhoneInOriginal(String rawText, String detectedPhone) {
        Matcher m = VIETNAMESE_PHONE_WITH_SEPARATORS.matcher(rawText);
        if (m.find()) {
            return m.replaceAll("***");
        }
        if (rawText.length() <= 20) {
            return "***";
        }
        return rawText.replace(detectedPhone, "***");
    }
}
