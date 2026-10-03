package com.oldbutgold.shop.modules.identity.application;

public class AvatarStorageUnavailableException extends RuntimeException {
    public AvatarStorageUnavailableException() {
        super("Không thể lưu ảnh đại diện lúc này. Vui lòng thử lại.");
    }
}
