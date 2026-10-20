package com.oldbutgold.shop.modules.commerce.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FulfillmentSimulationGuardTest {

    @Test
    @DisplayName("Default configuration (PROD, false) must fail closed with 403")
    void testFailClosedDefault() {
        FulfillmentSimulationGuard guard = new FulfillmentSimulationGuard("PROD", false);
        assertThat(guard.isSimulationAllowed()).isFalse();

        assertThatThrownBy(guard::ensureSimulationAllowed)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).isEqualTo("FORBIDDEN_SIMULATION_IN_PRODUCTION");
                });
    }

    @Test
    @DisplayName("PROD environment with simulationEnabled=true must still be blocked (403)")
    void testProdWithSimulationEnabledBlocked() {
        FulfillmentSimulationGuard guard = new FulfillmentSimulationGuard("PROD", true);
        assertThat(guard.isSimulationAllowed()).isFalse();

        assertThatThrownBy(guard::ensureSimulationAllowed)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).isEqualTo("FORBIDDEN_SIMULATION_IN_PRODUCTION");
                });
    }

    @Test
    @DisplayName("DEV environment with simulationEnabled=false must be blocked (403)")
    void testDevWithSimulationDisabledBlocked() {
        FulfillmentSimulationGuard guard = new FulfillmentSimulationGuard("DEV", false);
        assertThat(guard.isSimulationAllowed()).isFalse();

        assertThatThrownBy(guard::ensureSimulationAllowed)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).isEqualTo("FORBIDDEN_SIMULATION_IN_PRODUCTION");
                });
    }

    @Test
    @DisplayName("DEV environment with simulationEnabled=true must be allowed")
    void testDevWithSimulationEnabledAllowed() {
        FulfillmentSimulationGuard guard = new FulfillmentSimulationGuard("DEV", true);
        assertThat(guard.isSimulationAllowed()).isTrue();
        assertThatCode(guard::ensureSimulationAllowed).doesNotThrowAnyException();
    }
}
