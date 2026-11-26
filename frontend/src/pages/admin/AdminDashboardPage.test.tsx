import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AdminDashboardPage } from './AdminDashboardPage';
import { managementStore } from '../../features/management/managementStore';
import { adminDashboardApi } from '../../features/management/adminDashboardApi';

describe('AdminDashboardPage Component', () => {
  it('puts the revenue chart and calculated month comparison inside the main card', () => {
    render(<MemoryRouter><AdminDashboardPage /></MemoryRouter>);
    const card = screen.getByRole('article', { name: 'Tổng doanh thu phí sàn (Năm 2026)' });
    expect(card).toHaveTextContent('Giảm 56,6%');
    expect(card).toHaveTextContent('Tháng 10 chưa kết thúc');
    expect(card.querySelector('figure')).toHaveAttribute('aria-label', 'Doanh thu phí sàn theo tháng, đơn vị triệu đồng');
    expect(card.parentElement?.querySelectorAll('.og-report-secondary > article')).toHaveLength(3);
  });
  it('renders revenue charts, user stats, top searches, and triggers CSV export', () => {
    const exportRevenueSpy = vi.spyOn(managementStore, 'exportRevenueDataCsv').mockImplementation(() => {});
    const exportUserSpy = vi.spyOn(managementStore, 'exportUserDataCsv').mockImplementation(() => {});

    render(
      <MemoryRouter>
        <AdminDashboardPage />
      </MemoryRouter>
    );

    // Title
    expect(screen.getByRole('heading', { level: 1, name: /Tổng quan doanh thu & Báo cáo quản trị/i })).toBeInTheDocument();

    // Check KPIs
    expect(screen.getByText('Tổng doanh thu phí sàn (Năm 2026)')).toBeInTheDocument();
    expect(screen.getByText('Tổng giá trị hàng hóa (GMV)')).toBeInTheDocument();

    // Check Export Buttons
    const exportRevBtn = screen.getByRole('button', { name: /Kết xuất báo cáo doanh thu \(CSV\)/i });
    expect(exportRevBtn).toBeInTheDocument();
    fireEvent.click(exportRevBtn);
    expect(exportRevenueSpy).toHaveBeenCalled();

    const exportUserBtn = screen.getByRole('button', { name: /Kết xuất báo cáo người dùng \(CSV\)/i });
    expect(exportUserBtn).toBeInTheDocument();
    fireEvent.click(exportUserBtn);
    expect(exportUserSpy).toHaveBeenCalled();

    // Check Top Searched Items
    expect(screen.getByText('iPhone 13 / 14 cũ 99%')).toBeInTheDocument();
    expect(screen.getByText('Máy ảnh Fujifilm X-T series')).toBeInTheDocument();
  });

  it('keeps the visible report when export fails and retries the same snapshot', async () => {
    vi.spyOn(adminDashboardApi,'getReturnsReport').mockResolvedValueOnce({
      cutoffAt: '2026-11-25T10:00:00Z',
      timezone: 'Asia/Ho_Chi_Minh',
      fromDate: '2026-11-01',
      toDate: '2026-11-25',
      caseTypeFilter: 'ALL',
      totalResolvedCases: 3,
      returnRefundedCount: 1,
      returnExemptNoRefundCount: 0,
      returnRejectedCount: 0,
      returnWithdrawnCount: 1,
      systemIssueConfirmedCount: 1,
      systemIssueRejectedCount: 0,
      categoryBreakdown: [],
      resolvedCases: [],
    });
    const download = vi.spyOn(adminDashboardApi,'downloadCategoryReturnsCsv').mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce();
    const fake = vi.spyOn(managementStore,'exportReturnComplaintCsv');
    render(<MemoryRouter><AdminDashboardPage /></MemoryRouter>);
    await screen.findByText('Hồ sơ đã chốt');
    const button = screen.getByRole('button',{name:/Kết xuất báo cáo khiếu nại/});
    fireEvent.click(button);
    await screen.findByText(/Chưa xuất được tệp/);
    expect(screen.getByText('Hồ sơ đã chốt')).toBeInTheDocument();
    fireEvent.click(button);
    await waitFor(() => expect(download).toHaveBeenCalledTimes(2));
    expect(download.mock.calls[0]).toEqual(download.mock.calls[1]);
    expect(fake).not.toHaveBeenCalled();
  });
  it('distinguishes a failed query from a genuinely empty period', async () => {
    vi.spyOn(adminDashboardApi,'getReturnsReport').mockRejectedValueOnce(new Error('Chưa tổng hợp được dữ liệu'));
    render(<MemoryRouter><AdminDashboardPage /></MemoryRouter>);
    await screen.findByText(/Chưa tổng hợp được dữ liệu/);
    expect(screen.queryByText('Hồ sơ đã chốt')).toBeNull();
    expect(screen.queryByText('Điện thoại & Máy tính bảng')).toBeNull();
  });
  it('renders an empty period with its pending balance and no category chart', async () => {
    vi.spyOn(adminDashboardApi,'getReturnsReport').mockResolvedValueOnce({
      cutoffAt:'2026-11-25T10:00:00Z',timezone:'Asia/Ho_Chi_Minh',fromDate:'2026-11-01',toDate:'2026-11-25',
      caseTypeFilter:'ALL',totalResolvedCases:0,returnRefundedCount:0,returnExemptNoRefundCount:0,
      returnRejectedCount:0,returnWithdrawnCount:0,systemIssueConfirmedCount:0,systemIssueRejectedCount:0,
      openPendingCount:2,categoryBreakdown:[],resolvedCases:[]
    });
    render(<MemoryRouter><AdminDashboardPage /></MemoryRouter>);
    await screen.findByText(/Chưa có dữ liệu phát sinh/);
    expect(screen.getByText(/Hồ sơ đang chờ cuối kỳ: 2/)).toBeInTheDocument();
    expect(screen.queryByRole('figure',{name:/Biểu đồ hồ sơ/})).toBeNull();
  });
  it('uses the same category and cutoff for the chart, table and export', async () => {
    const cats=['Điện tử','Quần áo'].map(category => ({category,totalOrders:1,returnCount:1,complaintCount:0,returnRate:'100%',primaryIssue:''}));
    vi.spyOn(adminDashboardApi,'getReturnsReport').mockImplementation(async filters => {
      const rows=cats.filter(row => !filters?.categories || filters.categories.includes(row.category));
      return {cutoffAt:'2026-11-25T10:00:00Z',timezone:'Asia/Ho_Chi_Minh',fromDate:'2026-11-01',toDate:'2026-11-25',
        caseTypeFilter:'ALL',totalResolvedCases:rows.length,returnRefundedCount:rows.length,returnExemptNoRefundCount:0,
        returnRejectedCount:0,returnWithdrawnCount:0,systemIssueConfirmedCount:0,systemIssueRejectedCount:0,
        openPendingCount:0,categoryBreakdown:rows,resolvedCases:[]};
    });
    const csv=vi.spyOn(adminDashboardApi,'downloadCategoryReturnsCsv').mockResolvedValue();
    render(<MemoryRouter><AdminDashboardPage /></MemoryRouter>);
    fireEvent.click(await screen.findByLabelText('Chọn ngành hàng Điện tử'));
    await waitFor(() => expect(screen.queryByLabelText('Chọn ngành hàng Quần áo')).toBeNull());
    expect(screen.getAllByRole('meter')).toHaveLength(1);
    expect(screen.getByRole('option',{name:'Quần áo'})).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:/Kết xuất báo cáo khiếu nại/}));
    expect(csv).toHaveBeenCalledWith(['Điện tử'],expect.objectContaining({cutoffAt:'2026-11-25T10:00:00Z'}));
  });
  it('loads UC81 report snapshot and passes exact cutoffAt to CSV export, surfacing Exc 4a on failure', async () => {
    const reportSpy = vi.spyOn(adminDashboardApi, 'getReturnsReport').mockResolvedValueOnce({
      cutoffAt: '2026-11-25T10:00:00Z',
      timezone: 'Asia/Ho_Chi_Minh',
      fromDate: '2026-11-01',
      toDate: '2026-11-25',
      caseTypeFilter: 'ALL',
      totalResolvedCases: 3,
      returnRefundedCount: 1,
      returnExemptNoRefundCount: 0,
      returnRejectedCount: 0,
      returnWithdrawnCount: 1,
      systemIssueConfirmedCount: 1,
      systemIssueRejectedCount: 0,
      categoryBreakdown: [],
      resolvedCases: [],
    });
    const csvSpy = vi.spyOn(adminDashboardApi, 'downloadCategoryReturnsCsv').mockResolvedValueOnce();
    vi.spyOn(managementStore, 'exportReturnComplaintCsv').mockImplementation(() => {});

    render(
      <MemoryRouter>
        <AdminDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(reportSpy).toHaveBeenCalled();
      expect(screen.getByText(/Hồ sơ đã chốt/)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Kết xuất báo cáo khiếu nại \(CSV\)/i }));
    await waitFor(() => {
      expect(csvSpy).toHaveBeenCalledWith(undefined, expect.objectContaining({
        cutoffAt: '2026-11-25T10:00:00Z',
      }));
    });
  });
});
