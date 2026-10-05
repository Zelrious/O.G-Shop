import { render, screen, fireEvent, within } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AppHeader } from './AppHeader';
import * as authHook from '../../features/auth';

vi.mock('../../features/auth', () => ({
  useAuth: vi.fn(),
}));

describe('AppHeader Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders brand logo without underline and shows guest buttons when not authenticated', () => {
    (authHook.useAuth as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
      user: null,
      isAuthenticated: false,
      logout: vi.fn(),
    });

    render(
      <MemoryRouter>
        <AppHeader />
      </MemoryRouter>
    );

    const brandLink = screen.getByRole('link', { name: /Old but Gold/i });
    expect(brandLink).toBeInTheDocument();
    expect(brandLink).toHaveStyle({ textDecoration: 'none' });
    expect(screen.getByRole('link', { name: 'Đăng nhập' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Đăng ký' })).toBeInTheDocument();
  });

  it('renders user trigger and toggles dropdown menu with Profile and Logout when clicked', () => {
    const mockLogout = vi.fn();
    (authHook.useAuth as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
      user: {
        userId: 1,
        fullName: 'Huỳnh Văn A',
        email: 'huynh@example.com',
        roles: ['BUYER'],
      },
      isAuthenticated: true,
      logout: mockLogout,
    });

    render(
      <MemoryRouter>
        <AppHeader />
      </MemoryRouter>
    );

    // Standalone logout button should NOT exist on header bar
    expect(screen.queryByRole('button', { name: /^Đăng xuất$/ })).toBeNull();

    // User trigger button should be visible with full name (not just surname)
    const userTrigger = screen.getByLabelText(/Menu người dùng: Huỳnh Văn A/i);
    expect(userTrigger).toBeInTheDocument();
    expect(screen.getByText('Huỳnh Văn A')).toBeInTheDocument();

    // Navigation tabs are present with synchronized styling
    expect(screen.getByRole('link', { name: /Mua sắm/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Đơn mua/i })).toBeInTheDocument();

    // Dropdown is initially closed
    expect(screen.queryByRole('menu')).toBeNull();

    // Click trigger to open dropdown
    fireEvent.click(userTrigger);

    const menu = screen.getByRole('menu');
    expect(menu).toBeInTheDocument();
    expect(screen.getAllByText('Huỳnh Văn A').length).toBe(2);
    expect(screen.getByText('huynh@example.com')).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: /Hồ sơ & Sổ địa chỉ/i })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: /Đơn mua của tôi/i })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: /Đăng xuất/i })).toBeInTheDocument();

    // Clicking logout inside dropdown opens confirmation dialog
    fireEvent.click(screen.getByRole('menuitem', { name: /Đăng xuất/i }));
    expect(screen.getByText('Xác nhận đăng xuất')).toBeInTheDocument();
  });

  it('renders tab toggle button when scrolled and toggles tab bar', () => {
    (authHook.useAuth as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
      user: null,
      isAuthenticated: false,
      logout: vi.fn(),
    });

    render(
      <MemoryRouter>
        <AppHeader />
      </MemoryRouter>
    );

    // Initially at top: toggle button is not visible
    expect(screen.queryByRole('button', { name: /menu tab/i })).toBeNull();

    // Simulate scroll down
    Object.defineProperty(window, 'scrollY', { value: 100, writable: true });
    fireEvent.scroll(window);

    // Toggle button should now appear
    const toggleBtn = screen.getByRole('button', { name: /menu tab/i });
    expect(toggleBtn).toBeInTheDocument();

    // Clicking toggle button expands tabs
    fireEvent.click(toggleBtn);
    expect(toggleBtn).toHaveAttribute('aria-expanded', 'true');

    // Clicking again collapses
    fireEvent.click(toggleBtn);
    expect(toggleBtn).toHaveAttribute('aria-expanded', 'false');
  });

  it('renders "Trang chủ" tab and category dropdown with auto-hover behavior', () => {
    (authHook.useAuth as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
      user: null,
      isAuthenticated: false,
      logout: vi.fn(),
    });

    render(
      <MemoryRouter>
        <AppHeader />
      </MemoryRouter>
    );

    // Tab "Trang chủ" is rendered and points to "/"
    const nav = screen.getByRole('navigation', { name: /Điều hướng chính/i });
    const homeTab = within(nav).getByRole('link', { name: /Trang chủ/i });
    expect(homeTab).toBeInTheDocument();
    expect(homeTab).toHaveAttribute('href', '/');

    // "Danh mục sản phẩm" button exists
    const categoryBtn = screen.getByRole('button', { name: /Danh mục sản phẩm/i });
    expect(categoryBtn).toBeInTheDocument();

    // Hovering opens category menu
    const categoryWrap = categoryBtn.closest('.og-header__category-wrap');
    expect(categoryWrap).not.toBeNull();
    if (categoryWrap) {
      fireEvent.mouseEnter(categoryWrap);
    }

    // Category items are visible
    expect(screen.getByRole('menuitem', { name: /Tất cả sản phẩm cũ/i })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: /Điện tử/i })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: /Thời trang/i })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: /Đồ sưu tầm/i })).toBeInTheDocument();
  });

  it('renders user avatar image when avatarUrl is present and falls back on error', () => {
    (authHook.useAuth as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
      user: {
        userId: 2,
        fullName: 'Trần Thị B',
        email: 'tran@example.com',
        avatarUrl: 'https://res.cloudinary.com/demo/image/upload/v1/avatar.jpg',
        roles: ['BUYER'],
      },
      isAuthenticated: true,
      logout: vi.fn(),
    });

    render(
      <MemoryRouter>
        <AppHeader />
      </MemoryRouter>
    );

    const avatarImg = screen.getByRole('img', { hidden: true });
    expect(avatarImg).toHaveAttribute('src', 'https://res.cloudinary.com/demo/image/upload/v1/avatar.jpg');

    // Simulate image error
    fireEvent.error(avatarImg);
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.getByText('T')).toBeVisible();
  });
});
