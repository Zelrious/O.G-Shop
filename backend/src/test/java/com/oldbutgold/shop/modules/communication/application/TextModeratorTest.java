package com.oldbutgold.shop.modules.communication.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextModeratorTest {

    private TextModerator moderator;

    @BeforeEach
    void setUp() {
        moderator = new TextModerator();
        moderator.clearAllBuffers();
    }

    @Test
    @DisplayName("TC01-TC05: Detect phone numbers with dot, dash, space, asterisk, underscore separators")
    void tc01_to_tc05_phoneSeparators() {
        // Dot separator
        var r1 = moderator.moderate(1L, 100L, "SĐT của mình là 090.123.4567 nhé");
        assertThat(r1.isViolation()).isTrue();
        assertThat(r1.violationType()).isEqualTo("PHONE_LEAK");
        assertThat(r1.maskedText()).contains("***");
        assertThat(r1.maskedText()).doesNotContain("090.123.4567");

        // Asterisk separator
        var r2 = moderator.moderate(1L, 100L, "Liên hệ 090*123*4567 nha");
        assertThat(r2.isViolation()).isTrue();
        assertThat(r2.violationType()).isEqualTo("PHONE_LEAK");
        assertThat(r2.maskedText()).contains("***");

        // Dash separator
        var r3 = moderator.moderate(1L, 100L, "Alo 098-765-4321");
        assertThat(r3.isViolation()).isTrue();
        assertThat(r3.violationType()).isEqualTo("PHONE_LEAK");
        assertThat(r3.maskedText()).contains("***");

        // Space separator
        var r4 = moderator.moderate(1L, 100L, "0 9 1 2 3 4 5 6 7 8");
        assertThat(r4.isViolation()).isTrue();
        assertThat(r4.violationType()).isEqualTo("PHONE_LEAK");

        // Underscore separator
        var r5 = moderator.moderate(1L, 100L, "093_456_7890");
        assertThat(r5.isViolation()).isTrue();
        assertThat(r5.violationType()).isEqualTo("PHONE_LEAK");
    }

    @Test
    @DisplayName("TC06-TC07: Detect phone with homoglyphs, emojis, and zero-width spaces")
    void tc06_tc07_obfuscationAndHomoglyphs() {
        // Homoglyphs O/o instead of 0, l instead of 1
        var r1 = moderator.moderate(1L, 100L, "O9O.123.4567");
        assertThat(r1.isViolation()).isTrue();
        assertThat(r1.violationType()).isEqualTo("PHONE_LEAK");

        var r2 = moderator.moderate(1L, 100L, "090l234567");
        assertThat(r2.isViolation()).isTrue();
        assertThat(r2.violationType()).isEqualTo("PHONE_LEAK");

        // Zero-width space insertion
        var r3 = moderator.moderate(1L, 100L, "0\u200B9\u200B0\u200B1\u200B2\u200B3\u200B4\u200B5\u200B6\u200B7");
        assertThat(r3.isViolation()).isTrue();
        assertThat(r3.violationType()).isEqualTo("PHONE_LEAK");

        // Keycap emojis (0️⃣9️⃣0️⃣1️⃣2️⃣3️⃣4️⃣5️⃣6️⃣7️⃣)
        var r4 = moderator.moderate(1L, 100L, "0\uFE0F\u20E39\uFE0F\u20E30\uFE0F\u20E31\uFE0F\u20E32\uFE0F\u20E33\uFE0F\u20E34\uFE0F\u20E35\uFE0F\u20E36\uFE0F\u20E37\uFE0F\u20E3");
        assertThat(r4.isViolation()).isTrue();
        assertThat(r4.violationType()).isEqualTo("PHONE_LEAK");
    }

    @Test
    @DisplayName("TC08-TC10: Detect phone numbers split across 3-5 consecutive short messages (Sliding Window)")
    void tc08_to_tc10_slidingWindowSplitPhone() {
        long convId = 42L;
        long senderA = 101L;

        // Message 1 to 4: not yet 10 digits
        assertThat(moderator.moderate(convId, senderA, "09").isViolation()).isFalse();
        assertThat(moderator.moderate(convId, senderA, "87").isViolation()).isFalse();
        assertThat(moderator.moderate(convId, senderA, "65").isViolation()).isFalse();
        assertThat(moderator.moderate(convId, senderA, "43").isViolation()).isFalse();

        // Message 5 completes "0987654321" (10 digits) -> Violation!
        var r5 = moderator.moderate(convId, senderA, "21");
        assertThat(r5.isViolation()).isTrue();
        assertThat(r5.violationType()).isEqualTo("PHONE_LEAK");
        assertThat(r5.reason()).isEqualTo("SLIDING_WINDOW_PHONE_SPLIT");
        assertThat(r5.maskedText()).isEqualTo("***");
    }

    @Test
    @DisplayName("TC08-TC10: Interleaved messages from peer do not disrupt sliding window for sender")
    void tc08_slidingWindowWithInterleavedPeer() {
        long convId = 43L;
        long senderA = 101L;
        long senderB = 202L;

        assertThat(moderator.moderate(convId, senderA, "09").isViolation()).isFalse();
        assertThat(moderator.moderate(convId, senderB, "Dạ vâng shop ơi").isViolation()).isFalse();
        assertThat(moderator.moderate(convId, senderA, "87").isViolation()).isFalse();
        assertThat(moderator.moderate(convId, senderA, "65").isViolation()).isFalse();
        assertThat(moderator.moderate(convId, senderB, "Gửi số mình nhé").isViolation()).isFalse();
        assertThat(moderator.moderate(convId, senderA, "43").isViolation()).isFalse();

        // Sender A finishes the phone number
        var rA = moderator.moderate(convId, senderA, "21");
        assertThat(rA.isViolation()).isTrue();
        assertThat(rA.violationType()).isEqualTo("PHONE_LEAK");
    }

    @Test
    @DisplayName("TC41: Detect Vietnamese slang for platforms and spelled-out word numbers")
    void tc41_vietnameseSlangAndSpelledNumbers() {
        // Zalo slang: "da lô", "dép lào"
        var r1 = moderator.moderate(1L, 100L, "Kết bạn da lô trao đổi nhé");
        assertThat(r1.isViolation()).isTrue();
        assertThat(r1.violationType()).isEqualTo("PLATFORM_REDIRECT");
        assertThat(r1.reason()).isEqualTo("SLANG_ZALO_REDIRECT");
        assertThat(r1.maskedText()).contains("***");

        var r2 = moderator.moderate(1L, 100L, "Nhắn qua dép lào nha bạn");
        assertThat(r2.isViolation()).isTrue();
        assertThat(r2.violationType()).isEqualTo("PLATFORM_REDIRECT");

        // Facebook slang: "phây búc"
        var r3 = moderator.moderate(1L, 100L, "Check tin nhắn bên phây búc nhé");
        assertThat(r3.isViolation()).isTrue();
        assertThat(r3.violationType()).isEqualTo("PLATFORM_REDIRECT");

        // Telegram slang: "tê lê"
        var r4 = moderator.moderate(1L, 100L, "Add tê lê mình nha");
        assertThat(r4.isViolation()).isTrue();
        assertThat(r4.violationType()).isEqualTo("PLATFORM_REDIRECT");

        // Spelled out Vietnamese word numbers
        var r5 = moderator.moderate(1L, 100L, "không chín không một hai ba bốn năm sáu bảy");
        assertThat(r5.isViolation()).isTrue();
        assertThat(r5.violationType()).isEqualTo("PHONE_LEAK");

        // Mixed slang "k chín không..."
        var r6 = moderator.moderate(1L, 100L, "k chín không một hai ba bốn năm sáu bảy");
        assertThat(r6.isViolation()).isTrue();
        assertThat(r6.violationType()).isEqualTo("PHONE_LEAK");

        // Spelled out phone with "ko", "zero", and embedded in sentence
        var r7 = moderator.moderate(1L, 100L, "Alo bạn gọi số ko chín không một hai ba bốn năm sáu bảy nha");
        assertThat(r7.isViolation()).isTrue();
        assertThat(r7.violationType()).isEqualTo("PHONE_LEAK");
        assertThat(r7.maskedText()).contains("***");
        assertThat(r7.maskedText()).contains("Alo bạn gọi số");

        // Mixed words and digits (e.g. "không 9 không 1 hai ba 4 năm 6 bảy")
        var r8 = moderator.moderate(1L, 100L, "không 9 không 1 hai ba 4 năm 6 bảy");
        assertThat(r8.isViolation()).isTrue();
        assertThat(r8.violationType()).isEqualTo("PHONE_LEAK");
        assertThat(r8.maskedText()).isEqualTo("***");

        // Spelled out 12-digit CCCD in words
        var r9 = moderator.moderate(1L, 100L, "Số căn cước: không bảy chín một chín chín không một hai ba bốn năm");
        assertThat(r9.isViolation()).isTrue();
        assertThat(r9.violationType()).isEqualTo("PII_LEAK");
        assertThat(r9.reason()).isEqualTo("VIETNAMESE_WORD_CCCD_LEAK");
        assertThat(r9.maskedText()).contains("***");
        assertThat(r9.maskedText()).contains("Số căn cước:");
    }

    @Test
    @DisplayName("Anti-False-Positive: Normal sentences with few number words MUST NEVER be flagged")
    void antiFalsePositive_fewNumberWords() {
        var r1 = moderator.moderate(1L, 100L, "Tôi cần mua ba cái áo và hai cái quần");
        assertThat(r1.isViolation()).isFalse();

        var r2 = moderator.moderate(1L, 100L, "Nhà mình có bốn người, cần mua một bộ bàn ăn");
        assertThat(r2.isViolation()).isFalse();
    }

    @Test
    @DisplayName("Anti-False-Positive: Bargaining and item specifications MUST NEVER be flagged")
    void antiFalsePositive_pricesAndDimensions() {
        // Price bargaining
        var r1 = moderator.moderate(1L, 100L, "Fix 500k nhé bạn ơi");
        assertThat(r1.isViolation()).isFalse();
        assertThat(r1.maskedText()).isEqualTo("Fix 500k nhé bạn ơi");

        var r2 = moderator.moderate(1L, 100L, "Bớt 200k đi mình qua lấy luôn");
        assertThat(r2.isViolation()).isFalse();

        var r3 = moderator.moderate(1L, 100L, "Giá 2.500.000 đ có thương lượng không");
        assertThat(r3.isViolation()).isFalse();

        var r4 = moderator.moderate(1L, 100L, "Cọc sàn 50k qua cổng O.G");
        assertThat(r4.isViolation()).isFalse();

        // Dimensions and specifications
        var r5 = moderator.moderate(1L, 100L, "Bàn dài 120cm, rộng 60cm, cao 75cm");
        assertThat(r5.isViolation()).isFalse();

        var r6 = moderator.moderate(1L, 100L, "Trọng lượng 15kg, kích thước 50 x 30 x 20 cm");
        assertThat(r6.isViolation()).isFalse();

        var r7 = moderator.moderate(1L, 100L, "Mình mua 2 cái, nguồn 220v công suất 65w");
        assertThat(r7.isViolation()).isFalse();
    }

    @Test
    @DisplayName("Off-platform payment detection")
    void offPlatformPaymentDetection() {
        var r1 = moderator.moderate(1L, 100L, "Cọc trước 100k mình mới ship nha");
        assertThat(r1.isViolation()).isTrue();
        assertThat(r1.violationType()).isEqualTo("OFF_PLATFORM_PAYMENT");

        var r2 = moderator.moderate(1L, 100L, "Chuyển lúa ngoài cho đỡ phí sàn");
        assertThat(r2.isViolation()).isTrue();
        assertThat(r2.violationType()).isEqualTo("OFF_PLATFORM_PAYMENT");

        var r3 = moderator.moderate(1L, 100L, "Chuyển khoản riêng nhé");
        assertThat(r3.isViolation()).isTrue();
        assertThat(r3.violationType()).isEqualTo("OFF_PLATFORM_PAYMENT");
    }

    @Test
    @DisplayName("External links and defanged URLs detection")
    void externalLinksDetection() {
        var r1 = moderator.moderate(1L, 100L, "Xem link chotot[.]vn/item123 nhé");
        assertThat(r1.isViolation()).isTrue();
        assertThat(r1.violationType()).isEqualTo("PLATFORM_REDIRECT");

        var r2 = moderator.moderate(1L, 100L, "Vào https://zalo.me/g/abcde");
        assertThat(r2.isViolation()).isTrue();
        assertThat(r2.violationType()).isEqualTo("PLATFORM_REDIRECT");
    }

    @Test
    @DisplayName("Combination: Price with phone number preserves price but masks phone")
    void combination_priceWithPhone() {
        var r = moderator.moderate(1L, 100L, "Fix 500k gọi mình 0901234567 nha");
        assertThat(r.isViolation()).isTrue();
        assertThat(r.violationType()).isEqualTo("PHONE_LEAK");
        assertThat(r.maskedText()).contains("Fix 500k");
        assertThat(r.maskedText()).contains("***");
        assertThat(r.maskedText()).doesNotContain("0901234567");
    }

    @Test
    @DisplayName("PII Protection: Detect and mask Citizen Identity Card (CCCD 12 digits and CMND 9 digits)")
    void cccdAndPiiProtection() {
        // Standalone 12-digit CCCD starting with 0
        var r1 = moderator.moderate(1L, 100L, "Số của em là 079199012345 nha shop");
        assertThat(r1.isViolation()).isTrue();
        assertThat(r1.violationType()).isEqualTo("PII_LEAK");
        assertThat(r1.maskedText()).contains("***");
        assertThat(r1.maskedText()).doesNotContain("079199012345");

        // CCCD with spaces
        var r2 = moderator.moderate(1L, 100L, "Căn cước: 079 199 012 345");
        assertThat(r2.isViolation()).isTrue();
        assertThat(r2.violationType()).isEqualTo("PII_LEAK");
        assertThat(r2.maskedText()).contains("***");

        // CCCD with dots
        var r3 = moderator.moderate(1L, 100L, "CCCD số 001.201.012.345");
        assertThat(r3.isViolation()).isTrue();
        assertThat(r3.violationType()).isEqualTo("PII_LEAK");
        assertThat(r3.maskedText()).contains("***");

        // CCCD with dashes
        var r4 = moderator.moderate(1L, 100L, "Số CCCD: 079-199-012345");
        assertThat(r4.isViolation()).isTrue();
        assertThat(r4.violationType()).isEqualTo("PII_LEAK");
        assertThat(r4.maskedText()).contains("***");

        // 9-digit CMND with explicit keyword
        var r5 = moderator.moderate(1L, 100L, "CMND cũ của mình là 025678912");
        assertThat(r5.isViolation()).isTrue();
        assertThat(r5.violationType()).isEqualTo("PII_LEAK");
        assertThat(r5.maskedText()).contains("***");
        assertThat(r5.maskedText()).doesNotContain("025678912");
    }

    @Test
    @DisplayName("Composite Message: All violations (phone, link, CCCD, payment) are masked in a single pass")
    void composite_multipleViolationsInSingleMessage() {
        String input = "Chào bạn, vào link https://zalo.me/g/abcde hoặc kết bạn da lô số 0901234567, "
            + "gọi hotline O98.765.4321, gửi cccd 079199012345 rồi cọc trước 100k mình ship ngay.";

        var result = moderator.moderate(1L, 100L, input);
        assertThat(result.isViolation()).isTrue();
        assertThat(result.violationType()).isEqualTo("OFF_PLATFORM_PAYMENT");

        String masked = result.maskedText();
        assertThat(masked).doesNotContain("https://zalo.me/g/abcde");
        assertThat(masked).doesNotContain("da lô");
        assertThat(masked).doesNotContain("0901234567");
        assertThat(masked).doesNotContain("O98.765.4321");
        assertThat(masked).doesNotContain("079199012345");
        assertThat(masked).doesNotContain("cọc trước");
        assertThat(masked).contains("***");
    }

    @Test
    @DisplayName("Large Message (~1950 characters): Embedded multiple phones, links, CCCDs are all masked cleanly")
    void largeMessage_near2000Characters_withMultipleHiddenLeaks() {
        StringBuilder sb = new StringBuilder();
        sb.append("Chào bạn, mình cần bán lại chiếc máy tính xách tay Dell XPS 15 9500 chính hãng mua tại Việt Nam. ");
        sb.append("Máy đã qua sử dụng khoảng 1 năm nhưng ngoại hình còn rất mới tầm 98%, không cấn móp hay trầy xước nặng. ");
        sb.append("Cấu hình chi tiết: vi xử lý Intel Core i7 10750H, bộ nhớ RAM 16GB DDR4 bus 2933MHz, ổ cứng SSD 512GB NVMe. ");
        sb.append("Màn hình 15.6 inch độ phân giải 4K UHD sắc nét, kích thước 34.4 x 23.0 x 1.8 cm, trọng lượng chỉ khoảng 1.83kg. ");
        sb.append("Máy có sạc zin 130w Type-C đi kèm. Nếu bạn cần xem thêm video thực tế thì add da lô số 0901234567 để mình gửi clip test loa và màn hình nhé. ");
        sb.append("Ngoài ra bạn có thể xem thêm ảnh chi tiết tại link chotot[.]vn/laptop-dell-xps hoặc ghé trang fb.com/laptopcugiare xem thêm đồ công nghệ khác. ");
        sb.append("Mình ở khu vực Quận 1 TP.HCM, bạn nào qua tận nơi xem máy mình fix 200k tiền xăng xe vui vẻ. ");
        sb.append("Nếu bạn ở xa cần ship COD thì vui lòng cọc trước 100k tiền phí vận chuyển bưu điện nhé. ");
        sb.append("Để bạn yên tâm mình có thể chụp cả CCCD số 079199012345 và giấy tờ mua hàng cho bạn đối chiếu thông tin chính chủ. ");
        sb.append("Nếu số kia bận bạn có thể gọi hotline phụ O98.765.4321 hoặc 0912-345-678 đều được nha. ");
        sb.append("Hoặc nhắn số dự phòng không chín không một hai ba bốn năm sáu bảy nhé. ");

        // Pad with legitimate description to reach ~1950 characters
        while (sb.length() < 1920) {
            sb.append("Cam kết máy nguyên bản chưa qua sửa chữa, bao test 7 ngày hoàn tiền 100% nếu có lỗi phần cứng từ nhà sản xuất. ");
        }

        String rawText = sb.toString();
        assertThat(rawText.length()).isBetween(1800, 2000);

        var result = moderator.moderate(1L, 100L, rawText);
        assertThat(result.isViolation()).isTrue();

        String masked = result.maskedText();
        // Verify all sensitive leaks are masked
        assertThat(masked).doesNotContain("0901234567");
        assertThat(masked).doesNotContain("O98.765.4321");
        assertThat(masked).doesNotContain("0912-345-678");
        assertThat(masked).doesNotContain("079199012345");
        assertThat(masked).doesNotContain("da lô");
        assertThat(masked).doesNotContain("chotot[.]vn");
        assertThat(masked).doesNotContain("fb.com");
        assertThat(masked).doesNotContain("cọc trước");
        assertThat(masked).doesNotContain("không chín không một hai ba bốn năm sáu bảy");

        // Verify legitimate product specifications are preserved
        assertThat(masked).contains("Dell XPS 15");
        assertThat(masked).contains("1.83kg");
        assertThat(masked).contains("fix 200k");
        assertThat(masked).contains("130w");
    }

    @Test
    @DisplayName("Large Message (~1950 characters): Clean detailed product description MUST NEVER be flagged (Zero False Positive)")
    void largeMessage_near2000Characters_cleanProductDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Thông số kỹ thuật chi tiết của bàn làm việc công thái học gỗ sồi tự nhiên: ");
        sb.append("Chất liệu: mặt bàn gỗ sồi nhập khẩu dày 2.5cm, khung thép sơn tĩnh điện chịu lực cao lên đến 100kg. ");
        sb.append("Kích thước tổng thể: chiều dài 160cm x chiều rộng 80cm x chiều cao tùy chỉnh từ 70cm đến 120cm. ");
        sb.append("Động cơ nâng hạ kép êm ái, nguồn điện 220v tần số 50hz, công suất hoạt động 120w, độ ồn dưới 45db. ");
        sb.append("Mức giá niêm yết là 4.500.000 đ, có hỗ trợ giảm 300k cho khách mua combo kèm ghế công thái học. ");
        sb.append("Bộ sản phẩm bao gồm: 1 mặt bàn, 2 chân nâng hạ, 1 bộ điều khiển nhớ 4 vị trí, ốc vít và phụ kiện lắp ráp đầy đủ. ");

        while (sb.length() < 1920) {
            sb.append("Bảo hành chính hãng 24 tháng đối với động cơ và khung thép, bảo trì trọn đời tại hệ thống cửa hàng O.G Shop. ");
        }

        String rawText = sb.toString();
        assertThat(rawText.length()).isBetween(1800, 2000);

        var result = moderator.moderate(1L, 100L, rawText);
        assertThat(result.isViolation()).isFalse();
        assertThat(result.maskedText()).isEqualTo(rawText);
    }

    @Test
    @DisplayName("Edge Case: Prepending 0 before 123456789 (0123456789) must be caught as PHONE_LEAK")
    void testLeadingZeroBefore123456789() {
        var r1 = moderator.moderate(1L, 100L, "0123456789");
        assertThat(r1.isViolation()).isTrue();
        assertThat(r1.violationType()).isEqualTo("PHONE_LEAK");
        assertThat(r1.maskedText()).isEqualTo("***");

        var r2 = moderator.moderate(1L, 100L, "SĐT test của mình 0123456789 nha");
        assertThat(r2.isViolation()).isTrue();
        assertThat(r2.violationType()).isEqualTo("PHONE_LEAK");
        assertThat(r2.maskedText()).contains("***");
        assertThat(r2.maskedText()).doesNotContain("0123456789");
    }

    @Test
    @DisplayName("Edge Case: Interleaved random letters inside phone number (012345abkcsja6789, 12345abkcsja6789)")
    void testInterleavedLettersInPhone() {
        var r1 = moderator.moderate(1L, 100L, "012345abkcsja6789");
        assertThat(r1.isViolation()).isTrue();
        assertThat(r1.violationType()).isEqualTo("PHONE_LEAK");
        assertThat(r1.maskedText()).isEqualTo("***");

        var r2 = moderator.moderate(1L, 100L, "Alo 012345abkcsja6789 nha");
        assertThat(r2.isViolation()).isTrue();
        assertThat(r2.violationType()).isEqualTo("PHONE_LEAK");
        assertThat(r2.maskedText()).contains("***");
        assertThat(r2.maskedText()).doesNotContain("012345");

        var r3 = moderator.moderate(1L, 100L, "12345abkcsja6789");
        assertThat(r3.isViolation()).isTrue();
        assertThat(r3.violationType()).isEqualTo("PHONE_LEAK");
        assertThat(r3.maskedText()).isEqualTo("***");

        var r4 = moderator.moderate(1L, 100L, "090abc123xyz456");
        assertThat(r4.isViolation()).isTrue();
        assertThat(r4.violationType()).isEqualTo("PHONE_LEAK");
    }

    @Test
    @DisplayName("Edge Case: Omitted leading 0 (9-digit mobile phones e.g. 987654321, 909 123 456)")
    void testOmittedLeadingZeroNineDigits() {
        var r1 = moderator.moderate(1L, 100L, "987654321");
        assertThat(r1.isViolation()).isTrue();
        assertThat(r1.violationType()).isEqualTo("PHONE_LEAK");
        assertThat(r1.maskedText()).isEqualTo("***");

        var r2 = moderator.moderate(1L, 100L, "Gọi mình số 909 123 456 nha");
        assertThat(r2.isViolation()).isTrue();
        assertThat(r2.violationType()).isEqualTo("PHONE_LEAK");
        assertThat(r2.maskedText()).contains("***");
        assertThat(r2.maskedText()).doesNotContain("909 123 456");
    }

    @Test
    @DisplayName("Edge Case: Padded digits with instructions to pick numbers from middle / range")
    void testPaddedDigitsPickFromMiddle() {
        var r1 = moderator.moderate(1L, 100L, "123450912345678999 lấy 10 số ở giữa nha");
        assertThat(r1.isViolation()).isTrue();
        assertThat(r1.violationType()).isEqualTo("PHONE_LEAK");
        assertThat(r1.maskedText()).contains("***");
        assertThat(r1.maskedText()).doesNotContain("0912345678");

        var r2 = moderator.moderate(1L, 100L, "9990901234567888");
        assertThat(r2.isViolation()).isTrue();
        assertThat(r2.violationType()).isEqualTo("PHONE_LEAK");

        var r3 = moderator.moderate(1L, 100L, "00123456789000");
        assertThat(r3.isViolation()).isTrue();
        assertThat(r3.violationType()).isEqualTo("PHONE_LEAK");
    }

    @Test
    @DisplayName("Edge Case: Sliding window splitting 0123456789 across messages")
    void testSlidingWindowWith0123456789() {
        long convId = 99L;
        long senderA = 505L;

        assertThat(moderator.moderate(convId, senderA, "01").isViolation()).isFalse();
        assertThat(moderator.moderate(convId, senderA, "23").isViolation()).isFalse();
        assertThat(moderator.moderate(convId, senderA, "45").isViolation()).isFalse();
        assertThat(moderator.moderate(convId, senderA, "67").isViolation()).isFalse();

        var r5 = moderator.moderate(convId, senderA, "89");
        assertThat(r5.isViolation()).isTrue();
        assertThat(r5.violationType()).isEqualTo("PHONE_LEAK");
        assertThat(r5.maskedText()).isEqualTo("***");
    }

    @Test
    @DisplayName("Latency and Throughput Benchmark: Verify ultra-fast processing across message lengths")
    void latencyAndThroughputBenchmark() {
        String shortClean = "Chào shop, sản phẩm này còn hàng không shop ơi?";
        String shortViolation = "Alo shop, gọi mình số 0901234567 trao đổi nhé";
        String mediumComposite = "Chào bạn, add da lô số 0987-654-321 hoặc kết bạn fb.com/shopdoCu nhé, có cccd 079199012345, cọc trước 100k.";

        StringBuilder longBuilder = new StringBuilder();
        while (longBuilder.length() < 1850) {
            longBuilder.append("Bàn dài 120cm, nặng 15kg, fix 200k. Liên hệ 0901234567 hoặc chotot[.]vn, cccd 079199012345 nha. ");
        }
        String longComposite = longBuilder.toString();

        // 1. Warm up JIT compiler
        for (int i = 0; i < 500; i++) {
            moderator.moderate(1L, 100L, shortClean);
            moderator.moderate(1L, 100L, shortViolation);
            moderator.moderate(1L, 100L, mediumComposite);
            moderator.moderate(1L, 100L, longComposite);
        }

        // 2. Measure Short Clean
        int shortIterations = 2000;
        long t0 = System.nanoTime();
        for (int i = 0; i < shortIterations; i++) {
            moderator.moderate(1L, 100L, shortClean);
        }
        long t1 = System.nanoTime();
        double avgShortCleanMicros = (double) (t1 - t0) / shortIterations / 1000.0;

        // 3. Measure Short Violation
        t0 = System.nanoTime();
        for (int i = 0; i < shortIterations; i++) {
            moderator.moderate(1L, 100L, shortViolation);
        }
        t1 = System.nanoTime();
        double avgShortViolationMicros = (double) (t1 - t0) / shortIterations / 1000.0;

        // 4. Measure Medium Composite
        t0 = System.nanoTime();
        for (int i = 0; i < shortIterations; i++) {
            moderator.moderate(1L, 100L, mediumComposite);
        }
        t1 = System.nanoTime();
        double avgMediumCompositeMicros = (double) (t1 - t0) / shortIterations / 1000.0;

        // 5. Measure Long Composite (~1900 chars)
        int longIterations = 500;
        t0 = System.nanoTime();
        for (int i = 0; i < longIterations; i++) {
            moderator.moderate(1L, 100L, longComposite);
        }
        t1 = System.nanoTime();
        double avgLongCompositeMicros = (double) (t1 - t0) / longIterations / 1000.0;

        double opsPerSec = (double) longIterations / ((double) (t1 - t0) / 1_000_000_000.0);

        System.out.println("======================================================================");
        System.out.println("TEXT MODERATOR LATENCY BENCHMARK RESULTS (CPU LAPTOP):");
        System.out.printf(" - Short Clean (50 chars):        %.3f µs (%.5f ms)%n", avgShortCleanMicros, avgShortCleanMicros / 1000.0);
        System.out.printf(" - Short Violation (50 chars):    %.3f µs (%.5f ms)%n", avgShortViolationMicros, avgShortViolationMicros / 1000.0);
        System.out.printf(" - Medium Composite (115 chars):  %.3f µs (%.5f ms)%n", avgMediumCompositeMicros, avgMediumCompositeMicros / 1000.0);
        System.out.printf(" - Long Composite (~1900 chars):  %.3f µs (%.5f ms)%n", avgLongCompositeMicros, avgLongCompositeMicros / 1000.0);
        System.out.printf(" - Throughput (Long Composite):   %,.0f msgs/sec%n", opsPerSec);
        System.out.println("======================================================================");

        // Verification: Average latency must be < 10.0 ms even for 1900-char composite with multiple leaks
        assertThat(avgLongCompositeMicros / 1000.0).isLessThan(10.0);
        assertThat(avgShortCleanMicros / 1000.0).isLessThan(1.0);
    }
}

