package com.oldbutgold.shop.modules.identity.application;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Refresh session is invalid");
    }
}
