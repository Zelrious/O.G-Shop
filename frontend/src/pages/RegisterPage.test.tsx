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

describe('RegisterPage Clean Centered Layout', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('renders centered card layout by default', () => {
    render(
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    );

    // Centered Form Header content
    expect(screen.getByRole('heading', { name: 'Tạo tài khoản O.G Shop' })).toBeInTheDocument();
    expect(screen.getByText('Tham gia cộng đồng mua bán đồ cũ an toàn và minh bạch.')).toBeInTheDocument();
    expect(screen.getByLabelText(/Họ và tên/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Số điện thoại/)).toBeInTheDocument();

    // Check that compact logo is present
    expect(screen.getByTitle(/Old but Gold/)).toBeInTheDocument();
  });

  it('toggles seller opt-in checkbox cleanly without shifting form layout', () => {
    render(
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    );

    const checkbox = screen.getByLabelText('Mua và bán hàng');
    expect(checkbox).not.toBeChecked();

    fireEvent.click(checkbox);
    expect(checkbox).toBeChecked();
    expect(screen.getByText('Tiếp tục →')).toBeInTheDocument();
  });

  it('updates page title and context when advancing through seller onboarding steps', () => {
    render(
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    );

    fireEvent.change(screen.getByLabelText(/Họ và tên/), { target: { value: 'Seller User' } });
    fireEvent.change(screen.getByLabelText(/Địa chỉ Email/), { target: { value: 'seller@example.com' } });
    fireEvent.change(screen.getByLabelText(/^Số điện thoại/), { target: { value: '0901234567' } });
    fireEvent.change(screen.getByLabelText(/^Mật khẩu/), { target: { value: 'Password123@' } });
    fireEvent.change(screen.getByLabelText(/Xác nhận mật khẩu/), { target: { value: 'Password123@' } });
    fireEvent.click(screen.getByLabelText('Mua và bán hàng'));
    fireEvent.click(screen.getByText('Tiếp tục →'));

    // Step 2 page heading: Địa chỉ lấy hàng
    expect(screen.getByRole('heading', { name: 'Thiết lập địa chỉ lấy hàng' })).toBeInTheDocument();
    expect(screen.getByText(/Cung cấp địa chỉ lấy hàng để bưu tá/)).toBeInTheDocument();
  });
});
