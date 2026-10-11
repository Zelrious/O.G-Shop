import React, { useEffect } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { Alert } from '../shared/components';
import { LoginForm, LoginVisualPanel, useAuth } from '../features/auth';

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const notice = (location.state as { notice?: string } | null)?.notice;
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
    <div className="og-register-split-container og-login-split-container">
      {/* LEFT VISUAL STORYTELLING PANEL */}
      <LoginVisualPanel />

      {/* RIGHT FUNCTIONAL FORM PANEL */}
      <div className="og-register-form-panel og-login-form-panel">
        <div className="og-form-panel-header">
          <div className="og-form-panel-top-row">
            <div className="og-form-panel-title-group">
              <h1 className="og-form-panel-title">Đăng nhập O.G Shop</h1>
              <p className="og-form-panel-sub">
                Chào mừng bạn quay lại với sàn đồ cũ đáng tin cậy.
              </p>
            </div>

            {/* COMPACT LOGO AT TOP RIGHT */}
            <Link to="/" className="og-form-compact-logo" title="Old but Gold (O.G Shop) — Về trang chủ">
              <span className="og-compact-logo-coin">🪙</span>
              <span className="og-compact-logo-text">O.G</span>
              <span className="og-compact-logo-dot" />
            </Link>
          </div>
        </div>

        {notice && <Alert type="success">{notice}</Alert>}
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
          onNavigateToRegister={googleRegistration => navigate('/register', { state: { googleRegistration } })}
          onNavigateToForgot={() => navigate('/forgot-password')}
        />
      </div>
    </div>
  );
};
