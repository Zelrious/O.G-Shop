import { Icon } from '../shared/components/Icon/Icon';
import React, { useEffect, useCallback } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';

import { Alert } from '../shared/components';
import { LoginForm, LoginVisualPanel, useAuth, UserPrincipal } from '../features/auth';

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const notice = (location.state as { notice?: string } | null)?.notice;
  const from = (location.state as { from?: { pathname?: string; search?: string } } | null)?.from;
  const { user, isAuthenticated, isLoading } = useAuth();

  const resolveRedirectPath = useCallback((targetUser: UserPrincipal) => {
    if (targetUser.roles.includes('ADMIN')) {
      return '/admin';
    }
    if (targetUser.roles.includes('KTV')) {
      return '/ktv';
    }
    if (from?.pathname && from.pathname !== '/login' && from.pathname !== '/profile') {
      return `${from.pathname}${from.search || ''}`;
    }
    return '/marketplace';
  }, [from]);

  // If already authenticated, redirect according to role (regular users go to Mall / Marketplace)
  useEffect(() => {
    if (!isLoading && isAuthenticated && user) {
      navigate(resolveRedirectPath(user), { replace: true });
    }
  }, [user, isAuthenticated, isLoading, navigate, resolveRedirectPath]);


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
              <span className="og-compact-logo-coin"><Icon name="credit" style={{ width: '1em', height: '1em' }} /></span>
              <span className="og-compact-logo-text">O.G</span>
              <span className="og-compact-logo-dot" />
            </Link>
          </div>
        </div>

        {notice && <Alert type="success">{notice}</Alert>}
        <LoginForm
          onSuccess={(loggedInUser) => {
            navigate(resolveRedirectPath(loggedInUser), { replace: true });
          }}
          onNavigateToRegister={googleRegistration => navigate('/register', { state: { googleRegistration } })}
          onNavigateToForgot={() => navigate('/forgot-password')}
        />
      </div>
    </div>
  );
};
