package com.oldbutgold.shop.modules.identity.application;

public class AccountRestrictedException extends RuntimeException {
    private final String status;
    public AccountRestrictedException(String status,String reason) { super(reason);this.status=status; }
    public String accountStatus() { return status; }
}
