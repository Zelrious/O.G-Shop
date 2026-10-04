package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.shared.config.VnPayProperties;
import org.springframework.stereotype.Service;


import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Service
public class VnPayService {
    private final VnPayProperties properties;

    public VnPayService(VnPayProperties properties){
        this.properties = properties;
    }
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

        public boolean verifyCallback(Map<String, String> queryParams) {
        // 1. Lấy chữ ký do VNPAY gửi về
        String receivedHash = queryParams.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) {
            return false;
        }

        // 2. Tạo bản sao và loại bỏ 2 trường chữ ký ra khỏi dữ liệu cần băm
        Map<String, String> fields = new HashMap<>(queryParams);
        fields.remove("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");

        // 3. Sắp xếp các tham số còn lại theo thứ tự A-Z
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        for (Iterator<String> itr = fieldNames.iterator(); itr.hasNext(); ) {
            String fieldName = itr.next();
            String fieldValue = fields.get(fieldName);
            if (fieldValue != null && !fieldValue.isBlank()) {
                hashData.append(fieldName).append('=').append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                if (itr.hasNext()) {
                    hashData.append('&');
                }
            }
        }

        // 4. Băm lại chuỗi dữ liệu với hashSecret của shop
        String calculatedHash = hashData(properties.hashSecret(), hashData.toString());

        // 5. So sánh chữ ký ta tự tính với chữ ký VNPAY gửi về
        return calculatedHash.equalsIgnoreCase(receivedHash);
    }


    public String createPaymentUrl(String orderRef, long amountVND, String orderInfo, String ipAddress)
    {
        if(amountVND <= 0)
        {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", properties.tmnCode());
        params.put("vnp_Amount", String.valueOf(amountVND * 100)); // Nhân 100 theo chuẩn VNPAY
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", orderRef);
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", properties.returnUrl());
        params.put("vnp_IpAddr", ipAddress != null && !ipAddress.isBlank() ? ipAddress : "127.0.0.1");
        
        LocalDateTime now = LocalDateTime.now();
        params.put("vnp_CreateDate", now.format(DATE_FORMAT));
        params.put("vnp_ExpireDate", now.plusMinutes(15).format(DATE_FORMAT));

        List<String> fieldNames = new ArrayList<>(params.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        for(Iterator<String> itr = fieldNames.iterator(); itr.hasNext();)
        {
            String fieldName = itr.next();
            String fieldValue = params.get(fieldName);
            if(fieldValue != null && !fieldValue.isBlank())
            {
                hashData.append(fieldName).append("=").append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII)).append("=").append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                
                if(itr.hasNext())
                {
                    hashData.append("&");
                    query.append("&");
                }
            }
        }

        String secureHash = hashData(properties.hashSecret(), hashData.toString());

        query.append("&vnp_SecureHash=").append(secureHash);

        return properties.payUrl() + "?" + query;
    }

    private String hashData(String key, String data)
    {
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");

            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKey);

            byte[] bytes = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder(2 * bytes.length);

            for(byte b : bytes)
            {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch(Exception ex) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA512 for VNPAY", ex);
        }
    }
}