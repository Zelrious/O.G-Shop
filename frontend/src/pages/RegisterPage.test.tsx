import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { RegisterPage } from './RegisterPage';

const auth = vi.hoisted(() => ({ register: vi.fn(), verifyRegistration: vi.fn() }));
vi.mock('../features/auth/components/GoogleSignInButton', () => ({ GoogleSignInButton: () => null }));
vi.mock('../features/auth/useAuth', () => ({ useAuth: () => auth }));
vi.mock('../features/auth/authApi', () => ({
  authApi: { resendRegistration: vi.fn(), cancelRegistration: vi.fn() },
}));

describe('RegisterPage Split-Screen', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('renders split-screen layout with buyer visual by default', () => {
    render(
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    );

    // Left Visual Panel content
    expect(screen.getByText('Tìm lại giá trị trong những món đồ cũ.')).toBeInTheDocument();
    expect(screen.getByText(/Khám phá hàng ngàn sản phẩm độc đáo/)).toBeInTheDocument();

    // Right Form Panel content
    expect(screen.getByRole('heading', { name: 'Tạo tài khoản O.G Shop' })).toBeInTheDocument();
    expect(screen.getByLabelText(/Họ và tên/)).toBeInTheDocument();
  });

  it('transitions visual panel when role changes from buyer to seller', () => {
    render(
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    );

    // Select Seller card
    fireEvent.click(screen.getByLabelText('Mua và bán hàng'));

    // Seller visual text is rendered
    expect(screen.getByText('Biến món đồ cũ thành giá trị mới.')).toBeInTheDocument();
    expect(screen.getByText(/Tạo gian hàng, đăng sản phẩm nhanh chóng/)).toBeInTheDocument();
  });

  it('updates page title and context when advancing to seller onboarding step', () => {
    render(
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    );

    fireEvent.change(screen.getByLabelText(/Họ và tên/), { target: { value: 'Seller User' } });
    fireEvent.change(screen.getByLabelText(/Địa chỉ Email/), { target: { value: 'seller@example.com' } });
    fireEvent.change(screen.getByLabelText(/^Mật khẩu/), { target: { value: 'Password123@' } });
    fireEvent.change(screen.getByLabelText(/Xác nhận mật khẩu/), { target: { value: 'Password123@' } });
    fireEvent.click(screen.getByLabelText('Mua và bán hàng'));
    fireEvent.click(screen.getByText('Tiếp tục →'));

    // Step 2 page heading
    expect(screen.getByRole('heading', { name: 'Thiết lập thông tin người bán' })).toBeInTheDocument();
    expect(screen.getByText(/Thiết lập địa chỉ lấy hàng và tài khoản ngân hàng/)).toBeInTheDocument();
  });
});
