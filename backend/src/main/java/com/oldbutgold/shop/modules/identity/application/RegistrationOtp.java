package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.shared.config.AuthProperties;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Component
public class RegistrationOtp {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public RegistrationOtp(AuthProperties properties) {
        key = properties.jwtSigningKey().getBytes(StandardCharsets.UTF_8);
    }

    public String generate() { return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000)); }
    public String digest(UUID challenge, String code) { return hmac("registration-otp:" + challenge + ":" + code); }
    public String requesterDigest(String requester) { return hmac("registration-requester:" + requester); }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("OTP digest unavailable");
        }
    }
}
