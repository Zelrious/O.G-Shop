package com.oldbutgold.shop.modules.catalog.application;

public class ProductStateConflictException extends RuntimeException {
    public ProductStateConflictException(String message) {
        super(message);
    }

    public ProductStateConflictException() {
        super("Không thể thực hiện thao tác do trạng thái sản phẩm không hợp lệ.");
    }
}
