import React, { useState } from 'react';
import { profileApi } from '../index';
import { Alert, Button, Input } from '../../../shared/components';
import { acceptsNewPassword, NEW_PASSWORD_HINT } from '../../auth/passwordPolicy';
import { authApi, SESSION_EXPIRED_EVENT } from '../../auth/authApi';
import { useNavigate } from 'react-router-dom';

export const SecurityTab: React.FC = () => {
  const navigate = useNavigate();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentPassword) {
      setErrorMsg('Vui lòng nhập mật khẩu hiện tại.');
      return;
    }
    if (!acceptsNewPassword(newPassword)) {
      setErrorMsg(NEW_PASSWORD_HINT);
      return;
    }
    if (newPassword === currentPassword) {
      setErrorMsg('Mật khẩu mới không được trùng với mật khẩu cũ.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setErrorMsg('Mật khẩu xác nhận không khớp với mật khẩu mới.');
      return;
    }

    setIsSubmitting(true);
    setSuccessMsg(null);
    setErrorMsg(null);

    try {
      await profileApi.changePassword({
        currentPassword,
        newPassword,
      });
      setSuccessMsg('Đổi mật khẩu thành công. Hãy đăng nhập lại bằng mật khẩu mới.');
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      await authApi.logout().catch(() => undefined);
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
      navigate('/login', { replace: true, state: { notice: 'Đổi mật khẩu thành công. Các phiên cũ đã bị thu hồi. Hãy đăng nhập bằng mật khẩu mới.' } });
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Đổi mật khẩu thất bại. Vui lòng kiểm tra lại mật khẩu hiện tại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="og-profile-tab">
      <h3 style={{ margin: '0 0 16px', fontSize: '1.2rem', color: 'var(--og-color-text-primary)' }}>
        Bảo mật tài khoản & Đổi mật khẩu
      </h3>

      {successMsg && <Alert type="success">{successMsg}</Alert>}
      {errorMsg && <Alert type="danger">{errorMsg}</Alert>}

      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px', marginTop: '16px', maxWidth: '480px' }}>
        <div>
          <label style={{ display: 'block', fontSize: '0.88rem', fontWeight: 600, marginBottom: '6px' }}>
            Mật khẩu hiện tại <span style={{ color: 'var(--og-color-danger)' }}>*</span>
          </label>
          <Input
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            placeholder="Nhập mật khẩu đang dùng"
            required
          />
        </div>

        <div>
          <label style={{ display: 'block', fontSize: '0.88rem', fontWeight: 600, marginBottom: '6px' }}>
            Mật khẩu mới <span style={{ color: 'var(--og-color-danger)' }}>*</span>
          </label>
          <Input
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            placeholder="Tối thiểu 8 ký tự"
            required
          />
        </div>

        <div>
          <label style={{ display: 'block', fontSize: '0.88rem', fontWeight: 600, marginBottom: '6px' }}>
            Xác nhận mật khẩu mới <span style={{ color: 'var(--og-color-danger)' }}>*</span>
          </label>
          <Input
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            placeholder="Nhập lại mật khẩu mới"
            required
          />
        </div>

        <div style={{ marginTop: '10px' }}>
          <Button variant="primary" type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Đang cập nhật...' : 'Cập nhật mật khẩu'}
          </Button>
        </div>
      </form>
    </div>
  );
};
