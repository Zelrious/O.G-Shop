package com.oldbutgold.shop.modules.commerce.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static verification test for hardened V50 migration (renumbered from V46: Sync og70_write_view orders branch with GHN columns).
 *
 * <p>Strictly offline: Does NOT connect to PostgreSQL, does NOT boot Spring context,
 * and does NOT execute Flyway.
 */
class V50MigrationStaticTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");

    // Exact anchors defined in V22 and V46
    private static final String ANCHOR_INSERT = "purchase_shipping_used_at,record_type) SELECT r.order_id,r.checkout_group_id,r.buyer_id,r.seller_id,r.source_address_id,r.shipping_recipient_name,r.shipping_phone_number,r.shipping_province,r.shipping_district,r.shipping_ward,r.shipping_detail_address,r.subtotal,r.shipping_fee,r.total_amount,r.currency,r.status,r.payment_due_at,r.version,r.created_at,r.updated_at,r.completed_at,r.cancelled_at,r.cancellation_reason,r.buyer_system_fee,r.seller_system_fee,r.seller_proceeds,r.voucher_discount_amount,r.shipping_discount_amount,r.sponsor_type,r.workflow_model,r.group_id,r.return_allowed,r.delivery_method,r.carrier_snapshot,r.handover_day,r.points_discount_amount,r.paid_confirmed_at,r.seller_accept_due_at,r.valid_delivered_at,r.received_at,r.return_due_at,r.early_completed_at,r.early_completion_warning_version,r.purchase_shipping_used_at,''orders'' FROM jsonb_populate_record(NULL::og_compat.orders,$1) r";
    private static final String ANCHOR_UPDATE = "purchase_shipping_used_at=r.purchase_shipping_used_at FROM jsonb_populate_record(NULL::og_compat.orders,$1) r,jsonb_populate_record(NULL::og_compat.orders,$2) old_r WHERE t.order_id=old_r.order_id";
    private static final String ANCHOR_BUILD_OBJ = "''purchase_shipping_used_at'',to_jsonb(t)->''purchase_shipping_used_at'')";
    private static final String ANCHOR_RESULT = "'purchase_shipping_used_at',stored->'purchase_shipping_used_at');";

    // Exact replacements defined in V46
    private static final String REPLACE_INSERT = "purchase_shipping_used_at,record_type,shipping_ghn_province_id,shipping_ghn_district_id,shipping_ghn_ward_code) SELECT r.order_id,r.checkout_group_id,r.buyer_id,r.seller_id,r.source_address_id,r.shipping_recipient_name,r.shipping_phone_number,r.shipping_province,r.shipping_district,r.shipping_ward,r.shipping_detail_address,r.subtotal,r.shipping_fee,r.total_amount,r.currency,r.status,r.payment_due_at,r.version,r.created_at,r.updated_at,r.completed_at,r.cancelled_at,r.cancellation_reason,r.buyer_system_fee,r.seller_system_fee,r.seller_proceeds,r.voucher_discount_amount,r.shipping_discount_amount,r.sponsor_type,r.workflow_model,r.group_id,r.return_allowed,r.delivery_method,r.carrier_snapshot,r.handover_day,r.points_discount_amount,r.paid_confirmed_at,r.seller_accept_due_at,r.valid_delivered_at,r.received_at,r.return_due_at,r.early_completed_at,r.early_completion_warning_version,r.purchase_shipping_used_at,''orders'',r.shipping_ghn_province_id,r.shipping_ghn_district_id,r.shipping_ghn_ward_code FROM jsonb_populate_record(NULL::og_compat.orders,$1) r";
    private static final String REPLACE_UPDATE = "purchase_shipping_used_at=r.purchase_shipping_used_at,shipping_ghn_province_id=r.shipping_ghn_province_id,shipping_ghn_district_id=r.shipping_ghn_district_id,shipping_ghn_ward_code=r.shipping_ghn_ward_code FROM jsonb_populate_record(NULL::og_compat.orders,$1) r,jsonb_populate_record(NULL::og_compat.orders,$2) old_r WHERE t.order_id=old_r.order_id";
    private static final String REPLACE_BUILD_OBJ = "''purchase_shipping_used_at'',to_jsonb(t)->''purchase_shipping_used_at'',''shipping_ghn_province_id'',to_jsonb(t)->''shipping_ghn_province_id'',''shipping_ghn_district_id'',to_jsonb(t)->''shipping_ghn_district_id'',''shipping_ghn_ward_code'',to_jsonb(t)->''shipping_ghn_ward_code'')";
    private static final String REPLACE_RESULT = "'purchase_shipping_used_at',stored->'purchase_shipping_used_at','shipping_ghn_province_id',stored->'shipping_ghn_province_id','shipping_ghn_district_id',stored->'shipping_ghn_district_id','shipping_ghn_ward_code',stored->'shipping_ghn_ward_code');";

    @Test
    @DisplayName("V50 migration file exists and follows project naming convention")
    void testV50FileExists() {
        Path v50Path = MIGRATION_DIR.resolve("V50__sync_og70_write_view_orders_ghn_columns.sql");
        assertThat(Files.exists(v50Path))
                .as("V50 migration file must exist")
                .isTrue();
    }

    @Test
    @DisplayName("V50 SQL migration file contains all 4 exact anchors and all 4 exact replacements")
    void testV50AnchorsAndReplacementsPresentInMigrationSql() throws IOException {
        Path v50Path = MIGRATION_DIR.resolve("V50__sync_og70_write_view_orders_ghn_columns.sql");
        String v50Content = Files.readString(v50Path);

        // Verify all 4 anchors are present in the SQL migration file
        assertThat(v50Content)
                .as("V50 SQL must contain exact anchor INSERT")
                .contains(ANCHOR_INSERT);
        assertThat(v50Content)
                .as("V50 SQL must contain exact anchor UPDATE")
                .contains(ANCHOR_UPDATE);
        assertThat(v50Content)
                .as("V50 SQL must contain exact anchor build_object")
                .contains(ANCHOR_BUILD_OBJ);
        assertThat(v50Content)
                .as("V50 SQL must contain exact anchor result")
                .contains(ANCHOR_RESULT);

        // Verify all 4 replacements are present in the SQL migration file
        assertThat(v50Content)
                .as("V50 SQL must contain exact replacement INSERT")
                .contains(REPLACE_INSERT);
        assertThat(v50Content)
                .as("V50 SQL must contain exact replacement UPDATE")
                .contains(REPLACE_UPDATE);
        assertThat(v50Content)
                .as("V50 SQL must contain exact replacement build_object")
                .contains(REPLACE_BUILD_OBJ);
        assertThat(v50Content)
                .as("V50 SQL must contain exact replacement result")
                .contains(REPLACE_RESULT);
    }

    @Test
    @DisplayName("V46 anchors match V22 baseline with exact occurrence counts (1, 1, 2, 1)")
    void testAnchorsMatchV22BaselineExactOccurrences() throws IOException {
        Path v22Path = MIGRATION_DIR.resolve("V22__consolidate_database_to_48_tables.sql");
        String v22Content = Files.readString(v22Path);

        String writerDef = extractOg70WriteView(v22Content);

        // Extract orders branch
        int ordersStart = writerDef.indexOf("WHEN 'orders' THEN");
        int ordersEnd = writerDef.indexOf("WHEN 'order_items' THEN", ordersStart);
        assertThat(ordersStart).isGreaterThan(-1);
        assertThat(ordersEnd).isGreaterThan(ordersStart);
        String ordersBranch = writerDef.substring(ordersStart, ordersEnd);

        // Assert exact required occurrence counts in orders branch
        assertThat(countOccurrences(ordersBranch, ANCHOR_INSERT))
                .as("Anchor INSERT must occur exactly 1 time in orders branch")
                .isEqualTo(1);
        assertThat(countOccurrences(ordersBranch, ANCHOR_UPDATE))
                .as("Anchor UPDATE SET must occur exactly 1 time in orders branch")
                .isEqualTo(1);
        assertThat(countOccurrences(ordersBranch, ANCHOR_BUILD_OBJ))
                .as("Anchor build_object must occur exactly 2 times (UPDATE & DELETE) in orders branch")
                .isEqualTo(2);
        assertThat(countOccurrences(ordersBranch, ANCHOR_RESULT))
                .as("Anchor result must occur exactly 1 time in orders branch")
                .isEqualTo(1);

        // Assert anchors appear nowhere outside orders branch
        String nonOrdersBefore = writerDef.substring(0, ordersStart);
        String nonOrdersAfter = writerDef.substring(ordersEnd);
        assertThat(countOccurrences(nonOrdersBefore + nonOrdersAfter, ANCHOR_INSERT)).isEqualTo(0);
        assertThat(countOccurrences(nonOrdersBefore + nonOrdersAfter, ANCHOR_UPDATE)).isEqualTo(0);
        assertThat(countOccurrences(nonOrdersBefore + nonOrdersAfter, ANCHOR_BUILD_OBJ)).isEqualTo(0);
        assertThat(countOccurrences(nonOrdersBefore + nonOrdersAfter, ANCHOR_RESULT)).isEqualTo(0);
    }

    @Test
    @DisplayName("Simulated patch replaces exact counts and preserves non-orders branches byte-for-byte")
    void testSimulatedPatchPreservesNonOrdersBranchesAndReplacesExactOccurrences() throws IOException {
        Path v22Path = MIGRATION_DIR.resolve("V22__consolidate_database_to_48_tables.sql");
        String v22Content = Files.readString(v22Path);
        String writerDef = extractOg70WriteView(v22Content);

        // Apply replacements
        String patchedWriter = writerDef
                .replace(ANCHOR_INSERT, REPLACE_INSERT)
                .replace(ANCHOR_UPDATE, REPLACE_UPDATE)
                .replace(ANCHOR_BUILD_OBJ, REPLACE_BUILD_OBJ)
                .replace(ANCHOR_RESULT, REPLACE_RESULT);

        // Verify all anchors now have 0 occurrences
        assertThat(countOccurrences(patchedWriter, ANCHOR_INSERT)).isEqualTo(0);
        assertThat(countOccurrences(patchedWriter, ANCHOR_UPDATE)).isEqualTo(0);
        assertThat(countOccurrences(patchedWriter, ANCHOR_BUILD_OBJ)).isEqualTo(0);
        assertThat(countOccurrences(patchedWriter, ANCHOR_RESULT)).isEqualTo(0);

        // Verify replacements have exact required occurrence counts (1, 1, 2, 1)
        assertThat(countOccurrences(patchedWriter, REPLACE_INSERT)).isEqualTo(1);
        assertThat(countOccurrences(patchedWriter, REPLACE_UPDATE)).isEqualTo(1);
        assertThat(countOccurrences(patchedWriter, REPLACE_BUILD_OBJ)).isEqualTo(2);
        assertThat(countOccurrences(patchedWriter, REPLACE_RESULT)).isEqualTo(1);

        // Verify non-orders branches are completely unchanged
        // Checkout groups branch
        int cgStart = writerDef.indexOf("WHEN 'checkout_groups' THEN");
        int cgEnd = writerDef.indexOf("WHEN 'orders' THEN", cgStart);
        String v22Cg = writerDef.substring(cgStart, cgEnd);

        int patchedCgStart = patchedWriter.indexOf("WHEN 'checkout_groups' THEN");
        int patchedCgEnd = patchedWriter.indexOf("WHEN 'orders' THEN", patchedCgStart);
        String patchedCg = patchedWriter.substring(patchedCgStart, patchedCgEnd);
        assertThat(patchedCg)
                .as("Checkout groups branch must be byte-for-byte identical")
                .isEqualTo(v22Cg);

        // Order items branch
        int oiStart = writerDef.indexOf("WHEN 'order_items' THEN");
        int oiEnd = writerDef.indexOf("WHEN 'checkout_redemptions' THEN", oiStart);
        String v22Oi = writerDef.substring(oiStart, oiEnd);

        int patchedOiStart = patchedWriter.indexOf("WHEN 'order_items' THEN");
        int patchedOiEnd = patchedWriter.indexOf("WHEN 'checkout_redemptions' THEN", patchedOiStart);
        String patchedOi = patchedWriter.substring(patchedOiStart, patchedOiEnd);
        assertThat(patchedOi)
                .as("Order items branch must be byte-for-byte identical")
                .isEqualTo(v22Oi);
    }

    @Test
    @DisplayName("V50 SQL migration contains strict 4-patch idempotency guard and partial-state abort guard")
    void testIdempotencyAndPartialStateGuardsInMigrationSql() throws IOException {
        Path v50Path = MIGRATION_DIR.resolve("V50__sync_og70_write_view_orders_ghn_columns.sql");
        String v50Content = Files.readString(v50Path);

        // A1. Idempotency guard requires all 4 exact replacement counts: 1, 1, 2, 1
        assertThat(v50Content)
                .as("Idempotency guard must check conjunction of all 4 replacement counts")
                .contains("IF occ_r_insert = 1 AND occ_r_update = 1 AND occ_r_build_obj = 2 AND occ_r_result = 1 THEN")
                .contains("RETURN;");

        // A2. Partial-state guard must detect partial replacement and abort
        assertThat(v50Content)
                .as("Partial-state guard must detect partial replacement counts")
                .contains("IF occ_r_insert > 0 OR occ_r_update > 0 OR occ_r_build_obj > 0 OR occ_r_result > 0")
                .contains("RAISE EXCEPTION 'Migration V46 aborted: og70_write_view() is in an inconsistent, partially patched state");

        // A3. Exact occurrence validation before replacements
        assertThat(v50Content)
                .contains("IF occ <> 1 THEN\n        RAISE EXCEPTION 'Migration V46 failed: anchor \"orders INSERT\" expected 1 occurrence")
                .contains("IF occ <> 1 THEN\n        RAISE EXCEPTION 'Migration V46 failed: anchor \"orders UPDATE SET\" expected 1 occurrence")
                .contains("IF occ <> 2 THEN\n        RAISE EXCEPTION 'Migration V46 failed: anchor \"orders build_object\" expected 2 occurrences")
                .contains("IF occ <> 1 THEN\n        RAISE EXCEPTION 'Migration V46 failed: anchor \"orders result\" expected 1 occurrence");

        // A3. Post-replacement length delta validation
        assertThat(v50Content)
                .contains("IF length(def) - length(prev) <> 1 * (length(r_insert) - length(a_insert)) THEN")
                .contains("IF length(def) - length(prev) <> 1 * (length(r_update) - length(a_update)) THEN")
                .contains("IF length(def) - length(prev) <> 2 * (length(r_build_obj) - length(a_build_obj)) THEN")
                .contains("IF length(def) - length(prev) <> 1 * (length(r_result) - length(a_result)) THEN");
    }

    @Test
    @DisplayName("Occurrence counting logic detects exact counts and abort conditions")
    void testOccurrenceCountingLogic() {
        String testText = "alpha_anchor_beta_anchor_gamma";
        String anchor = "anchor";

        // Logic equivalent to PL/pgSQL: (length(def) - length(replace(def, anchor, ''))) / length(anchor)
        int calculatedCount = (testText.length() - testText.replace(anchor, "").length()) / anchor.length();
        assertThat(calculatedCount).isEqualTo(2);

        // Missing anchor count
        int missingCount = (testText.length() - testText.replace("nonexistent", "").length()) / "nonexistent".length();
        assertThat(missingCount).isEqualTo(0);

        // Single anchor count
        String singleText = "foo_anchor_bar";
        int singleCount = (singleText.length() - singleText.replace(anchor, "").length()) / anchor.length();
        assertThat(singleCount).isEqualTo(1);
    }

    @Test
    @DisplayName("V46 orders result matches og_compat.orders 47-column count")
    void testOrdersColumnCountParity() throws IOException {
        Path v36Path = MIGRATION_DIR.resolve("V36__add_ghn_address_codes.sql");
        String v36Content = Files.readString(v36Path);

        // Verify og_compat.orders view in V36 defines 47 columns
        int viewStart = v36Content.indexOf("CREATE OR REPLACE VIEW og_compat.orders AS");
        int viewEnd = v36Content.indexOf("FROM public.orders p", viewStart);
        assertThat(viewStart).isGreaterThan(-1);
        assertThat(viewEnd).isGreaterThan(viewStart);

        String viewDef = v36Content.substring(viewStart, viewEnd);
        int columnCount = countOccurrences(viewDef, "p.");
        assertThat(columnCount)
                .as("og_compat.orders view in V36 must define exactly 47 columns")
                .isEqualTo(47);

        // Verify the full result projection in patched orders branch contains all 47 stored columns
        Path v22Path = MIGRATION_DIR.resolve("V22__consolidate_database_to_48_tables.sql");
        String v22Content = Files.readString(v22Path);
        String writerDef = extractOg70WriteView(v22Content);
        String patchedWriter = writerDef.replace(ANCHOR_RESULT, REPLACE_RESULT);

        int ordersStart = patchedWriter.indexOf("WHEN 'orders' THEN");
        int ordersEnd = patchedWriter.indexOf("WHEN 'order_items' THEN", ordersStart);
        String patchedOrders = patchedWriter.substring(ordersStart, ordersEnd);
        int resultStart = patchedOrders.indexOf("result:=jsonb_build_object(");
        int resultEnd = patchedOrders.indexOf(");", resultStart);
        String resultDef = patchedOrders.substring(resultStart, resultEnd);

        int totalResultColumns = countOccurrences(resultDef, "stored->");
        assertThat(totalResultColumns)
                .as("Full result projection in og70_write_view orders branch must map all 47 stored columns")
                .isEqualTo(47);

        // Verify REPLACE_RESULT contains the tail anchor column + 3 GHN columns
        assertThat(countOccurrences(REPLACE_RESULT, "stored->"))
                .as("Replacement result fragment must project 4 stored columns (1 anchor tail + 3 GHN columns)")
                .isEqualTo(4);
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

    private static int countOccurrences(String text, String target) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }
}
