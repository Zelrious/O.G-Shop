import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AdminDashboardPage } from './AdminDashboardPage';
import { managementStore } from '../../features/management/managementStore';

describe('AdminDashboardPage Component', () => {
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
});
