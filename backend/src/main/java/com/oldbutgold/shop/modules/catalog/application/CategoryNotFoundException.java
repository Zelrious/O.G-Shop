package com.oldbutgold.shop.modules.catalog.application;

public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException(String message) {
        super(message);
    }

    public CategoryNotFoundException() {
        super("Không tìm thấy danh mục yêu cầu.");
    }
}
