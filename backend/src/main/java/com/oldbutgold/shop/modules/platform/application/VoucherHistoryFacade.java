package com.oldbutgold.shop.modules.platform.application;

import java.util.*;

/** Commerce command receipts and audit history, owned and persisted by Platform. */
public interface VoucherHistoryFacade {
    record Receipt(long actorId,Map<String,Object> request,Map<String,Object> result) {}
    Optional<Receipt> find(String key);
    void record(long actor,long voucher,String action,String key,Map<String,Object> request,Map<String,Object> result);
}
