package com.oldbutgold.shop.modules.commerce.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * GAP-3: Readiness / Post-deployment Verification for Canonical Reward Policy.
 * Exposes /actuator/health/rewardPolicy status without failing whole application liveness.
 */
@Component("rewardPolicyHealthIndicator")
public class RewardPolicyReadinessIndicator implements HealthIndicator {

    public static final String DEFAULT_CANONICAL_CODE = "REWARD_CHECKOUT_STANDARD";
    public static final BigDecimal EXPECTED_POINT_VALUE = new BigDecimal("1.00");
    public static final org.springframework.boot.actuate.health.Status STATUS_NOT_READY = new org.springframework.boot.actuate.health.Status("NOT_READY");

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.reward.canonical-policy-code:}")
    private String configuredCanonicalPolicyCode;

    @Autowired
    public RewardPolicyReadinessIndicator(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record ReadinessReport(
            boolean isReady,
            String policyCode,
            Long policyId,
            BigDecimal pointValue,
            Long createdByAdminId,
            String message
    ) {}

    public ReadinessReport checkReadiness() {
        if (jdbcTemplate == null) {
            return new ReadinessReport(false, resolvePolicyCode(), null, null, null, "No database connection available");
        }

        String policyCode = resolvePolicyCode();
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT policy_id, policy_code, point_value, created_by FROM og_compat.reward_policies WHERE policy_code = ? ORDER BY policy_id ASC",
                    policyCode
            );
        } catch (DataAccessException e) {
            return new ReadinessReport(false, policyCode, null, null, null, "Database error querying reward_policies: " + e.getMessage());
        }

        if (rows.isEmpty()) {
            return new ReadinessReport(false, policyCode, null, null, null,
                    "Canonical reward policy '" + policyCode + "' is not provisioned. Coin checkout remains fail-closed.");
        }

        if (rows.size() > 1) {
            return new ReadinessReport(false, policyCode, null, null, null,
                    "Ambiguous multiple reward policies found for code: " + policyCode);
        }

        Map<String, Object> row = rows.get(0);
        Long policyId = row.get("policy_id") != null ? ((Number) row.get("policy_id")).longValue() : null;
        BigDecimal pointValue = row.get("point_value") != null ? new BigDecimal(row.get("point_value").toString()) : null;
        Long createdBy = row.get("created_by") != null ? ((Number) row.get("created_by")).longValue() : null;

        if (pointValue == null || pointValue.compareTo(EXPECTED_POINT_VALUE) != 0) {
            return new ReadinessReport(false, policyCode, policyId, pointValue, createdBy,
                    "Invalid point_value: expected 1.00, got: " + pointValue);
        }

        if (createdBy == null) {
            return new ReadinessReport(false, policyCode, policyId, pointValue, null,
                    "Missing created_by Admin ID on canonical reward policy");
        }

        return new ReadinessReport(true, policyCode, policyId, pointValue, createdBy,
                "Canonical reward policy is verified and ready.");
    }

    @Override
    public Health health() {
        ReadinessReport report = checkReadiness();
        if (report.isReady()) {
            return Health.up()
                    .withDetail("policyCode", report.policyCode())
                    .withDetail("policyId", report.policyId())
                    .withDetail("pointValue", report.pointValue())
                    .withDetail("createdByAdminId", report.createdByAdminId())
                    .withDetail("status", "READY")
                    .build();
        } else {
            // Returns NOT_READY status so that Kubernetes readiness probe marks the pod NOT_READY (503),
            // while Kubernetes liveness probe (livenessState) remains UP (200) so the pod is not killed.
            return Health.status(STATUS_NOT_READY)
                    .withDetail("policyCode", report.policyCode())
                    .withDetail("status", "NOT_READY")
                    .withDetail("reason", report.message())
                    .withDetail("coinCheckoutStatus", "FAIL_CLOSED_BLOCKED")
                    .build();
        }
    }

    private String resolvePolicyCode() {
        if (configuredCanonicalPolicyCode != null && !configuredCanonicalPolicyCode.isBlank()) {
            return configuredCanonicalPolicyCode.trim();
        }
        String env = System.getenv("REWARD_CANONICAL_POLICY_CODE");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return DEFAULT_CANONICAL_CODE;
    }

    public void setConfiguredCanonicalPolicyCode(String code) {
        this.configuredCanonicalPolicyCode = code;
    }
}

