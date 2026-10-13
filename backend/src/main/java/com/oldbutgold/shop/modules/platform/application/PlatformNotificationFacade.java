package com.oldbutgold.shop.modules.platform.application;

import java.util.List;

/** Durable, private in-app notifications. No network work in the command transaction. */
public interface PlatformNotificationFacade {
    enum Destination { SELLER_LISTING, KTV_MODERATION, ACCOUNT_SELF, KTV_ACCOUNT, CONVERSATION, VOUCHER_WALLET, ADMIN_VOUCHERS, KTV_VOUCHERS }
    record Notice(long recipientId, String type, String title, String content,
                  Destination destination, long productId, long revision) {}
    void enqueue(String eventKey, long productId, List<Notice> notices);
}
