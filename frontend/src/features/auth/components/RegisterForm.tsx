import React, { useEffect, useState, useRef } from 'react';
import { useAuth } from '../useAuth';
import { authApi, AuthRequestError } from '../authApi';
import { acceptsNewPassword, NEW_PASSWORD_HINT } from '../passwordPolicy';
import type { RegistrationChallenge, RegistrationCompletion, SellerRegistration, GoogleRegistration, GoogleResponse, AuthChallenge } from '../types';
import { GoogleSignInButton } from './GoogleSignInButton';
import { AuthOtpForm } from './AuthOtpForm';
import { Input, Button, Alert } from '../../../shared/components';
import { SellerRegistrationFields } from './SellerRegistrationFields';
import { RoleSelectorCards } from './RoleSelectorCards';
import { RegisterStepIndicator } from './RegisterStepIndicator';
import { PasswordInput } from './PasswordInput';
import { OtpBoxes } from './OtpBoxes';
import { emptySeller } from '../registrationDraft';

interface RegisterFormProps {
  onSuccess?: (result: RegistrationCompletion) => void;
  onNavigateToLogin?: () => void;
  onStepChange?: (step: number, role: 'BUYER' | 'SELLER') => void;
  googleRegistration?: GoogleRegistration;
}

export const RegisterForm: React.FC<RegisterFormProps> = ({
  onSuccess,
  onNavigateToLogin,
  onStepChange,
  googleRegistration,
}) => {
  const { register, verifyRegistration, reloadCurrentUser } = useAuth();
  const [googleProfile, setGoogleProfile] = useState(googleRegistration);
  const [loginChallenge, setLoginChallenge] = useState<AuthChallenge | null>(null);

  // Multi-step state: 1: Account, 2: Seller Profile (only for SELLER)
  const [step, setStep] = useState<1 | 2>(1);

  // Form Fields
  const [fullName, setFullName] = useState(googleRegistration?.fullName || '');
  const [email, setEmail] = useState(googleRegistration?.email || '');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [accountType, setAccountType] = useState<'BUYER' | 'SELLER'>('BUYER');
  const [seller, setSeller] = useState<SellerRegistration>(emptySeller);

  // OTP Verification state
  const [pending, setPending] = useState<RegistrationChallenge | null>(null);
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  const [retryAt, setRetryAt] = useState(0);
  const [exhausted, setExhausted] = useState(false);

  const topRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    onStepChange?.(pending ? (accountType === 'SELLER' ? 3 : 2) : step, accountType);
  }, [step, accountType, pending, onStepChange]);

  useEffect(() => {
    if (!pending && !retryAt) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [pending, retryAt]);

  const scrollToTop = () => {
    if (topRef.current) {
      const rect = topRef.current.getBoundingClientRect();
      if (rect.top < 0 || rect.top > window.innerHeight) {
        topRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    }
  };

  const showError = (err: unknown) => {
    setError(err instanceof Error ? err.message : 'Không thể xử lý yêu cầu. Vui lòng thử lại.');
    if (err instanceof AuthRequestError) {
      if (err.retryAfter > 0) {
        const receivedAt = Date.now();
        setNow(receivedAt);
        setRetryAt(receivedAt + err.retryAfter * 1000);
      }
      if (err.code === 'OTP_ATTEMPTS_EXHAUSTED' || err.code === 'OTP_UNAVAILABLE') {
        setExhausted(true);
      }
    }
  };

  const validateStep1 = (): boolean => {
    if (!fullName.trim() || !email.trim()) {
      setError('Vui lòng nhập họ tên và email.');
      return false;
    }
    if (!acceptsNewPassword(password)) {
      setError(NEW_PASSWORD_HINT);
      return false;
    }
    if (password !== confirmPassword) {
      setError('Xác nhận mật khẩu không khớp.');
      return false;
    }
    return true;
  };

  const validateStep2Seller = (): boolean => {
    if (!seller.storeName.trim()) {
      setError('Vui lòng nhập tên cửa hàng.');
      return false;
    }
    if (!seller.contactPhone.trim()) {
      setError('Vui lòng nhập số điện thoại liên hệ cửa hàng.');
      return false;
    }
    const p = seller.pickupAddress;
    if (!p.province.trim() || !p.district.trim() || !p.ward.trim() || !p.detailAddress.trim()) {
      setError('Vui lòng điền đầy đủ địa chỉ lấy hàng.');
      return false;
    }
    const r = seller.returnAddress;
    if (!r.province.trim() || !r.district.trim() || !r.ward.trim() || !r.detailAddress.trim()) {
      setError('Vui lòng điền đầy đủ địa chỉ trả hàng.');
      return false;
    }
    if (!seller.bankName.trim() || !seller.accountNumber.trim() || !seller.accountHolder.trim()) {
      setError('Vui lòng điền đầy đủ thông tin ngân hàng nhận thanh toán.');
      return false;
    }
    return true;
  };

  const handleNextFromStep1 = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setNotice(null);

    if (!validateStep1()) return;

    if (accountType === 'SELLER') {
      setStep(2);
      scrollToTop();
    } else {
      // For Buyer, proceed directly to registration API
      await executeRegistration();
    }
  };

  const handleNextFromStep2 = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setNotice(null);

    if (!validateStep2Seller()) return;
    await executeRegistration();
  };

  const executeRegistration = async () => {
    if (isSubmitting || retryAt > Date.now()) return;
    setIsSubmitting(true);
    setError(null);
    setNotice(null);

    try {
      const result = await register({
        fullName: fullName.trim(),
        email: email.trim(),
        phoneNumber: phoneNumber.trim() || undefined,
        password,
        accountType,
        seller: accountType === 'SELLER' ? seller : undefined,
        googleTicket: googleProfile?.registrationTicket,
      });

      setPending(result);
      setPassword('');
      setConfirmPassword('');
      setExhausted(false);
      setNow(Date.now());
      scrollToTop();
    } catch (err) {
      showError(err);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleVerifyOtp = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!pending || isSubmitting) return;
    setError(null);
    setNotice(null);

    if (!/^[0-9]{6}$/.test(code)) {
      setError('Vui lòng nhập mã gồm 6 chữ số.');
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await verifyRegistration(pending.challengeId, code);
      setPending(null);
      setCode('');
      setNotice('Đăng ký thành công. Bạn đã nhận 3.000 xu.');
      onSuccess?.(result);
    } catch (err) {
      showError(err);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleBackToStep1 = (event?: React.SyntheticEvent) => {
    event?.preventDefault();
    setError(null);
    setNotice(null);
    setStep(1);
    scrollToTop();
  };

  const resend = async () => {
    if (!pending || isSubmitting) return;
    setIsSubmitting(true);
    setError(null);
    try {
      setPending(await authApi.resendRegistration(pending.challengeId));
      setCode('');
      setExhausted(false);
      setRetryAt(0);
      setNow(Date.now());
      setNotice('Đã gửi mã mới. Vui lòng dùng mã trong email mới nhất.');
    } catch (err) {
      showError(err);
    } finally {
      setIsSubmitting(false);
    }
  };

  const cancel = async () => {
    if (!pending || isSubmitting) return;
    setIsSubmitting(true);
    setError(null);
    try {
      await authApi.cancelRegistration(pending.challengeId);
      setPending(null);
      setCode('');
      setStep(1);
      setExhausted(false);
      setNotice('Đã hủy đăng ký. Chưa có tài khoản được tạo.');
    } catch (err) {
      showError(err);
    } finally {
      setIsSubmitting(false);
    }
  };

  const resendSeconds = Math.max(
    0,
    Math.ceil((Math.max(pending ? Date.parse(pending.resendAt) : 0, retryAt) - now) / 1000)
  );
  const expired = pending !== null && Date.parse(pending.expiresAt) <= now;

  // Calculate current active step for the indicator
  const currentStepNumber = pending ? (accountType === 'SELLER' ? 3 : 2) : step;

  const completeGoogle = async (result: GoogleResponse) => {
    if ('registrationTicket' in result) {
      setGoogleProfile(result); setEmail(result.email); setFullName(result.fullName);
      setStep(1); setPassword(''); setConfirmPassword(''); return;
    }
    if ('challengeId' in result) { setLoginChallenge(result); return; }
    await reloadCurrentUser();
    onSuccess?.({ ...result, nextStep: 'PROFILE', awardedCoins: 0 });
  };
  if (loginChallenge) return <AuthOtpForm key={loginChallenge.challengeId} challenge={loginChallenge} onChange={setLoginChallenge}
    onVerify={async value => completeGoogle(await authApi.verifyLogin(loginChallenge, value))}
    onCancel={() => setLoginChallenge(null)} />;

  return (
    <div ref={topRef}>
      {/* 1. PROGRESS INDICATOR */}
      <RegisterStepIndicator
        currentStep={currentStepNumber}
        accountType={accountType}
        onStepClick={(targetStep) => {
          if (step === 2 && targetStep === 1 && !pending) {
            handleBackToStep1();
          }
        }}
      />

      {/* 2. ALERTS */}
      {error && <Alert type="danger">{error}</Alert>}
      {notice && <Alert type="success">{notice}</Alert>}
      {!pending && resendSeconds > 0 && (
        <p role="timer" aria-live="off" style={{ fontSize: '0.9rem', color: 'var(--og-color-text-secondary)' }}>
          Bạn có thể gửi lại mã sau <strong>{resendSeconds} giây</strong>.
        </p>
      )}

      {/* 3. STEP CONTENT */}
      {pending ? (
        /* STEP 3 (or Step 2 for Buyer): EMAIL OTP VERIFICATION */
        <form onSubmit={handleVerifyOtp} className="og-step-enter">
          <Alert type="info">
            Mã xác thực đã được gửi đến <strong>{pending.email}</strong>. Tài khoản sẽ được tạo sau khi bạn xác nhận email.
          </Alert>

          <OtpBoxes
            id="register-otp-input"
            label="Mã xác thực email"
            value={code}
            onChange={setCode}
            disabled={isSubmitting || expired || exhausted}
            autoFocus
          />

          <div className="og-otp-status-row">
            <div className="og-otp-expires-text">
              {expired ? (
                <span style={{ color: 'var(--og-color-danger)' }}>Mã đã hết hạn. Hãy gửi lại để nhận mã mới.</span>
              ) : (
                <>
                  Mã có hiệu lực trong: <strong>{(() => {
                    const secs = Math.max(0, Math.ceil((Date.parse(pending.expiresAt) - now) / 1000));
                    const m = Math.floor(secs / 60);
                    const s = secs % 60;
                    return `${m}:${s < 10 ? '0' : ''}${s}`;
                  })()}</strong>
                </>
              )}
            </div>
          </div>

          <Button type="submit" fullWidth isLoading={isSubmitting} disabled={expired || exhausted}>
            Xác nhận và tạo tài khoản
          </Button>

          <div style={{ display: 'flex', gap: '12px', marginTop: '16px', flexWrap: 'wrap' }}>
            <Button
              type="button"
              variant="outline"
              onClick={() => { void resend(); }}
              disabled={isSubmitting || resendSeconds > 0}
            >
              {resendSeconds > 0 ? 'Gửi lại sau ' + resendSeconds + ' giây' : 'Gửi lại mã'}
            </Button>
            <Button type="button" variant="ghost" onClick={() => { void cancel(); }} disabled={isSubmitting}>
              Hủy đăng ký
            </Button>
          </div>
        </form>
      ) : step === 1 ? (
        /* STEP 1: ACCOUNT INFORMATION & ROLE SELECTION */
        <form key="account" onSubmit={handleNextFromStep1} className="og-step-enter">
          <fieldset disabled={isSubmitting} style={{ border: 0, padding: 0, margin: 0 }}>
            <div className="og-form-grid-2">
              <div className="og-form-col-full">
                <Input
                  label="Họ và tên"
                  value={fullName}
                  onChange={(event) => setFullName(event.target.value)}
                  required
                  maxLength={120}
                  autoComplete="name"
                  placeholder="Nhập họ và tên đầy đủ của bạn"
                />
              </div>

              <Input
                label="Địa chỉ Email"
                readOnly={Boolean(googleProfile)}
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                required
                maxLength={320}
                autoComplete="email"
                placeholder="name@example.com"
              />

              <Input
                label="Số điện thoại (không bắt buộc)"
                type="tel"
                value={phoneNumber}
                onChange={(event) => setPhoneNumber(event.target.value)}
                pattern="(0|\+84)[35789][0-9]{8}"
                autoComplete="tel"
                placeholder="0912345678"
              />

              <PasswordInput
                label="Mật khẩu"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
                autoComplete="new-password"
                helperText="Ít nhất 8 ký tự."
                placeholder="••••••••"
              />

              <PasswordInput
                label="Xác nhận mật khẩu"
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                required
                autoComplete="new-password"
                placeholder="••••••••"
              />
            </div>

            {/* ROLE SELECTOR CARDS */}
            <RoleSelectorCards
              value={accountType}
              onChange={setAccountType}
              disabled={isSubmitting}
            />
          </fieldset>

          {/* ACTION BUTTON */}
          <div className="og-step-actions">
            <Button
              type="submit"
              fullWidth
              isLoading={isSubmitting}
              disabled={resendSeconds > 0}
            >
              {accountType === 'SELLER' ? 'Tiếp tục →' : 'Gửi mã xác thực email'}
            </Button>
          </div>

          {/* DIVIDER & CENTERED GOOGLE SIGN-IN */}
          {googleProfile ? (
            <div style={{ marginTop: '16px' }}>
              <Alert type="info">
                Google đã xác minh: {googleProfile.email}. Bổ sung thông tin, đặt mật khẩu và xác thực email bằng OTP để hoàn tất đăng ký.
              </Alert>
              <Button type="button" variant="ghost" disabled={isSubmitting} onClick={() => setGoogleProfile(undefined)}>
                Đổi tài khoản Google
              </Button>
            </div>
          ) : (
            <GoogleSignInButton
              onResult={completeGoogle}
              showDivider
              dividerText="Hoặc tiếp tục với Google"
            />
          )}

          {/* BOTTOM LINK TO LOGIN */}
          <div style={{ marginTop: '20px', textAlign: 'center', fontSize: '0.92rem' }}>
            Đã có tài khoản?{' '}
            <button
              type="button"
              className="og-button og-button--ghost"
              onClick={onNavigateToLogin}
              disabled={isSubmitting}
              style={{ fontWeight: 600, padding: '4px 8px', color: 'var(--og-color-primary)' }}
            >
              Đăng nhập
            </button>
          </div>
        </form>
      ) : (
        /* STEP 2: SELLER PROFILE (ONLY FOR SELLER) */
        <form key="seller" onSubmit={handleNextFromStep2} className="og-step-enter">
          <fieldset disabled={isSubmitting} style={{ border: 0, padding: 0, margin: 0 }}>
            <SellerRegistrationFields
              value={seller}
              onChange={setSeller}
              disabled={isSubmitting}
            />
          </fieldset>

          {/* ACTION BUTTONS (BACK & NEXT) */}
          <div className="og-step-actions og-step-actions--between">
            <Button
              type="button"
              variant="outline"
              onClick={handleBackToStep1}
              disabled={isSubmitting}
            >
              ← Quay lại
            </Button>
            <Button
              type="submit"
              isLoading={isSubmitting}
              disabled={resendSeconds > 0}
            >
              Gửi mã xác thực email
            </Button>
          </div>

          <div style={{ marginTop: '20px', textAlign: 'center', fontSize: '0.92rem' }}>
            Đã có tài khoản?{' '}
            <button
              type="button"
              className="og-button og-button--ghost"
              onClick={onNavigateToLogin}
              disabled={isSubmitting}
              style={{ fontWeight: 600, padding: '4px 8px', color: 'var(--og-color-primary)' }}
            >
              Đăng nhập
            </button>
          </div>
        </form>
      )}
    </div>
  );
};
