package com.oldbutgold.shop.modules.identity.application;

public class DuplicatePhoneException extends RuntimeException {
    public DuplicatePhoneException() {
        super("Số điện thoại này đã được sử dụng bởi một tài khoản khác.");
    }

    public DuplicatePhoneException(String message) {
        super(message);
    }
}

