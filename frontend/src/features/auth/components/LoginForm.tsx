import { useState } from 'react';
import { useAuth } from '../useAuth';
import { Input, Button, Alert } from '../../../shared/components';

interface LoginFormProps {
  onSuccess?: () => void;
  onNavigateToRegister?: () => void;
  onNavigateToForgot?: () => void;
}

export const LoginForm: React.FC<LoginFormProps> = ({
  onSuccess,
  onNavigateToRegister,
  onNavigateToForgot,
}) => {
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!email.trim() || !password) {
      setError('Vui lòng nhập đầy đủ email và mật khẩu.');
      return;
    }

    setIsSubmitting(true);
    try {
      await login({ email: email.trim(), password, rememberMe });
      onSuccess?.();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Đăng nhập không thành công. Vui lòng thử lại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} noValidate>
      {error && <Alert type="danger">{error}</Alert>}

      <Input
        label="Địa chỉ Email"
        type="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        placeholder="ví dụ: ban@ogshop.vn"
        required
        autoComplete="email"
      />

      <Input
        label="Mật khẩu"
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        placeholder="••••••••"
        required
        autoComplete="current-password"
      />

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontSize: '0.88rem' }}>
          <input
            type="checkbox"
            checked={rememberMe}
            onChange={(e) => setRememberMe(e.target.checked)}
          />
          Ghi nhớ đăng nhập
        </label>
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

      <div style={{ marginTop: '20px', textAlign: 'center', fontSize: '0.9rem', color: 'var(--og-color-text-secondary)' }}>
        Chưa có tài khoản?{' '}
        <button
          type="button"
          className="og-button og-button--ghost"
          style={{ padding: '0', color: 'var(--og-color-primary)', fontWeight: 700 }}
          onClick={onNavigateToRegister}
        >
          Đăng ký ngay
        </button>
      </div>

    </form>
  );
};
