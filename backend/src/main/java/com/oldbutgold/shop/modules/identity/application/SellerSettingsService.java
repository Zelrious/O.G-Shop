package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AddressRepository;
import com.oldbutgold.shop.modules.identity.api.SellerSettingsDtos.*;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.SellerVerificationRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class SellerSettingsService {
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final SellerVerificationRepository sellerVerificationRepository;
    private final EkycRepository ekycRepository;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SellerSettingsService(
            UserRepository userRepository,
            AddressRepository addressRepository,
            SellerVerificationRepository sellerVerificationRepository,
            EkycRepository ekycRepository,
            JdbcTemplate jdbc,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.sellerVerificationRepository = sellerVerificationRepository;
        this.ekycRepository = ekycRepository;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SellerSettingsResponse getSettings(long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));

        String storeName = user.getFullName() != null ? user.getFullName() + "'s Shop" : "Cửa hàng O.G";
        Long pickupAddressId = null;
        Long returnAddressId = null;

        String sql = """
            SELECT store_name, pickup_address_id, return_address_id
            FROM public.seller_profiles
            WHERE user_id = ? AND record_type = 'seller_profiles'
            ORDER BY seller_profile_id DESC LIMIT 1
            """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, userId);
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            if (row.get("store_name") != null) {
                storeName = (String) row.get("store_name");
            }
            if (row.get("pickup_address_id") instanceof Number n) {
                pickupAddressId = n.longValue();
            }
            if (row.get("return_address_id") instanceof Number n) {
                returnAddressId = n.longValue();
            }
        }

        boolean isVerified = ekycRepository.hasVerifiedProfile(userId);
        String ekycState = ekycRepository.findLatestProfile(userId).map(p -> p.state()).orElse("NOT_STARTED");

        return new SellerSettingsResponse(
                user.getId(),
                storeName,
                user.getPhoneNumber(),
                pickupAddressId,
                returnAddressId,
                user.getBankName(),
                maskAccountNumber(user.getBankAccountNumber()),
                user.getBankAccountHolder(),
                isVerified,
                ekycState
        );
    }

    public SellerSettingsResponse updateSettings(long userId, UpdateSellerSettingsRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));

        if (request.pickupAddressId() != null) {
            addressRepository.findByIdAndUserIdAndDeletedAtIsNull(request.pickupAddressId(), userId)
                    .orElseThrow(() -> new IllegalArgumentException("Địa chỉ lấy hàng không hợp lệ hoặc không thuộc tài khoản."));
        }

        if (request.returnAddressId() != null) {
            addressRepository.findByIdAndUserIdAndDeletedAtIsNull(request.returnAddressId(), userId)
                    .orElseThrow(() -> new IllegalArgumentException("Địa chỉ trả hàng không hợp lệ hoặc không thuộc tài khoản."));
        }

        Instant now = clock.instant();
        String checkSql = """
            SELECT seller_profile_id, state, pickup_address_id
            FROM public.seller_profiles
            WHERE user_id = ? AND record_type = 'seller_profiles'
            ORDER BY seller_profile_id DESC LIMIT 1
            """;
        List<Map<String, Object>> rows = jdbc.queryForList(checkSql, userId);

        if (rows.isEmpty()) {
            String insertSql = """
                INSERT INTO public.seller_profiles(user_id, state, store_name, contact_phone, pickup_address_id, return_address_id, bank_snapshot, updated_at, record_type)
                VALUES(?, 'PENDING_EKYC', ?, ?, ?, ?, '{}'::jsonb, ?, 'seller_profiles')
                """;
            jdbc.update(insertSql, userId, request.storeName(), user.getPhoneNumber(), request.pickupAddressId(), request.returnAddressId(), Timestamp.from(now));
        } else {
            long profileId = ((Number) rows.get(0).get("seller_profile_id")).longValue();
            String updateSql = """
                UPDATE public.seller_profiles
                SET store_name = COALESCE(?, store_name),
                    pickup_address_id = COALESCE(?, pickup_address_id),
                    return_address_id = COALESCE(?, return_address_id),
                    contact_phone = COALESCE(?, contact_phone),
                    updated_at = ?
                WHERE seller_profile_id = ?
                """;
            jdbc.update(updateSql, request.storeName(), request.pickupAddressId(), request.returnAddressId(), user.getPhoneNumber(), Timestamp.from(now), profileId);
        }

        return getSettings(userId);
    }

    private static String maskAccountNumber(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        if (trimmed.length() <= 4) return "****";
        return "*".repeat(trimmed.length() - 4) + trimmed.substring(trimmed.length() - 4);
    }
}
