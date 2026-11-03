package com.oldbutgold.shop;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("cloud")
@Disabled("Manual verification of Supabase Cloud DB connection")
class CloudSmokeTest {

    static {
        OgShopApplication.loadDotEnv();
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testCloudConnection() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM roles", Integer.class);
        assertThat(count).isEqualTo(4);
        System.out.println(">>> SPRING BOOT CLOUD DB VERIFICATION SUCCESSFUL! Roles count: " + count);
    }
}
