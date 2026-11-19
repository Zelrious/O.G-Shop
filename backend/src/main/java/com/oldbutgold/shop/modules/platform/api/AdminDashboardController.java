package com.oldbutgold.shop.modules.platform.api;

import com.oldbutgold.shop.modules.platform.api.AdminDashboardDtos.DashboardStatsResponse;
import com.oldbutgold.shop.modules.platform.application.AdminDashboardService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    public AdminDashboardController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getStats() {
        return ResponseEntity.ok(dashboardService.getDashboardStats());
    }

    @GetMapping("/export/revenue")
    public ResponseEntity<byte[]> exportRevenue() {
        byte[] csv = dashboardService.exportRevenueCsv();
        return createCsvResponse("bao-cao-doanh-thu-phi-san.csv", csv);
    }

    @GetMapping("/export/users")
    public ResponseEntity<byte[]> exportUsers() {
        byte[] csv = dashboardService.exportUserGrowthCsv();
        return createCsvResponse("bao-cao-so-luong-nguoi-dung.csv", csv);
    }

    @GetMapping("/export/searches")
    public ResponseEntity<byte[]> exportSearches() {
        byte[] csv = dashboardService.exportSearchTrendsCsv();
        return createCsvResponse("thong-ke-mat-hang-tim-mua-nhieu-it.csv", csv);
    }

    @GetMapping("/export/returns")
    public ResponseEntity<byte[]> exportReturns(
            @RequestParam(name = "categories", required = false) List<String> categories
    ) {
        byte[] csv = dashboardService.exportCategoryReturnsCsv(categories);
        return createCsvResponse("thong-ke-hang-bi-tra-va-khieu-nai.csv", csv);
    }

    private ResponseEntity<byte[]> createCsvResponse(String filename, byte[] content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", filename);
        return ResponseEntity.ok()
                .headers(headers)
                .body(content);
    }
}
