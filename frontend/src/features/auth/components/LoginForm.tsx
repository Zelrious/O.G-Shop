import { useState } from 'react';
import { useAuth } from '../useAuth';
import type { UserPrincipal, AuthChallenge, GoogleRegistration, GoogleResponse } from '../types';
import { authApi, AuthRequestError } from '../authApi';
import { AuthOtpForm } from './AuthOtpForm';
import { GoogleSignInButton } from './GoogleSignInButton';
import { Input, Button, Alert } from '../../../shared/components';
import { PasswordInput } from './PasswordInput';

interface LoginFormProps {
  onSuccess?: (user: UserPrincipal) => void;
  onNavigateToRegister?: (registration?: GoogleRegistration) => void;
  onNavigateToForgot?: () => void;
}

export const LoginForm: React.FC<LoginFormProps> = ({
  onSuccess,
  onNavigateToRegister,
  onNavigateToForgot,
}) => {
  const { login, reloadCurrentUser } = useAuth();
  const [pending, setPending] = useState<AuthChallenge | null>(null);
  const [restriction, setRestriction] = useState<string | null>(null);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setRestriction(null);

    if (!email.trim() || !password) {
      setError('Vui lòng nhập đầy đủ email và mật khẩu.');
      return;
    }

    setIsSubmitting(true);
    try {
      const loggedUser = await login({ email: email.trim(), password });
      if ('challengeId' in loggedUser) { setPending(loggedUser); setPassword(''); return; }
      onSuccess?.(loggedUser);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Đăng nhập không thành công. Vui lòng thử lại.');
      setRestriction(err instanceof AuthRequestError && err.code === 'ACCOUNT_RESTRICTED'
        ? `${err.details?.accountStatus || ''}. ${err.details?.supportInstruction || ''} Chức năng hiện có: liên hệ hỗ trợ.` : null);
    } finally {
      setIsSubmitting(false);
    }
  };

  const complete = async (result: GoogleResponse) => {
    if ('registrationTicket' in result) { onNavigateToRegister?.(result); return; }
    if ('challengeId' in result) { setPending(result); return; }
    await reloadCurrentUser(); onSuccess?.(result.user);
  };

  if (pending) {
    return (
      <AuthOtpForm
        key={pending.challengeId}
        challenge={pending}
        onChange={setPending}
        onVerify={async (code) => complete(await authApi.verifyLogin(pending, code))}
        onCancel={() => {
          setPending(null);
          setPassword('');
          setError(null);
        }}
      />
    );
  }

  return (
    <div className="og-login-form-wrapper">
      <form key="credentials" onSubmit={handleSubmit} noValidate>
        {error && <Alert type="danger">{error}</Alert>}
        {restriction && <Alert type="info">{restriction}</Alert>}

        <Input
          label="Địa chỉ Email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="name@example.com"
          required
          autoComplete="email"
        />

        <PasswordInput
          label="Mật khẩu"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="••••••••"
          required
          autoComplete="current-password"
          toggleAriaLabel="Hiển thị ký tự"
        />

        <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', marginBottom: '20px' }}>
          {onNavigateToForgot && (
            <button
              type="button"
              className="og-button og-button--ghost"
              style={{ padding: '0', fontSize: '0.88rem', color: 'var(--og-color-primary)' }}
              onClick={onNavigateToForgot}
            >
              Quên mật khẩu?
            </button>
          )}
        </div>

        <Button type="submit" variant="primary" fullWidth isLoading={isSubmitting}>
          Đăng nhập
        </Button>
      </form>

      {/* DIVIDER & CENTERED GOOGLE SIGN-IN BUTTON */}
      <GoogleSignInButton onResult={complete} showDivider dividerText="Hoặc tiếp tục với Google" />

      {/* BOTTOM FOOTER LINK */}
      <div style={{ marginTop: '20px', textAlign: 'center', fontSize: '0.9rem', color: 'var(--og-color-text-secondary)' }}>
        Chưa có tài khoản?{' '}
        <button
          type="button"
          className="og-button og-button--ghost"
          style={{ padding: '0', color: 'var(--og-color-primary)', fontWeight: 700 }}
          onClick={() => onNavigateToRegister?.()}
        >
          Đăng ký ngay
        </button>
      </div>
    </div>
  );
};
