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

    public void clearBuffer(long conversationId) {
        slidingWindow.remove(conversationId);
    }

    public void clearAllBuffers() {
        slidingWindow.clear();
    }

    /**
     * Main moderation method called before persisting a message.
     * Evaluates all violation types comprehensively across the message
     * to ensure multiple leaks (phones, links, payment keywords, CCCD)
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

        // 1. Check & mask Off-Platform Payment keywords
        Matcher paymentMatcher = OFF_PLATFORM_PAYMENT_PATTERN.matcher(currentText);
        if (paymentMatcher.find()) {
            currentText = paymentMatcher.replaceAll("***");
            isViolation = true;
            primaryViolationType = "OFF_PLATFORM_PAYMENT";
            primaryReason = "OFF_PLATFORM_PAYMENT_DETECTED";
        }

        // 2. Check & mask Platform Redirects (Zalo, FB, Tele)
        Matcher zaloMatcher = ZALO_SLANG.matcher(currentText);
        if (zaloMatcher.find()) {
            currentText = zaloMatcher.replaceAll("***");
            isViolation = true;
            if (primaryViolationType == null) {
                primaryViolationType = "PLATFORM_REDIRECT";
                primaryReason = "SLANG_ZALO_REDIRECT";
            }
        }

        Matcher fbMatcher = FACEBOOK_SLANG.matcher(currentText);
        if (fbMatcher.find()) {
            currentText = fbMatcher.replaceAll("***");
            isViolation = true;
            if (primaryViolationType == null) {
                primaryViolationType = "PLATFORM_REDIRECT";
                primaryReason = "SLANG_FACEBOOK_REDIRECT";
            }
        }

        Matcher teleMatcher = TELEGRAM_SLANG.matcher(currentText);
        if (teleMatcher.find()) {
            currentText = teleMatcher.replaceAll("***");
            isViolation = true;
            if (primaryViolationType == null) {
                primaryViolationType = "PLATFORM_REDIRECT";
                primaryReason = "SLANG_TELEGRAM_REDIRECT";
            }
        }

        // 3. Check & mask External URLs and defanged links
        Matcher urlMatcher = EXTERNAL_URL.matcher(currentText);
        if (urlMatcher.find()) {
            currentText = urlMatcher.replaceAll("***");
            isViolation = true;
            if (primaryViolationType == null) {
                primaryViolationType = "PLATFORM_REDIRECT";
                primaryReason = "EXTERNAL_LINK_REDIRECT";
            }
        }

        // 4. Check & mask Citizen Identity Cards (CCCD / CMND - PII Protection)
        // 4a. With explicit keyword (e.g. cccd: 079199012345 or cmnd số 025678912)
        Matcher piiKeywordMatcher = PII_ID_CARD_KEYWORD.matcher(currentText);
        if (piiKeywordMatcher.find()) {
            currentText = maskPiiKeyword(currentText);
            isViolation = true;
            if (primaryViolationType == null || "PLATFORM_REDIRECT".equals(primaryViolationType)) {
                primaryViolationType = "PII_LEAK";
                primaryReason = "CCCD_CMND_KEYWORD_DETECTED";
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
            if (primaryViolationType == null || "PLATFORM_REDIRECT".equals(primaryViolationType)) {
                primaryViolationType = "PII_LEAK";
                primaryReason = "CCCD_NUMBER_DETECTED";
            }
        }

        // 5. Check & mask Phone Numbers (Separators, Homoglyphs, Emojis, Zero-width)
        // Recompute on currentText so already-masked CCCDs won't trigger phone regex
        String antiFalseForPhone = stripAntiFalsePositive(currentText);
        Matcher phoneMatcher = VIETNAMESE_PHONE_WITH_HOMOGLYPHS.matcher(antiFalseForPhone);
        if (phoneMatcher.find()) {
            currentText = VIETNAMESE_PHONE_WITH_HOMOGLYPHS.matcher(currentText).replaceAll("***");
            isViolation = true;
            if (primaryViolationType == null || "PLATFORM_REDIRECT".equals(primaryViolationType)) {
                primaryViolationType = "PHONE_LEAK";
                primaryReason = "DIRECT_PHONE_DETECTED";
            }
        }

        // Check if normalized text has direct phone matching (fallback for edge cases)
        Matcher sepMatcher = VIETNAMESE_PHONE_WITH_SEPARATORS.matcher(currentText);
        if (sepMatcher.find()) {
            currentText = sepMatcher.replaceAll("***");
            isViolation = true;
            if (primaryViolationType == null || "PLATFORM_REDIRECT".equals(primaryViolationType)) {
                primaryViolationType = "PHONE_LEAK";
                primaryReason = "DIRECT_PHONE_DETECTED";
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
                if (isWordCccd) {
                    if (primaryViolationType == null || "PLATFORM_REDIRECT".equals(primaryViolationType)) {
                        primaryViolationType = "PII_LEAK";
                        primaryReason = "VIETNAMESE_WORD_CCCD_LEAK";
                    }
                } else {
                    if (primaryViolationType == null || "PLATFORM_REDIRECT".equals(primaryViolationType)) {
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
                if (isCccd) {
                    if (primaryViolationType == null || "PLATFORM_REDIRECT".equals(primaryViolationType)) {
                        primaryViolationType = "PII_LEAK";
                        primaryReason = "CCCD_NUMBER_DETECTED";
                    }
                } else {
                    if (primaryViolationType == null || "PLATFORM_REDIRECT".equals(primaryViolationType)) {
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
                if (primaryViolationType == null || "PLATFORM_REDIRECT".equals(primaryViolationType)) {
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
            return TextModerationResult.violation(primaryViolationType, currentText, primaryReason);
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
