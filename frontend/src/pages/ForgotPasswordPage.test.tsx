import { render, screen, fireEvent } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ForgotPasswordPage } from './ForgotPasswordPage';
import { authApi } from '../features/auth/authApi';

vi.mock('../features/auth/authApi', () => ({
  authApi: {
    startReset: vi.fn(),
    verifyReset: vi.fn(),
    finishReset: vi.fn(),
    cancelChallenge: vi.fn(),
    resendChallenge: vi.fn(),
  },
  AuthRequestError: class AuthRequestError extends Error {},
}));

describe('ForgotPasswordPage Split-Screen', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('renders split-screen layout with visual storytelling panel and 3-step progress stepper', () => {
    render(
      <MemoryRouter>
        <ForgotPasswordPage />
      </MemoryRouter>
    );

    // Left Visual Panel content
    expect(screen.getByText('Bảo mật tài khoản')).toBeInTheDocument();
    expect(screen.getByText('Khôi phục quyền truy cập nhanh chóng.')).toBeInTheDocument();

    // Right Form Panel header and stepper
    expect(screen.getByRole('heading', { name: 'Khôi phục mật khẩu O.G Shop' })).toBeInTheDocument();
    expect(screen.getByText('Email')).toBeInTheDocument();
    expect(screen.getByText('Mã OTP')).toBeInTheDocument();
    expect(screen.getByText('Mật khẩu mới')).toBeInTheDocument();
    expect(screen.getByLabelText(/^Địa chỉ Email/)).toBeInTheDocument();
  });

  it('updates page title and active stepper node when moving to OTP step', async () => {
    vi.mocked(authApi.startReset).mockResolvedValue({
      status: 'OTP_REQUIRED',
      challengeId: 'reset-challenge-1',
      purpose: 'PASSWORD_RESET',
      email: 'user@example.com',
      expiresAt: new Date(Date.now() + 300000).toISOString(),
      resendAt: new Date(Date.now() - 1000).toISOString(),
      attemptsRemaining: 5,
    });

    render(
      <MemoryRouter>
        <ForgotPasswordPage />
      </MemoryRouter>
    );

    fireEvent.change(screen.getByLabelText(/^Địa chỉ Email/), { target: { value: 'user@example.com' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi mã đặt lại mật khẩu' }));

    await screen.findByRole('heading', { name: 'Xác thực mã OTP' });
    expect(screen.getByText('Nhập mã gồm 6 chữ số đã được gửi tới email của bạn.')).toBeInTheDocument();
    expect(screen.getByLabelText(/^Mã OTP/)).toBeInTheDocument();
  });
});
