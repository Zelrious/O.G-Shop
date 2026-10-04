import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AdminLayoutShell } from './AdminLayoutShell';
import * as authHook from '../../features/auth';

vi.mock('../../features/auth', () => ({
  useAuth: vi.fn(),
}));

describe('AdminLayoutShell Component', () => {
  it('renders Admin header, role badge, navigation links without emojis or AD codes, and logout button', () => {
    (authHook.useAuth as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
      user: {
        userId: 1,
        fullName: 'Admin Tổng Quản',
        email: 'admin@ogshop.vn',
        roles: ['ADMIN'],
      },
      isAuthenticated: true,
      logout: vi.fn(),
    });

    render(
      <MemoryRouter>
        <AdminLayoutShell />
      </MemoryRouter>
    );

    // Verify Brand & user
    expect(screen.getByText('Quản trị viên')).toBeInTheDocument();
    expect(screen.getByText('Admin Tổng Quản')).toBeInTheDocument();

    // Verify all 7 Admin Navigation Links (NO AD-XX codes)
    expect(screen.getByRole('link', { name: /Tổng quan doanh thu/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Quản lý tài khoản KTV/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /^Thông báo/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Lịch sử thao tác hệ thống/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Quản lý phí hệ thống/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Tạo & Quản lý Voucher/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Điều hướng thông báo/i })).toBeInTheDocument();

    // Verify NO "Về sàn mua sắm" link exists
    expect(screen.queryByText(/Về sàn mua sắm/i)).toBeNull();

    // Verify Logout button exists
    expect(screen.getByRole('button', { name: /Đăng xuất hệ thống/i })).toBeInTheDocument();
  });
});
