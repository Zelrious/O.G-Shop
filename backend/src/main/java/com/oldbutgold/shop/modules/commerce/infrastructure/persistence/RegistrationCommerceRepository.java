package com.oldbutgold.shop.modules.commerce.infrastructure.persistence;

import com.oldbutgold.shop.modules.commerce.application.RegistrationCommerceFacade.Address;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public class RegistrationCommerceRepository {
    private final JdbcTemplate jdbc;
    public RegistrationCommerceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void creditRegistration(long userId) {
        jdbc.update("INSERT INTO og_compat.reward_accounts(user_id) VALUES(?)", userId);
        jdbc.update("""
                INSERT INTO og_compat.reward_ledger(entry_id,user_id,entry_type,points_delta,reason,command_key)
                VALUES(?,?,'CREDIT_REGISTRATION',3000,'UC22: verified email registration',?)
                """, UUID.randomUUID(), userId, "REGISTER_EMAIL:" + userId);
    }

    public long createAddress(long userId, String name, String phone, Address address, boolean defaultAddress) {
        return jdbc.queryForObject("""
                INSERT INTO og_compat.addresses(user_id,recipient_name,phone_number,province,district,ward,
                    detail_address,is_default) VALUES(?,?,?,?,?,?,?,?) RETURNING address_id
                """, Long.class, userId, name, phone, address.province().strip(), address.district().strip(),
                address.ward().strip(), address.detailAddress().strip(), defaultAddress);
    }
}
