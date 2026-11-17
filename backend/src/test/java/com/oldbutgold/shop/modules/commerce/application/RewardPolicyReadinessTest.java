package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.platform.infrastructure.SchemaMigrationCli;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RewardPolicyReadinessTest {

    @Test
    @DisplayName("GAP-3: checkReadiness returns NOT_READY when policy is missing in reward_policies")
    void checkReadiness_whenPolicyMissing_returnsNotReady() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq("REWARD_CHECKOUT_STANDARD")))
                .thenReturn(Collections.emptyList());

        RewardPolicyReadinessIndicator indicator = new RewardPolicyReadinessIndicator(jdbcTemplate);
        RewardPolicyReadinessIndicator.ReadinessReport report = indicator.checkReadiness();

        assertThat(report.isReady()).isFalse();
        assertThat(report.message()).contains("Canonical reward policy 'REWARD_CHECKOUT_STANDARD' is not provisioned");

        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(RewardPolicyReadinessIndicator.STATUS_NOT_READY);
        assertThat(health.getDetails()).containsEntry("status", "NOT_READY");
        assertThat(health.getDetails()).containsEntry("coinCheckoutStatus", "FAIL_CLOSED_BLOCKED");
    }

    @Test
    @DisplayName("GAP-3: checkReadiness returns NOT_READY when policy has invalid point_value (not 1.00)")
    void checkReadiness_whenPolicyHasInvalidPointValue_returnsNotReady() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq("REWARD_CHECKOUT_STANDARD")))
                .thenReturn(List.of(Map.of(
                        "policy_id", 101L,
                        "policy_code", "REWARD_CHECKOUT_STANDARD",
                        "point_value", new BigDecimal("2.00"),
                        "created_by", 1001L
                )));

        RewardPolicyReadinessIndicator indicator = new RewardPolicyReadinessIndicator(jdbcTemplate);
        RewardPolicyReadinessIndicator.ReadinessReport report = indicator.checkReadiness();

        assertThat(report.isReady()).isFalse();
        assertThat(report.message()).contains("Invalid point_value: expected 1.00, got: 2.00");

        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(RewardPolicyReadinessIndicator.STATUS_NOT_READY);
    }

    @Test
    @DisplayName("GAP-3: checkReadiness returns NOT_READY when multiple ambiguous policies exist")
    void checkReadiness_whenMultiplePoliciesExist_returnsNotReady() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq("REWARD_CHECKOUT_STANDARD")))
                .thenReturn(List.of(
                        Map.of("policy_id", 1L, "point_value", BigDecimal.ONE, "created_by", 1001L),
                        Map.of("policy_id", 2L, "point_value", BigDecimal.ONE, "created_by", 1001L)
                ));

        RewardPolicyReadinessIndicator indicator = new RewardPolicyReadinessIndicator(jdbcTemplate);
        RewardPolicyReadinessIndicator.ReadinessReport report = indicator.checkReadiness();

        assertThat(report.isReady()).isFalse();
        assertThat(report.message()).contains("Ambiguous multiple reward policies found");
    }

    @Test
    @DisplayName("GAP-3: checkReadiness returns READY when canonical policy is valid (1.00, valid admin)")
    void checkReadiness_whenPolicyValid_returnsReadyAndHealthUp() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq("REWARD_CHECKOUT_STANDARD")))
                .thenReturn(List.of(Map.of(
                        "policy_id", 100L,
                        "policy_code", "REWARD_CHECKOUT_STANDARD",
                        "point_value", new BigDecimal("1.00"),
                        "created_by", 1001L
                )));

        RewardPolicyReadinessIndicator indicator = new RewardPolicyReadinessIndicator(jdbcTemplate);
        RewardPolicyReadinessIndicator.ReadinessReport report = indicator.checkReadiness();

        assertThat(report.isReady()).isTrue();
        assertThat(report.policyId()).isEqualTo(100L);
        assertThat(report.pointValue()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(report.createdByAdminId()).isEqualTo(1001L);

        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("status", "READY");
        assertThat(health.getDetails()).containsEntry("policyId", 100L);
    }

    @Test
    @DisplayName("GAP-3: checkReadiness handles null jdbcTemplate gracefully without throwing")
    void checkReadiness_whenJdbcTemplateNull_handlesGracefully() {
        RewardPolicyReadinessIndicator indicator = new RewardPolicyReadinessIndicator(null);
        RewardPolicyReadinessIndicator.ReadinessReport report = indicator.checkReadiness();

        assertThat(report.isReady()).isFalse();
        assertThat(report.message()).contains("No database connection available");
    }

    @Test
    @DisplayName("GAP-2: SchemaMigrationCli throws when action is unknown")
    void schemaMigrationCli_throwsWhenActionUnknown() {
        assertThatThrownBy(() -> SchemaMigrationCli.main(new String[]{"unknown-action"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Usage: SchemaMigrationCli");
    }

    @Test
    @DisplayName("GAP-2: SchemaMigrationCli throws when adminId is missing for provision-reward-policy")
    void schemaMigrationCli_throwsWhenAdminIdMissing() {
        assertThatThrownBy(() -> SchemaMigrationCli.resolveAdminId(new String[]{"provision-reward-policy"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required Admin ID");
    }

    @Test
    @DisplayName("GAP-2: SchemaMigrationCli throws when adminId is non-numeric")
    void schemaMigrationCli_throwsWhenAdminIdNonNumeric() {
        assertThatThrownBy(() -> SchemaMigrationCli.resolveAdminId(new String[]{"provision-reward-policy", "not-a-number"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Admin ID format");
    }

    @Test
    @DisplayName("GAP-2: SchemaMigrationCli parses valid numeric adminId correctly")
    void schemaMigrationCli_parsesValidAdminId() {
        long id = SchemaMigrationCli.resolveAdminId(new String[]{"provision-reward-policy", "12345"});
        assertThat(id).isEqualTo(12345L);
    }
}

