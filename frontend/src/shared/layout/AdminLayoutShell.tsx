import React from 'react';
import { NavLink, Link, Outlet } from 'react-router-dom';
import { useAuth } from '../../features/auth';

export const AdminLayoutShell: React.FC = () => {
  const { user } = useAuth();

  return (
    <div className="og-admin-shell">
      {/* Sidebar */}
      <aside className="og-admin-sidebar" aria-label="Bảng điều khiển quản trị O.G">
        <Link to="/admin" className="og-admin-sidebar__brand">
          <span>🛡️</span>
          <span>O.G Trọng Tài & QTV</span>
        </Link>

        <div style={{ padding: '0 12px', fontSize: '0.8rem', color: '#688977' }}>
          Đăng nhập: {user?.fullName || 'Quản trị viên'}
        </div>

        <nav className="og-admin-sidebar__nav" style={{ marginTop: '12px' }}>
          <NavLink
            to="/admin"
            end
            className={({ isActive }) =>
              `og-admin-sidebar__item ${isActive ? 'og-admin-sidebar__item--active' : ''}`
            }
          >
            <span>📈</span>
            <span>Dashboard KPI (AD-01)</span>
          </NavLink>

          <NavLink
            to="/admin/users"
            className={({ isActive }) =>
              `og-admin-sidebar__item ${isActive ? 'og-admin-sidebar__item--active' : ''}`
            }
          >
            <span>👥</span>
            <span>Quản lý User (AD-02)</span>
          </NavLink>

          <NavLink
            to="/admin/kyc"
            className={({ isActive }) =>
              `og-admin-sidebar__item ${isActive ? 'og-admin-sidebar__item--active' : ''}`
            }
          >
            <span>🛡️</span>
            <span>Hàng chờ KYC (AD-05)</span>
          </NavLink>

          <NavLink
            to="/admin/moderation"
            className={({ isActive }) =>
              `og-admin-sidebar__item ${isActive ? 'og-admin-sidebar__item--active' : ''}`
            }
          >
            <span>🔍</span>
            <span>Kiểm duyệt tin (AD-11)</span>
          </NavLink>

          <NavLink
            to="/admin/transactions"
            className={({ isActive }) =>
              `og-admin-sidebar__item ${isActive ? 'og-admin-sidebar__item--active' : ''}`
            }
          >
            <span>💳</span>
            <span>Ký quỹ Escrow (AD-13)</span>
          </NavLink>

          <NavLink
            to="/admin/complaints"
            className={({ isActive }) =>
              `og-admin-sidebar__item ${isActive ? 'og-admin-sidebar__item--active' : ''}`
            }
          >
            <span>⚖️</span>
            <span>Phân xử tranh chấp (AD-15)</span>
          </NavLink>

          <NavLink
            to="/admin/announcements"
            className={({ isActive }) =>
              `og-admin-sidebar__item ${isActive ? 'og-admin-sidebar__item--active' : ''}`
            }
          >
            <span>📢</span>
            <span>Thông báo khẩn (AD-09)</span>
          </NavLink>
        </nav>

        <div style={{ marginTop: 'auto', paddingTop: '16px', borderTop: '1px solid #233c30' }}>
          <Link
            to="/marketplace"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              color: '#89a897',
              fontSize: '0.85rem',
              textDecoration: 'none',
              padding: '8px 12px',
            }}
          >
            <span>←</span>
            <span>Về sàn mua sắm</span>
          </Link>
        </div>
      </aside>

      {/* Main Admin Content */}
      <main className="og-admin-content">
        <Outlet />
      </main>
    </div>
  );
};
