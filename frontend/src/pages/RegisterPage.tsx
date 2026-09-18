import React from 'react';
import { useNavigate } from 'react-router-dom';
import { RegisterForm } from '../features/auth';
import { Card } from '../shared/components';

export const RegisterPage: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div style={{ maxWidth: '480px', margin: '40px auto' }}>
      <Card
        title="Tạo tài khoản O.G Shop"
        subtitle="Tham gia cộng đồng mua bán đồ cũ an toàn và minh bạch."
      >
        <RegisterForm
          onSuccess={() => navigate('/profile')}
          onNavigateToLogin={() => navigate('/login')}
        />
      </Card>
    </div>
  );
};
