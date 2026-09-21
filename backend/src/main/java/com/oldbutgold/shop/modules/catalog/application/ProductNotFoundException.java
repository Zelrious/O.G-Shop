package com.oldbutgold.shop.modules.catalog.application;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String message) {
        super(message);
    }

    public ProductNotFoundException() {
        super("Không tìm thấy sản phẩm yêu cầu.");
    }
}
