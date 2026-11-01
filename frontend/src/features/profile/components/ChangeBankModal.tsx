import React, { useState, useEffect } from 'react';
import { Button, Alert, Input } from '../../../shared/components';
import { UserProfile } from '../types';
import { profileApi } from '../profileApi';
import { QuickFaceAuthModal } from '../../verification/components/QuickFaceAuthModal';
import { BankSelect } from '../../auth';
import { SecurityOutlineIcons, securityModalStyles } from './securityModalShared';

export interface ChangeBankModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (updatedProfile: UserProfile) => void;
  currentBankName?: string | null;
  currentBankAccountNumberMasked?: string | null;
  currentBankAccountHolder?: string | null;
  accountEmail?: string | null;
  isVerifiedSeller?: boolean;
}

type BankModalStep = 'SELECT_METHOD' | 'OTP_INPUT' | 'ENTER_NEW_BANK';

const BANK_COOLDOWN_KEY = 'og_bank_otp_resend_until';
const BANK_CHALLENGE_KEY = 'og_bank_challenge_id';

function getStoredBankCooldown(): { secondsLeft: number; challengeId: string | null } {
  try {
    const rawUntil = sessionStorage.getItem(BANK_COOLDOWN_KEY);
    const challengeId = sessionStorage.getItem(BANK_CHALLENGE_KEY);
    if (!rawUntil) return { secondsLeft: 0, challengeId: null };
    const diff = Math.ceil((Number(rawUntil) - Date.now()) / 1000);
    return {
      secondsLeft: diff > 0 ? diff : 0,
      challengeId: diff > 0 ? challengeId : null,
    };
  } catch {
    return { secondsLeft: 0, challengeId: null };
  }
}

function setStoredBankCooldown(seconds: number, challengeId: string) {
  try {
    sessionStorage.setItem(BANK_COOLDOWN_KEY, String(Date.now() + seconds * 1000));
    sessionStorage.setItem(BANK_CHALLENGE_KEY, challengeId);
  } catch {
    // ignore
  }
}

function clearStoredBankCooldown() {
  try {
    sessionStorage.removeItem(BANK_COOLDOWN_KEY);
    sessionStorage.removeItem(BANK_CHALLENGE_KEY);
  } catch {
    // ignore
  }
}

export const ChangeBankModal: React.FC<ChangeBankModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  currentBankName,
  currentBankAccountHolder,
  accountEmail,
  isVerifiedSeller = false,
}) => {
  const [step, setStep] = useState<BankModalStep>('SELECT_METHOD');
  const [showQuickFaceModal, setShowQuickFaceModal] = useState<boolean>(false);

  // OTP State
  const [bankChallengeId, setBankChallengeId] = useState<string | null>(null);
  const [bankOtpCode, setBankOtpCode] = useState<string>('');
  const [maskedEmail, setMaskedEmail] = useState<string>('');
  const [otpError, setOtpError] = useState<string | null>(null);
  const [isRequestingOtp, setIsRequestingOtp] = useState<boolean>(false);
  const [resendCooldown, setResendCooldown] = useState<number>(0);

  // Form State
  const [bankName, setBankName] = useState<string>(currentBankName || '');
  const [bankAccountNumber, setBankAccountNumber] = useState<string>('');
  const [bankAccountHolder, setBankAccountHolder] = useState<string>(currentBankAccountHolder || '');
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState<boolean>(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Verified Credentials
  const [verifiedQuickAuthResultId, setVerifiedQuickAuthResultId] = useState<string | null>(null);
  const [verifiedOtpCredentials, setVerifiedOtpCredentials] = useState<{ challengeId: string; code: string } | null>(null);

  // Timer cooldown
  useEffect(() => {
    if (resendCooldown <= 0) return;
    const timer = setInterval(() => {
      setResendCooldown((prev) => (prev > 0 ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(timer);
  }, [resendCooldown]);

  const requestOtpDirectly = async () => {
    setIsRequestingOtp(true);
    setOtpError(null);
    try {
      const res = await profileApi.requestBankChangeOtp();
      const email = res.maskedEmail || accountEmail || 'email đăng ký';
      setBankChallengeId(res.challengeId);
      setMaskedEmail(email);
      setResendCooldown(60);
      setStoredBankCooldown(60, res.challengeId);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Không thể gửi mã xác thực OTP qua Email.';
      setOtpError(msg);
      if (msg.includes('đợi') || msg.includes('trước khi') || msg.includes('429')) {
        const stored = getStoredBankCooldown();
        if (stored.secondsLeft <= 0) {
          setResendCooldown(60);
          setStoredBankCooldown(60, bankChallengeId || '');
        }
      }
    } finally {
      setIsRequestingOtp(false);
    }
  };

  // Handle open / reset & auto OTP for unverified users with cooldown persistence
  useEffect(() => {
    if (!isOpen) return;

    setBankOtpCode('');
    setOtpError(null);
    setFormError(null);
    setSuccessMessage(null);
    setBankAccountNumber('');
    setBankName(currentBankName || '');
    setBankAccountHolder(currentBankAccountHolder || '');
    setVerifiedQuickAuthResultId(null);
    setVerifiedOtpCredentials(null);

    const stored = getStoredBankCooldown();
    if (stored.secondsLeft > 0) {
      setResendCooldown(stored.secondsLeft);
      if (stored.challengeId) setBankChallengeId(stored.challengeId);
    } else {
      setResendCooldown(0);
    }

    if (isVerifiedSeller) {
      setStep('SELECT_METHOD');
    } else {
      // Unverified account -> check if cooldown & challenge exists
      setStep('OTP_INPUT');
      if (stored.secondsLeft > 0 && stored.challengeId) {
        // Already requested recently, don't fire request again
      } else {
        void requestOtpDirectly();
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, isVerifiedSeller]);

  const handleRequestEmailOtp = async () => {
    if (resendCooldown > 0) {
      // Cooldown is active; proceed to OTP input directly
      setStep('OTP_INPUT');
      return;
    }
    setStep('OTP_INPUT');
    await requestOtpDirectly();
  };

  const handleVerifyOtp = () => {
    if (!bankChallengeId || bankOtpCode.trim().length !== 6) {
      setOtpError('Vui lòng nhập đủ mã OTP 6 chữ số.');
      return;
    }
    setVerifiedOtpCredentials({
      challengeId: bankChallengeId,
      code: bankOtpCode.trim(),
    });
    setOtpError(null);
    setStep('ENTER_NEW_BANK');
  };

  const handleFaceAuthSuccess = (resultId: string) => {
    setShowQuickFaceModal(false);
    setVerifiedQuickAuthResultId(resultId);
    setFormError(null);
    setStep('ENTER_NEW_BANK');
  };

  const handleSaveBank = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanBank = bankName.trim();
    const cleanAcc = bankAccountNumber.trim();
    const cleanHolder = bankAccountHolder.trim().toUpperCase();

    if (!cleanBank || !cleanAcc || !cleanHolder) {
      setFormError('Vui lòng điền đầy đủ Tên ngân hàng, Số tài khoản và Tên chủ tài khoản.');
      return;
    }

    setIsSaving(true);
    setFormError(null);

    try {
      const updated = await profileApi.updateBankAccount({
        bankName: cleanBank,
        bankAccountNumber: cleanAcc,
        bankAccountHolder: cleanHolder,
        quickAuthResultId: verifiedQuickAuthResultId || undefined,
        challengeId: verifiedOtpCredentials?.challengeId || undefined,
        otpCode: verifiedOtpCredentials?.code || undefined,
      });

      clearStoredBankCooldown();
      setSuccessMessage('Cập nhật tài khoản ngân hàng thành công!');
      setTimeout(() => {
        onSuccess(updated);
        onClose();
      }, 1000);
    } catch (err: unknown) {
      setFormError(err instanceof Error ? err.message : 'Cập nhật tài khoản thất bại.');
    } finally {
      setIsSaving(false);
    }
  };

  if (!isOpen) return null;

  return (
    <>
      <div
        style={securityModalStyles.overlay}
        role="dialog"
        aria-modal="true"
        aria-labelledby="change-bank-title"
      >
        <div style={securityModalStyles.dialog}>
          {/* Nút đóng [X] */}
          <button
            type="button"
            onClick={onClose}
            disabled={isSaving || successMessage !== null}
            aria-label="Đóng cửa sổ"
            style={{
              ...securityModalStyles.closeBtn,
              cursor: isSaving || successMessage !== null ? 'not-allowed' : 'pointer',
            }}
          >
            <SecurityOutlineIcons.Close />
          </button>

          {/* Tiêu đề & Bước */}
          <div style={{ textAlign: 'center', marginBottom: '20px', paddingRight: '20px', paddingLeft: '20px' }}>
            <h3
              id="change-bank-title"
              style={{
                margin: '0 0 6px',
                fontSize: '1.25rem',
                fontWeight: 700,
                color: 'var(--og-color-text-primary, #1e293b)',
              }}
            >
              {step === 'ENTER_NEW_BANK'
                ? 'Nhập Tài Khoản Ngân Hàng Mới'
                : 'Xác thực thay đổi tài khoản'}
            </h3>
            <p
              style={{
                margin: 0,
                fontSize: '0.88rem',
                color: 'var(--og-color-text-secondary, #64748b)',
                lineHeight: 1.5,
              }}
            >
              {step === 'ENTER_NEW_BANK'
                ? 'Vui lòng nhập thông tin tài khoản ngân hàng chính xác để nhận giải ngân tiền Escrow.'
                : 'Để bảo vệ quyền sở hữu và số tiền thanh toán Escrow, vui lòng xác thực danh tính.'}
            </p>
          </div>

          {/* BƯỚC 1: Chọn phương thức xác thực */}
          {step === 'SELECT_METHOD' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {isVerifiedSeller && (
                <div
                  onClick={() => setShowQuickFaceModal(true)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '16px',
                    padding: '16px 18px',
                    borderRadius: '14px',
                    border: '1.5px solid var(--og-color-border, #e2e8f0)',
                    backgroundColor: 'var(--og-color-surface-subtle, #f8fafc)',
                    cursor: 'pointer',
                    transition: 'all 0.2s ease',
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.borderColor = 'var(--og-color-primary, #3b82f6)';
                    e.currentTarget.style.transform = 'translateY(-2px)';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.borderColor = 'var(--og-color-border, #e2e8f0)';
                    e.currentTarget.style.transform = 'translateY(0)';
                  }}
                >
                  <div
                    style={{
                      width: '46px',
                      height: '46px',
                      borderRadius: '12px',
                      border: '1px solid var(--og-color-border, #cbd5e1)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      color: 'var(--og-color-primary, #3b82f6)',
                      flexShrink: 0,
                    }}
                  >
                    <SecurityOutlineIcons.FaceScan />
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '2px' }}>
                      <strong style={{ fontSize: '0.96rem' }}>Quét khuôn mặt Quick Face Auth</strong>
                      <span
                        style={{
                          fontSize: '0.72rem',
                          fontWeight: 600,
                          color: '#059669',
                          backgroundColor: 'rgba(16, 185, 129, 0.12)',
                          padding: '2px 8px',
                          borderRadius: '12px',
                        }}
                      >
                        Khuyên dùng
                      </span>
                    </div>
                    <span style={{ fontSize: '0.82rem', color: 'var(--og-color-text-secondary, #64748b)' }}>
                      Quét khuôn mặt 2 giây qua camera đối chiếu mẫu eKYC đã duyệt
                    </span>
                  </div>
                </div>
              )}

              {/* Option 2: Email OTP */}
              <div
                onClick={handleRequestEmailOtp}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '16px',
                  padding: '16px 18px',
                  borderRadius: '14px',
                  border: resendCooldown > 0
                    ? '1.5px solid #fed7aa'
                    : '1.5px solid var(--og-color-border, #e2e8f0)',
                  backgroundColor: resendCooldown > 0
                    ? '#fffbeb'
                    : 'var(--og-color-surface-subtle, #f8fafc)',
                  cursor: isRequestingOtp ? 'not-allowed' : 'pointer',
                  transition: 'all 0.2s ease',
                  opacity: isRequestingOtp ? 0.7 : 1,
                }}
                onMouseEnter={(e) => {
                  if (!isRequestingOtp) {
                    e.currentTarget.style.borderColor = 'var(--og-color-primary, #3b82f6)';
                    e.currentTarget.style.transform = 'translateY(-2px)';
                  }
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.borderColor = resendCooldown > 0 ? '#fed7aa' : 'var(--og-color-border, #e2e8f0)';
                  e.currentTarget.style.transform = 'translateY(0)';
                }}
              >
                <div
                  style={{
                    width: '46px',
                    height: '46px',
                    borderRadius: '12px',
                    border: '1px solid var(--og-color-border, #cbd5e1)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: resendCooldown > 0 ? '#d97706' : 'var(--og-color-text-primary, #334155)',
                    flexShrink: 0,
                    backgroundColor: resendCooldown > 0 ? '#fef3c7' : 'transparent',
                  }}
                >
                  <SecurityOutlineIcons.MailOtp />
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '2px', flexWrap: 'wrap' }}>
                    <span style={{ fontWeight: 600, fontSize: '0.96rem' }}>
                      Nhận mã xác thực OTP qua Email
                    </span>
                    {resendCooldown > 0 && (
                      <span
                        style={{
                          fontSize: '0.72rem',
                          fontWeight: 600,
                          color: '#b45309',
                          backgroundColor: '#fef3c7',
                          border: '1px solid #fde68a',
                          padding: '2px 8px',
                          borderRadius: '10px',
                        }}
                      >
                        ⏳ Đã gửi mã ({resendCooldown}s)
                      </span>
                    )}
                  </div>
                  <span style={{ fontSize: '0.82rem', color: 'var(--og-color-text-secondary, #64748b)' }}>
                    {resendCooldown > 0
                      ? 'Mã OTP đã được gửi. Bấm để tiếp tục nhập mã hoặc đợi hết thời gian để gửi lại.'
                      : 'Gửi mã OTP 6 số đến email chính chủ tài khoản của bạn'}
                  </span>
                </div>
              </div>

              {resendCooldown > 0 && (
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '10px 14px',
                    borderRadius: '10px',
                    backgroundColor: '#f8fafc',
                    border: '1px solid #e2e8f0',
                    fontSize: '0.84rem',
                  }}
                >
                  <span style={{ color: 'var(--og-color-text-secondary, #64748b)' }}>
                    Mã xác thực còn hiệu lực. Bạn có thể nhập ngay:
                  </span>
                  <Button
                    size="sm"
                    variant="outline"
                    type="button"
                    onClick={() => setStep('OTP_INPUT')}
                  >
                    Nhập mã OTP đã gửi
                  </Button>
                </div>
              )}

              {otpError && (
                <div style={{ marginTop: '4px' }}>
                  <Alert type="danger">{otpError}</Alert>
                </div>
              )}

              <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '14px' }}>
                <Button variant="outline" type="button" onClick={onClose}>
                  Hủy bỏ
                </Button>
              </div>
            </div>
          )}

          {/* BƯỚC 2: Nhập mã OTP Email */}
          {step === 'OTP_INPUT' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <Alert type="info">
                Mã xác thực 6 chữ số đã được gửi tới email <strong>{maskedEmail || accountEmail}</strong>. Vui lòng kiểm tra hộp thư.
              </Alert>

              {otpError && <Alert type="danger">{otpError}</Alert>}

              <div>
                <label style={{ display: 'block', fontSize: '0.88rem', fontWeight: 600, marginBottom: '6px' }}>
                  Mã OTP 6 chữ số <span style={{ color: 'var(--og-color-danger, #ef4444)' }}>*</span>
                </label>
                <input
                  type="text"
                  value={bankOtpCode}
                  onChange={(e) => setBankOtpCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  placeholder="000000"
                  maxLength={6}
                  style={{
                    width: '100%',
                    height: '48px',
                    textAlign: 'center',
                    fontSize: '1.4rem',
                    fontWeight: 700,
                    letterSpacing: '0.35em',
                    fontFamily: 'monospace',
                    borderRadius: '10px',
                    border: '1.5px solid var(--og-color-border, #cbd5e1)',
                    backgroundColor: 'var(--og-color-surface, #ffffff)',
                    color: 'var(--og-color-text-primary, #0f172a)',
                    boxSizing: 'border-box',
                    outline: 'none',
                    transition: 'border-color 0.2s ease',
                  }}
                  onFocus={(e) => (e.target.style.borderColor = 'var(--og-color-primary, #3b82f6)')}
                  onBlur={(e) => (e.target.style.borderColor = 'var(--og-color-border, #cbd5e1)')}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
                {isVerifiedSeller ? (
                  <Button
                    variant="outline"
                    size="sm"
                    type="button"
                    onClick={() => setStep('SELECT_METHOD')}
                  >
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                      <SecurityOutlineIcons.ArrowLeft />
                      <span>Quay lại</span>
                    </span>
                  </Button>
                ) : (
                  <Button variant="outline" size="sm" type="button" onClick={onClose}>
                    Hủy bỏ
                  </Button>
                )}
                <div style={{ display: 'flex', gap: '8px' }}>
                  <Button
                    variant="outline"
                    size="sm"
                    type="button"
                    onClick={requestOtpDirectly}
                    disabled={resendCooldown > 0 || isRequestingOtp}
                  >
                    {resendCooldown > 0 ? `Gửi lại sau (${resendCooldown}s)` : 'Gửi lại mã'}
                  </Button>
                  <Button
                    variant="primary"
                    size="md"
                    type="button"
                    onClick={handleVerifyOtp}
                    disabled={bankOtpCode.length !== 6}
                  >
                    Xác nhận mã
                  </Button>
                </div>
              </div>
            </div>
          )}

          {/* BƯỚC 3: Nhập thông tin tài khoản ngân hàng mới (Tận dụng điền form) */}
          {step === 'ENTER_NEW_BANK' && (
            <form onSubmit={handleSaveBank} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                  padding: '10px 14px',
                  borderRadius: '10px',
                  backgroundColor: 'rgba(16, 185, 129, 0.1)',
                  color: '#065f46',
                  fontSize: '0.86rem',
                  fontWeight: 500,
                }}
              >
                <SecurityOutlineIcons.CheckCircle />
                <span>Xác thực danh tính thành công. Vui lòng nhập thông tin tài khoản ngân hàng mới.</span>
              </div>

              {formError && <Alert type="danger">{formError}</Alert>}
              {successMessage && <Alert type="success">{successMessage}</Alert>}

              <BankSelect
                id="change-bank-select"
                label="Tên ngân hàng"
                value={bankName}
                onChange={(selected) => setBankName(selected)}
                placeholder="Chọn hoặc tìm kiếm ngân hàng (Ví dụ: MB Bank, Vietcombank, Techcombank...)"
                required
              />

              <div>
                <label style={{ display: 'block', fontSize: '0.88rem', fontWeight: 600, marginBottom: '6px' }}>
                  Số tài khoản ngân hàng <span style={{ color: 'var(--og-color-danger, #ef4444)' }}>*</span>
                </label>
                <Input
                  value={bankAccountNumber}
                  onChange={(e) => setBankAccountNumber(e.target.value.replace(/\D/g, ''))}
                  placeholder="Nhập số tài khoản ngân hàng (chỉ gồm số)"
                  required
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.88rem', fontWeight: 600, marginBottom: '6px' }}>
                  Tên chủ tài khoản (Viết hoa không dấu) <span style={{ color: 'var(--og-color-danger, #ef4444)' }}>*</span>
                </label>
                <Input
                  value={bankAccountHolder}
                  onChange={(e) => setBankAccountHolder(e.target.value.toUpperCase())}
                  placeholder="Ví dụ: NGUYEN VAN A"
                  required
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px' }}>
                <Button
                  variant="outline"
                  type="button"
                  onClick={onClose}
                  disabled={isSaving || successMessage !== null}
                >
                  Hủy bỏ
                </Button>
                <Button
                  variant="primary"
                  type="submit"
                  disabled={isSaving || !bankName.trim() || !bankAccountNumber.trim() || !bankAccountHolder.trim() || successMessage !== null}
                >
                  {isSaving ? 'Đang lưu...' : 'Lưu tài khoản ngân hàng'}
                </Button>
              </div>
            </form>
          )}
        </div>
      </div>

      {/* Quick Face Auth Modal */}
      {showQuickFaceModal && (
        <QuickFaceAuthModal
          isOpen={showQuickFaceModal}
          purpose="CHANGE_BANK"
          onClose={() => setShowQuickFaceModal(false)}
          onSuccess={handleFaceAuthSuccess}
        />
      )}
    </>
  );
};
