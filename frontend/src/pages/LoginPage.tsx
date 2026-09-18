import React from 'react';
import { useNavigate } from 'react-router-dom';
import { LoginForm } from '../features/auth';
import { Card } from '../shared/components';

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div style={{ maxWidth: '440px', margin: '40px auto' }}>
      <Card
        title="Đăng nhập O.G Shop"
        subtitle="Chào mừng bạn quay lại với sàn đồ cũ đáng tin cậy."
      >
        <LoginForm
          onSuccess={() => navigate('/profile')}
          onNavigateToRegister={() => navigate('/register')}
          onNavigateToForgot={() => navigate('/forgot-password')}
        />
      </Card>
    </div>
  );
};
