package com.oldbutgold.shop.modules.identity.application;

import java.time.Duration;

public interface RegistrationEmailPort {
    void sendCode(String email, String code, Duration lifetime);
    default void sendAuthCode(String email, String code, Duration lifetime, String purpose) {
        sendCode(email,code,lifetime);
    }
}
