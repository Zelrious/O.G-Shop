package com.oldbutgold.shop.modules.commerce.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static verification test for hardened V55 forward migration (renumbered from V51: Fix og70_write_view V44/V48 defect).
 *
 * <p>Strictly offline: Does NOT connect to PostgreSQL, does NOT boot Spring context,
 * and does NOT execute Flyway.
 */
class V55MigrationStaticTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");

    private static final String ANCHOR_LISTING_FEE_CHARGES =
            "public.listing_fee_charges (charge_id,assessment_id,amount,state,created_at,command_key,record_type,expires_at) SELECT";
    private static final String REPLACE_LISTING_FEE_CHARGES =
            "public.listing_fee_charges (charge_id,assessment_id,amount,state,created_at,command_key,record_type) SELECT";

    private static final String ANCHOR_PAYMENTS =
            "public.payments (source_payment_intents_intent_id,purpose,group_id,listing_charge_id,expected_amount,currency,payment_method,state,deadline,created_at,command_key,record_type,expires_at) SELECT";
    private static final String REPLACE_PAYMENTS =
            "public.payments (source_payment_intents_intent_id,purpose,group_id,listing_charge_id,expected_amount,currency,payment_method,state,deadline,created_at,command_key,record_type) SELECT";

    private static final String ANCHOR_PENALTY_LEDGER =
            "public.penalty_ledger (entry_id,user_id,report_id,policy_id,points_delta,adjustment_of,decided_by,reason,created_at,command_key,record_type,expires_at) SELECT";
    private static final String REPLACE_PENALTY_LEDGER =
            "public.penalty_ledger (entry_id,user_id,report_id,policy_id,points_delta,adjustment_of,decided_by,reason,created_at,command_key,record_type) SELECT";

    private static final String ANCHOR_NOTIFICATIONS =
            "public.notifications (source_notification_dispatches_dispatch_id,sender_id,event_id,title,content,target_data,created_at,command_key,record_type,expires_at) SELECT";
    private static final String REPLACE_NOTIFICATIONS =
            "public.notifications (source_notification_dispatches_dispatch_id,sender_id,event_id,title,content,target_data,created_at,command_key,record_type) SELECT";

    @Test
    @DisplayName("V55 migration file exists and follows Flyway naming convention")
    void testV55FileExists() {
        Path v55Path = MIGRATION_DIR.resolve("V55__fix_og70_write_view_v48_defect.sql");
        assertThat(Files.exists(v55Path))
                .as("V55 migration file must exist")
                .isTrue();
    }

    @Test
    @DisplayName("V55 SQL migration file contains all 4 exact defective anchors and 4 exact replacements")
    void testV55AnchorsAndReplacementsPresent() throws IOException {
        Path v55Path = MIGRATION_DIR.resolve("V55__fix_og70_write_view_v48_defect.sql");
        String v55Content = Files.readString(v55Path);

        assertThat(v55Content).contains(ANCHOR_LISTING_FEE_CHARGES);
        assertThat(v55Content).contains(REPLACE_LISTING_FEE_CHARGES);

        assertThat(v55Content).contains(ANCHOR_PAYMENTS);
        assertThat(v55Content).contains(REPLACE_PAYMENTS);

        assertThat(v55Content).contains(ANCHOR_PENALTY_LEDGER);
        assertThat(v55Content).contains(REPLACE_PENALTY_LEDGER);

        assertThat(v55Content).contains(ANCHOR_NOTIFICATIONS);
        assertThat(v55Content).contains(REPLACE_NOTIFICATIONS);
    }

    @Test
    @DisplayName("V55 protects reward_ledger and previous migrations through invariant checks")
    void testV55InvariantGuardsPresent() throws IOException {
        Path v55Path = MIGRATION_DIR.resolve("V55__fix_og70_write_view_v48_defect.sql");
        String v55Content = Files.readString(v55Path);

        // reward_ledger preservation
        assertThat(v55Content).contains("public.reward_ledger (entry_id,user_id,policy_id,entry_type,points_delta,order_id,group_id,actor_id,reason,created_at,command_key,record_type,expires_at) SELECT");
        assertThat(v55Content).contains("''reward_ledger'',r.expires_at FROM jsonb_populate_record(NULL::og_compat.reward_ledger");

        // V47, V50, V51 preservation checks
        assertThat(v55Content).contains("case_evidence%request_id%");
        assertThat(v55Content).contains("shipping_ghn_province_id%shipping_ghn_district_id%shipping_ghn_ward_code%");
        assertThat(v55Content).contains("reward_policy_id%");
    }
}
