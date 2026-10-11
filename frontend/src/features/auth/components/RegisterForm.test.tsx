import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { RegisterForm } from './RegisterForm';
import { authApi, AuthRequestError } from '../authApi';
import { acceptsNewPassword } from '../passwordPolicy';
import type { RegistrationChallenge, RegistrationCompletion } from '../types';

const auth = vi.hoisted(() => ({ register: vi.fn(), verifyRegistration: vi.fn() }));
vi.mock('./GoogleSignInButton', () => ({ GoogleSignInButton: () => null }));
vi.mock('../useAuth', () => ({ useAuth: () => auth }));
vi.mock('../authApi', async importOriginal => ({
  ...await importOriginal<typeof import('../authApi')>(),
  authApi: { resendRegistration: vi.fn(), cancelRegistration: vi.fn() },
}));
const pending = (): RegistrationChallenge => ({ status: 'OTP_REQUIRED', challengeId: 'first',
  email: 'buyer@example.invalid', expiresAt: new Date(Date.now() + 300000).toISOString(),
  resendAt: new Date(Date.now() - 1000).toISOString(), attemptsRemaining: 5 });
const completed: RegistrationCompletion = { user: { userId: 1, email: 'buyer@example.invalid',
  fullName: 'Buyer', roles: ['BUYER'], createdAt: '2026-10-10' }, accessToken: 'fixture', expiresIn: 900,
  nextStep: 'PROFILE', awardedCoins: 3000 };
function enterBuyer(password = 'Password123@') {
  fireEvent.change(screen.getByLabelText(/Họ và tên/), { target: { value: 'Buyer' } });
  fireEvent.change(screen.getByLabelText(/Địa chỉ Email/), { target: { value: 'buyer@example.invalid' } });
  fireEvent.change(screen.getByLabelText(/^Mật khẩu/), { target: { value: password } });
  fireEvent.change(screen.getByLabelText(/Xác nhận mật khẩu/), { target: { value: password } });
}
function enterSeller() {
  fireEvent.click(screen.getByLabelText('Mua và bán hàng'));
  fireEvent.click(screen.getByText('Tiếp tục →'));
  fireEvent.change(screen.getByLabelText(/Tên cửa hàng/), { target: { value: 'Test Store' } });
  fireEvent.change(screen.getByLabelText(/Số điện thoại liên hệ/), { target: { value: '0900000000' } });
  const pickup = within(screen.getByRole('group', { name: 'Địa chỉ lấy hàng' }));
  for (const [label, value] of [['Tỉnh / Thành phố', 'P'], ['Quận / Huyện', 'D'], ['Phường / Xã', 'W'], ['Địa chỉ chi tiết', 'A']]) {
    fireEvent.change(pickup.getByLabelText(new RegExp(label)), { target: { value } });
  }
  fireEvent.change(screen.getByLabelText(/^Ngân hàng/), { target: { value: 'Mock Bank' } });
  fireEvent.change(screen.getByLabelText(/Số tài khoản ngân hàng/), { target: { value: '12345678' } });
  fireEvent.change(screen.getByLabelText(/Tên chủ tài khoản/), { target: { value: 'TEST SELLER' } });
}
beforeEach(() => {
  vi.resetAllMocks();
  auth.register.mockResolvedValue(pending());
  auth.verifyRegistration.mockResolvedValue(completed);
});
afterEach(() => vi.useRealTimers());
describe('email registration', () => {
  it('keeps Seller fields after Back without reusing the submit form', () => {
    render(<RegisterForm />);
    enterBuyer(); enterSeller();
    const sellerForm = screen.getByText('← Quay lại').closest('form');
    fireEvent.click(screen.getByText('← Quay lại'));
    expect(screen.getByLabelText(/Họ và tên/)).toHaveValue('Buyer');
    expect(screen.getByText('Tiếp tục →').closest('form')).not.toBe(sellerForm);
    expect(auth.register).not.toHaveBeenCalled();
    fireEvent.click(screen.getByText('Tiếp tục →'));
    expect(screen.getByLabelText(/Tên cửa hàng/)).toHaveValue('Test Store');
    expect(auth.register).not.toHaveBeenCalled();
  });
  it.each(['BUYER', 'SELLER'] as const)('shows the server wait on the %s form and allows retry only after it expires', async accountType => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-11T00:00:00Z'));
    auth.register.mockRejectedValueOnce(new AuthRequestError('Vui lòng chờ trước khi gửi mã mới.', 'OTP_RESEND_TOO_SOON', 60));
    render(<RegisterForm />);
    enterBuyer();
    if (accountType === 'SELLER') enterSeller();
    // Time spent filling the form must not inflate the server's 60-second wait.
    await act(async () => { vi.advanceTimersByTime(120000); });
    const send = screen.getByRole('button', { name: 'Gửi mã xác thực email' });
    const form = send.closest('form')!;
    await act(async () => { fireEvent.click(send); });
    expect(screen.getByRole('timer')).toHaveTextContent('Bạn có thể gửi lại mã sau 60 giây.');
    expect(send).toBeDisabled();
    expect(screen.getByLabelText(accountType === 'SELLER' ? /Tên cửa hàng/ : /Địa chỉ Email/))
      .toHaveValue(accountType === 'SELLER' ? 'Test Store' : 'buyer@example.invalid');
    expect(screen.queryByLabelText(/Mã xác thực email/)).not.toBeInTheDocument();
    await act(async () => { fireEvent.submit(form); });
    expect(auth.register).toHaveBeenCalledTimes(1);
    await act(async () => { vi.advanceTimersByTime(1000); });
    expect(screen.getByRole('timer')).toHaveTextContent('59 giây');
    await act(async () => { vi.advanceTimersByTime(59000); });
    expect(screen.queryByRole('timer')).not.toBeInTheDocument();
    expect(send).toBeEnabled();
    await act(async () => { fireEvent.click(send); });
    expect(auth.register).toHaveBeenCalledTimes(2);
    expect(screen.getByLabelText(/Mã xác thực email/)).toBeInTheDocument();
  });

  it('keeps OTP verification available while waiting after a successful send and a 429 resend', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-11T00:00:00Z'));
    auth.register.mockResolvedValue({ ...pending(), resendAt: new Date(Date.now() + 60000).toISOString() });
    vi.mocked(authApi.resendRegistration)
      .mockRejectedValueOnce(new AuthRequestError('Vui lòng chờ trước khi gửi mã mới.', 'OTP_RESEND_TOO_SOON', 30))
      .mockResolvedValue({ ...pending(), challengeId: 'replacement' });
    render(<RegisterForm />);
    enterBuyer();
    await act(async () => { fireEvent.click(screen.getByText('Gửi mã xác thực email')); });
    expect(screen.getByRole('button', { name: 'Gửi lại sau 60 giây' })).toBeDisabled();
    expect(screen.getByText('Xác nhận và tạo tài khoản')).toBeEnabled();
    await act(async () => { vi.advanceTimersByTime(60000); });
    fireEvent.change(screen.getByLabelText(/Mã xác thực email/), { target: { value: '123456' } });
    await act(async () => { fireEvent.click(screen.getByText('Gửi lại mã')); });
    expect(screen.getByRole('button', { name: 'Gửi lại sau 30 giây' })).toBeDisabled();
    expect(screen.getByLabelText(/Mã xác thực email/)).toHaveValue('123456');
    expect(screen.getByText('Xác nhận và tạo tài khoản')).toBeEnabled();
    await act(async () => { vi.advanceTimersByTime(30000); });
    await act(async () => { fireEvent.click(screen.getByText('Gửi lại mã')); });
    expect(authApi.resendRegistration).toHaveBeenCalledTimes(2);
    expect(authApi.resendRegistration).toHaveBeenLastCalledWith('first');
    expect(screen.getByLabelText(/Mã xác thực email/)).toHaveValue('');
  });

  it('allows buyer without phone and waits for OTP before completing', async () => {
    const success = vi.fn();
    render(<RegisterForm onSuccess={success} />);
    enterBuyer();
    fireEvent.click(screen.getByText('Gửi mã xác thực email'));
    await screen.findByLabelText(/Mã xác thực email/);
    expect(auth.register).toHaveBeenCalledWith(expect.objectContaining({ accountType: 'BUYER', phoneNumber: undefined }));
    expect(success).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText(/Mã xác thực email/), { target: { value: '123456' } });
    fireEvent.click(screen.getByText('Xác nhận và tạo tài khoản'));
    await waitFor(() => expect(success).toHaveBeenCalledWith(completed));
    expect(auth.verifyRegistration).toHaveBeenCalledWith('first', '123456');
  });
  it('keeps pending state on incorrect code and verifies only the new challenge after resend', async () => {
    const success = vi.fn();
    auth.verifyRegistration.mockRejectedValueOnce(new AuthRequestError('Mã không đúng', 'OTP_INVALID'));
    vi.mocked(authApi.resendRegistration).mockResolvedValue({ ...pending(), challengeId: 'replacement' });
    render(<RegisterForm onSuccess={success} />);
    enterBuyer();
    fireEvent.click(screen.getByText('Gửi mã xác thực email'));
    const code = await screen.findByLabelText(/Mã xác thực email/);
    fireEvent.change(code, { target: { value: '000000' } });
    fireEvent.click(screen.getByText('Xác nhận và tạo tài khoản'));
    await screen.findByText('Mã không đúng');
    expect(success).not.toHaveBeenCalled();
    fireEvent.click(screen.getByText('Gửi lại mã'));
    await waitFor(() => expect(code).toHaveValue(''));
    fireEvent.change(code, { target: { value: '654321' } });
    fireEvent.click(screen.getByText('Xác nhận và tạo tài khoản'));
    await waitFor(() => expect(auth.verifyRegistration).toHaveBeenLastCalledWith('replacement', '654321'));
  });
  it('cancels without creating session and clears plaintext password inputs', async () => {
    vi.mocked(authApi.cancelRegistration).mockResolvedValue();
    const success = vi.fn();
    render(<RegisterForm onSuccess={success} />);
    enterBuyer();
    fireEvent.click(screen.getByText('Gửi mã xác thực email'));
    await screen.findByLabelText(/Mã xác thực email/);
    fireEvent.click(screen.getByText('Hủy đăng ký'));
    await screen.findByText('Đã hủy đăng ký. Chưa có tài khoản được tạo.');
    expect(authApi.cancelRegistration).toHaveBeenCalledWith('first');
    expect(screen.getByLabelText(/^Mật khẩu/)).toHaveValue('');
    expect(screen.getByLabelText(/Xác nhận mật khẩu/)).toHaveValue('');
    expect(auth.verifyRegistration).not.toHaveBeenCalled();
    expect(success).not.toHaveBeenCalled();
  });
  it('requires seller information and copies pickup address when return address is shared', async () => {
    render(<RegisterForm />);
    enterBuyer();
    fireEvent.click(screen.getByLabelText('Mua và bán hàng'));
    fireEvent.click(screen.getByText('Tiếp tục →'));
    fireEvent.change(screen.getByLabelText(/Tên cửa hàng/), { target: { value: 'Test Store' } });
    fireEvent.change(screen.getByLabelText(/Số điện thoại liên hệ/), { target: { value: '0900000000' } });
    const pickup = within(screen.getByRole('group', { name: 'Địa chỉ lấy hàng' }));
    for (const [label, value] of [['Tỉnh / Thành phố', 'P'], ['Quận / Huyện', 'D'], ['Phường / Xã', 'W'], ['Địa chỉ chi tiết', 'A']]) {
      fireEvent.change(pickup.getByLabelText(new RegExp(label)), { target: { value } });
    }
    fireEvent.change(screen.getByLabelText(/^Ngân hàng/), { target: { value: 'Mock Bank' } });
    fireEvent.change(screen.getByLabelText(/Số tài khoản ngân hàng/), { target: { value: '12345678' } });
    fireEvent.change(screen.getByLabelText(/Tên chủ tài khoản/), { target: { value: 'TEST SELLER' } });
    fireEvent.click(screen.getByText('Gửi mã xác thực email'));
    await screen.findByLabelText(/Mã xác thực email/);
    expect(auth.register).toHaveBeenCalledWith(expect.objectContaining({ accountType: 'SELLER',
      seller: expect.objectContaining({ pickupAddress: { province: 'P', district: 'D', ward: 'W', detailAddress: 'A' },
        returnAddress: { province: 'P', district: 'D', ward: 'W', detailAddress: 'A' } }) }));
  });
  it('preserves account and seller fields when navigating back from step 2 to step 1', () => {
    render(<RegisterForm />);
    enterBuyer();
    fireEvent.click(screen.getByLabelText('Mua và bán hàng'));
    fireEvent.click(screen.getByText('Tiếp tục →'));
    fireEvent.change(screen.getByLabelText(/Tên cửa hàng/), { target: { value: 'My Shop' } });
    fireEvent.click(screen.getByText('← Quay lại'));
    expect(screen.getByLabelText(/Họ và tên/)).toHaveValue('Buyer');
    expect(screen.getByLabelText(/Địa chỉ Email/)).toHaveValue('buyer@example.invalid');
    expect(screen.getByLabelText('Mua và bán hàng')).toBeChecked();
    // Return to Step 2 to verify seller field is still preserved
    fireEvent.click(screen.getByText('Tiếp tục →'));
    expect(screen.getByLabelText(/Tên cửa hàng/)).toHaveValue('My Shop');
  });
  it('switches role from seller to buyer and submits as buyer without seller payload', async () => {
    render(<RegisterForm />);
    enterBuyer();
    fireEvent.click(screen.getByLabelText('Mua và bán hàng'));
    fireEvent.click(screen.getByText('Tiếp tục →'));
    fireEvent.change(screen.getByLabelText(/Tên cửa hàng/), { target: { value: 'My Shop' } });
    fireEvent.click(screen.getByText('← Quay lại'));
    // Switch to BUYER
    fireEvent.click(screen.getByLabelText('Mua hàng'));
    fireEvent.click(screen.getByText('Gửi mã xác thực email'));
    await screen.findByLabelText(/Mã xác thực email/);
    expect(auth.register).toHaveBeenCalledWith(expect.objectContaining({
      accountType: 'BUYER',
      seller: undefined,
    }));
  });
  it('shows validation error when passwords do not match and prevents transition', () => {
    render(<RegisterForm />);
    fireEvent.change(screen.getByLabelText(/Họ và tên/), { target: { value: 'Test User' } });
    fireEvent.change(screen.getByLabelText(/Địa chỉ Email/), { target: { value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText(/^Mật khẩu/), { target: { value: 'Password123@' } });
    fireEvent.change(screen.getByLabelText(/Xác nhận mật khẩu/), { target: { value: 'Mismatch123@' } });
    fireEvent.click(screen.getByLabelText('Mua và bán hàng'));
    fireEvent.click(screen.getByText('Tiếp tục →'));
    expect(screen.getByText('Xác nhận mật khẩu không khớp.')).toBeInTheDocument();
    expect(auth.register).not.toHaveBeenCalled();
  });
  it('toggles password visibility with accessible button', () => {
    render(<RegisterForm />);
    const passwordInput = screen.getByLabelText(/^Mật khẩu/);
    expect(passwordInput).toHaveAttribute('type', 'password');
    const toggleButtons = screen.getAllByLabelText('Hiện mật khẩu');
    fireEvent.click(toggleButtons[0]);
    expect(passwordInput).toHaveAttribute('type', 'text');
    fireEvent.click(screen.getByLabelText('Ẩn mật khẩu'));
    expect(passwordInput).toHaveAttribute('type', 'password');
  });
  it('blocks passwords longer than the BCrypt UTF-8 limit', () => {
    render(<RegisterForm />);
    enterBuyer('ắ'.repeat(25));
    fireEvent.click(screen.getByText('Gửi mã xác thực email'));
    expect(auth.register).not.toHaveBeenCalled();
    expect(acceptsNewPassword('ắ'.repeat(24))).toBe(true);
    expect(acceptsNewPassword('ắ'.repeat(25))).toBe(false);
  });
});

