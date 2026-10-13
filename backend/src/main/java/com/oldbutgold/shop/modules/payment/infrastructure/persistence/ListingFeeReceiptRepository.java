package com.oldbutgold.shop.modules.payment.infrastructure.persistence;

import com.oldbutgold.shop.modules.catalog.application.CommandKeyConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.UUID;

@Repository
public class ListingFeeReceiptRepository {
    private final JdbcTemplate jdbc;
    public ListingFeeReceiptRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean isReplay(UUID chargeId,BigDecimal amount,String key) {
        var receipts=jdbc.query("SELECT charge_id,amount FROM og_compat.listing_fee_receipts WHERE provider='LISTING_MOCK' AND provider_transaction_id=?",
            (rs,row)->new Receipt(rs.getObject(1,UUID.class),rs.getBigDecimal(2)),key);
        if(receipts.isEmpty()) return false;
        Receipt r=receipts.getFirst();
        if(!r.chargeId().equals(chargeId)||r.amount().compareTo(amount)!=0)
            throw new CommandKeyConflictException("Mã thanh toán đã được dùng cho khoản phí khác.");
        return true;
    }
    public void confirm(UUID chargeId,BigDecimal amount,String key) {
        jdbc.update("""
            INSERT INTO og_compat.listing_fee_receipts(receipt_id,charge_id,provider,provider_transaction_id,amount)
            VALUES(?,?,'LISTING_MOCK',?,?)
            """,UUID.randomUUID(),chargeId,key,amount);
    }
    private record Receipt(UUID chargeId,BigDecimal amount) {}
}
