package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.shared.config.VnPayProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class VnPayService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private final VnPayProperties properties;
    private final Clock clock;
    @Autowired
    public VnPayService(VnPayProperties properties, Clock clock) { this.properties = properties; this.clock = clock; }
    public VnPayService(VnPayProperties properties) { this(properties, Clock.systemUTC()); }

    public String merchantCode() { return properties.tmnCode(); }
    public static long scaledAmount(BigDecimal amount) {
        try {
            if (amount == null || amount.compareTo(BigDecimal.valueOf(1000)) < 0 || amount.stripTrailingZeros().scale() > 0)
                throw new IllegalArgumentException("VNPAY requires whole VND amounts of at least 1,000.");
            long scaled = amount.movePointRight(2).longValueExact();
            if (scaled > 999_999_999_999L) throw new IllegalArgumentException("Amount exceeds VNPAY's 12-digit limit.");
            return scaled;
        } catch (ArithmeticException error) { throw new IllegalArgumentException("Invalid VNPAY amount.", error); }
    }
    public boolean verifyCallback(Map<String, String> parameters) {
        String hash = parameters.get("vnp_SecureHash");
        if (hash == null || !hash.matches("[0-9a-fA-F]{128}")) return false;
        return MessageDigest.isEqual(HexFormat.of().parseHex(hash), HexFormat.of().parseHex(sign(parameters)));
    }
    private String canonical(Map<String, String> parameters) {
        return new TreeMap<>(parameters).entrySet().stream()
                .filter(e -> e.getKey().startsWith("vnp_") && !e.getKey().equals("vnp_SecureHash")
                        && !e.getKey().equals("vnp_SecureHashType") && e.getValue() != null && !e.getValue().isBlank())
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII))
                .collect(Collectors.joining("&"));
    }
    public String sign(Map<String, String> parameters) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(properties.hashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(canonical(parameters).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw new IllegalStateException("Cannot sign VNPAY payload", error); }
    }
    public String createPaymentUrl(String reference, long amount, String info, String ip) {
        return createPaymentUrl(reference, BigDecimal.valueOf(amount), info, ip, clock.instant().plusSeconds(900), null);
    }
    public String createPaymentUrl(String reference, BigDecimal amount, String info, String ip, Instant expiresAt, Long orderId) {
        if (!expiresAt.isAfter(clock.instant())) throw new IllegalStateException("Payment deadline has passed.");
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0"); params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", properties.tmnCode()); params.put("vnp_Amount", Long.toString(scaledAmount(amount)));
        params.put("vnp_CurrCode", "VND"); params.put("vnp_TxnRef", reference);
        params.put("vnp_OrderInfo", info); params.put("vnp_OrderType", "other"); params.put("vnp_Locale", "vn");
        String returnUrl = properties.returnUrl();
        if (orderId != null) returnUrl += (returnUrl.contains("?") ? "&" : "?") + "orderId=" + orderId;
        params.put("vnp_ReturnUrl", returnUrl); params.put("vnp_IpAddr", ip == null || ip.isBlank() ? "127.0.0.1" : ip);
        params.put("vnp_CreateDate", DATE_FORMAT.format(clock.instant()));
        params.put("vnp_ExpireDate", DATE_FORMAT.format(expiresAt));
        return properties.payUrl() + "?" + canonical(params) + "&vnp_SecureHash=" + sign(params);
    }
}