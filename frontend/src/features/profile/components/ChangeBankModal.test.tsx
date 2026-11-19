import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ChangeBankModal } from './ChangeBankModal';
import { profileApi } from '../profileApi';
import { UserProfile } from '../types';

describe('ChangeBankModal Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders nothing when isOpen is false', () => {
    const { container } = render(
      <ChangeBankModal
        isOpen={false}
        onClose={vi.fn()}
        onSuccess={vi.fn()}
      />
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders method selection dialog when opened for verified seller', () => {
    render(
      <ChangeBankModal
        isOpen={true}
        onClose={vi.fn()}
        onSuccess={vi.fn()}
        isVerifiedSeller={true}
        accountEmail="test@ogshop.vn"
      />
    );

    expect(screen.getByText('Xác thực thay đổi tài khoản')).toBeInTheDocument();
    expect(screen.getByText('Quét khuôn mặt Quick Face Auth')).toBeInTheDocument();
    expect(screen.getByText('Nhận mã xác thực OTP qua Email')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Đóng cửa sổ' })).toBeInTheDocument();
  });

  it('automatically requests OTP and transitions to OTP input for non-verified user', async () => {
    vi.spyOn(profileApi, 'requestBankChangeOtp').mockResolvedValueOnce({
      challengeId: 'bank-challenge-123',
      maskedEmail: 'te***@ogshop.vn',
      expiresAt: '2026-11-01T14:00:00Z',
      resendAt: '2026-11-01T13:30:00Z',
    });

    render(
      <ChangeBankModal
        isOpen={true}
        onClose={vi.fn()}
        onSuccess={vi.fn()}
        isVerifiedSeller={false}
        accountEmail="test@ogshop.vn"
      />
    );

    await waitFor(() => {
      expect(profileApi.requestBankChangeOtp).toHaveBeenCalledTimes(1);
      expect(screen.getByText(/Mã xác thực 6 chữ số đã được gửi tới email/i)).toBeInTheDocument();
      expect(screen.getByPlaceholderText('000000')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Xác nhận mã' })).toBeInTheDocument();
    });
  });

  it('transitions to enter new bank form and submits bank updates', async () => {
    vi.spyOn(profileApi, 'requestBankChangeOtp').mockResolvedValueOnce({
      challengeId: 'bank-challenge-123',
      maskedEmail: 'te***@ogshop.vn',
      expiresAt: '2026-11-01T14:00:00Z',
      resendAt: '2026-11-01T13:30:00Z',
    });

    const updatedProfile: UserProfile = {
      id: 1,
      email: 'test@ogshop.vn',
      fullName: 'Nguyen Van Test',
      roles: ['BUYER', 'SELLER'],
      verifiedSeller: true,
      bankName: 'Techcombank',
      bankAccountNumberMasked: '******7890',
      bankAccountHolder: 'NGUYEN VAN TEST',
      createdAt: '2026-11-01T14:00:00Z',
    };

    vi.spyOn(profileApi, 'updateBankAccount').mockResolvedValueOnce(updatedProfile);

    const onSuccess = vi.fn();

    render(
      <ChangeBankModal
        isOpen={true}
        onClose={vi.fn()}
        onSuccess={onSuccess}
        isVerifiedSeller={false}
      />
    );

    // Wait for OTP step
    await waitFor(() => expect(screen.getByPlaceholderText('000000')).toBeInTheDocument());

    // Enter OTP
    fireEvent.change(screen.getByPlaceholderText('000000'), { target: { value: '654321' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận mã' }));

    // Should transition to step 3
    await waitFor(() => {
      expect(screen.getByText('Nhập Tài Khoản Ngân Hàng Mới')).toBeInTheDocument();
      expect(screen.getByPlaceholderText(/MB Bank, Vietcombank/i)).toBeInTheDocument();
    });

    // Fill new bank form
    fireEvent.change(screen.getByPlaceholderText(/MB Bank, Vietcombank/i), { target: { value: 'Techcombank' } });
    fireEvent.change(screen.getByPlaceholderText(/chỉ gồm số/i), { target: { value: '19031234567890' } });
    fireEvent.change(screen.getByPlaceholderText(/NGUYEN VAN A/i), { target: { value: 'Nguyen Van Test' } });

    // Submit
    fireEvent.click(screen.getByRole('button', { name: 'Lưu tài khoản ngân hàng' }));

    await waitFor(() => {
      expect(profileApi.updateBankAccount).toHaveBeenCalledWith({
        bankName: 'Techcombank',
        bankAccountNumber: '19031234567890',
        bankAccountHolder: 'NGUYEN VAN TEST',
        quickAuthResultId: undefined,
        challengeId: 'bank-challenge-123',
        otpCode: '654321',
      });
      expect(screen.getByText('Cập nhật tài khoản ngân hàng thành công!')).toBeInTheDocument();
    });
  });

  it('allows selecting bank from custom combobox dropdown with logos', async () => {
    vi.spyOn(profileApi, 'requestBankChangeOtp').mockResolvedValueOnce({
      challengeId: 'bank-challenge-456',
      maskedEmail: 'te***@ogshop.vn',
      expiresAt: '2026-11-01T14:00:00Z',
      resendAt: '2026-11-01T13:30:00Z',
    });

    render(
      <ChangeBankModal
        isOpen={true}
        onClose={vi.fn()}
        onSuccess={vi.fn()}
        isVerifiedSeller={false}
      />
    );

    await waitFor(() => expect(screen.getByPlaceholderText('000000')).toBeInTheDocument());
    fireEvent.change(screen.getByPlaceholderText('000000'), { target: { value: '111222' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận mã' }));

    await waitFor(() => {
      expect(screen.getByText('Nhập Tài Khoản Ngân Hàng Mới')).toBeInTheDocument();
    });

    // Focus on bank combobox input to open dropdown
    const bankInput = screen.getByPlaceholderText(/MB Bank, Vietcombank/i);
    fireEvent.focus(bankInput);

    // Combobox listbox should open with options
    await waitFor(() => {
      expect(screen.getByRole('listbox', { name: 'Danh sách ngân hàng khả dụng' })).toBeInTheDocument();
      expect(screen.getByText('Vietcombank')).toBeInTheDocument();
      expect(screen.getByText('MB Bank')).toBeInTheDocument();
    });

    // Select MB Bank from combobox
    const mbOption = screen.getByText('MB Bank');
    fireEvent.click(mbOption);

    // Combobox should now display MB Bank and its logo
    await waitFor(() => {
      expect(screen.getByAltText('MB Bank')).toBeInTheDocument();
    });
  });

  it('restores active cooldown, displays countdown notice and allows navigating directly to OTP input without duplicate request', async () => {
    // Setup existing active cooldown in sessionStorage
    const futureTime = Date.now() + 45000;
    sessionStorage.setItem('og_bank_otp_resend_until', String(futureTime));
    sessionStorage.setItem('og_bank_challenge_id', 'persisted-challenge-xyz');

    const requestSpy = vi.spyOn(profileApi, 'requestBankChangeOtp');

    render(
      <ChangeBankModal
        isOpen={true}
        onClose={vi.fn()}
        onSuccess={vi.fn()}
        isVerifiedSeller={true}
        accountEmail="test@ogshop.vn"
      />
    );

    // Should display cooldown badge and message
    expect(screen.getByText(/Đã gửi mã/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Nhập mã OTP đã gửi' })).toBeInTheDocument();

    // No duplicate network request fired
    expect(requestSpy).not.toHaveBeenCalled();

    // Clicking "Nhập mã OTP đã gửi" should transition to OTP_INPUT
    fireEvent.click(screen.getByRole('button', { name: 'Nhập mã OTP đã gửi' }));

    await waitFor(() => {
      expect(screen.getByPlaceholderText('000000')).toBeInTheDocument();
      expect(screen.getByText(/test@ogshop\.vn/i)).toBeInTheDocument();
    });

    sessionStorage.clear();
  });
});

