package com.oldbutgold.shop.modules.platform.application;
public interface InteractionAuditFacade {
    void blockChanged(long seller, long buyer, boolean before, boolean after, String reason);
}
