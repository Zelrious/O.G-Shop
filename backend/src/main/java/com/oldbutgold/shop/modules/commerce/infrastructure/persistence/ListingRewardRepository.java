package com.oldbutgold.shop.modules.commerce.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public class ListingRewardRepository {
    private final JdbcTemplate jdbc;
    public ListingRewardRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean grant(long sellerId,long productId) {
        // Different products can open concurrently. Serialize the once-per-user reward.
        jdbc.queryForObject("SELECT user_id FROM public.users WHERE user_id=? FOR UPDATE",Long.class,sellerId);
        if(jdbc.queryForObject("SELECT count(*) FROM og_compat.reward_ledger WHERE user_id=? AND entry_type='CREDIT_FIRST_LISTING'",Long.class,sellerId)>0) return false;
        if(jdbc.queryForObject("SELECT count(*) FROM og_compat.reward_accounts WHERE user_id=?",Long.class,sellerId)==0)
            jdbc.update("INSERT INTO og_compat.reward_accounts(user_id) VALUES(?)",sellerId);
        jdbc.update("""
            INSERT INTO og_compat.reward_ledger(entry_id,user_id,entry_type,points_delta,reason,command_key)
            VALUES(?,?,'CREDIT_FIRST_LISTING',10000,?,?)
            """,UUID.randomUUID(),sellerId,"FIRST_LISTING:"+productId,"FIRST_LISTING:"+sellerId);
        return true;
    }
}
