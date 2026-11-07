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
}
