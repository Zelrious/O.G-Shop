package com.oldbutgold.shop.modules.identity.application;
import java.util.UUID;
public interface GoogleIdentityPort {
    AuthFlowService.GoogleProfile verify(String credential,UUID nonce);
}
