import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LoginForm } from './LoginForm';
import { ForgotPasswordForm } from './ForgotPasswordForm';
import { AuthOtpForm } from './AuthOtpForm';
import { authApi, AuthRequestError } from '../authApi';
import type { AuthChallenge, AuthResponse } from '../types';
const auth = vi.hoisted(() => ({ login: vi.fn(), reloadCurrentUser: vi.fn() }));
vi.mock('../useAuth', () => ({ useAuth: () => auth }));
vi.mock('./GoogleSignInButton', () => ({ GoogleSignInButton: () => null }));
vi.mock('../authApi', async original => ({ ...await original<typeof import('../authApi')>(), authApi: {
  verifyLogin: vi.fn(), startReset: vi.fn(), verifyReset: vi.fn(), finishReset: vi.fn(), resendChallenge: vi.fn(), cancelChallenge: vi.fn(),
} }));
const pending = (purpose: AuthChallenge['purpose'] = 'LOGIN_EMAIL_2FA'): AuthChallenge => ({
  status: 'OTP_REQUIRED', challengeId: 'challenge', purpose, email: 'fixture@example.invalid', attemptsRemaining: 5,
  expiresAt: new Date(Date.now() + 300000).toISOString(), resendAt: new Date(Date.now() + 60000).toISOString(),
});
const session: AuthResponse = { user: { userId: 1, fullName: 'Buyer', email: 'fixture@example.invalid', roles: ['BUYER'], createdAt: '2026-10-11' }, accessToken: 'fixture', expiresIn: 900 };
beforeEach(() => { vi.resetAllMocks(); auth.reloadCurrentUser.mockResolvedValue(undefined); });
afterEach(() => vi.useRealTimers());
function loginFields() {
  fireEvent.change(screen.getByLabelText(/^Địa chỉ Email/), { target: { value: 'fixture@example.invalid' } });
  fireEvent.change(screen.getByLabelText(/^Mật khẩu/), { target: { value: 'Password123@' } });
  fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));
}
describe('login and reset OTP', () => {
  it('does not complete login until OTP succeeds; wrong code keeps the OTP form', async () => {
    const success = vi.fn(); auth.login.mockResolvedValue(pending());
    vi.mocked(authApi.verifyLogin).mockRejectedValueOnce(new AuthRequestError('Mã xác thực không đúng.', 'OTP_INVALID')).mockResolvedValueOnce(session);
    render(<LoginForm onSuccess={success} />); loginFields();
    await screen.findByLabelText(/^Mã OTP/); expect(success).not.toHaveBeenCalled();
    expect(screen.queryByLabelText(/^Mật khẩu/)).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/^Mã OTP/), { target: { value: '111111' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác thực mã' }));
    await screen.findByText('Mã xác thực không đúng.'); expect(success).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText(/^Mã OTP/), { target: { value: '222222' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác thực mã' }));
    await waitFor(() => expect(success).toHaveBeenCalledWith(session.user));
    expect(auth.reloadCurrentUser).toHaveBeenCalledTimes(1);
  });
  it('shows structured restriction support after the server verifies credentials', async () => {
    auth.login.mockRejectedValue(new AuthRequestError('Lý do khóa đã được xác nhận.', 'ACCOUNT_RESTRICTED', 0,
      { accountStatus: 'LOCKED', supportInstruction: 'Liên hệ hỗ trợ O.G Shop.' }));
    render(<LoginForm />); loginFields();
    await screen.findByText('Lý do khóa đã được xác nhận.');
    expect(screen.getByText(/LOCKED.*Liên hệ hỗ trợ/)).toBeInTheDocument();
  });
  it('completes email → OTP → new password and confirmation, then asks for a new login', async () => {
    vi.mocked(authApi.startReset).mockResolvedValue(pending('PASSWORD_RESET'));
    vi.mocked(authApi.verifyReset).mockResolvedValue({ ...pending('PASSWORD_RESET'), status: 'RESET_READY', challengeId: 'grant' });
    vi.mocked(authApi.finishReset).mockRejectedValueOnce(new Error('Mật khẩu mới không được trùng với mật khẩu cũ.')).mockResolvedValueOnce(undefined);
    const success = vi.fn(); render(<ForgotPasswordForm onSuccess={success} />);
    fireEvent.change(screen.getByLabelText(/^Địa chỉ Email/), { target: { value: 'fixture@example.invalid' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi mã đặt lại mật khẩu' }));
    await screen.findByLabelText(/^Mã OTP/);
    expect(screen.getByText(/Nếu email thuộc tài khoản/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Mật khẩu mới/)).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/^Mã OTP/), { target: { value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác thực mã' }));
    await screen.findByLabelText(/^Mật khẩu mới/);
    fireEvent.change(screen.getByLabelText(/^Mật khẩu mới/), { target: { value: 'NewPassword123@' } });
    fireEvent.change(screen.getByLabelText(/^Xác nhận mật khẩu mới/), { target: { value: 'Mismatch123@' } });
    fireEvent.click(screen.getByRole('button', { name: 'Đặt lại mật khẩu' }));
    expect(authApi.finishReset).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText(/^Xác nhận mật khẩu mới/), { target: { value: 'NewPassword123@' } });
    fireEvent.click(screen.getByRole('button', { name: 'Đặt lại mật khẩu' }));
    await screen.findByText('Mật khẩu mới không được trùng với mật khẩu cũ.');
    expect(screen.getByLabelText(/^Mật khẩu mới/)).toHaveValue('NewPassword123@');
    fireEvent.click(screen.getByRole('button', { name: 'Đặt lại mật khẩu' }));
    await screen.findByText(/Đã đặt lại mật khẩu/);expect(success).not.toHaveBeenCalled();
    expect(authApi.finishReset).toHaveBeenLastCalledWith('grant', 'NewPassword123@', 'NewPassword123@');
    fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));expect(success).toHaveBeenCalledTimes(1);
  });
  it('allows cancelling OTP to edit the email, without resetting a password', async () => {
    vi.mocked(authApi.startReset).mockResolvedValue(pending('PASSWORD_RESET'));vi.mocked(authApi.cancelChallenge).mockResolvedValue(undefined);
    render(<ForgotPasswordForm />);
    fireEvent.change(screen.getByLabelText(/^Địa chỉ Email/), { target: { value: 'first@example.invalid' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi mã đặt lại mật khẩu' }));await screen.findByLabelText(/^Mã OTP/);
    fireEvent.click(screen.getByRole('button', { name: 'Quay lại' }));await screen.findByLabelText(/^Địa chỉ Email/);
    expect(screen.getByLabelText(/^Địa chỉ Email/)).toHaveValue('first@example.invalid');expect(authApi.finishReset).not.toHaveBeenCalled();
  });
  it('counts resend wait down and applies Retry-After without disabling OTP verification', async () => {
    vi.useFakeTimers();vi.setSystemTime(new Date('2026-10-11T00:00:00Z'));
    vi.mocked(authApi.resendChallenge).mockRejectedValue(new AuthRequestError('Chờ thêm.', 'OTP_RESEND_TOO_SOON', 30));
    render(<AuthOtpForm challenge={pending()} onChange={vi.fn()} onVerify={vi.fn()} onCancel={vi.fn()} />);
    expect(screen.getByRole('button', { name: 'Gửi lại mã' })).toBeDisabled();
    await act(async () => { await vi.advanceTimersByTimeAsync(60000); });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi lại mã' }));await act(async () => {});
    expect(screen.getByRole('timer')).toHaveTextContent('30 giây');expect(screen.getByRole('button', { name: 'Xác thực mã' })).toBeEnabled();
    await act(async () => { await vi.advanceTimersByTimeAsync(30000); });expect(screen.getByRole('button', { name: 'Gửi lại mã' })).toBeEnabled();
  });
});
