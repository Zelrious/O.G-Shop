package com.oldbutgold.shop.modules.catalog.application;

public class SellerRequiredException extends RuntimeException {
    public SellerRequiredException(String message) {
        super(message);
    }

    public SellerRequiredException() {
        super("Bạn cần kích hoạt quyền Người bán để thực hiện thao tác này.");
    }
}
