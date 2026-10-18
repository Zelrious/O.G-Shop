import { Icon } from '../components/Icon/Icon';
import React from 'react';
import { Outlet, Link, useLocation } from 'react-router-dom';

export const GuestLayout: React.FC = () => {
  const location = useLocation();
  const isSplitAuth =
    location.pathname.startsWith('/register') ||
    location.pathname.startsWith('/login') ||
    location.pathname.startsWith('/forgot-password');

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(180deg, var(--og-color-bg-subtle) 0%, var(--og-color-bg) 100%)',
        padding: isSplitAuth ? '12px 16px' : '24px 16px',
      }}
    >
      {!isSplitAuth && (
        <div style={{ marginBottom: '24px', textAlign: 'center' }}>
          <Link
            to="/"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '8px',
              textDecoration: 'none',
              fontFamily: 'var(--og-font-serif)',
              fontSize: '1.75rem',
              fontWeight: 700,
              color: 'var(--og-color-primary)',
            }}
          >
            <span><Icon name="credit" style={{ width: '1em', height: '1em' }} /></span>
            <span>Old but Gold</span>
            <span
              style={{
                width: '8px',
                height: '8px',
                borderRadius: '50%',
                background: 'var(--og-color-accent-gold)',
              }}
            />
          </Link>
          <p style={{ margin: '6px 0 0', color: 'var(--og-color-text-secondary)', fontSize: '0.92rem' }}>
            Nền tảng mua bán đồ cũ C2C đáng tin cậy
          </p>
        </div>
      )}

      <div style={{ width: '100%', maxWidth: isSplitAuth ? '1220px' : '440px' }}>
        <div key={location.pathname} className="og-auth-page-container">
          <Outlet />
        </div>
      </div>

      {!isSplitAuth && (
        <footer style={{ marginTop: '32px', textAlign: 'center', fontSize: '0.82rem', color: 'var(--og-color-text-muted)' }}>
          © {new Date().getFullYear()} Old but Gold. Bảo vệ giao dịch & Giữ tiền an toàn.
        </footer>
      )}
    </div>
  );
};
