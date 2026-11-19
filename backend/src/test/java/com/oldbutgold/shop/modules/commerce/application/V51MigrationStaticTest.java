package com.oldbutgold.shop.modules.commerce.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static verification test for V51 migration (renumbered from V47: UC83 Group Payment and Coin Remediation).
 * Offline test: does not require running database.
 */
class V51MigrationStaticTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");
    private static final Path SEED_DIR = Path.of("src/main/resources/db/seed");

    private static final String ANCHOR_INSERT = "payment_due_at,record_type) SELECT r.group_id,r.buyer_id,r.command_key,r.request_digest,r.address_snapshot,r.delivery_method,r.carrier,r.payment_method,r.expected_total,r.state,r.created_at,r.payment_due_at,''checkout_groups'' FROM jsonb_populate_record(NULL::og_compat.checkout_groups,$1) r";
    private static final String ANCHOR_UPDATE = "payment_due_at=r.payment_due_at FROM jsonb_populate_record(NULL::og_compat.checkout_groups,$1) r,jsonb_populate_record(NULL::og_compat.checkout_groups,$2) old_r WHERE t.group_id=old_r.group_id";
    private static final String ANCHOR_BUILD_OBJ = "''payment_due_at'',to_jsonb(t)->''payment_due_at'')";
    private static final String ANCHOR_RESULT = "'payment_due_at',stored->'payment_due_at');";

    private static final String REPLACE_INSERT = "payment_due_at,reward_policy_id,record_type) SELECT r.group_id,r.buyer_id,r.command_key,r.request_digest,r.address_snapshot,r.delivery_method,r.carrier,r.payment_method,r.expected_total,r.state,r.created_at,r.payment_due_at,r.reward_policy_id,''checkout_groups'' FROM jsonb_populate_record(NULL::og_compat.checkout_groups,$1) r";
    private static final String REPLACE_UPDATE = "payment_due_at=r.payment_due_at,reward_policy_id=r.reward_policy_id FROM jsonb_populate_record(NULL::og_compat.checkout_groups,$1) r,jsonb_populate_record(NULL::og_compat.checkout_groups,$2) old_r WHERE t.group_id=old_r.group_id";
    private static final String REPLACE_BUILD_OBJ = "''payment_due_at'',to_jsonb(t)->''payment_due_at'',''reward_policy_id'',to_jsonb(t)->''reward_policy_id'')";
    private static final String REPLACE_RESULT = "'payment_due_at',stored->'payment_due_at','reward_policy_id',stored->'reward_policy_id');";

    @Test
    @DisplayName("V51 migration and seed files exist")
    void testV51FilesExist() throws IOException {
        Path v51Path = MIGRATION_DIR.resolve("V51__uc83_group_payment_and_coin_remediation.sql");
        assertThat(Files.exists(v51Path)).as("V51 migration file must exist").isTrue();

        Path seedPath = SEED_DIR.resolve("R__provision_canonical_reward_policy.sql");
        assertThat(Files.exists(seedPath)).as("Repeatable seed script must exist").isTrue();

        String seedContent = Files.readString(seedPath);
        assertThat(seedContent)
                .as("Seed script must not auto-select admin via LIMIT 1")
                .doesNotContain("ORDER BY u.user_id ASC LIMIT 1")
                .doesNotContain("ORDER BY user_id ASC LIMIT 1");
        assertThat(seedContent)
                .as("Seed script must read from deployment configuration parameter app.reward_policy_admin_id")
                .contains("app.reward_policy_admin_id");
        assertThat(seedContent)
                .as("Seed script must fail-closed when admin configuration is missing")
                .contains("Missing explicit deployment configuration for Admin ID");
    }

    @Test
    @DisplayName("V51 SQL migration file contains all 4 exact anchors and all 4 exact replacements")
    void testV51AnchorsAndReplacementsPresent() throws IOException {
        Path v51Path = MIGRATION_DIR.resolve("V51__uc83_group_payment_and_coin_remediation.sql");
        String v51Content = Files.readString(v51Path);

        assertThat(v51Content).contains(ANCHOR_INSERT);
        assertThat(v51Content).contains(ANCHOR_UPDATE);
        assertThat(v51Content).contains(ANCHOR_BUILD_OBJ);
        assertThat(v51Content).contains(ANCHOR_RESULT);

        assertThat(v51Content).contains(REPLACE_INSERT);
        assertThat(v51Content).contains(REPLACE_UPDATE);
        assertThat(v51Content).contains(REPLACE_BUILD_OBJ);
        assertThat(v51Content).contains(REPLACE_RESULT);
    }

    @Test
    @DisplayName("V47 anchors match V22 baseline checkout_groups branch with exact occurrences")
    void testAnchorsMatchV22CheckoutGroupsBranch() throws IOException {
        Path v22Path = MIGRATION_DIR.resolve("V22__consolidate_database_to_48_tables.sql");
        String v22Content = Files.readString(v22Path);
        String writerDef = extractOg70WriteView(v22Content);

        int cgStart = writerDef.indexOf("WHEN 'checkout_groups' THEN");
        int cgEnd = writerDef.indexOf("WHEN 'orders' THEN", cgStart);
        assertThat(cgStart).isGreaterThan(-1);
        assertThat(cgEnd).isGreaterThan(cgStart);
        String cgBranch = writerDef.substring(cgStart, cgEnd);

        assertThat(countOccurrences(cgBranch, ANCHOR_INSERT)).isEqualTo(1);
        assertThat(countOccurrences(cgBranch, ANCHOR_UPDATE)).isEqualTo(1);
        assertThat(countOccurrences(cgBranch, ANCHOR_BUILD_OBJ)).isEqualTo(2);
        assertThat(countOccurrences(cgBranch, ANCHOR_RESULT)).isEqualTo(1);
    }

    private static String extractOg70WriteView(String sqlContent) {
        String startMarker = "CREATE FUNCTION public.og70_write_view() RETURNS trigger LANGUAGE plpgsql";
        String endMarker = "END $write$;";
        int startIdx = sqlContent.indexOf(startMarker);
        assertThat(startIdx).as("og70_write_view start marker must exist in V22").isGreaterThan(-1);
        int endIdx = sqlContent.indexOf(endMarker, startIdx);
        assertThat(endIdx).as("og70_write_view end marker must exist in V22").isGreaterThan(startIdx);
        endIdx += endMarker.length();
        return sqlContent.substring(startIdx, endIdx);
    }

    @Test
    @DisplayName("V51 Clean Chained Validator Layer preserves chain, eliminates early RETURN, and fails closed")
    void testV51CleanChainedValidatorLayerStructure() throws IOException {
        Path v51Path = MIGRATION_DIR.resolve("V51__uc83_group_payment_and_coin_remediation.sql");
        String v51Content = Files.readString(v51Path);

        // 1. Backup validator creation
        assertThat(v51Content).contains("og51_validate_existing_row");
        assertThat(v51Content).contains("Migration V51 failed: backup validator og51_validate_existing_row not created");

        // 2. og70_validate_row redefinition contains dual ownership
        assertThat(v51Content).contains("r.group_id IS NOT NULL AND r.order_id IS NULL");
        assertThat(v51Content).contains("r.group_id IS NULL AND r.order_id IS NOT NULL");

        // 3. Unconditional delegation without early return in DEBIT_CHECKOUT
        int debitStart = v51Content.indexOf("IF s = 'reward_ledger' AND j->>'entry_type' = 'DEBIT_CHECKOUT' THEN");
        assertThat(debitStart).isGreaterThan(-1);
        int debitEnd = v51Content.indexOf("END IF;", debitStart);
        assertThat(debitEnd).isGreaterThan(debitStart);
        String debitBlock = v51Content.substring(debitStart, debitEnd);
        assertThat(debitBlock).doesNotContain("RETURN;");

        // 4. Delegation to backup validator
        assertThat(v51Content).contains("PERFORM public.og51_validate_existing_row(s, j, skip_not_valid);");
        assertThat(v51Content).contains("Backup validator public.og51_validate_existing_row missing");
    }

    @Test
    @DisplayName("V51 canonical provisioning function enforces active ADMIN authority and idempotent return")
    void testCanonicalRewardPolicyProvisioningFunction() throws IOException {
        Path v51Path = MIGRATION_DIR.resolve("V51__uc83_group_payment_and_coin_remediation.sql");
        String v51Content = Files.readString(v51Path);

        assertThat(v51Content).contains("og_provision_canonical_reward_policy");
        assertThat(v51Content).contains("REWARD_CHECKOUT_STANDARD");
        assertThat(v51Content).contains("r.role_name = 'ADMIN'");
        assertThat(v51Content).contains("u.status = 'ACTIVE'");
        assertThat(v51Content).contains("max_redemption_percentage");
    }

    private static int countOccurrences(String text, String target) {
        if (text == null || target == null || target.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }
}
