package com.oldbutgold.shop.modules.payment.application;

public class PaymentStateConflictException extends IllegalStateException {
    public PaymentStateConflictException(String message) { super(message); }
}
