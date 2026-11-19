package com.oldbutgold.shop.modules.trustsafety.application;

import org.springframework.http.HttpStatus;

/**
 * Standard business exception for Trust & Safety returns module.
 * Directly maps to ApiError responses with explicit HTTP status and business error codes.
 */
public class TrustSafetyBusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public TrustSafetyBusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}

