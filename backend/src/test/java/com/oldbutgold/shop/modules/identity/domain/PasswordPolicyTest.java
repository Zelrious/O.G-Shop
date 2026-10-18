package com.oldbutgold.shop.modules.identity.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PasswordPolicyTest {
    @Test void enforcesNewPasswordLengthAndBcryptUtf8Limit() {
        assertThat(PasswordPolicy.acceptsNewPassword("short6")).isFalse();
        assertThat(PasswordPolicy.acceptsNewPassword("        ")).isFalse();
        assertThat(PasswordPolicy.acceptsNewPassword("password")).isTrue();
        assertThat(PasswordPolicy.acceptsNewPassword("a".repeat(72))).isTrue();
        assertThat(PasswordPolicy.acceptsNewPassword("a".repeat(73))).isFalse();
        assertThat(PasswordPolicy.acceptsNewPassword("ắ".repeat(24))).isTrue();
        assertThat(PasswordPolicy.acceptsNewPassword("ắ".repeat(25))).isFalse();
        assertThat(PasswordPolicy.acceptsNewPassword(null)).isFalse();
    }
}
