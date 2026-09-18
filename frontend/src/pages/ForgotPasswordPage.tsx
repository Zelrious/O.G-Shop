import React from 'react';
import { useNavigate } from 'react-router-dom';
import { ForgotPasswordForm } from '../features/auth';
import { Card } from '../shared/components';

export const ForgotPasswordPage: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div style={{ maxWidth: '440px', margin: '40px auto' }}>
      <Card
        title="Khôi phục mật khẩu"
        subtitle="Nhập email để nhận mã xác minh OTP đặt lại mật khẩu."
      >
        <ForgotPasswordForm
          onSuccess={() => navigate('/login')}
          onNavigateToLogin={() => navigate('/login')}
        />
      </Card>
    </div>
  );
};
