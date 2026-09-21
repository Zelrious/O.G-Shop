import React from 'react';
import { NavLink, Link, Outlet } from 'react-router-dom';
import { useAuth } from '../../features/auth';

export const SellerLayoutShell: React.FC = () => {
  const { user } = useAuth();

  return (
    <div className="og-app-shell-root">
      {/* Top mini header for Seller Center */}
      <header
        style={{
          height: '60px',
          background: 'var(--og-color-surface)',
          borderBottom: '1px solid var(--og-color-border)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 24px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <Link
            to="/"
            style={{
              fontFamily: 'var(--og-font-serif)',
              fontSize: '1.25rem',
              fontWeight: 700,
              color: 'var(--og-color-primary)',
              textDecoration: 'none',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
            }}
          >
            <span>🪙 Old but Gold</span>
          </Link>
          <span style={{ color: 'var(--og-color-border-strong)' }}>|</span>
          <span style={{ fontWeight: 700, color: 'var(--og-color-gold-text)', fontSize: '0.95rem' }}>
            🏪 Kênh Người Bán (Seller Center)
          </span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <Link
            to="/marketplace"
            style={{
              fontSize: '0.88rem',
              color: 'var(--og-color-text-secondary)',
              textDecoration: 'none',
            }}
          >
            ← Quay lại Chợ mua sắm
          </Link>
          <span style={{ fontSize: '0.88rem', fontWeight: 600, color: 'var(--og-color-text-primary)' }}>
            👤 {user?.fullName || 'Người bán'}
          </span>
        </div>
      </header>

      {/* Main Seller Body */}
      <div className="og-seller-shell">
        <aside className="og-seller-sidebar" aria-label="Menu quản lý người bán">
          <nav className="og-seller-sidebar__nav">
            <NavLink
              to="/seller"
              end
              className={({ isActive }) =>
                `og-seller-sidebar__item ${isActive ? 'og-seller-sidebar__item--active' : ''}`
              }
            >
              <span>📊</span>
              <span>Tổng quan (SL-01)</span>
            </NavLink>

            <NavLink
              to="/seller/products/new"
              className={({ isActive }) =>
                `og-seller-sidebar__item ${isActive ? 'og-seller-sidebar__item--active' : ''}`
              }
            >
              <span>➕</span>
              <span>Đăng tin bán (SL-02)</span>
            </NavLink>

            <NavLink
              to="/seller/products"
              className={({ isActive }) =>
                `og-seller-sidebar__item ${isActive ? 'og-seller-sidebar__item--active' : ''}`
              }
            >
              <span>📦</span>
              <span>Quản lý tin đăng (SL-07)</span>
            </NavLink>

            <NavLink
              to="/seller/orders"
              className={({ isActive }) =>
                `og-seller-sidebar__item ${isActive ? 'og-seller-sidebar__item--active' : ''}`
              }
            >
              <span>📋</span>
              <span>Đơn hàng cần gửi (SL-09)</span>
            </NavLink>

            <NavLink
              to="/seller-verification"
              className={({ isActive }) =>
                `og-seller-sidebar__item ${isActive ? 'og-seller-sidebar__item--active' : ''}`
              }
            >
              <span>🛡️</span>
              <span>Định danh KYC (PF-07)</span>
            </NavLink>
          </nav>
        </aside>

        <main className="og-seller-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
};
