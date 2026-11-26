package com.oldbutgold.shop.modules.platform.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class AdminDashboardDtos {

    public record MonthlyRevenuePoint(
            String month,
            double gmv,
            double revenue,
            int orderCount
    ) {}

    public record MonthlyUserGrowthPoint(
            String month,
            int newUsers,
            int totalUsers
    ) {}

    public record SearchTrendItem(
            String name,
            int searches,
            int orders,
            String rate,
            String note
    ) {}

    public record CategoryReturnStat(
            String category,
            int totalOrders,
            int returnCount,
            int complaintCount,
            String returnRate,
            String primaryIssue
    ) {}

    public record ReturnReportRow(
            Long caseId,
            String caseType,
            String outcomeBucket,
            String finalState,
            Long orderId,
            String category,
            Instant closedAt,
            String source
    ) {}

    public record ReturnsReportResponse(
            Instant cutoffAt,
            String timezone,
            LocalDate fromDate,
            LocalDate toDate,
            String caseTypeFilter,
            long totalResolvedCases,
            long returnRefundedCount,
            long returnExemptNoRefundCount,
            long returnRejectedCount,
            long returnWithdrawnCount,
            long systemIssueConfirmedCount,
            long systemIssueRejectedCount,
            long openPendingCount,
            List<CategoryReturnStat> categoryBreakdown,
            List<ReturnReportRow> resolvedCases
    ) {
        public ReturnsReportResponse {
            categoryBreakdown = categoryBreakdown != null ? List.copyOf(categoryBreakdown) : List.of();
            resolvedCases = resolvedCases != null ? List.copyOf(resolvedCases) : List.of();
        }
    }

    public record DashboardStatsResponse(
            long totalRevenueVnd,
            long totalGmvVnd,
            long escrowBalanceVnd,
            int totalUsers,
            int newUsersThisMonth,
            double disputeRate,
            int disputeCount,
            double revenueChangePercent,
            String trendLabel,
            List<MonthlyRevenuePoint> monthlyRevenue,
            List<MonthlyUserGrowthPoint> userGrowth,
            List<SearchTrendItem> topSearchedItems,
            List<SearchTrendItem> leastSearchedItems,
            List<CategoryReturnStat> categoryReturns
    ) {}
}
