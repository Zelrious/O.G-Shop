package com.oldbutgold.shop.modules.identity.application;

public class RegistrationException extends RuntimeException {
    private final String code;
    private final int status;
    private final long retryAfter;

    public RegistrationException(String code, String message, int status) {
        this(code, message, status, 0);
    }

    public RegistrationException(String code, String message, int status, long retryAfter) {
        super(message);
        this.code = code;
        this.status = status;
        this.retryAfter = retryAfter;
    }

    public String code() { return code; }
    public int status() { return status; }
    public long retryAfter() { return retryAfter; }
}
