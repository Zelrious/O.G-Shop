package com.oldbutgold.shop.modules.commerce.application;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component @ConditionalOnProperty(name="app.vouchers.reminders-enabled",havingValue="true",matchIfMissing=true)
public class VoucherReminderDispatcher {
    private final VoucherReminderService service;private int offset;
    public VoucherReminderDispatcher(VoucherReminderService service){this.service=service;}
    @Scheduled(fixedDelayString="${app.vouchers.reminders-delay-ms:60000}") public void run(){try{int count=service.scan(offset);offset=count<500?0:offset+count;}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Voucher reminder deferred ({})",e.getClass().getSimpleName());}}
}
