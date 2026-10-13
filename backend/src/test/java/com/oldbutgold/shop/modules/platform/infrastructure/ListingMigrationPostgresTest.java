package com.oldbutgold.shop.modules.platform.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="OGSHOP_PHASE1_TEST_DB_URL",matches=".+")
class ListingMigrationPostgresTest {
    @Test void upgradeFromV26PreservesLegacyRowsWithoutInventingFeeOrReward() {
        String url=System.getenv("OGSHOP_PHASE1_TEST_DB_URL");
        if(!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/ogshop_phase1_test")) throw new IllegalStateException("Dedicated phase1 DB required");
        String base=url.substring(0,url.lastIndexOf('/')+1),name="ogshop_phase1_upgrade_"+UUID.randomUUID().toString().replace("-","");
        var root=new JdbcTemplate(new DriverManagerDataSource(base+"postgres",System.getenv("OGSHOP_TEST_DB_USER"),System.getenv("OGSHOP_TEST_DB_PASSWORD")));
        root.execute("CREATE DATABASE "+name);
        try {
            var source=new DriverManagerDataSource(base+name,System.getenv("OGSHOP_TEST_DB_USER"),System.getenv("OGSHOP_TEST_DB_PASSWORD"));
            var old=Flyway.configure().dataSource(source).defaultSchema("public").locations("classpath:db/migration")
                .callbacks(new LegacyModerationHistoryCallback()).target("26").load();
            old.migrate();var jdbc=new JdbcTemplate(source);
            long user=jdbc.queryForObject("INSERT INTO og_compat.users(email,password_hash,full_name) VALUES('upgrade@example.invalid','FAKE','Legacy fixture') RETURNING user_id",Long.class);
            long category=jdbc.queryForObject("SELECT min(category_id) FROM og_compat.categories",Long.class);
            long product=new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(source)).execute(tx->{
                    long id=jdbc.queryForObject("INSERT INTO og_compat.products(seller_id,category_id,title,description,listed_price,condition) VALUES(?,?,'Legacy draft','Keep original data',80000,'GOOD') RETURNING product_id",Long.class,user,category);
                    jdbc.update("INSERT INTO og_compat.product_categories(product_id,category_id) VALUES(?,?)",id,category);
                    return id;
                });
            String before=jdbc.queryForObject("SELECT to_jsonb(p)::text FROM public.products p WHERE product_id=?",String.class,product);
            var current=Flyway.configure().dataSource(source).defaultSchema("public").locations("classpath:db/migration").callbacks(new LegacyModerationHistoryCallback()).load();
            current.migrate();current.validate();
            assertThat(jdbc.queryForObject("SELECT (to_jsonb(p)-'manual_listing_fee_required')::text FROM public.products p WHERE product_id=?",String.class,product)).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT manual_listing_fee_required FROM public.products WHERE product_id=?",Boolean.class,product)).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM og_compat.listing_fee_receipts",Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM og_compat.reward_ledger",Long.class)).isZero();
        } finally { if(name.matches("ogshop_phase1_upgrade_[0-9a-f]{32}")) root.execute("DROP DATABASE "+name+" WITH (FORCE)"); }
    }
}
