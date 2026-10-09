package com.oldbutgold.shop.modules.platform.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Synthetic fixtures in an independently named local database. */
@EnabledIfEnvironmentVariable(named = "OGSHOP_EXPANSION_TEST_DB_URL", matches = ".+")
class RefreshSessionRemovalPostgresTest {
    private JdbcTemplate root;
    private DriverManagerDataSource ds;
    private String database;

    private DriverManagerDataSource source(String name) {
        String url = System.getenv("OGSHOP_EXPANSION_TEST_DB_URL");
        if (!url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/ogshop_[a-z0-9_]*test[a-z0-9_]*")) {
            throw new IllegalStateException("Use an explicit local test database URL");
        }
        return new DriverManagerDataSource(url.substring(0, url.lastIndexOf('/') + 1) + name,
                System.getenv("OGSHOP_TEST_DB_USER"), System.getenv("OGSHOP_TEST_DB_PASSWORD"));
    }

    private Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(ds).defaultSchema("public").schemas("public")
                .locations("classpath:db/migration").callbacks(new LegacyModerationHistoryCallback());
        if (target != null) config.target(target);
        return config.load();
    }

    private Map<String, Map<String, Object>> fingerprints(JdbcTemplate jdbc) {
        var result = new LinkedHashMap<String, Map<String, Object>>();
        var tables = jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public' "
                + "AND tablename NOT IN ('flyway_schema_history','refresh_sessions') ORDER BY tablename", String.class);
        for (String table : tables) {
            result.put(table, jdbc.queryForMap("SELECT count(*) AS rows,"
                    + "md5(coalesce(string_agg(md5(to_jsonb(t)::text),'' ORDER BY md5(to_jsonb(t)::text)),'')) AS fingerprint"
                    + " FROM public." + table + " t"));
        }
        return result;
    }

    @BeforeEach void setup() {
        database = "ogshop_refresh_removal_" + UUID.randomUUID().toString().replace("-", "");
        root = new JdbcTemplate(source("postgres"));
        root.execute("CREATE DATABASE " + database);
        ds = source(database);
        flyway("23").migrate();
    }

    @AfterEach void cleanup() {
        if (root != null && database != null && database.matches("ogshop_refresh_removal_[0-9a-f]{32}")) {
            root.execute("DROP DATABASE " + database);
        }
    }

    @Test void populatedUpgradeRemovesOnlyRefreshStorageAndPreservesAllOtherTables() {
        var jdbc = new JdbcTemplate(ds);
        long user = jdbc.queryForObject("INSERT INTO og_compat.users(email,password_hash,full_name)"
                + " VALUES('refresh-fixture@example.invalid','FAKE','Fixture') RETURNING user_id", Long.class);
        jdbc.update("INSERT INTO og_compat.refresh_sessions(session_id,user_id,family_id,token_digest,issued_at,expires_at)"
                + " VALUES(?,?,?, ?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '1 day')",
                UUID.randomUUID(), user, UUID.randomUUID(), "a".repeat(64));
        var before = fingerprints(jdbc);
        var checksums = jdbc.queryForList("SELECT version,checksum FROM public.flyway_schema_history ORDER BY installed_rank");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.refresh_sessions", Long.class)).isEqualTo(1);

        flyway(null).migrate();
        flyway(null).validate();

        assertThat(jdbc.queryForObject("SELECT to_regclass('public.refresh_sessions')::text", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT to_regclass('og_compat.refresh_sessions')::text", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_tables WHERE schemaname='public' "
                + "AND tablename<>'flyway_schema_history'", Long.class)).isEqualTo(47);
        assertThat(fingerprints(jdbc)).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT version,checksum FROM public.flyway_schema_history "
                + "WHERE version::int<=23 ORDER BY installed_rank")).isEqualTo(checksums);
        jdbc.update("INSERT INTO og_compat.auth_challenges(challenge_id,user_id,subject_key,purpose,code_digest,expires_at,next_send_at,command_key)"
                + " VALUES(?,?,?,'REGISTER_EMAIL',?,CURRENT_TIMESTAMP+INTERVAL '5 minutes',CURRENT_TIMESTAMP,?)",
                UUID.randomUUID(), user, "refresh-fixture@example.invalid", "b".repeat(64), UUID.randomUUID().toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.auth_challenges", Long.class)).isEqualTo(1);
    }
}
