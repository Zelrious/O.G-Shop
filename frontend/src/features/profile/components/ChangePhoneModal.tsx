import React, { useState, useEffect } from 'react';
import { Button, Alert } from '../../../shared/components';
import { profileApi } from '../profileApi';
import { QuickFaceAuthModal } from '../../verification/components/QuickFaceAuthModal';
import { SecurityOutlineIcons as OutlineIcons } from './securityModalShared';

export interface ChangePhoneModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (newPhoneNumber: string) => void;
  currentPhoneNumber?: string | null;
  currentFullName?: string | null;
  accountEmail?: string | null;
  isVerifiedSeller?: boolean;
}

type ModalStep = 'SELECT_METHOD' | 'OTP_INPUT' | 'ENTER_NEW_PHONE';

const PHONE_COOLDOWN_KEY = 'og_phone_otp_resend_until';
const PHONE_CHALLENGE_KEY = 'og_phone_challenge_id';

function getStoredPhoneCooldown(): { secondsLeft: number; challengeId: string | null } {
  try {
    const rawUntil = sessionStorage.getItem(PHONE_COOLDOWN_KEY);
    const challengeId = sessionStorage.getItem(PHONE_CHALLENGE_KEY);
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

function setStoredPhoneCooldown(seconds: number, challengeId: string) {
  try {
    sessionStorage.setItem(PHONE_COOLDOWN_KEY, String(Date.now() + seconds * 1000));
    sessionStorage.setItem(PHONE_CHALLENGE_KEY, challengeId);
  } catch {
    // ignore
  }
}

function clearStoredPhoneCooldown() {
  try {
    sessionStorage.removeItem(PHONE_COOLDOWN_KEY);
    sessionStorage.removeItem(PHONE_CHALLENGE_KEY);
  } catch {
    // ignore
  }
}

export const ChangePhoneModal: React.FC<ChangePhoneModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  currentPhoneNumber,
  currentFullName,
  accountEmail,
  isVerifiedSeller = false,
}) => {
  const [step, setStep] = useState<ModalStep>('SELECT_METHOD');
  const [showQuickFaceModal, setShowQuickFaceModal] = useState<boolean>(false);

  // OTP State
  const [phoneChallengeId, setPhoneChallengeId] = useState<string | null>(null);
  const [phoneOtpCode, setPhoneOtpCode] = useState<string>('');
  const [maskedEmail, setMaskedEmail] = useState<string>('');
  const [otpError, setOtpError] = useState<string | null>(null);
  const [isRequestingOtp, setIsRequestingOtp] = useState<boolean>(false);
  const [isVerifyingOtp, setIsVerifyingOtp] = useState<boolean>(false);
  const [resendCooldown, setResendCooldown] = useState<number>(0);

  // New Phone & Real-time Validation State
  const [newPhone, setNewPhone] = useState<string>('');
  const [phoneInputError, setPhoneInputError] = useState<string | null>(null);
  const [isPhoneValidFormat, setIsPhoneValidFormat] = useState<boolean>(false);
  const [isSavingPhone, setIsSavingPhone] = useState<boolean>(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Verified Credentials from Step 1
  const [verifiedQuickAuthResultId, setVerifiedQuickAuthResultId] = useState<string | null>(null);
  const [verifiedOtpCredentials, setVerifiedOtpCredentials] = useState<{ challengeId: string; code: string } | null>(null);

  // Cooldown timer
  useEffect(() => {
    if (resendCooldown <= 0) return;
    const timer = setInterval(() => {
      setResendCooldown((prev) => (prev > 0 ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(timer);
  }, [resendCooldown]);

  // Reset state when modal opens with cooldown persistence
  useEffect(() => {
    if (!isOpen) return;
    setStep('SELECT_METHOD');
    setShowQuickFaceModal(false);
    setPhoneOtpCode('');
    setOtpError(null);
    setNewPhone('');
    setPhoneInputError(null);
    setIsPhoneValidFormat(false);
    setServerError(null);
    setSuccessMessage(null);
    setVerifiedQuickAuthResultId(null);
    setVerifiedOtpCredentials(null);

    const stored = getStoredPhoneCooldown();
    if (stored.secondsLeft > 0) {
      setResendCooldown(stored.secondsLeft);
      if (stored.challengeId) setPhoneChallengeId(stored.challengeId);
    } else {
      setResendCooldown(0);
      setPhoneChallengeId(null);
    }
  }, [isOpen]);

  // Real-time phone input validation
  const handlePhoneInputChange = (rawVal: string) => {
    // Only allow numeric characters
    const numeric = rawVal.replace(/\D/g, '');
    setNewPhone(numeric);
    setServerError(null);

    if (!numeric) {
      setPhoneInputError('Vui lòng nhập số điện thoại muốn đổi.');
      setIsPhoneValidFormat(false);
      return;
    }

    // Check Vietnamese mobile length & prefix
    if (!numeric.startsWith('0')) {
      setPhoneInputError('Số điện thoại di động phải bắt đầu bằng chữ số 0 (ví dụ: 0912345678).');
      setIsPhoneValidFormat(false);
      return;
    }

    if (numeric.length > 1 && !/^(0[35789])/.test(numeric)) {
      setPhoneInputError('Đầu số không hợp lệ. Vui lòng nhập đầu số di động Việt Nam (03, 05, 07, 08, 09).');
      setIsPhoneValidFormat(false);
      return;
    }

    if (numeric.length < 10) {
      setPhoneInputError(`Số điện thoại cần đủ 10 chữ số (hiện có ${numeric.length}/10 số)...`);
      setIsPhoneValidFormat(false);
      return;
    }

    if (numeric.length > 10) {
      setPhoneInputError('Số điện thoại không được vượt quá 10 chữ số.');
      setIsPhoneValidFormat(false);
      return;
    }

    // Check if same as current phone
    const cleanCurrent = (currentPhoneNumber || '').replace(/\D/g, '');
    if (cleanCurrent && numeric === cleanCurrent) {
      setPhoneInputError('Số điện thoại mới trùng với số điện thoại bạn đang sử dụng hiện tại.');
      setIsPhoneValidFormat(false);
      return;
    }

    // Valid format!
    setPhoneInputError(null);
    setIsPhoneValidFormat(true);
  };

  // Step 1: Request Email OTP
  const handleRequestEmailOtp = async () => {
    if (resendCooldown > 0) {
      setStep('OTP_INPUT');
      return;
    }
    setIsRequestingOtp(true);
    setOtpError(null);
    try {
      const res = await profileApi.requestPhoneChangeOtp();
      const email = res.maskedEmail || accountEmail || 'email đăng ký';
      setPhoneChallengeId(res.challengeId);
      setMaskedEmail(email);
      setStep('OTP_INPUT');
      setResendCooldown(60);
      setStoredPhoneCooldown(60, res.challengeId);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Không thể gửi mã xác thực OTP qua Email.';
      setOtpError(msg);
      if (msg.includes('đợi') || msg.includes('trước khi') || msg.includes('429')) {
        const stored = getStoredPhoneCooldown();
        if (stored.secondsLeft <= 0) {
          setResendCooldown(60);
          setStoredPhoneCooldown(60, phoneChallengeId || '');
        }
      }
    } finally {
      setIsRequestingOtp(false);
    }
  };

  // Step 2: Confirm OTP
  const handleVerifyOtp = async () => {
    if (!phoneChallengeId || phoneOtpCode.trim().length !== 6) {
      setOtpError('Vui lòng nhập đủ mã OTP 6 chữ số.');
      return;
    }
    setIsVerifyingOtp(true);
    setOtpError(null);
    try {
      await profileApi.verifyPhoneOtp({
        challengeId: phoneChallengeId,
        code: phoneOtpCode.trim(),
      });
      setVerifiedOtpCredentials({
        challengeId: phoneChallengeId,
        code: phoneOtpCode.trim(),
      });
      setStep('ENTER_NEW_PHONE');
    } catch (err: unknown) {
      setOtpError(err instanceof Error ? err.message : 'Mã OTP không chính xác hoặc đã hết hạn.');
    } finally {
      setIsVerifyingOtp(false);
    }
  };

  // Face Auth Success
  const handleFaceAuthSuccess = (resultId: string) => {
    setShowQuickFaceModal(false);
    setVerifiedQuickAuthResultId(resultId);
    setServerError(null);
    setStep('ENTER_NEW_PHONE');
  };

  // Step 3: Save New Phone & Check Database Conflicts
  const handleSavePhone = async () => {
    if (!isPhoneValidFormat || !newPhone.trim()) {
      setPhoneInputError('Vui lòng nhập số điện thoại hợp lệ gồm 10 chữ số.');
      return;
    }

    const cleanCurrent = (currentPhoneNumber || '').replace(/\D/g, '');
    if (cleanCurrent && newPhone.trim() === cleanCurrent) {
      setServerError('Số điện thoại này trùng với số điện thoại bạn đang sử dụng hiện tại.');
      return;
    }

    setIsSavingPhone(true);
    setServerError(null);

    try {
      // 1. Kiểm tra tính khả dụng với máy chủ trước
      const checkRes = await profileApi.checkPhoneAvailability(newPhone.trim());
      if (!checkRes.available) {
        if (checkRes.reason === 'SAME_AS_CURRENT') {
          setServerError('Số điện thoại này trùng với số điện thoại bạn đang sử dụng hiện tại.');
        } else if (checkRes.reason === 'ALREADY_EXISTS') {
          setServerError('Số điện thoại này đã được đăng ký bởi một người khác. Vui lòng nhập số khác.');
        } else {
          setServerError(checkRes.message || 'Số điện thoại này không khả dụng.');
        }
        setIsSavingPhone(false);
        return;
      }

      // 2. Thực hiện cập nhật số điện thoại
      await profileApi.updateProfile({
        fullName: currentFullName?.trim() || undefined,
        phoneNumber: newPhone.trim(),
        quickAuthResultId: verifiedQuickAuthResultId || undefined,
        challengeId: verifiedOtpCredentials?.challengeId || undefined,
        otpCode: verifiedOtpCredentials?.code || undefined,
      });
      clearStoredPhoneCooldown();
      setSuccessMessage('Đã cập nhật số điện thoại thành công!');
      setTimeout(() => {
        onSuccess(newPhone.trim());
        onClose();
      }, 1000);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Cập nhật số điện thoại thất bại.';
      if (msg.includes('đã được sử dụng') || msg.includes('trùng')) {
        setServerError('Số điện thoại này đã được đăng ký bởi một người khác. Vui lòng sử dụng số điện thoại khác.');
      } else {
        setServerError(msg);
      }
    } finally {
      setIsSavingPhone(false);
    }
  };

  if (!isOpen) return null;

  return (
    <>
      <div
        style={{
          position: 'fixed',
          inset: 0,
          zIndex: 9999,
          backgroundColor: 'rgba(0, 0, 0, 0.72)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '16px',
          backdropFilter: 'blur(6px)',
        }}
        role="dialog"
        aria-modal="true"
        aria-labelledby="change-phone-title"
      >
        <div
          style={{
            position: 'relative',
            width: '100%',
            maxWidth: '500px',
            backgroundColor: 'var(--og-color-surface, #ffffff)',
            color: 'var(--og-color-text-primary, #1e293b)',
            borderRadius: '20px',
            boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.45)',
            border: '1px solid var(--og-color-border, #e2e8f0)',
            maxHeight: '92vh',
            overflowY: 'auto',
            padding: '28px 24px',
            boxSizing: 'border-box',
          }}
        >
          {/* Nút đóng [X] dạng outline SVG thanh mảnh */}
          <button
            type="button"
            onClick={onClose}
            disabled={isSavingPhone || successMessage !== null}
            aria-label="Đóng cửa sổ"
            style={{
              position: 'absolute',
              top: '16px',
              right: '16px',
              width: '36px',
              height: '36px',
              borderRadius: '50%',
              border: '1px solid var(--og-color-border, #e2e8f0)',
              background: 'var(--og-color-surface-subtle, #f8fafc)',
              color: 'var(--og-color-text-secondary, #64748b)',
              cursor: isSavingPhone || successMessage !== null ? 'not-allowed' : 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              transition: 'all 0.2s ease',
              padding: 0,
            }}
          >
            <OutlineIcons.Close />
          </button>

          {/* Tiêu đề & Bước */}
          <div style={{ textAlign: 'center', marginBottom: '20px', paddingRight: '20px', paddingLeft: '20px' }}>
            <h3
              id="change-phone-title"
              style={{
                margin: '0 0 6px',
                fontSize: '1.25rem',
                fontWeight: 700,
                color: 'var(--og-color-text-primary, #1e293b)',
              }}
            >
              {step === 'ENTER_NEW_PHONE'
                ? 'Nhập Số Điện Thoại Mới'
                : 'Xác Thực Đổi Số Điện Thoại'}
            </h3>
            <p
              style={{
                margin: 0,
                fontSize: '0.88rem',
                color: 'var(--og-color-text-secondary, #64748b)',
                lineHeight: 1.5,
              }}
            >
              {step === 'ENTER_NEW_PHONE'
                ? 'Vui lòng nhập số điện thoại chính xác để nhận thông báo và liên hệ đơn hàng.'
                : 'Để bảo vệ an toàn cho tài khoản và gian hàng, vui lòng xác thực danh tính của bạn.'}
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
                    <OutlineIcons.FaceScan />
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '2px' }}>
                      <strong style={{ fontSize: '0.96rem' }}>Xác thực khuôn mặt (Face Auth)</strong>
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

              {/* Lựa chọn 2: OTP qua Email */}
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
                  <OutlineIcons.MailOtp />
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
                <div style={{ marginTop: '8px' }}>
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
                  value={phoneOtpCode}
                  onChange={(e) => setPhoneOtpCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
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
                <Button
                  variant="outline"
                  size="sm"
                  type="button"
                  onClick={() => setStep('SELECT_METHOD')}
                  disabled={isVerifyingOtp}
                >
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                    <OutlineIcons.ArrowLeft />
                    <span>Quay lại</span>
                  </span>
                </Button>
                <div style={{ display: 'flex', gap: '8px' }}>
                  <Button
                    variant="outline"
                    size="sm"
                    type="button"
                    onClick={handleRequestEmailOtp}
                    disabled={resendCooldown > 0 || isRequestingOtp || isVerifyingOtp}
                  >
                    {resendCooldown > 0 ? `Gửi lại sau (${resendCooldown}s)` : 'Gửi lại mã'}
                  </Button>
                  <Button
                    variant="primary"
                    size="md"
                    type="button"
                    onClick={handleVerifyOtp}
                    disabled={phoneOtpCode.length !== 6 || isVerifyingOtp}
                  >
                    {isVerifyingOtp ? 'Đang xác thực...' : 'Xác nhận mã'}
                  </Button>
                </div>
              </div>
            </div>
          )}

          {/* BƯỚC 3: Nhập số điện thoại mới & Real-time Validation */}
          {step === 'ENTER_NEW_PHONE' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
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
                <OutlineIcons.CheckCircle />
                <span>Xác thực danh tính thành công. Vui lòng nhập số điện thoại mới.</span>
              </div>

              {serverError && <Alert type="danger">{serverError}</Alert>}
              {successMessage && <Alert type="success">{successMessage}</Alert>}

              <div>
                <label style={{ display: 'block', fontSize: '0.88rem', fontWeight: 600, marginBottom: '6px' }}>
                  Số điện thoại mới <span style={{ color: 'var(--og-color-danger, #ef4444)' }}>*</span>
                </label>
                <div style={{ position: 'relative' }}>
                  <input
                    type="tel"
                    value={newPhone}
                    onChange={(e) => handlePhoneInputChange(e.target.value)}
                    placeholder="VD: 0912345678"
                    maxLength={10}
                    disabled={isSavingPhone || successMessage !== null}
                    style={{
                      width: '100%',
                      height: '46px',
                      padding: '0 40px 0 14px',
                      fontSize: '1.05rem',
                      fontWeight: 600,
                      borderRadius: '10px',
                      border: '1.5px solid',
                      borderColor:
                        isPhoneValidFormat
                          ? '#10b981'
                          : phoneInputError
                            ? '#ef4444'
                            : 'var(--og-color-border, #cbd5e1)',
                      backgroundColor: 'var(--og-color-surface, #ffffff)',
                      color: 'var(--og-color-text-primary, #0f172a)',
                      outline: 'none',
                      boxSizing: 'border-box',
                      transition: 'border-color 0.2s ease',
                    }}
                  />
                  {isPhoneValidFormat && (
                    <span
                      style={{
                        position: 'absolute',
                        right: '12px',
                        top: '50%',
                        transform: 'translateY(-50%)',
                        display: 'flex',
                        alignItems: 'center',
                      }}
                    >
                      <OutlineIcons.CheckCircle />
                    </span>
                  )}
                </div>

                {/* Real-time Validation Error Display */}
                {phoneInputError && (
                  <span
                    style={{
                      display: 'block',
                      fontSize: '0.8rem',
                      color: '#ef4444',
                      marginTop: '6px',
                      fontWeight: 500,
                    }}
                  >
                    ⚠️ {phoneInputError}
                  </span>
                )}

                {!phoneInputError && !isPhoneValidFormat && (
                  <span
                    style={{
                      display: 'block',
                      fontSize: '0.78rem',
                      color: 'var(--og-color-text-secondary, #64748b)',
                      marginTop: '6px',
                    }}
                  >
                    Số điện thoại di động Việt Nam gồm 10 chữ số (đầu số 03, 05, 07, 08, 09).
                  </span>
                )}
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px' }}>
                <Button
                  variant="outline"
                  size="md"
                  type="button"
                  onClick={onClose}
                  disabled={isSavingPhone || successMessage !== null}
                >
                  Hủy bỏ
                </Button>
                <Button
                  variant="primary"
                  size="md"
                  type="button"
                  onClick={handleSavePhone}
                  disabled={!isPhoneValidFormat || isSavingPhone || successMessage !== null}
                >
                  {isSavingPhone ? 'Đang kiểm tra & lưu...' : 'Lưu số điện thoại'}
                </Button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Quick Face Auth Modal (UC08) with continuous scanning */}
      <QuickFaceAuthModal
        isOpen={showQuickFaceModal}
        onClose={() => setShowQuickFaceModal(false)}
        onSuccess={handleFaceAuthSuccess}
        title="Xác thực khuôn mặt đổi số điện thoại"
        description="Vui lòng nhìn thẳng và giữ khuôn mặt ổn định trong vòng 2 giây."
      />
    </>
  );
};
