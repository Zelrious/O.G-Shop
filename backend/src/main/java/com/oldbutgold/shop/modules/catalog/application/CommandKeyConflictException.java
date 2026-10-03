package com.oldbutgold.shop.modules.catalog.application;

public class CommandKeyConflictException extends RuntimeException {
    public CommandKeyConflictException(String message) {
        super(message);
    }
}
