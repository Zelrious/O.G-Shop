package com.oldbutgold.shop.modules.platform.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "OGSHOP_UPGRADE_TEST_DB_URL", matches = ".+")
class ModerationMigrationUpgradeTest {
    @Test void preservesPreV10HistoryWithoutInventingContentProof() {
        var ds = new DriverManagerDataSource(System.getenv("OGSHOP_UPGRADE_TEST_DB_URL"),
                System.getenv("OGSHOP_TEST_DB_USER"), System.getenv("OGSHOP_TEST_DB_PASSWORD"));
        var jdbc = new JdbcTemplate(ds);
        String schema = "task59_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).target("9")
                    .callbacks(new LegacyModerationHistoryCallback()).load().migrate();
            long user = jdbc.queryForObject("INSERT INTO " + schema + ".users(email,password_hash,full_name) VALUES('upgrade@example.test','fixture','Upgrade') RETURNING user_id", Long.class);
            long category = jdbc.queryForObject("SELECT category_id FROM " + schema + ".categories LIMIT 1", Long.class);
            var transaction = new org.springframework.transaction.support.TransactionTemplate(
                    new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
            long product = transaction.execute(status -> {
                long id = jdbc.queryForObject("INSERT INTO " + schema + ".products(seller_id,category_id,title,description,listed_price,condition,status) VALUES(?,?,'Legacy','Fixture',100000,'GOOD','HIDDEN') RETURNING product_id", Long.class, user, category);
                jdbc.update("INSERT INTO " + schema + ".product_categories(product_id,category_id) VALUES(?,?)", id, category);
                return id;
            });
            long decision = jdbc.queryForObject("INSERT INTO " + schema + ".product_moderation_decisions(product_id,reviewer_id,decision,reason,product_version,command_key) VALUES(?,?,'APPROVED','Original approval note',1,'legacy-note') RETURNING decision_id", Long.class, product, user);
            Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).target("21")
                    .callbacks(new LegacyModerationHistoryCallback()).load().migrate();
            assertThat(jdbc.queryForObject("SELECT original_record->>'reason' FROM " + schema + ".product_moderation_legacy_history WHERE decision_id=?", String.class, decision)).isEqualTo("Original approval note");
            assertThat(jdbc.queryForObject("SELECT decision_content_revision FROM " + schema + ".product_moderation_decisions WHERE decision_id=?", Long.class, decision)).isNull();
            assertThatThrownBy(() -> jdbc.update("DELETE FROM " + schema + ".product_moderation_legacy_history WHERE decision_id=?", decision))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class).hasMessageContaining("append-only");
        } finally {
            if (!schema.matches("task59_upgrade_[a-f0-9]{32}")) throw new IllegalStateException("Unsafe test schema");
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }
}
