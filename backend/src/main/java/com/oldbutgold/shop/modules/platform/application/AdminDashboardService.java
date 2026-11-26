package com.oldbutgold.shop.modules.platform.application;

import com.oldbutgold.shop.modules.platform.api.AdminDashboardDtos.*;
import com.oldbutgold.shop.modules.platform.infrastructure.persistence.AdminDashboardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.List;
import java.util.Locale;

@Service
public class AdminDashboardService {

    private final AdminDashboardRepository repository;
    private Clock clock = Clock.systemUTC();
    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    public AdminDashboardService(AdminDashboardRepository repository) {
        this.repository = repository;
    }

    @Autowired(required = false)
    public void setClock(Clock clock) {
        if (clock != null) {
            this.clock = clock;
        }
    }

    public DashboardStatsResponse getDashboardStats() {
        long totalRevenue = repository.getTotalPlatformRevenue();
        long totalGmv = repository.getTotalGmv();
        long escrowBalance = repository.getEscrowBalance();
        int totalUsers = repository.getTotalUsers();
        int newUsersThisMonth = repository.getNewUsersThisMonth();
        double disputeRate = repository.getDisputeRate();
        int disputeCount = repository.getDisputeCount();

        List<MonthlyRevenuePoint> monthlyRevenue = repository.getMonthlyRevenue();
        List<MonthlyUserGrowthPoint> userGrowth = repository.getMonthlyUserGrowth();
        List<SearchTrendItem> topSearched = repository.getTopSearchedItems();
        List<SearchTrendItem> leastSearched = repository.getLeastSearchedItems();
        List<CategoryReturnStat> categoryReturns = repository.getCategoryReturns();

        double revenueChangePercent = 0.0;
        String trendLabel = "Tăng 0.0%";
        if (monthlyRevenue.size() >= 2) {
            MonthlyRevenuePoint latest = monthlyRevenue.get(monthlyRevenue.size() - 1);
            MonthlyRevenuePoint previous = monthlyRevenue.get(monthlyRevenue.size() - 2);
            if (previous.revenue() > 0) {
                revenueChangePercent = (latest.revenue() - previous.revenue()) / previous.revenue() * 100.0;
                double absChange = Math.abs(revenueChangePercent);
                String formatted = String.format(Locale.GERMANY, "%.1f%%", absChange);
                trendLabel = (revenueChangePercent < 0 ? "Giảm " : "Tăng ") + formatted;
            }
        }

        return new DashboardStatsResponse(
                totalRevenue,
                totalGmv,
                escrowBalance,
                totalUsers,
                newUsersThisMonth,
                disputeRate,
                disputeCount,
                Math.round(revenueChangePercent * 10.0) / 10.0,
                trendLabel,
                monthlyRevenue,
                userGrowth,
                topSearched,
                leastSearched,
                categoryReturns
        );
    }

    public ReturnsReportResponse getReturnsReport(
            LocalDate fromDate,
            LocalDate toDate,
            String timezone,
            String caseType,
            List<String> categories,
            Instant requestedCutoffAt
    ) {
        String effectiveTz = (timezone == null || timezone.isBlank()) ? "Asia/Ho_Chi_Minh" : timezone.trim();
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(effectiveTz);
        } catch (ZoneRulesException ex) {
            throw new IllegalArgumentException("Múi giờ báo cáo không hợp lệ: " + effectiveTz);
        }

        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        LocalDate effectiveTo = toDate != null ? toDate : today;
        LocalDate effectiveFrom = fromDate != null ? fromDate : effectiveTo.minusDays(30);
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new IllegalArgumentException("Khoảng thời gian báo cáo không hợp lệ: fromDate không được lớn hơn toDate.");
        }

        String effectiveType = (caseType == null || caseType.isBlank()) ? "ALL" : caseType.trim().toUpperCase(Locale.ROOT);
        if (!"ALL".equals(effectiveType) && !"RETURN".equals(effectiveType) && !"SYSTEM_ISSUE".equals(effectiveType)) {
            throw new IllegalArgumentException("Loại hồ sơ lọc không hợp lệ: " + effectiveType);
        }

        Instant fromInstant = effectiveFrom.atStartOfDay(zoneId).toInstant();
        Instant toExclusiveInstant = effectiveTo.plusDays(1).atStartOfDay(zoneId).toInstant();
        Instant effectiveCutoff = requestedCutoffAt != null ? requestedCutoffAt : clock.instant();

        return repository.queryResolvedCasesReport(
                fromInstant,
                toExclusiveInstant,
                effectiveCutoff,
                effectiveTz,
                effectiveFrom,
                effectiveTo,
                effectiveType,
                categories
        );
    }

    public byte[] exportReturnsReportCsv(
            Long adminId,
            LocalDate fromDate,
            LocalDate toDate,
            String timezone,
            String caseType,
            List<String> categories,
            Instant cutoffAt
    ) {
        ReturnsReportResponse report = getReturnsReport(fromDate, toDate, timezone, caseType, categories, cutoffAt);
        var filters = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        filters.put("fromDate", report.fromDate().toString());
        filters.put("toDate", report.toDate().toString());
        filters.put("caseType", report.caseTypeFilter());
        var categoryFilter = filters.putArray("categories");
        if (categories != null) categories.forEach(categoryFilter::add);
        repository.recordExportRun(adminId, "RETURN_CASES", filters.toString(), report.timezone(), report.cutoffAt());

        StringBuilder sb = new StringBuilder();
        sb.append("Thời điểm tổng hợp (cutoffAt),Múi giờ,Từ ngày,Đến ngày,Loại hồ sơ,Tổng hồ sơ đã xử lý,Trả hàng hoàn tiền,Trả hàng miễn hoàn,Trả hàng bị bác,Buyer rút yêu cầu,Lỗi hệ thống xác nhận,Lỗi hệ thống bị bác,Hồ sơ đang chờ cuối kỳ\n");
        sb.append(String.format("%s,%s,%s,%s,%s,%d,%d,%d,%d,%d,%d,%d,%d\n\n",
                report.cutoffAt(),
                escapeCsv(report.timezone()),
                report.fromDate(),
                report.toDate(),
                report.caseTypeFilter(),
                report.totalResolvedCases(),
                report.returnRefundedCount(),
                report.returnExemptNoRefundCount(),
                report.returnRejectedCount(),
                report.returnWithdrawnCount(),
                report.systemIssueConfirmedCount(),
                report.systemIssueRejectedCount(), report.openPendingCount()
        ));
        sb.append("Danh mục,Tổng hồ sơ đã đóng,Số hồ sơ trả hàng,Số hồ sơ lỗi hệ thống,Tỷ trọng hồ sơ trả hàng (%)\n");
        for (CategoryReturnStat item : report.categoryBreakdown()) {
            sb.append(String.format("%s,%d,%d,%d,%s\n",
                    escapeCsv(item.category()), item.totalOrders(), item.returnCount(), item.complaintCount(), item.returnRate()));
        }
        sb.append("\nCase ID,Loại hồ sơ,Phân loại kết quả,Trạng thái cuối,Đơn hàng ID,Danh mục,Thời điểm đóng,Nguồn\n");
        for (ReturnReportRow row : report.resolvedCases()) {
            sb.append(String.format("%d,%s,%s,%s,%s,%s,%s,%s\n",
                    row.caseId(),
                    escapeCsv(row.caseType()),
                    escapeCsv(row.outcomeBucket()),
                    escapeCsv(row.finalState()),
                    row.orderId() != null ? row.orderId().toString() : "",
                    escapeCsv(row.category()),
                    row.closedAt(), row.source()));
        }
        return prependBom(sb.toString());
    }

    public byte[] exportRevenueCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Tháng,Tổng GMV (₫),Doanh thu phí sàn Escrow (₫),Số đơn hoàn tất\n");
        List<MonthlyRevenuePoint> list = repository.getMonthlyRevenue();
        for (MonthlyRevenuePoint p : list) {
            long gmvVnd = (long) (p.gmv() * 1_000_000L);
            long feeVnd = (long) (p.revenue() * 1_000_000L);
            sb.append(String.format("Tháng %s/2026,%d,%d,%d\n", p.month().replace("T", ""), gmvVnd, feeVnd, p.orderCount()));
        }
        return prependBom(sb.toString());
    }

    public byte[] exportUserGrowthCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Tháng,Người dùng mới,Người dùng hoạt động,Tổng người dùng tích lũy\n");
        List<MonthlyUserGrowthPoint> list = repository.getMonthlyUserGrowth();
        for (MonthlyUserGrowthPoint p : list) {
            int activeUsers = (int) (p.totalUsers() * 0.45);
            sb.append(String.format("Tháng %s/2026,%d,%d,%d\n", p.month().replace("T", ""), p.newUsers(), activeUsers, p.totalUsers()));
        }
        return prependBom(sb.toString());
    }

    public byte[] exportSearchTrendsCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Phân loại,Tên mặt hàng / Từ khóa,Lượt tìm kiếm trong tháng,Lượt mua hoàn tất,Tỷ lệ chuyển đổi,Ghi chú\n");
        for (SearchTrendItem item : repository.getTopSearchedItems()) {
            sb.append(String.format("TOP NHIỀU,%s,%d,%d,%s,%s\n", escapeCsv(item.name()), item.searches(), item.orders(), item.rate(), escapeCsv(item.note())));
        }
        for (SearchTrendItem item : repository.getLeastSearchedItems()) {
            sb.append(String.format("TOP ÍT,%s,%d,%d,%s,%s\n", escapeCsv(item.name()), item.searches(), item.orders(), item.rate(), escapeCsv(item.note())));
        }
        return prependBom(sb.toString());
    }

    public byte[] exportCategoryReturnsCsv(List<String> selectedCategories) {
        StringBuilder sb = new StringBuilder();
        sb.append("Danh mục,Số đơn bán,Số đơn yêu cầu trả hàng,Số vụ khiếu nại tranh chấp,Tỷ lệ hoàn hàng (%),Lý do chính\n");
        List<CategoryReturnStat> list = repository.getCategoryReturns();
        for (CategoryReturnStat item : list) {
            if (selectedCategories != null && !selectedCategories.isEmpty() && !selectedCategories.contains(item.category())) {
                continue;
            }
            sb.append(String.format("%s,%d,%d,%d,%s,%s\n", escapeCsv(item.category()), item.totalOrders(), item.returnCount(), item.complaintCount(), item.returnRate(), escapeCsv(item.primaryIssue())));
        }
        return prependBom(sb.toString());
    }

    private static byte[] prependBom(String content) {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[UTF8_BOM.length + contentBytes.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(contentBytes, 0, result, UTF8_BOM.length, contentBytes.length);
        return result;
    }

    private static String escapeCsv(String str) {
        if (str == null) return "";
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }
}
