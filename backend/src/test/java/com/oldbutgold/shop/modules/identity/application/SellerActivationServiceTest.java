package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RoleEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RoleRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.SellerVerificationEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.SellerVerificationRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserRepository;
import com.oldbutgold.shop.shared.config.SellerVerificationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SellerActivationServiceTest {

    private UserRepository users;
    private RoleRepository roles;
    private SellerVerificationRepository verifications;
    private Clock clock;
    private RoleEntity buyerRole;
    private RoleEntity sellerRole;
    private UserEntity user;
    private Instant fixedInstant;

    @BeforeEach
    void setUp() throws Exception {
        users = mock(UserRepository.class);
        roles = mock(RoleRepository.class);
        verifications = mock(SellerVerificationRepository.class);
        fixedInstant = Instant.parse("2026-09-20T10:00:00Z");
        clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        buyerRole = createRole("BUYER");
        sellerRole = createRole("SELLER");
        when(roles.findByRoleName("SELLER")).thenReturn(Optional.of(sellerRole));

        user = new UserEntity("buyer@ogshop.vn", "{noop}secret", "Nguyen Van A", "0901234567", fixedInstant);
        Field idField = UserEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, 1L);
        user.getRoles().add(buyerRole);

        when(users.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void activateSuccessWhenMvpBypassEnabled() throws Exception {
        SellerVerificationProperties properties = new SellerVerificationProperties("MVP_BYPASS");
        SellerActivationService service = new SellerActivationService(users, roles, verifications, properties, clock);

        when(verifications.findFirstByUserIdAndStatus(1L, "VERIFIED")).thenReturn(Optional.empty());
        when(verifications.findFirstByUserIdAndStatus(1L, "PENDING")).thenReturn(Optional.empty());

        SellerVerificationEntity savedEntity = new SellerVerificationEntity(user, "MVP_BYPASS", fixedInstant);
        Field verificationIdField = SellerVerificationEntity.class.getDeclaredField("id");
        verificationIdField.setAccessible(true);
        verificationIdField.set(savedEntity, 101L);

        when(verifications.save(any(SellerVerificationEntity.class))).thenReturn(savedEntity);

        var result = service.activate(1L);

        assertThat(result.verificationId()).isEqualTo(101L);
        assertThat(result.status()).isEqualTo("VERIFIED");
        assertThat(result.verificationMethod()).isEqualTo("MVP_BYPASS");
        assertThat(user.getRoles()).extracting(RoleEntity::getRoleName).contains("BUYER", "SELLER");
        verify(verifications).save(any(SellerVerificationEntity.class));
    }

    @Test
    void idempotentWhenCalledTwiceReturnsSameVerificationWithoutDuplicate() throws Exception {
        SellerVerificationProperties properties = new SellerVerificationProperties("MVP_BYPASS");
        SellerActivationService service = new SellerActivationService(users, roles, verifications, properties, clock);

        SellerVerificationEntity existingEntity = new SellerVerificationEntity(user, "MVP_BYPASS", fixedInstant);
        Field verificationIdField = SellerVerificationEntity.class.getDeclaredField("id");
        verificationIdField.setAccessible(true);
        verificationIdField.set(existingEntity, 202L);

        user.getRoles().add(sellerRole);
        when(verifications.findFirstByUserIdAndStatus(1L, "VERIFIED")).thenReturn(Optional.of(existingEntity));

        var result = service.activate(1L);

        assertThat(result.verificationId()).isEqualTo(202L);
        assertThat(result.status()).isEqualTo("VERIFIED");
        assertThat(result.verificationMethod()).isEqualTo("MVP_BYPASS");
        verify(verifications, never()).save(any(SellerVerificationEntity.class));
    }

    @Test
    void repairsMissingSellerRoleWhenAlreadyVerified() throws Exception {
        SellerVerificationProperties properties = new SellerVerificationProperties("MVP_BYPASS");
        SellerActivationService service = new SellerActivationService(users, roles, verifications, properties, clock);

        SellerVerificationEntity existingEntity = new SellerVerificationEntity(user, "AI_EKYC", fixedInstant);
        Field verificationIdField = SellerVerificationEntity.class.getDeclaredField("id");
        verificationIdField.setAccessible(true);
        verificationIdField.set(existingEntity, 303L);

        // User only has BUYER role, missing SELLER
        when(verifications.findFirstByUserIdAndStatus(1L, "VERIFIED")).thenReturn(Optional.of(existingEntity));

        var result = service.activate(1L);

        assertThat(result.verificationId()).isEqualTo(303L);
        assertThat(result.status()).isEqualTo("VERIFIED");
        assertThat(result.verificationMethod()).isEqualTo("AI_EKYC");
        assertThat(user.getRoles()).extracting(RoleEntity::getRoleName).contains("SELLER");
        verify(verifications, never()).save(any(SellerVerificationEntity.class));
    }

    @Test
    void upgradesPendingVerificationToMvpBypass() throws Exception {
        SellerVerificationProperties properties = new SellerVerificationProperties("MVP_BYPASS");
        SellerActivationService service = new SellerActivationService(users, roles, verifications, properties, clock);

        SellerVerificationEntity pendingEntity = new SellerVerificationEntity(user, "MANUAL_ID_DOCUMENT", fixedInstant);
        Field verificationIdField = SellerVerificationEntity.class.getDeclaredField("id");
        verificationIdField.setAccessible(true);
        verificationIdField.set(pendingEntity, 404L);
        Field statusField = SellerVerificationEntity.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(pendingEntity, "PENDING");

        when(verifications.findFirstByUserIdAndStatus(1L, "VERIFIED")).thenReturn(Optional.empty());
        when(verifications.findFirstByUserIdAndStatus(1L, "PENDING")).thenReturn(Optional.of(pendingEntity));

        var result = service.activate(1L);

        assertThat(result.verificationId()).isEqualTo(404L);
        assertThat(result.status()).isEqualTo("VERIFIED");
        assertThat(result.verificationMethod()).isEqualTo("MVP_BYPASS");
        assertThat(user.getRoles()).extracting(RoleEntity::getRoleName).contains("SELLER");
        verify(verifications, never()).save(any(SellerVerificationEntity.class));
    }

    @Test
    void throwsExceptionAndDoesNotMutateWhenMvpBypassDisabled() {
        SellerVerificationProperties properties = new SellerVerificationProperties("DISABLED");
        SellerActivationService service = new SellerActivationService(users, roles, verifications, properties, clock);

        assertThatThrownBy(() -> service.activate(1L))
                .isInstanceOf(SellerActivationDisabledException.class)
                .hasMessageContaining("Chế độ kích hoạt Seller tức thì hiện không khả dụng");

        verify(users, never()).findById(any());
        verify(verifications, never()).save(any());
    }

    @Test
    void throwsBadCredentialsWhenUserNotFound() {
        SellerVerificationProperties properties = new SellerVerificationProperties("MVP_BYPASS");
        SellerActivationService service = new SellerActivationService(users, roles, verifications, properties, clock);

        when(users.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activate(999L))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Account is unavailable");
    }

    private static RoleEntity createRole(String name) throws Exception {
        var constructor = RoleEntity.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        RoleEntity role = constructor.newInstance();
        Field roleName = RoleEntity.class.getDeclaredField("roleName");
        roleName.setAccessible(true);
        roleName.set(role, name);
        return role;
    }
}
