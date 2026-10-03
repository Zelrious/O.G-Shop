import React from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { AppHeader } from './AppHeader';
import { MobileBottomNavigation } from './MobileBottomNavigation';

// Clean line-art outline SVG icons (hollow fill, currentColor stroke)
const SellerNavIcons = {
  Dashboard: () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="3" width="7" height="9" rx="1" />
      <rect x="14" y="3" width="7" height="5" rx="1" />
      <rect x="14" y="12" width="7" height="9" rx="1" />
      <rect x="3" y="16" width="7" height="5" rx="1" />
    </svg>
  ),
  Create: () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="9" />
      <line x1="12" y1="8" x2="12" y2="16" />
      <line x1="8" y1="12" x2="16" y2="12" />
    </svg>
  ),
  Listings: () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z" />
      <line x1="7" y1="7" x2="7.01" y2="7" />
    </svg>
  ),
  Orders: () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M16.5 9.4 7.55 4.24" />
      <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
      <polyline points="3.29 7 12 12 20.71 7" />
      <line x1="12" y1="22" x2="12" y2="12" />
    </svg>
  ),
  Kyc: () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      <polyline points="9 12 11 14 15 10" />
    </svg>
  ),
  Store: () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 9l1-5h16l1 5" />
      <path d="M3 9a3 3 0 0 0 6 0 3 3 0 0 0 6 0 3 3 0 0 0 6 0" />
      <path d="M4 14v6a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-6" />
      <path d="M9 21v-4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v4" />
    </svg>
  ),
};

export const SellerLayoutShell: React.FC = () => {
  return (
    <div className="og-app-shell-root">
      {/* Standard Unified Header across the web app */}
      <AppHeader />

      {/* Main Seller Body with Sticky Left Sidebar */}
      <div className="og-seller-shell">
        <aside className="og-seller-sidebar" aria-label="Menu quản lý người bán">
          <div className="og-seller-sidebar__header">
            <div className="og-seller-sidebar__title">
              <span className="og-seller-sidebar__title-icon" aria-hidden="true">
                <SellerNavIcons.Store />
              </span>
              <span>Kênh Người Bán</span>
            </div>
            <div className="og-seller-sidebar__subtitle">Seller Center</div>
          </div>

          <nav className="og-seller-sidebar__nav">
            <NavLink
              to="/seller"
              end
              className={({ isActive }) =>
                `og-seller-sidebar__item ${isActive ? 'og-seller-sidebar__item--active' : ''}`
              }
            >
              <span className="og-seller-sidebar__icon" aria-hidden="true">
                <SellerNavIcons.Dashboard />
              </span>
              <span>Tổng quan</span>
            </NavLink>

            <NavLink
              to="/seller/products/new"
              className={({ isActive }) =>
                `og-seller-sidebar__item ${isActive ? 'og-seller-sidebar__item--active' : ''}`
              }
            >
              <span className="og-seller-sidebar__icon" aria-hidden="true">
                <SellerNavIcons.Create />
              </span>
              <span>Đăng tin bán</span>
            </NavLink>

            <NavLink
              to="/seller/products"
              className={({ isActive }) =>
                `og-seller-sidebar__item ${isActive ? 'og-seller-sidebar__item--active' : ''}`
              }
            >
              <span className="og-seller-sidebar__icon" aria-hidden="true">
                <SellerNavIcons.Listings />
              </span>
              <span>Quản lý tin đăng</span>
            </NavLink>

            <NavLink
              to="/seller/orders"
              className={({ isActive }) =>
                `og-seller-sidebar__item ${isActive ? 'og-seller-sidebar__item--active' : ''}`
              }
            >
              <span className="og-seller-sidebar__icon" aria-hidden="true">
                <SellerNavIcons.Orders />
              </span>
              <span>Đơn hàng cần gửi</span>
            </NavLink>

            <NavLink
              to="/seller-verification"
              className={({ isActive }) =>
                `og-seller-sidebar__item ${isActive ? 'og-seller-sidebar__item--active' : ''}`
              }
            >
              <span className="og-seller-sidebar__icon" aria-hidden="true">
                <SellerNavIcons.Kyc />
              </span>
              <span>Định danh KYC</span>
            </NavLink>
          </nav>
        </aside>

        <main className="og-seller-content">
          <Outlet />
        </main>
      </div>

      <MobileBottomNavigation />
    </div>
  );
};
