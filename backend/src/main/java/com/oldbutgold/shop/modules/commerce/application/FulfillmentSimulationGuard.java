package com.oldbutgold.shop.modules.commerce.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Chốt chặn bảo vệ mô phỏng giao hàng GHN (fail-closed).
 * Mặc định app.environment = PROD, app.ghn.simulation-enabled = false.
 * Chặn hoàn toàn endpoint mô phỏng trên PROD hoặc khi simulation không được bật tường minh.
 */
@Component
public class FulfillmentSimulationGuard {

    private final String environment;
    private final boolean simulationEnabled;

    public FulfillmentSimulationGuard(
            @Value("${app.environment:PROD}") String environment,
            @Value("${app.ghn.simulation-enabled:false}") boolean simulationEnabled
    ) {
        this.environment = (environment != null && !environment.isBlank())
                ? environment.trim().toUpperCase()
                : "PROD";
        this.simulationEnabled = simulationEnabled;
    }

    /**
     * Kiểm tra chốt chặn môi trường đầu tiên trước mọi truy vấn DB hay xác thực chủ sở hữu đơn.
     * Ném 403 FORBIDDEN với mã FORBIDDEN_SIMULATION_IN_PRODUCTION nếu môi trường là PROD hoặc simulation bị tắt.
     */
    public void ensureSimulationAllowed() {
        if ("PROD".equals(this.environment) || !this.simulationEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN_SIMULATION_IN_PRODUCTION");
        }
    }

    public boolean isSimulationAllowed() {
        return !"PROD".equals(this.environment) && this.simulationEnabled;
    }

    public String getEnvironment() {
        return environment;
    }

    public boolean isSimulationEnabled() {
        return simulationEnabled;
    }
}
