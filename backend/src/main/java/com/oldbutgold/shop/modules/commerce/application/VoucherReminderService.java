package com.oldbutgold.shop.modules.commerce.application;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.VoucherWalletRepository;
import com.oldbutgold.shop.modules.platform.application.PlatformNotificationFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class VoucherReminderService {
    private final VoucherWalletRepository repo;private final PlatformNotificationFacade notices;private final Clock clock;
    public VoucherReminderService(VoucherWalletRepository repo,PlatformNotificationFacade notices,Clock clock){this.repo=repo;this.notices=notices;this.clock=clock;}
    @Transactional public int scan(int offset){Instant now=clock.instant();var grants=repo.expiring(now.plus(Duration.ofHours(24)),offset);for(var g:grants){boolean expired=!now.isBefore(g.expiresAt());String type=expired?"VOUCHER_EXPIRED":"VOUCHER_EXPIRING";notices.enqueue("voucher-reminder:"+type+":"+g.id(),g.campaign().id(),List.of(new PlatformNotificationFacade.Notice(g.userId(),type,expired?"Voucher đã hết hạn":"Voucher sắp hết hạn",g.campaign().code()+" · Hạn dùng: "+g.expiresAt(),PlatformNotificationFacade.Destination.VOUCHER_WALLET,g.campaign().id(),0)));}return grants.size();}
}
