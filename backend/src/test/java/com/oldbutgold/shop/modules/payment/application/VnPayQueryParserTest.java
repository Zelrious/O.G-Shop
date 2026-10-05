package com.oldbutgold.shop.modules.payment.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VnPayQueryParserTest {

    @Test
    @DisplayName("Parse valid ASCII query successfully")
    void parse_validAsciiQuery_success() {
        String query = "vnp_Amount=10000000&vnp_Command=pay&vnp_CurrCode=VND&vnp_OrderInfo=Thanh%20toan%20don%20hang";
        VnPayQueryParser.ParseResult result = VnPayQueryParser.parse(query);

        assertThat(result.processingError()).isNull();
        assertThat(result.cleanParams()).isNotNull();
        assertThat(result.cleanParams().get("vnp_Amount")).isEqualTo("10000000");
        assertThat(result.cleanParams().get("vnp_Command")).isEqualTo("pay");
        assertThat(result.cleanParams().get("vnp_CurrCode")).isEqualTo("VND");
        assertThat(result.cleanParams().get("vnp_OrderInfo")).isEqualTo("Thanh toan don hang");
        assertThat(result.sanitized()).isFalse();
        assertThat(result.truncated()).isFalse();
        assertThat(result.sha256Hex()).isNotNull();
    }

    @Test
    @DisplayName("Reject non-ASCII raw query characters")
    void parse_nonAsciiRawCharacters_rejected() {
        // Raw Vietnamese unicode characters in raw query string instead of percent-encoded
        String query = "vnp_Amount=10000000&vnp_OrderInfo=Thanh toán";
        VnPayQueryParser.ParseResult result = VnPayQueryParser.parse(query);

        assertThat(result.processingError()).isEqualTo("NON_ASCII_RAW_QUERY");
        assertThat(result.cleanParams()).isEmpty();
    }

    @Test
    @DisplayName("Sanitize lone surrogates and set sanitized flag")
    void parse_loneSurrogate_sanitized() {
        // High surrogate \uD83D without matching low surrogate
        String query = "vnp_Amount=10000000&vnp_OrderInfo=bad\uD83Dquery";
        VnPayQueryParser.ParseResult result = VnPayQueryParser.parse(query);

        assertThat(result.sanitized()).isTrue();
        assertThat(result.processingError()).isEqualTo("NON_ASCII_RAW_QUERY");
        assertThat(result.rawQuery()).contains("?");
    }

    @Test
    @DisplayName("Truncate query exceeding 4096 code points")
    void parse_queryExceeding4096CodePoints_truncated() {
        StringBuilder sb = new StringBuilder("vnp_OrderInfo=");
        while (sb.length() < 4100) {
            sb.append("A");
        }
        String longQuery = sb.toString();

        VnPayQueryParser.ParseResult result = VnPayQueryParser.parse(longQuery);

        assertThat(result.truncated()).isTrue();
        assertThat(result.processingError()).isEqualTo("RAW_QUERY_TOO_LARGE");
        assertThat(result.rawQuery().length()).isEqualTo(4096);
        assertThat(result.originalLength()).isEqualTo(longQuery.length());
        assertThat(result.sha256Hex()).isEqualTo(VnPayQueryParser.computeSha256Hex(longQuery));
    }

    @Test
    @DisplayName("Detect duplicate vnp parameter")
    void parse_duplicateVnpParam_rejected() {
        String query = "vnp_Amount=10000000&vnp_Amount=20000000";
        VnPayQueryParser.ParseResult result = VnPayQueryParser.parse(query);

        assertThat(result.processingError()).isEqualTo("DUPLICATE_VNP_PARAM:vnp_amount");
        assertThat(result.cleanParams()).isEmpty();
    }

    @Test
    @DisplayName("Detect non-canonical casing of vnp parameter")
    void parse_nonCanonicalVnpCase_rejected() {
        String query = "vnp_amount=10000000&vnp_Command=pay";
        VnPayQueryParser.ParseResult result = VnPayQueryParser.parse(query);

        assertThat(result.processingError()).isEqualTo("NONCANONICAL_VNP_PARAM_CASE:vnp_amount");
        assertThat(result.cleanParams()).isEmpty();
    }

    @Test
    @DisplayName("Reject invalid percent encoding")
    void parse_invalidPercentEncoding_rejected() {
        String query = "vnp_Amount=10000000&vnp_OrderInfo=Test%zz";
        VnPayQueryParser.ParseResult result = VnPayQueryParser.parse(query);

        assertThat(result.processingError()).isEqualTo("INVALID_PERCENT_ENCODING");
        assertThat(result.cleanParams()).isEmpty();
    }

    @Test
    @DisplayName("Reject empty parameter name")
    void parse_emptyParamName_rejected() {
        String query = "=10000000&vnp_Command=pay";
        VnPayQueryParser.ParseResult result = VnPayQueryParser.parse(query);

        assertThat(result.processingError()).isEqualTo("EMPTY_PARAM_NAME");
        assertThat(result.cleanParams()).isEmpty();
    }

    @Test
    @DisplayName("Preserve base64 equals signs in parameter value")
    void parse_base64ValueWithEquals_preserved() {
        String query = "vnp_SecureHash=abc123456789==&vnp_Amount=10000000";
        VnPayQueryParser.ParseResult result = VnPayQueryParser.parse(query);

        assertThat(result.processingError()).isNull();
        assertThat(result.cleanParams().get("vnp_SecureHash")).isEqualTo("abc123456789==");
    }

    @Test
    @DisplayName("Handle parameter without value")
    void parse_paramWithoutEquals_emptyValue() {
        String query = "vnp_BankCode&vnp_Amount=10000000";
        VnPayQueryParser.ParseResult result = VnPayQueryParser.parse(query);

        assertThat(result.processingError()).isNull();
        assertThat(result.cleanParams().get("vnp_BankCode")).isEqualTo("");
        assertThat(result.cleanParams().get("vnp_Amount")).isEqualTo("10000000");
    }

    @Test
    @DisplayName("Ignore adjacent ampersands")
    void parse_adjacentAmpersands_ignored() {
        String query = "vnp_Amount=10000000&&&vnp_Command=pay";
        VnPayQueryParser.ParseResult result = VnPayQueryParser.parse(query);

        assertThat(result.processingError()).isNull();
        assertThat(result.cleanParams().get("vnp_Amount")).isEqualTo("10000000");
        assertThat(result.cleanParams().get("vnp_Command")).isEqualTo("pay");
    }

    @Test
    @DisplayName("Decode percent-encoded parameter name")
    void parse_percentEncodedParamName_decoded() {
        String query = "vnp%5FAmount=10000000&vnp_Command=pay";
        VnPayQueryParser.ParseResult result = VnPayQueryParser.parse(query);

        assertThat(result.processingError()).isNull();
        assertThat(result.cleanParams().get("vnp_Amount")).isEqualTo("10000000");
    }

    @Test
    @DisplayName("Detect percent-encoded NUL byte in forensic field")
    void parse_percentEncodedNulByte_forensicFieldNull() {
        String query = "vnp_Amount=10000000%00&vnp_Command=pay";
        VnPayQueryParser.ParseResult result = VnPayQueryParser.parse(query);

        assertThat(result.processingError()).isNull();
        assertThat(result.vnpAmountText()).isNull();
        assertThat(result.cleanParams().get("vnp_Amount")).contains("\0");
    }
}
