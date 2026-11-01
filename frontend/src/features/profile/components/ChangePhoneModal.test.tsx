import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ChangePhoneModal } from './ChangePhoneModal';
import { profileApi } from '../profileApi';

describe('ChangePhoneModal Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders nothing when isOpen is false', () => {
    const { container } = render(
      <ChangePhoneModal
        isOpen={false}
        onClose={vi.fn()}
        onSuccess={vi.fn()}
        currentPhoneNumber="0912345678"
      />
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders method selection dialog when opened', () => {
    render(
      <ChangePhoneModal
        isOpen={true}
        onClose={vi.fn()}
        onSuccess={vi.fn()}
        currentPhoneNumber="0912345678"
        accountEmail="test@example.com"
        isVerifiedSeller={true}
      />
    );

    expect(screen.getByText('Xác Thực Đổi Số Điện Thoại')).toBeInTheDocument();
    expect(screen.getByText('Xác thực khuôn mặt (Face Auth)')).toBeInTheDocument();
    expect(screen.getByText('Nhận mã xác thực OTP qua Email')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Đóng cửa sổ' })).toBeInTheDocument();
  });

  it('transitions to OTP input step when Email OTP is selected', async () => {
    vi.spyOn(profileApi, 'requestPhoneChangeOtp').mockResolvedValueOnce({
      challengeId: 'challenge-123',
      maskedEmail: 'te***@example.com',
      expiresAt: '2026-11-01T12:00:00Z',
      resendAt: '2026-11-01T11:51:00Z',
    });

    render(
      <ChangePhoneModal
        isOpen={true}
        onClose={vi.fn()}
        onSuccess={vi.fn()}
        currentPhoneNumber="0912345678"
        accountEmail="test@example.com"
      />
    );

    const otpOption = screen.getByText('Nhận mã xác thực OTP qua Email');
    fireEvent.click(otpOption);

    await waitFor(() => {
      expect(screen.getByText(/Mã xác thực 6 chữ số đã được gửi tới email/i)).toBeInTheDocument();
      expect(screen.getByPlaceholderText('000000')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Xác nhận mã' })).toBeInTheDocument();
    });
  });

  it('validates phone input continuously in real-time', async () => {
    vi.spyOn(profileApi, 'requestPhoneChangeOtp').mockResolvedValueOnce({
      challengeId: 'challenge-123',
      maskedEmail: 'te***@example.com',
      expiresAt: '2026-11-01T12:00:00Z',
      resendAt: '2026-11-01T11:51:00Z',
    });
    vi.spyOn(profileApi, 'verifyPhoneOtp').mockResolvedValueOnce(undefined);

    render(
      <ChangePhoneModal
        isOpen={true}
        onClose={vi.fn()}
        onSuccess={vi.fn()}
        currentPhoneNumber="0912345678"
      />
    );

    // Go to OTP
    fireEvent.click(screen.getByText('Nhận mã xác thực OTP qua Email'));
    await waitFor(() => expect(screen.getByPlaceholderText('000000')).toBeInTheDocument());

    // Enter valid 6-digit OTP
    fireEvent.change(screen.getByPlaceholderText('000000'), { target: { value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận mã' }));

    // Should transition to Enter New Phone step
    await waitFor(() => {
      expect(screen.getByText('Nhập Số Điện Thoại Mới')).toBeInTheDocument();
      expect(screen.getByPlaceholderText('VD: 0912345678')).toBeInTheDocument();
    });

    const phoneInput = screen.getByPlaceholderText('VD: 0912345678');
    const saveBtn = screen.getByRole('button', { name: 'Lưu số điện thoại' });
    expect(saveBtn).toBeDisabled();

    // 1. Gõ đầu số không hợp lệ (ví dụ: 01...)
    fireEvent.change(phoneInput, { target: { value: '012' } });
    expect(screen.getByText(/Đầu số không hợp lệ/i)).toBeInTheDocument();
    expect(saveBtn).toBeDisabled();

    // 2. Gõ thiếu số (< 10 số)
    fireEvent.change(phoneInput, { target: { value: '098765' } });
    expect(screen.getByText(/Số điện thoại cần đủ 10 chữ số \(hiện có 6\/10 số\)/i)).toBeInTheDocument();
    expect(saveBtn).toBeDisabled();

    // 3. Gõ trùng số hiện tại (0912345678)
    fireEvent.change(phoneInput, { target: { value: '0912345678' } });
    expect(screen.getByText(/Số điện thoại mới trùng với số điện thoại bạn đang sử dụng hiện tại/i)).toBeInTheDocument();
    expect(saveBtn).toBeDisabled();

    // 4. Gõ số hợp lệ (0987654321) -> Lỗi biến mất, nút Lưu được enable
    fireEvent.change(phoneInput, { target: { value: '0987654321' } });
    expect(screen.queryByText(/⚠️/i)).toBeNull();
    expect(saveBtn).not.toBeDisabled();
  });

  it('handles database conflict with another user and allows re-entry', async () => {
    vi.spyOn(profileApi, 'requestPhoneChangeOtp').mockResolvedValueOnce({
      challengeId: 'challenge-123',
      maskedEmail: 'te***@example.com',
      expiresAt: '2026-11-01T12:00:00Z',
      resendAt: '2026-11-01T11:51:00Z',
    });
    vi.spyOn(profileApi, 'verifyPhoneOtp').mockResolvedValueOnce(undefined);
    vi.spyOn(profileApi, 'checkPhoneAvailability').mockResolvedValueOnce({
      available: false,
      reason: 'ALREADY_EXISTS',
      message: 'Số điện thoại này đã được đăng ký bởi một người khác.',
    });

    render(
      <ChangePhoneModal
        isOpen={true}
        onClose={vi.fn()}
        onSuccess={vi.fn()}
        currentPhoneNumber="0912345678"
      />
    );

    fireEvent.click(screen.getByText('Nhận mã xác thực OTP qua Email'));
    await waitFor(() => expect(screen.getByPlaceholderText('000000')).toBeInTheDocument());

    fireEvent.change(screen.getByPlaceholderText('000000'), { target: { value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận mã' }));

    await waitFor(() => expect(screen.getByPlaceholderText('VD: 0912345678')).toBeInTheDocument());

    const phoneInput = screen.getByPlaceholderText('VD: 0912345678');
    fireEvent.change(phoneInput, { target: { value: '0999888777' } });

    const saveBtn = screen.getByRole('button', { name: 'Lưu số điện thoại' });
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(screen.getByText(/Số điện thoại này đã được đăng ký bởi một người khác/i)).toBeInTheDocument();
    });

    // Cho phép người dùng nhập lại ngay tại ô đó
    expect(phoneInput).not.toBeDisabled();
  });

  it('restores active cooldown, displays countdown notice and allows navigating directly to OTP input without duplicate request', async () => {
    const futureTime = Date.now() + 45000;
    sessionStorage.setItem('og_phone_otp_resend_until', String(futureTime));
    sessionStorage.setItem('og_phone_challenge_id', 'persisted-phone-challenge-xyz');
    sessionStorage.setItem('og_phone_masked_email', 'phone***@ogshop.vn');

    const requestSpy = vi.spyOn(profileApi, 'requestPhoneChangeOtp');

    render(
      <ChangePhoneModal
        isOpen={true}
        onClose={vi.fn()}
        onSuccess={vi.fn()}
        isVerifiedSeller={true}
        accountEmail="test@ogshop.vn"
      />
    );

    // Should display cooldown badge and button to enter existing OTP
    expect(screen.getByText(/Đã gửi mã/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Nhập mã OTP đã gửi' })).toBeInTheDocument();

    // No duplicate network request fired
    expect(requestSpy).not.toHaveBeenCalled();

    // Clicking "Nhập mã OTP đã gửi" should transition to OTP_INPUT
    fireEvent.click(screen.getByRole('button', { name: 'Nhập mã OTP đã gửi' }));

    await waitFor(() => {
      expect(screen.getByPlaceholderText('000000')).toBeInTheDocument();
      expect(screen.getByText(/phone\*\*\*@ogshop.vn/i)).toBeInTheDocument();
    });

    sessionStorage.clear();
  });
});
