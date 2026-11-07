package com.oldbutgold.shop.modules.communication.application;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
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
        String reason
    ) {
        public static TextModerationResult clean(String text) {
            return new TextModerationResult(false, null, text, null);
        }

        public static TextModerationResult violation(String violationType, String maskedText, String reason) {
            return new TextModerationResult(true, violationType, maskedText, reason);
        }
    }

    public record MessageSnippet(long senderId, String digits, Instant timestamp) {}

    // In-memory sliding window buffer: conversationId -> Deque of up to 10 snippets
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

    // Invisible / zero-width and control characters
    private static final Pattern INVISIBLE_CHARS = Pattern.compile(
        "[\\u200B-\\u200D\\uFEFF\\u200E\\u200F\\u202A-\\u202E\\u2060-\\u2069]"
    );

    // Emoji and symbols (including keycap sequences 0️⃣-9️⃣)
    private static final Pattern KEYCAP_COMBINER = Pattern.compile("[\\uFE0F\\u20E3]");
    private static final Pattern EMOJI_AND_SYMBOLS = Pattern.compile(
        "[\\uD83C-\\uD83E][\\uDC00-\\uDFFF]|[\\u2600-\\u27BF]|[\\u2300-\\u23FF]|[\\u2B50-\\u2B55]|[\\u203C\\u2049\\u2122\\u2139\\u2194-\\u2199\\u21A9-\\u21AA\\u25AA-\\u25AB\\u25B6\\u25C0\\u25FB-\\u25FE]"
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

    // Direct phone regex matching 10 digits starting with 03, 05, 07, 08, 09 or +84
    private static final Pattern VIETNAMESE_PHONE_WITH_SEPARATORS = Pattern.compile(
        "(?i)(?:\\+?84|0)[\\s.*_()/\\-]*[35789](?:[\\s.*_()/\\-]*\\d){8}"
    );

    // Pure 10-digit Vietnamese phone regex
    private static final Pattern STRICT_VN_PHONE = Pattern.compile(
        "(?:\\+?84|0)[35789]\\d{8}"
    );

    public void clearBuffer(long conversationId) {
        slidingWindow.remove(conversationId);
    }

    public void clearAllBuffers() {
        slidingWindow.clear();
    }

    /**
     * Main moderation method called before persisting a message.
     */
    public TextModerationResult moderate(long conversationId, long senderId, String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return TextModerationResult.clean(rawText);
        }

        // 1. Check Off-Platform Payment keywords
        Matcher paymentMatcher = OFF_PLATFORM_PAYMENT_PATTERN.matcher(rawText);
        if (paymentMatcher.find()) {
            String masked = paymentMatcher.replaceAll("***");
            return TextModerationResult.violation("OFF_PLATFORM_PAYMENT", masked, "OFF_PLATFORM_PAYMENT_DETECTED");
        }

        // 2. Check Platform Redirects (Zalo, FB, Tele, URLs)
        Matcher zaloMatcher = ZALO_SLANG.matcher(rawText);
        if (zaloMatcher.find()) {
            String masked = zaloMatcher.replaceAll("***");
            masked = VIETNAMESE_PHONE_WITH_SEPARATORS.matcher(masked).replaceAll("***");
            return TextModerationResult.violation("PLATFORM_REDIRECT", masked, "SLANG_ZALO_REDIRECT");
        }

        Matcher fbMatcher = FACEBOOK_SLANG.matcher(rawText);
        if (fbMatcher.find()) {
            String masked = fbMatcher.replaceAll("***");
            return TextModerationResult.violation("PLATFORM_REDIRECT", masked, "SLANG_FACEBOOK_REDIRECT");
        }

        Matcher teleMatcher = TELEGRAM_SLANG.matcher(rawText);
        if (teleMatcher.find()) {
            String masked = teleMatcher.replaceAll("***");
            return TextModerationResult.violation("PLATFORM_REDIRECT", masked, "SLANG_TELEGRAM_REDIRECT");
        }

        Matcher urlMatcher = EXTERNAL_URL.matcher(rawText);
        if (urlMatcher.find()) {
            String masked = urlMatcher.replaceAll("***");
            return TextModerationResult.violation("PLATFORM_REDIRECT", masked, "EXTERNAL_LINK_REDIRECT");
        }

        // 3. Single-message Phone Leak detection (handles obfuscation, homoglyphs, word numbers)
        TextModerationResult singlePhoneResult = checkSingleMessagePhone(rawText);
        if (singlePhoneResult.isViolation()) {
            return singlePhoneResult;
        }

        // 4. Stateful Sliding Window buffer check (for fragmented numbers across messages)
        return checkSlidingWindow(conversationId, senderId, rawText);
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
        Matcher wordMatcher = STRICT_VN_PHONE.matcher(extractAllDigits(wordNormalized));
        if (wordMatcher.find()) {
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
            StringBuilder joined = new StringBuilder();
            for (MessageSnippet s : queue) {
                if (s.senderId() == senderId && s.digits() != null && !s.digits().isEmpty()) {
                    joined.append(s.digits());
                }
            }
            joined.append(currentDigits);

            String combined = joined.toString();
            Matcher m = STRICT_VN_PHONE.matcher(combined);
            if (m.find()) {
                queue.removeIf(s -> s.senderId() == senderId);
                String masked = "***";
                return TextModerationResult.violation("PHONE_LEAK", masked, "SLIDING_WINDOW_PHONE_SPLIT");
            }

            queue.addLast(new MessageSnippet(senderId, currentDigits, Instant.now()));
            while (queue.size() > 10) {
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
        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])k(?=[\\s\\p{Punct}]*(?:[35789]|ba|năm|nam|bảy|bay|tám|tam|chín|chin))", "0 ");

        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])(?:không|khong)(?=[\\s\\p{Punct}]|$)", "0");
        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])(?:một|mot|mốt)(?=[\\s\\p{Punct}]|$)", "1");
        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])(?:hai)(?=[\\s\\p{Punct}]|$)", "2");
        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])(?:ba)(?=[\\s\\p{Punct}]|$)", "3");
        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])(?:bốn|bon|tư|tu)(?=[\\s\\p{Punct}]|$)", "4");
        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])(?:năm|nam|lăm|lam)(?=[\\s\\p{Punct}]|$)", "5");
        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])(?:sáu|sau)(?=[\\s\\p{Punct}]|$)", "6");
        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])(?:bảy|bay|bẩy)(?=[\\s\\p{Punct}]|$)", "7");
        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])(?:tám|tam)(?=[\\s\\p{Punct}]|$)", "8");
        s = s.replaceAll("(?i)(?<=^|[\\s\\p{Punct}])(?:chín|chin)(?=[\\s\\p{Punct}]|$)", "9");
        return s;
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
