package com.oldbutgold.shop.modules.identity.domain;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static boolean acceptsNewPassword(String value) {
        return value != null && !value.isBlank() && value.length() >= 8
                && value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public static void requireNewPassword(String value) {
        if (!acceptsNewPassword(value)) {
            throw new IllegalArgumentException("Mật khẩu phải có ít nhất 8 ký tự và không vượt quá 72 byte UTF-8.");
        }
    }
}
