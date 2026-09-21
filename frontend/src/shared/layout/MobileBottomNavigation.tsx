import React from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../../features/auth';

export const MobileBottomNavigation: React.FC = () => {
  const { isAuthenticated, user } = useAuth();
  const isSeller = isAuthenticated && user?.roles.includes('SELLER');

  return (
    <nav className="og-mobile-bottom-nav" aria-label="Điều hướng chính di động">
      <NavLink
        to="/"
        className={({ isActive }) =>
          `og-mobile-tab ${isActive ? 'og-mobile-tab--active' : ''}`
        }
        end
      >
        <span className="og-mobile-tab__icon" aria-hidden="true">🏠</span>
        <span>Trang chủ</span>
      </NavLink>

      <NavLink
        to="/marketplace"
        className={({ isActive }) =>
          `og-mobile-tab ${isActive ? 'og-mobile-tab--active' : ''}`
        }
      >
        <span className="og-mobile-tab__icon" aria-hidden="true">🔍</span>
        <span>Mua sắm</span>
      </NavLink>

      <NavLink
        to={isSeller ? '/seller/products/new' : isAuthenticated ? '/seller-verification' : '/login'}
        className={({ isActive }) =>
          `og-mobile-tab ${isActive ? 'og-mobile-tab--active' : ''}`
        }
        style={{ color: 'var(--og-color-gold-text)' }}
      >
        <span className="og-mobile-tab__icon" aria-hidden="true">➕</span>
        <span>Đăng bán</span>
      </NavLink>

      <NavLink
        to="/messages"
        className={({ isActive }) =>
          `og-mobile-tab ${isActive ? 'og-mobile-tab--active' : ''}`
        }
      >
        <span className="og-mobile-tab__icon" aria-hidden="true">💬</span>
        <span>Tin nhắn</span>
      </NavLink>

      <NavLink
        to={isAuthenticated ? '/profile' : '/login'}
        className={({ isActive }) =>
          `og-mobile-tab ${isActive ? 'og-mobile-tab--active' : ''}`
        }
      >
        <span className="og-mobile-tab__icon" aria-hidden="true">👤</span>
        <span>{isAuthenticated ? 'Tài khoản' : 'Đăng nhập'}</span>
      </NavLink>
    </nav>
  );
};
