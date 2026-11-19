package com.oldbutgold.shop.modules.platform.application;

import com.oldbutgold.shop.modules.platform.api.AdminDashboardDtos.*;
import com.oldbutgold.shop.modules.platform.infrastructure.persistence.AdminDashboardRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
public class AdminDashboardService {

    private final AdminDashboardRepository repository;
    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    public AdminDashboardService(AdminDashboardRepository repository) {
        this.repository = repository;
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
                String formatted = String.format(Locale.GERMANY, "%.1f%%", absChange); // uses comma as decimal separator in vi/de
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
