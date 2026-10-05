package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentIpnRawReceiptRepository extends JpaRepository<PaymentIpnRawReceiptEntity, Long> {
}
