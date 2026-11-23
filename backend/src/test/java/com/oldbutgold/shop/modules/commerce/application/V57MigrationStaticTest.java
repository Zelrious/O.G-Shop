package com.oldbutgold.shop.modules.commerce.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V57MigrationStaticTest {

    private static final Path V57_PATH = Path.of(
            "src/main/resources/db/migration/V57__remediate_v49_reward_ledger_expires_at_in_dispatch.sql"
    );

    @Test
    @DisplayName("V57 exists and enforces unique reward_ledger branch markers (when=2, header=1, mark=1)")
    void v57EnforcesUniqueBranchMarkers() throws Exception {
        assertThat(V57_PATH).exists();
        String sql = Files.readString(V57_PATH, StandardCharsets.UTF_8);

        assertThat(sql).contains("cnt_when <> 2 OR cnt_header <> 1 OR cnt_mark <> 1");
        assertThat(sql).contains("branch_pattern expected 1 match");
        assertThat(sql).contains("USING ERRCODE = '23514'");
    }

    @Test
    @DisplayName("V57 requires full ordered State A/C invariants (r_nj, r_oj, b_exp, a_val, r_val) for no-op")
    void v57RequiresFullOrderedStateACInvariants() throws Exception {
        String sql = Files.readString(V57_PATH, StandardCharsets.UTF_8);

        assertThat(sql).contains("cnt_r_nj = 1 AND cnt_r_oj = 1 AND cnt_b_exp = 1 AND cnt_a_val = 1 AND cnt_r_val = 1");
        assertThat(sql).contains("AND cnt_a_nj = 0 AND cnt_a_oj = 0");
        assertThat(sql).doesNotContain("LIKE '%WHEN ''reward_ledger''%");
    }

    @Test
    @DisplayName("V57 verifies post-patch invariants and full-function marker counts before EXECUTE")
    void v57VerifiesPostPatchBeforeExecute() throws Exception {
        String sql = Files.readString(V57_PATH, StandardCharsets.UTF_8);

        assertThat(sql).contains("post-patch branch verification failed");
        assertThat(sql).contains("post-patch marker counts changed unexpectedly");
        assertThat(sql).contains("EXECUTE patched_def;");
    }
}

