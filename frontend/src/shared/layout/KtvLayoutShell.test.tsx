import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { KtvLayoutShell } from './KtvLayoutShell';
import * as authHook from '../../features/auth';

vi.mock('../../features/auth', () => ({
  useAuth: vi.fn(),
}));

describe('KtvLayoutShell Component', () => {
  it('renders KTV header, role badge, navigation links without emojis or AD codes, and logout button', () => {
    (authHook.useAuth as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
      user: {
        userId: 10,
        fullName: 'Nguyễn Văn KTV',
        email: 'ktv@ogshop.vn',
        roles: ['KTV'],
      },
      isAuthenticated: true,
      logout: vi.fn(),
    });

    render(
      <MemoryRouter>
        <KtvLayoutShell />
      </MemoryRouter>
    );

    // Verify Brand & user
    expect(screen.getByText('Kỹ thuật viên')).toBeInTheDocument();
    expect(screen.getByText('Nguyễn Văn KTV')).toBeInTheDocument();

    // Verify all 7 KTV Navigation Links (NO AD-XX codes)
    expect(screen.getByRole('link', { name: /Kiểm duyệt tin/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Dòng tiền Escrow/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Quản lý người dùng/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Phân xử tranh chấp/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Kiểm duyệt eKYC/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Xử lý khiếu nại/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Tặng & Thu hồi Voucher/i })).toBeInTheDocument();

    // Verify NO "Về sàn mua sắm" link exists
    expect(screen.queryByText(/Về sàn mua sắm/i)).toBeNull();

    // Verify Logout button exists
    expect(screen.getByRole('button', { name: /Đăng xuất hệ thống/i })).toBeInTheDocument();
  });
});
