package com.oldbutgold.shop.modules.fulfillment.infrastructure.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.Random;

/**
 * Bộ sinh mã đối tác `client_order_code` gửi cho GHN.
 * Cấu trúc: OG-{ENV}-{orderId}-{randomSuffix} (tối đa 34 ký tự <= 50 ký tự theo giới hạn của GHN).
 * Mã này được sinh duy nhất một lần khi tạo ShipmentEntity (PENDING) và lưu vào database;
 * mọi lần retry gọi sang GHN đều tái sử dụng mã đã lưu, không bao giờ sinh lại mã mới.
 */
@Component
public class ClientOrderCodeGenerator {

    private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int SUFFIX_LENGTH = 6;
    private final String environment;
    private final Random random;

    private static final java.util.Set<String> ALLOWED_ENVIRONMENTS = java.util.Set.of("DEV", "STG", "PROD");

    @org.springframework.beans.factory.annotation.Autowired
    public ClientOrderCodeGenerator(@Value("${app.environment}") String environment) {
        this(environment, new SecureRandom());
    }

    public ClientOrderCodeGenerator(String environment, Random random) {
        if (environment == null || environment.isBlank()) {
            throw new IllegalArgumentException("Cấu hình 'app.environment' là bắt buộc (chỉ chấp nhận: DEV, STG, PROD). Hiện tại đang thiếu hoặc rỗng.");
        }
        String normalized = environment.trim().toUpperCase();
        if (!ALLOWED_ENVIRONMENTS.contains(normalized)) {
            throw new IllegalArgumentException("Cấu hình 'app.environment' không hợp lệ: '" + environment + "'. Chỉ chấp nhận một trong các giá trị: DEV, STG, PROD.");
        }
        this.environment = normalized;
        this.random = Objects.requireNonNull(random, "Random generator must not be null");
    }

    /**
     * Sinh mã client_order_code cho đơn hàng.
     *
     * @param orderId ID đơn hàng (BIGINT)
     * @return Chuỗi mã đối tác gửi GHN, định dạng OG-{ENV}-{orderId}-{suffix}
     */
    public String generate(Long orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
        }
        return "OG-" + environment + "-" + orderId + "-" + suffix;
    }

    public String getEnvironment() {
        return environment;
    }
}
