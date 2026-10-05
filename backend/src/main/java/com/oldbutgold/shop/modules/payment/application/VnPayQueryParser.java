package com.oldbutgold.shop.modules.payment.application;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class VnPayQueryParser {

    public static final int DEFAULT_MAX_CHARS = 4096;

    public static final Set<String> KNOWN_VNP_PARAM_NAMES = Set.of(
            "vnp_TmnCode",
            "vnp_Amount",
            "vnp_BankCode",
            "vnp_BankTranNo",
            "vnp_CardType",
            "vnp_PayDate",
            "vnp_OrderInfo",
            "vnp_TransactionNo",
            "vnp_ResponseCode",
            "vnp_TransactionStatus",
            "vnp_TxnRef",
            "vnp_SecureHashType",
            "vnp_SecureHash",
            "vnp_CurrCode",
            "vnp_CreateDate",
            "vnp_ExpireDate",
            "vnp_Locale",
            "vnp_IpAddr",
            "vnp_Inv_Phone",
            "vnp_Inv_Email",
            "vnp_Inv_Customer",
            "vnp_Inv_Address",
            "vnp_Inv_Company",
            "vnp_Inv_Taxcode",
            "vnp_Inv_Type"
    );

    public record ParseResult(
            String rawQuery,
            int originalLength,
            boolean truncated,
            boolean sanitized,
            String sha256Hex,
            String processingError,
            Map<String, String> cleanParams,
            String vnpTxnRef,
            String vnpTransactionNo,
            String vnpAmountText
    ) {
        public boolean isValid() {
            return processingError == null;
        }
    }

    public static ParseResult parse(String rawQueryString) {
        return parse(rawQueryString, DEFAULT_MAX_CHARS);
    }

    public static ParseResult parse(String rawQueryString, int maxChars) {
        if (rawQueryString == null) {
            rawQueryString = "";
        }

        // Step 1: Sanitize lone surrogates
        StringBuilder sanitizedSb = new StringBuilder(rawQueryString.length());
        boolean sanitized = false;
        for (int i = 0; i < rawQueryString.length(); i++) {
            char ch = rawQueryString.charAt(i);
            if (Character.isSurrogate(ch)) {
                if (Character.isHighSurrogate(ch) && i + 1 < rawQueryString.length() && Character.isLowSurrogate(rawQueryString.charAt(i + 1))) {
                    sanitizedSb.append(ch);
                    sanitizedSb.append(rawQueryString.charAt(i + 1));
                    i++;
                } else {
                    sanitizedSb.append('?');
                    sanitized = true;
                }
            } else {
                sanitizedSb.append(ch);
            }
        }
        String cleanString = sanitizedSb.toString();

        // Step 2: Calculate original code points and truncate if needed
        int originalLength = cleanString.codePointCount(0, cleanString.length());
        boolean truncated = false;
        String finalQuery = cleanString;
        if (originalLength > maxChars) {
            int cutIndex = cleanString.offsetByCodePoints(0, maxChars);
            finalQuery = cleanString.substring(0, cutIndex);
            truncated = true;
        }

        // Step 3: Compute SHA-256 on full clean string
        String sha256Hex = computeSha256Hex(cleanString);

        // Step 4: Validate ASCII printable (U+0020 - U+007E)
        if (sanitized || !isAllAsciiPrintable(cleanString)) {
            return new ParseResult(
                    finalQuery, originalLength, truncated, sanitized, sha256Hex,
                    "NON_ASCII_RAW_QUERY", Collections.emptyMap(), null, null, null
            );
        }

        // Step 5: Check if query was truncated
        if (truncated) {
            return new ParseResult(
                    finalQuery, originalLength, truncated, sanitized, sha256Hex,
                    "RAW_QUERY_TOO_LARGE", Collections.emptyMap(), null, null, null
            );
        }

        // Step 6: Parse key-value pairs
        if (cleanString.isEmpty()) {
            return new ParseResult(
                    finalQuery, originalLength, truncated, sanitized, sha256Hex,
                    null, Collections.emptyMap(), null, null, null
            );
        }

        String[] pairs = cleanString.split("&", -1);
        Map<String, String> decodedParams = new LinkedHashMap<>();
        Map<String, String> vnpNormalizedSeen = new HashMap<>(); // lowercase -> original decoded name

        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue; // Skip empty segment between &&
            }

            int eqIdx = pair.indexOf('=');
            String rawKey = eqIdx >= 0 ? pair.substring(0, eqIdx) : pair;
            String rawVal = eqIdx >= 0 ? pair.substring(eqIdx + 1) : "";

            if (rawKey.isEmpty()) {
                return new ParseResult(
                        finalQuery, originalLength, truncated, sanitized, sha256Hex,
                        "EMPTY_PARAM_NAME", Collections.emptyMap(), null, null, null
                );
            }

            String decodedKey;
            String decodedVal;
            try {
                decodedKey = percentDecodeUtf8(rawKey);
                decodedVal = percentDecodeUtf8(rawVal);
            } catch (IllegalArgumentException e) {
                return new ParseResult(
                        finalQuery, originalLength, truncated, sanitized, sha256Hex,
                        "INVALID_PERCENT_ENCODING", Collections.emptyMap(), null, null, null
                );
            }

            // Check duplicate vnp_* param (case-insensitive)
            String lowerKey = decodedKey.toLowerCase(Locale.ROOT);
            if (lowerKey.startsWith("vnp_")) {
                if (vnpNormalizedSeen.containsKey(lowerKey)) {
                    String sanitizedParamName = sanitizeParamName(lowerKey);
                    return new ParseResult(
                            finalQuery, originalLength, truncated, sanitized, sha256Hex,
                            "DUPLICATE_VNP_PARAM:" + sanitizedParamName, Collections.emptyMap(), null, null, null
                    );
                }
                vnpNormalizedSeen.put(lowerKey, decodedKey);
            }

            decodedParams.put(decodedKey, decodedVal);
        }

        // Step 7: Check NONCANONICAL_VNP_PARAM_CASE
        for (String decodedKey : decodedParams.keySet()) {
            String lowerKey = decodedKey.toLowerCase(Locale.ROOT);
            if (lowerKey.startsWith("vnp_")) {
                // Rule (a): Prefix must be exactly "vnp_" lowercase
                if (!decodedKey.startsWith("vnp_")) {
                    return new ParseResult(
                            finalQuery, originalLength, truncated, sanitized, sha256Hex,
                            "NONCANONICAL_VNP_PARAM_CASE:" + sanitizeParamName(lowerKey), Collections.emptyMap(), null, null, null
                    );
                }

                // Rule (b): If key matches any known param case-insensitively, it must match exact case
                for (String known : KNOWN_VNP_PARAM_NAMES) {
                    if (known.equalsIgnoreCase(decodedKey) && !known.equals(decodedKey)) {
                        return new ParseResult(
                                finalQuery, originalLength, truncated, sanitized, sha256Hex,
                                "NONCANONICAL_VNP_PARAM_CASE:" + sanitizeParamName(lowerKey), Collections.emptyMap(), null, null, null
                        );
                    }
                }
            }
        }

        // Step 8: Extract forensic columns (only if no NUL character and within length limit)
        String vnpTxnRef = extractForensicField(decodedParams.get("vnp_TxnRef"), 100);
        String vnpTransactionNo = extractForensicField(decodedParams.get("vnp_TransactionNo"), 100);
        String vnpAmountText = extractForensicField(decodedParams.get("vnp_Amount"), Integer.MAX_VALUE);

        return new ParseResult(
                finalQuery, originalLength, truncated, sanitized, sha256Hex,
                null, Collections.unmodifiableMap(decodedParams), vnpTxnRef, vnpTransactionNo, vnpAmountText
        );
    }

    private static String extractForensicField(String val, int maxLen) {
        if (val == null || val.indexOf('\0') >= 0 || val.length() > maxLen) {
            return null;
        }
        return val;
    }

    private static boolean isAllAsciiPrintable(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                return false;
            }
        }
        return true;
    }

    private static String sanitizeParamName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lower.length() && sb.length() < 64; i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                sb.append(c);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    public static String percentDecodeUtf8(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '+') {
                baos.write(' ');
            } else if (c == '%') {
                if (i + 2 >= s.length()) {
                    throw new IllegalArgumentException("Incomplete percent-encoding at index " + i);
                }
                int d1 = Character.digit(s.charAt(i + 1), 16);
                int d2 = Character.digit(s.charAt(i + 2), 16);
                if (d1 < 0 || d2 < 0) {
                    throw new IllegalArgumentException("Invalid hex in percent-encoding at index " + i);
                }
                baos.write((d1 << 4) | d2);
                i += 2;
            } else {
                baos.write((byte) c);
            }
        }

        byte[] bytes = baos.toByteArray();
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            CharBuffer cb = decoder.decode(ByteBuffer.wrap(bytes));
            return cb.toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Invalid UTF-8 byte sequence in decoded percent-encoding", e);
        }
    }

    static String computeSha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
