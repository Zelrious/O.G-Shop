package com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReturnReasonCodeTest - Return reason prefix parser and formatter unit tests")
class ReturnReasonCodeTest {

    @Test
    @DisplayName("Should successfully parse valid prefixes for all 3 standardized reason codes")
    void testParseValidStandardReasons() {
        var r1 = ReturnReasonCode.parse("[CHANGE_MIND] Người mua đổi ý, chưa mở hộp");
        assertEquals(ReturnReasonCode.CHANGE_MIND, r1.reasonCode());
        assertEquals("Người mua đổi ý, chưa mở hộp", r1.detail());
        assertTrue(r1.hasStandardReason());

        var r2 = ReturnReasonCode.parse("[NOT_AS_DESCRIBED] Hàng giao sai phiên bản màu xanh");
        assertEquals(ReturnReasonCode.NOT_AS_DESCRIBED, r2.reasonCode());
        assertEquals("Hàng giao sai phiên bản màu xanh", r2.detail());
        assertTrue(r2.hasStandardReason());

        var r3 = ReturnReasonCode.parse("[DAMAGED_OR_DEFECTIVE] Màn hình bị nứt góc và liệt cảm ứng");
        assertEquals(ReturnReasonCode.DAMAGED_OR_DEFECTIVE, r3.reasonCode());
        assertEquals("Màn hình bị nứt góc và liệt cảm ứng", r3.detail());
        assertTrue(r3.hasStandardReason());
    }

    @Test
    @DisplayName("Should parse prefix without whitespace after closing square bracket")
    void testParseWithoutSpaceAfterBracket() {
        var result = ReturnReasonCode.parse("[CHANGE_MIND]Không ưng ý");
        assertEquals(ReturnReasonCode.CHANGE_MIND, result.reasonCode());
        assertEquals("Không ưng ý", result.detail());
    }

    @Test
    @DisplayName("Should parse prefix when no detail description is provided")
    void testParseWithoutDetail() {
        var result = ReturnReasonCode.parse("[DAMAGED_OR_DEFECTIVE]");
        assertEquals(ReturnReasonCode.DAMAGED_OR_DEFECTIVE, result.reasonCode());
        assertEquals("", result.detail());
    }

    @Test
    @DisplayName("Should parse prefix with extra leading and trailing whitespace")
    void testParseWithExtraWhitespace() {
        var result = ReturnReasonCode.parse("   [NOT_AS_DESCRIBED]   Thiếu phụ kiện sạc đi kèm   ");
        assertEquals(ReturnReasonCode.NOT_AS_DESCRIBED, result.reasonCode());
        assertEquals("Thiếu phụ kiện sạc đi kèm", result.detail());
    }

    @Test
    @DisplayName("Should parse plain text without prefix as legacy backward compatible description")
    void testParsePlainLegacyText() {
        var result = ReturnReasonCode.parse("Tôi muốn trả hàng vì lý do cá nhân");
        assertNull(result.reasonCode());
        assertEquals("Tôi muốn trả hàng vì lý do cá nhân", result.detail());
        assertFalse(result.hasStandardReason());
    }

    @Test
    @DisplayName("Should parse unknown tag as plain text without standard reason code")
    void testParseUnknownTag() {
        var result = ReturnReasonCode.parse("[DELIVERY_ISSUE] Giao trễ 3 ngày");
        assertNull(result.reasonCode());
        assertEquals("[DELIVERY_ISSUE] Giao trễ 3 ngày", result.detail());
        assertFalse(result.hasStandardReason());
    }

    @Test
    @DisplayName("Should handle null or blank input gracefully")
    void testParseNullOrBlank() {
        var rNull = ReturnReasonCode.parse(null);
        assertNull(rNull.reasonCode());
        assertEquals("", rNull.detail());

        var rBlank = ReturnReasonCode.parse("   ");
        assertNull(rBlank.reasonCode());
        assertEquals("", rBlank.detail());
    }

    @Test
    @DisplayName("Should format prefix correctly from Enum and detail text")
    void testFormat() {
        String formatted = ReturnReasonCode.format(ReturnReasonCode.CHANGE_MIND, "Đổi ý cá nhân");
        assertEquals("[CHANGE_MIND] Đổi ý cá nhân", formatted);

        String formattedEmpty = ReturnReasonCode.format(ReturnReasonCode.CHANGE_MIND, "");
        assertEquals("[CHANGE_MIND]", formattedEmpty);

        String formattedNull = ReturnReasonCode.format(ReturnReasonCode.CHANGE_MIND, null);
        assertEquals("[CHANGE_MIND]", formattedNull);
    }

    @Test
    @DisplayName("Should integrate with CaseEntity helper methods correctly")
    void testCaseEntityIntegration() {
        CaseEntity caseEntity = new CaseEntity(
                CaseEntity.TYPE_RETURN,
                100L,
                200L,
                "[DAMAGED_OR_DEFECTIVE] Hàng bị móp méo hộp và nứt vỡ",
                "CMD_TEST_1"
        );

        assertEquals(ReturnReasonCode.DAMAGED_OR_DEFECTIVE, caseEntity.getReasonCode());
        assertEquals("Hàng bị móp méo hộp và nứt vỡ", caseEntity.getCleanDescription());

        caseEntity.setReasonAndDetail(ReturnReasonCode.NOT_AS_DESCRIBED, "Sai kích thước sản phẩm");
        assertEquals(ReturnReasonCode.NOT_AS_DESCRIBED, caseEntity.getReasonCode());
        assertEquals("Sai kích thước sản phẩm", caseEntity.getCleanDescription());
        assertEquals("[NOT_AS_DESCRIBED] Sai kích thước sản phẩm", caseEntity.getDescription());
    }
}
