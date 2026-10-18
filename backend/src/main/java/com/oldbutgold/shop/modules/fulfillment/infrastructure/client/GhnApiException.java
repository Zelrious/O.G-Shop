package com.oldbutgold.shop.modules.fulfillment.infrastructure.client;

public class GhnApiException extends RuntimeException {

    public GhnApiException(String message) {
        super(message);
    }

    public GhnApiException(String message, Throwable cause) {
        super(message, cause);
    }
}