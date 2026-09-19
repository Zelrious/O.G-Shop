package com.oldbutgold.shop.modules.identity.application;

public class EkycUnavailableException extends RuntimeException {
    public EkycUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public EkycUnavailableException(String message) {
        super(message);
    }
}
