package com.oldbutgold.shop.modules.platform.api;

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
