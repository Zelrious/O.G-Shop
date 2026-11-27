package com.oldbutgold.shop.modules.platform.application;

import com.oldbutgold.shop.modules.platform.api.AdminDashboardDtos.DashboardStatsResponse;
import com.oldbutgold.shop.modules.platform.infrastructure.persistence.AdminDashboardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class AdminDashboardServiceTest {

    private AdminDashboardRepository repository;
    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(AdminDashboardRepository.class);
        service = new AdminDashboardService(repository);

        // Default mock behaviors
        when(repository.getTotalPlatformRevenue()).thenReturn(254_750_000L);
        when(repository.getTotalGmv()).thenReturn(10_190_000_000L);
        when(repository.getEscrowBalance()).thenReturn(1_850_000_000L);
        when(repository.getTotalUsers()).thenReturn(3890);
        when(repository.getNewUsersThisMonth()).thenReturn(210);
        when(repository.getDisputeRate()).thenReturn(2.7);
        when(repository.getDisputeCount()).thenReturn(28);

        AdminDashboardRepository realRepo = new AdminDashboardRepository(null);
        when(repository.getMonthlyRevenue()).thenReturn(realRepo.getMonthlyRevenue());
        when(repository.getMonthlyUserGrowth()).thenReturn(realRepo.getMonthlyUserGrowth());
        when(repository.getTopSearchedItems()).thenReturn(realRepo.getTopSearchedItems());
        when(repository.getLeastSearchedItems()).thenReturn(realRepo.getLeastSearchedItems());
        when(repository.getCategoryReturns()).thenReturn(realRepo.getCategoryReturns());
    }

    @Test
    void testGetDashboardStats() {
        DashboardStatsResponse stats = service.getDashboardStats();

        assertNotNull(stats);
        assertEquals(254_750_000L, stats.totalRevenueVnd());
        assertEquals(10_190_000_000L, stats.totalGmvVnd());
        assertEquals(1_850_000_000L, stats.escrowBalanceVnd());
        assertEquals(3890, stats.totalUsers());
        assertEquals(210, stats.newUsersThisMonth());
        assertEquals(2.7, stats.disputeRate());
        assertEquals(28, stats.disputeCount());

        // Trend calculation: (20.5 - 47.25) / 47.25 * 100 = -56.61%
        assertEquals(-56.6, stats.revenueChangePercent());
        assertTrue(stats.trendLabel().contains("Giảm 56,6%"));

        assertEquals(10, stats.monthlyRevenue().size());
        assertEquals(10, stats.userGrowth().size());
        assertEquals(5, stats.topSearchedItems().size());
        assertEquals(5, stats.leastSearchedItems().size());
        assertEquals(5, stats.categoryReturns().size());
    }

    @Test
    void testExportRevenueCsv() {
        byte[] csvBytes = service.exportRevenueCsv();
        assertNotNull(csvBytes);
        assertTrue(csvBytes.length > 3);

        // Check BOM
        assertEquals((byte) 0xEF, csvBytes[0]);
        assertEquals((byte) 0xBB, csvBytes[1]);
        assertEquals((byte) 0xBF, csvBytes[2]);

        String content = new String(csvBytes, 3, csvBytes.length - 3, StandardCharsets.UTF_8);
        assertTrue(content.contains("Tháng,Tổng GMV (₫),Doanh thu phí sàn Escrow (₫),Số đơn hoàn tất"));
        assertTrue(content.contains("Tháng 1/2026,450000000,11250000,140"));
        assertTrue(content.contains("Tháng 10/2026,820000000,20500000,240"));
    }

    @Test
    void testExportUserGrowthCsv() {
        byte[] csvBytes = service.exportUserGrowthCsv();
        assertNotNull(csvBytes);

        String content = new String(csvBytes, 3, csvBytes.length - 3, StandardCharsets.UTF_8);
        assertTrue(content.contains("Tháng,Người dùng mới,Người dùng hoạt động,Tổng người dùng tích lũy"));
        assertTrue(content.contains("Tháng 1/2026,120,540,1200"));
        assertTrue(content.contains("Tháng 10/2026,210,1750,3890"));
    }

    @Test
    void testExportSearchTrendsCsv() {
        byte[] csvBytes = service.exportSearchTrendsCsv();
        assertNotNull(csvBytes);

        String content = new String(csvBytes, 3, csvBytes.length - 3, StandardCharsets.UTF_8);
        assertTrue(content.contains("Phân loại,Tên mặt hàng / Từ khóa,Lượt tìm kiếm trong tháng,Lượt mua hoàn tất,Tỷ lệ chuyển đổi,Ghi chú"));
        assertTrue(content.contains("TOP NHIỀU,iPhone 13 / 14 cũ 99%,14850,312,2.1%"));
        assertTrue(content.contains("TOP ÍT,Băng đĩa cassette cũ,42,1,2.4%"));
    }

    @Test
    void testExportCategoryReturnsCsvWithFilter() {
        // Without filter: all 5 categories
        byte[] allBytes = service.exportCategoryReturnsCsv(null);
        String allContent = new String(allBytes, 3, allBytes.length - 3, StandardCharsets.UTF_8);
        assertTrue(allContent.contains("Điện thoại & Máy tính bảng"));
        assertTrue(allContent.contains("Máy ảnh & Quang học"));
        assertTrue(allContent.contains("Thời trang & Túi vintage"));

        // With filter: only selected categories
        byte[] filteredBytes = service.exportCategoryReturnsCsv(List.of("Điện thoại & Máy tính bảng"));
        String filteredContent = new String(filteredBytes, 3, filteredBytes.length - 3, StandardCharsets.UTF_8);
        assertTrue(filteredContent.contains("Điện thoại & Máy tính bảng"));
        assertFalse(filteredContent.contains("Máy ảnh & Quang học"));
        assertFalse(filteredContent.contains("Thời trang & Túi vintage"));
    }

    @Test
    void testEscapeCsvNeutralizesFormulaInjectionAndQuotesSpecialChars() {
        assertEquals("", AdminDashboardService.escapeCsv(null));
        assertEquals("'=SUM(A1:A2)", AdminDashboardService.escapeCsv("=SUM(A1:A2)"));
        assertEquals("'+1+1", AdminDashboardService.escapeCsv("+1+1"));
        assertEquals("'-cmd|' /C calc'!A0", AdminDashboardService.escapeCsv("-cmd|' /C calc'!A0"));
        assertEquals("'@SUM(1+1)*cmd", AdminDashboardService.escapeCsv("@SUM(1+1)*cmd"));
        assertEquals("'  =1+1", AdminDashboardService.escapeCsv("  =1+1"));
        assertEquals("'\t=cmd()", AdminDashboardService.escapeCsv("\t=cmd()"));
        assertEquals("\"'\r=cmd()\"", AdminDashboardService.escapeCsv("\r=cmd()"));
        assertEquals("\"a,b\"\"c\nd\"", AdminDashboardService.escapeCsv("a,b\"c\nd"));
        assertEquals("\"line1\rline2\"", AdminDashboardService.escapeCsv("line1\rline2"));
    }
}
