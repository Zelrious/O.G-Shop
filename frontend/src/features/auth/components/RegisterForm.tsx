import { useState } from 'react';
import { useAuth } from '../useAuth';
import { Input, Button, Alert } from '../../../shared/components';

interface RegisterFormProps {
  onSuccess?: () => void;
  onNavigateToLogin?: () => void;
}

export const RegisterForm: React.FC<RegisterFormProps> = ({ onSuccess, onNavigateToLogin }) => {
  const { register } = useAuth();
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!fullName.trim() || !email.trim() || !phoneNumber.trim() || !password) {
      setError('Vui lòng điền đầy đủ tất cả các trường.');
      return;
    }

    if (password.length < 8) {
      setError('Mật khẩu phải có độ dài tối thiểu 8 ký tự.');
      return;
    }

    if (password !== confirmPassword) {
      setError('Xác nhận mật khẩu không khớp. Vui lòng kiểm tra lại.');
      return;
    }

    setIsSubmitting(true);
    try {
      await register({
        fullName: fullName.trim(),
        email: email.trim(),
        phoneNumber: phoneNumber.trim(),
        password,
      });
      onSuccess?.();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Đăng ký thất bại. Vui lòng thử lại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} noValidate>
      {error && <Alert type="danger">{error}</Alert>}

      <Input
        label="Họ và tên"
        type="text"
        value={fullName}
        onChange={(e) => setFullName(e.target.value)}
        placeholder="ví dụ: Nguyễn Văn An"
        required
        autoComplete="name"
      />

      <Input
        label="Địa chỉ Email"
        type="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        placeholder="an.nguyen@example.com"
        required
        autoComplete="email"
      />

      <Input
        label="Số điện thoại"
        type="tel"
        value={phoneNumber}
        onChange={(e) => setPhoneNumber(e.target.value)}
        placeholder="0912345678"
        required
        autoComplete="tel"
      />

      <Input
        label="Mật khẩu"
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        placeholder="Tối thiểu 8 ký tự"
        helperText="Nên chứa chữ cái, chữ số và ký tự đặc biệt"
        required
        autoComplete="new-password"
      />

      <Input
        label="Xác nhận mật khẩu"
        type="password"
        value={confirmPassword}
        onChange={(e) => setConfirmPassword(e.target.value)}
        placeholder="Nhập lại mật khẩu"
        required
        autoComplete="new-password"
      />

      <Button type="submit" variant="primary" fullWidth isLoading={isSubmitting}>
        Tạo tài khoản
      </Button>

      <div style={{ marginTop: '20px', textAlign: 'center', fontSize: '0.9rem', color: 'var(--og-color-text-secondary)' }}>
        Đã có tài khoản?{' '}
        <button
          type="button"
          className="og-button og-button--ghost"
          style={{ padding: '0', color: 'var(--og-color-primary)', fontWeight: 700 }}
          onClick={onNavigateToLogin}
        >
          Đăng nhập
        </button>
      </div>
    </form>
  );
};
