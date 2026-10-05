package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.identity.infrastructure.persistence.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.util.Optional;
import java.util.Set;

class DefaultIdentityCatalogFacadeTest {
    @ParameterizedTest @ValueSource(strings = {"ADMIN", "KTV", "BUYER", "SELLER"})
    void checksCurrentDatabaseRoleAndActiveStatus(String name) {
        var users = mock(UserRepository.class);
        var facade = new DefaultIdentityCatalogFacade(users, mock(SellerVerificationRepository.class));
        var user = mock(UserEntity.class); var role = mock(RoleEntity.class);
        when(role.getRoleName()).thenReturn(name); when(user.getRoles()).thenReturn(Set.of(role));
        when(users.findById(7L)).thenReturn(Optional.of(user)); when(user.getStatus()).thenReturn("ACTIVE");
        assertThat(facade.isModeratorActive(7)).isEqualTo(name.equals("ADMIN") || name.equals("KTV"));
        when(user.getStatus()).thenReturn("LOCKED");
        assertThat(facade.isModeratorActive(7)).isFalse();
        assertThat(facade.isModeratorActive(999)).isFalse();
    }
}
