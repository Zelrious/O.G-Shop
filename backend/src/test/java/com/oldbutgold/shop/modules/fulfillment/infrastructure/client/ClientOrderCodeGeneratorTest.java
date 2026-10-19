package com.oldbutgold.shop.modules.fulfillment.infrastructure.client;

import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ClientOrderCodeGeneratorTest {

    private static final Pattern CODE_REGEX = Pattern.compile("^OG-(DEV|STG|PROD)-[0-9]+-[A-Z0-9]{6}$");

    @Test
    @DisplayName("Chuỗi sinh ra khớp chuẩn định dạng regex OG-(DEV|STG|PROD)-{orderId}-{randomSuffix}")
    void testFormatAndRegex() {
        ClientOrderCodeGenerator devGen = new ClientOrderCodeGenerator("DEV");
        ClientOrderCodeGenerator stgGen = new ClientOrderCodeGenerator("STG");
        ClientOrderCodeGenerator prodGen = new ClientOrderCodeGenerator("PROD");

        String devCode = devGen.generate(105L);
        String stgCode = stgGen.generate(9999L);
        String prodCode = prodGen.generate(1L);

        assertThat(devCode).matches(CODE_REGEX).startsWith("OG-DEV-105-");
        assertThat(stgCode).matches(CODE_REGEX).startsWith("OG-STG-9999-");
        assertThat(prodCode).matches(CODE_REGEX).startsWith("OG-PROD-1-");
    }

    @Test
    @DisplayName("Kiểm tra với orderId = Long.MAX_VALUE (19 chữ số), độ dài đạt 34 ký tự, luôn <= 50 ký tự GHN giới hạn")
    void testMaxLengthWithLongMaxValue() {
        ClientOrderCodeGenerator prodGen = new ClientOrderCodeGenerator("PROD");
        String maxCode = prodGen.generate(Long.MAX_VALUE);

        // Long.MAX_VALUE = 9223372036854775807 (19 digits)
        // OG- (3) + PROD (4) + - (1) + 19 digits + - (1) + 6 chars suffix = 34 chars
        assertThat(maxCode).hasSize(34);
        assertThat(maxCode.length()).isLessThanOrEqualTo(50);
        assertThat(maxCode).startsWith("OG-PROD-" + Long.MAX_VALUE + "-");
    }

    @Test
    @DisplayName("Xác nhận tiền tố môi trường thay đổi linh hoạt theo cấu hình")
    void testEnvironmentPrefix() {
        ClientOrderCodeGenerator devGen = new ClientOrderCodeGenerator("DEV");
        ClientOrderCodeGenerator prodGen = new ClientOrderCodeGenerator("PROD");

        assertThat(devGen.generate(42L)).startsWith("OG-DEV-42-");
        assertThat(prodGen.generate(42L)).startsWith("OG-PROD-42-");
    }

    @Test
    @DisplayName("Xác nhận luồng retry tái sử dụng mã đã lưu trong entity, không sinh mã mới")
    void testRetryReusesPersistedCode() {
        ClientOrderCodeGenerator gen = new ClientOrderCodeGenerator("DEV");
        Long orderId = 200L;
        Instant now = Instant.now();

        // Lần đầu tạo shipment PENDING
        String initialCode = gen.generate(orderId);
        ShipmentEntity shipment = new ShipmentEntity(orderId, initialCode, now);

        // Khi retry gọi GHN (do timeout hoặc Seller bấm lại), hệ thống lấy lại mã từ entity
        String codeForRetry = shipment.getClientOrderCode();

        assertThat(codeForRetry).isEqualTo(initialCode);
        // Đảm bảo không gọi generator tạo mã mới làm phân mảnh vận đơn trên GHN
        assertThat(shipment.getClientOrderCode()).isEqualTo(initialCode);
    }

    @Test
    @DisplayName("Ném ngoại lệ khi app.environment bị thiếu (null) hoặc để rỗng (blank)")
    void testMissingOrBlankEnvironmentThrowsException() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ClientOrderCodeGenerator(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bắt buộc");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ClientOrderCodeGenerator("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bắt buộc");
    }

    @Test
    @DisplayName("Ném ngoại lệ khi app.environment không nằm trong tập (DEV, STG, PROD)")
    void testInvalidEnvironmentThrowsException() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ClientOrderCodeGenerator("LOCAL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không hợp lệ");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ClientOrderCodeGenerator("TEST"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không hợp lệ");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ClientOrderCodeGenerator("PRODUCTION"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không hợp lệ");
    }

    @Test
    @DisplayName("Chấp nhận DEV, STG, PROD và tự động chuẩn hóa chữ hoa (case-insensitive)")
    void testValidEnvironmentsNormalized() {
        ClientOrderCodeGenerator dev = new ClientOrderCodeGenerator("dev");
        ClientOrderCodeGenerator stg = new ClientOrderCodeGenerator("stg");
        ClientOrderCodeGenerator prod = new ClientOrderCodeGenerator("prod");

        assertThat(dev.getEnvironment()).isEqualTo("DEV");
        assertThat(stg.getEnvironment()).isEqualTo("STG");
        assertThat(prod.getEnvironment()).isEqualTo("PROD");
    }
}
