package com.oldbutgold.shop.modules.catalog.application;

public class ProductVersionConflictException extends RuntimeException {
    public ProductVersionConflictException(String message) {
        super(message);
    }

    public ProductVersionConflictException() {
        super("Tin đăng đã được cập nhật bởi một phiên làm việc khác. Vui lòng tải lại trang.");
    }
}
