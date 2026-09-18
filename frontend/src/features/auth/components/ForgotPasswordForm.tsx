import React, { useState } from 'react';
import { mockAuthService } from '../mockAuthService';
import { Input, Button, Alert } from '../../../shared/components';

interface ForgotPasswordFormProps {
  onSuccess?: () => void;
  onNavigateToLogin?: () => void;
}

export const ForgotPasswordForm: React.FC<ForgotPasswordFormProps> = ({
  onSuccess,
  onNavigateToLogin,
}) => {
  const [step, setStep] = useState<'REQUEST' | 'RESET'>('REQUEST');
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [message, setMessage] = useState<{ type: 'info' | 'success' | 'danger'; text: string } | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleRequestOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessage(null);

    if (!email.trim()) {
      setMessage({ type: 'danger', text: 'Vui lòng nhập địa chỉ email.' });
      return;
    }

    setIsSubmitting(true);
    try {
      await mockAuthService.requestPasswordReset(email.trim());
      setStep('RESET');
      setMessage({
        type: 'info',
        text: 'Mã OTP xác thực đã được gửi tới email của bạn (Mã thử nghiệm: 123456).',
      });
    } catch (err: unknown) {
      setMessage({ type: 'danger', text: err instanceof Error ? err.message : 'Có lỗi xảy ra.' });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleResetPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessage(null);

    if (!otp.trim() || !newPassword) {
      setMessage({ type: 'danger', text: 'Vui lòng điền đầy đủ mã OTP và mật khẩu mới.' });
      return;
    }

    if (newPassword.length < 8) {
      setMessage({ type: 'danger', text: 'Mật khẩu mới phải có tối thiểu 8 ký tự.' });
      return;
    }

    if (newPassword !== confirmPassword) {
      setMessage({ type: 'danger', text: 'Xác nhận mật khẩu mới không khớp.' });
      return;
    }

    setIsSubmitting(true);
    try {
      await mockAuthService.resetPassword(email.trim(), otp.trim(), newPassword);
      setMessage({
        type: 'success',
        text: 'Đặt lại mật khẩu thành công! Bạn có thể đăng nhập ngay bây giờ.',
      });
      setTimeout(() => {
        onSuccess?.();
      }, 1500);
    } catch (err: unknown) {
      setMessage({ type: 'danger', text: err instanceof Error ? err.message : 'Có lỗi xảy ra.' });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div>
      {message && <Alert type={message.type}>{message.text}</Alert>}

      {step === 'REQUEST' ? (
        <form onSubmit={handleRequestOtp} noValidate>
          <Input
            label="Nhập email tài khoản"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="an.nguyen@example.com"
            helperText="Chúng tôi sẽ gửi mã OTP xác nhận đặt lại mật khẩu."
            required
            autoComplete="email"
          />

          <Button type="submit" variant="primary" fullWidth isLoading={isSubmitting}>
            Gửi mã xác thực OTP
          </Button>
        </form>
      ) : (
        <form onSubmit={handleResetPassword} noValidate>
          <Input
            label="Mã OTP xác thực"
            type="text"
            value={otp}
            onChange={(e) => setOtp(e.target.value)}
            placeholder="123456"
            helperText="Nhập mã 6 chữ số (Mã demo: 123456)"
            required
          />

          <Input
            label="Mật khẩu mới"
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            placeholder="Tối thiểu 8 ký tự"
            required
            autoComplete="new-password"
          />

          <Input
            label="Xác nhận mật khẩu mới"
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            placeholder="Nhập lại mật khẩu mới"
            required
            autoComplete="new-password"
          />

          <Button type="submit" variant="primary" fullWidth isLoading={isSubmitting}>
            Xác nhận đổi mật khẩu
          </Button>
        </form>
      )}

      <div style={{ marginTop: '20px', textAlign: 'center', fontSize: '0.9rem', color: 'var(--og-color-text-secondary)' }}>
        Nhớ mật khẩu?{' '}
        <button
          type="button"
          className="og-button og-button--ghost"
          style={{ padding: '0', color: 'var(--og-color-primary)', fontWeight: 700 }}
          onClick={onNavigateToLogin}
        >
          Quay lại Đăng nhập
        </button>
      </div>
    </div>
  );
};
