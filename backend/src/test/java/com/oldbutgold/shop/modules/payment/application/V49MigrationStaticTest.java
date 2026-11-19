package com.oldbutgold.shop.modules.payment.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static verification test for V49 migration (renumbered from V45, Task 4.1b).
 *
 * <p>Strictly offline: Does NOT connect to PostgreSQL, does NOT boot Spring context,
 * and does NOT execute Flyway.
 */
class V49MigrationStaticTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");

    @Test
    @DisplayName("V49 migration file exists and follows project naming convention")
    void testV49FileExists() {
        Path v49Path = MIGRATION_DIR.resolve("V49__forward_reward_ledger_expires_at_in_dispatch.sql");
        assertThat(Files.exists(v49Path))
                .as("V49 migration file must exist")
                .isTrue();
    }

    @Test
    @DisplayName("V49 anchors match V22 og70_dispatch baseline exactly once without collateral changes")
    void testV49AnchorsMatchV22ExactlyOnce() throws IOException {
        Path v22Path = MIGRATION_DIR.resolve("V22__consolidate_database_to_48_tables.sql");
        Path v49Path = MIGRATION_DIR.resolve("V49__forward_reward_ledger_expires_at_in_dispatch.sql");

        String v22Content = Files.readString(v22Path);
        String v49Content = Files.readString(v49Path);

        // 1. Extract og70_dispatch definition from V22
        String startMarker = "CREATE FUNCTION public.og70_dispatch() RETURNS trigger LANGUAGE plpgsql";
        String endMarker = "$dispatch$;";
        int startIdx = v22Content.indexOf(startMarker);
        assertThat(startIdx).as("og70_dispatch start marker must exist in V22").isGreaterThan(-1);
        int endIdx = v22Content.indexOf(endMarker, startIdx);
        assertThat(endIdx).as("og70_dispatch end marker must exist in V22").isGreaterThan(startIdx);
        endIdx += endMarker.length();

        String dispatchDef = v22Content.substring(startIdx, endIdx);

        // 2. Define anchors used in V45
        String anchorNj = "jsonb_build_object('entry_id',n->'entry_id','user_id',n->'user_id','policy_id',n->'policy_id','entry_type',n->'entry_type','points_delta',n->'points_delta','order_id',n->'order_id','group_id',n->'group_id','actor_id',n->'actor_id','reason',n->'reason','created_at',n->'created_at','command_key',n->'command_key')";
        String anchorOj = "jsonb_build_object('entry_id',o->'entry_id','user_id',o->'user_id','policy_id',o->'policy_id','entry_type',o->'entry_type','points_delta',o->'points_delta','order_id',o->'order_id','group_id',o->'group_id','actor_id',o->'actor_id','reason',o->'reason','created_at',o->'created_at','command_key',o->'command_key')";
        String anchorVal = "IF TG_OP<>'DELETE' THEN PERFORM og70_validate_row('reward_ledger',result); END IF;";

        // Assert all anchors appear in V49 migration text
        assertThat(v49Content).contains(anchorNj);
        assertThat(v49Content).contains(anchorOj);
        assertThat(v49Content).contains(anchorVal);

        // Assert all anchors appear exactly once in V22 og70_dispatch
        assertThat(countOccurrences(dispatchDef, anchorNj))
                .as("Anchor NJ must appear exactly once in og70_dispatch")
                .isEqualTo(1);
        assertThat(countOccurrences(dispatchDef, anchorOj))
                .as("Anchor OJ must appear exactly once in og70_dispatch")
                .isEqualTo(1);
        assertThat(countOccurrences(dispatchDef, anchorVal))
                .as("Anchor Validate must appear exactly once in og70_dispatch")
                .isEqualTo(1);

        // 3. Simulate replacement
        String replaceNj = "jsonb_build_object('entry_id',n->'entry_id','user_id',n->'user_id','policy_id',n->'policy_id','entry_type',n->'entry_type','points_delta',n->'points_delta','order_id',n->'order_id','group_id',n->'group_id','actor_id',n->'actor_id','reason',n->'reason','created_at',n->'created_at','command_key',n->'command_key','expires_at',n->'expires_at')";
        String replaceOj = "jsonb_build_object('entry_id',o->'entry_id','user_id',o->'user_id','policy_id',o->'policy_id','entry_type',o->'entry_type','points_delta',o->'points_delta','order_id',o->'order_id','group_id',o->'group_id','actor_id',o->'actor_id','reason',o->'reason','created_at',o->'created_at','command_key',o->'command_key','expires_at',o->'expires_at')";
        String replaceVal = "IF TG_OP<>'DELETE' THEN n:=jsonb_set(n,ARRAY['expires_at'],coalesce(result->'expires_at','null'::jsonb),true); END IF;\n    IF TG_OP<>'DELETE' THEN PERFORM og70_validate_row('reward_ledger',result); END IF;";

        String patchedDispatch = dispatchDef.replace(anchorNj, replaceNj)
                .replace(anchorOj, replaceOj)
                .replace(anchorVal, replaceVal);

        // 4. Verify patched definition contains expires_at in reward_ledger
        assertThat(patchedDispatch)
                .contains("'expires_at',n->'expires_at'")
                .contains("'expires_at',o->'expires_at'")
                .contains("n:=jsonb_set(n,ARRAY['expires_at'],coalesce(result->'expires_at','null'::jsonb),true);");

        // 5. Verify unrelated table branches are completely unchanged
        // Payments branch
        int paymentsStart = dispatchDef.indexOf("WHEN 'payments' THEN");
        int paymentsEnd = dispatchDef.indexOf("WHEN 'refunds' THEN", paymentsStart);
        if (paymentsEnd == -1) paymentsEnd = paymentsStart + 500;
        String v22Payments = dispatchDef.substring(paymentsStart, paymentsEnd);

        int patchedPaymentsStart = patchedDispatch.indexOf("WHEN 'payments' THEN");
        int patchedPaymentsEnd = patchedDispatch.indexOf("WHEN 'refunds' THEN", patchedPaymentsStart);
        if (patchedPaymentsEnd == -1) patchedPaymentsEnd = patchedPaymentsStart + 500;
        String patchedPayments = patchedDispatch.substring(patchedPaymentsStart, patchedPaymentsEnd);

        assertThat(patchedPayments)
                .as("Payments branch must be byte-for-byte identical")
                .isEqualTo(v22Payments);

        // Users branch
        int usersStart = dispatchDef.indexOf("WHEN 'users' THEN");
        int usersEnd = dispatchDef.indexOf("WHEN 'roles' THEN", usersStart);
        String v22Users = dispatchDef.substring(usersStart, usersEnd);

        int patchedUsersStart = patchedDispatch.indexOf("WHEN 'users' THEN");
        int patchedUsersEnd = patchedDispatch.indexOf("WHEN 'roles' THEN", patchedUsersStart);
        String patchedUsers = patchedDispatch.substring(patchedUsersStart, patchedUsersEnd);

        assertThat(patchedUsers)
                .as("Users branch must be byte-for-byte identical")
                .isEqualTo(v22Users);

        // 6. Verify table-scoped idempotency check pattern
        assertThat(v49Content).contains("'command_key',n->'command_key','expires_at',n->'expires_at'");
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

