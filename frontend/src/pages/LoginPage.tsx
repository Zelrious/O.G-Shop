import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { LoginForm, useAuth } from '../features/auth';
import { Card } from '../shared/components';

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const { user, isAuthenticated, isLoading } = useAuth();

  // If already authenticated, redirect according to role
  useEffect(() => {
    if (!isLoading && isAuthenticated && user) {
      if (user.roles.includes('ADMIN')) {
        navigate('/admin', { replace: true });
      } else if (user.roles.includes('KTV')) {
        navigate('/ktv', { replace: true });
      } else {
        navigate('/profile', { replace: true });
      }
    }
  }, [user, isAuthenticated, isLoading, navigate]);

  return (
    <div style={{ maxWidth: '440px', margin: '40px auto' }}>
      <Card
        title="Đăng nhập O.G Shop"
        subtitle="Chào mừng bạn quay lại với sàn đồ cũ đáng tin cậy."
      >
        <LoginForm
          onSuccess={(loggedInUser) => {
            if (loggedInUser.roles.includes('ADMIN')) {
              navigate('/admin', { replace: true });
            } else if (loggedInUser.roles.includes('KTV')) {
              navigate('/ktv', { replace: true });
            } else {
              navigate('/profile', { replace: true });
            }
          }}
          onNavigateToRegister={() => navigate('/register')}
          onNavigateToForgot={() => navigate('/forgot-password')}
        />
      </Card>
    </div>
  );
};
