package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RoleEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RoleRepository;
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
    private EkycRepository ekycRepository;
    private Clock clock;
    private RoleEntity buyerRole;
    private RoleEntity sellerRole;
    private UserEntity user;
    private Instant fixedInstant;

    @BeforeEach
    void setUp() throws Exception {
        users = mock(UserRepository.class);
        roles = mock(RoleRepository.class);
        ekycRepository = mock(EkycRepository.class);
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
    void activateSuccessWhenUserHasVerifiedEkyc() throws Exception {
        SellerVerificationProperties properties = new SellerVerificationProperties("MVP_BYPASS");
        SellerActivationService service = new SellerActivationService(users, roles, ekycRepository, properties, clock);

        when(ekycRepository.hasVerifiedProfile(1L)).thenReturn(true);

        var result = service.activate(1L);

        assertThat(result.verificationId()).isEqualTo(0L);
        assertThat(result.status()).isEqualTo("VERIFIED");
        assertThat(result.verificationMethod()).isEqualTo("AI_EKYC");
        assertThat(user.getRoles()).extracting(RoleEntity::getRoleName).contains("BUYER", "SELLER");
    }

    @Test
    void throwsExceptionWhenUserHasNoVerifiedEkyc() {
        SellerVerificationProperties properties = new SellerVerificationProperties("MVP_BYPASS");
        SellerActivationService service = new SellerActivationService(users, roles, ekycRepository, properties, clock);

        when(ekycRepository.hasVerifiedProfile(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.activate(1L))
                .isInstanceOf(SellerActivationDisabledException.class)
                .hasMessageContaining("phải hoàn tất xác thực eKYC và được KTV duyệt");

        verify(users, never()).findById(any());
    }

    @Test
    void idempotentWhenCalledTwiceGrantsSellerRoleOnlyOnce() throws Exception {
        SellerVerificationProperties properties = new SellerVerificationProperties("MVP_BYPASS");
        SellerActivationService service = new SellerActivationService(users, roles, ekycRepository, properties, clock);

        when(ekycRepository.hasVerifiedProfile(1L)).thenReturn(true);

        // First call
        var result1 = service.activate(1L);
        assertThat(result1.verificationMethod()).isEqualTo("AI_EKYC");
        assertThat(user.getRoles()).extracting(RoleEntity::getRoleName).contains("SELLER");

        // Second call - role already granted, should still work
        var result2 = service.activate(1L);
        assertThat(result2.verificationMethod()).isEqualTo("AI_EKYC");
    }

    @Test
    void throwsExceptionAndDoesNotMutateWhenMvpBypassDisabled() {
        SellerVerificationProperties properties = new SellerVerificationProperties("DISABLED");
        SellerActivationService service = new SellerActivationService(users, roles, ekycRepository, properties, clock);

        assertThatThrownBy(() -> service.activate(1L))
                .isInstanceOf(SellerActivationDisabledException.class)
                .hasMessageContaining("Chế độ kích hoạt Seller tức thì hiện không khả dụng");

        verify(users, never()).findById(any());
    }

    @Test
    void throwsBadCredentialsWhenUserNotFound() {
        SellerVerificationProperties properties = new SellerVerificationProperties("MVP_BYPASS");
        SellerActivationService service = new SellerActivationService(users, roles, ekycRepository, properties, clock);

        when(ekycRepository.hasVerifiedProfile(999L)).thenReturn(true);
        when(users.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activate(999L))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Account is unavailable");
    }

    @Test
    void throwsExceptionWhenIdentityWorkflowIsUC83() {
        SellerVerificationProperties properties = new SellerVerificationProperties("MVP_BYPASS");
        SellerActivationService service = new SellerActivationService(users, roles, ekycRepository, properties, clock);

        when(ekycRepository.hasVerifiedProfile(1L)).thenReturn(true);
        // User with UC83 workflow
        user.setIdentityWorkflow("UC83");

        assertThatThrownBy(() -> service.activate(1L))
                .isInstanceOf(SellerActivationDisabledException.class)
                .hasMessageContaining("Hồ sơ Người bán cần hoàn tất eKYC và được KTV duyệt");
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
