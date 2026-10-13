import { useConsoleHover } from './useConsoleHover';
import { NotificationBell } from '../../features/notifications/NotificationBell';
import { ThemeSwitch } from './ThemeSwitch';
import React, { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../features/auth';
import { ConsoleIcons } from './ConsoleIcons';
import { ConfirmDialog } from '../components';
import { useManagementStore } from '../../features/management/useManagementStore';

export const AdminLayoutShell: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
  const { unreadAlertsCount } = useManagementStore();
  const { isHovered, onMouseEnter, onMouseLeave } = useConsoleHover();
  const [isFocused, setIsFocused] = useState(false);
  const [isPinned, setIsPinned] = useState(false);

  const isExpanded = isPinned || isHovered || isFocused;

  return (
    <div className="og-admin-shell">
      {/* Admin Sidebar */}
      <aside
        className={`og-admin-sidebar og-admin-sidebar--collapsible ${
          isExpanded ? 'og-admin-sidebar--expanded' : 'og-admin-sidebar--collapsed'
        }`}
        aria-label="Bảng điều hành quản trị hệ thống O.G Admin"
        onMouseEnter={onMouseEnter}
        onMouseLeave={onMouseLeave}
        onFocus={() => setIsFocused(true)}
        onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget as Node)) setIsFocused(false); }}
      >
        {/* Brand Header */}
        <div
          className="og-admin-sidebar__brand"
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            minHeight: '36px',
            position: 'relative',
          }}
        >
          <span
            className="og-admin-sidebar__brand-text"
            style={{
              fontWeight: 800,
              fontSize: '1.2rem',
              color: 'var(--og-color-text-primary)',
              letterSpacing: '-0.02em',
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              transition: 'opacity 0.2s ease',
            }}
          >
            Quản trị viên
          </span>
          {isExpanded && (
            <button
              type="button"
              onClick={() => setIsPinned(!isPinned)}
              title={isPinned ? 'Bỏ ghim (tự động thu gọn khi rê chuột ra)' : 'Ghim mở rộng cố định'}
              style={{
                background: isPinned ? 'var(--og-color-primary-light)' : 'transparent',
                border: isPinned ? '1px solid #a7f3d0' : '1px solid transparent',
                borderRadius: '6px',
                padding: '4px 6px',
                cursor: 'pointer',
                color: isPinned ? 'var(--og-color-primary)' : 'var(--og-color-text-muted)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                transition: 'all 0.15s ease',
              }}
              onMouseEnter={(e) => {
                if (!isPinned) e.currentTarget.style.color = 'var(--og-color-text-primary)';
              }}
              onMouseLeave={(e) => {
                if (!isPinned) e.currentTarget.style.color = 'var(--og-color-text-muted)';
              }}
              aria-label={isPinned ? 'Bỏ ghim thanh điều hướng' : 'Ghim thanh điều hướng'}
            >
              <ConsoleIcons.Pin size={15} />
            </button>
          )}
        </div>

        {/* User Account: Avatar + Username */}
        <div
          className="og-admin-sidebar__user-box"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: isExpanded ? '10px' : '0',
            padding: isExpanded ? '8px 10px' : '6px',
            background: isExpanded ? 'var(--og-color-bg)' : 'transparent',
            borderRadius: '8px',
            border: isExpanded ? '1px solid #e2e8f0' : '1px solid transparent',
            justifyContent: isExpanded ? 'flex-start' : 'center',
            transition: 'all 0.2s ease',
          }}
          title={user?.fullName || user?.email || 'Quản trị viên'}
        >
          <div
            style={{
              width: '36px',
              height: '36px',
              borderRadius: '50%',
              background: 'var(--og-color-primary-fill)',
              color: 'var(--og-color-text-inverse)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 700,
              fontSize: '0.9rem',
              flexShrink: 0,
              overflow: 'hidden',
            }}
          >
            {user?.avatarUrl ? (
              <img src={user.avatarUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
            ) : (
              (user?.fullName || user?.email || 'A').charAt(0).toUpperCase()
            )}
          </div>
          <div className="og-admin-sidebar__user-text" style={{ minWidth: 0, flex: 1 }}>
            <div
              style={{
                fontWeight: 600,
                fontSize: '0.88rem',
                color: 'var(--og-color-text-primary)',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
              title={user?.fullName || user?.email || 'Quản trị viên'}
            >
              {user?.fullName || 'Quản trị viên'}
            </div>
            <div
              style={{
                fontSize: '0.74rem',
                color: 'var(--og-color-text-secondary)',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {user?.email || 'admin@ogshop.vn'}
            </div>
          </div>
        </div>

        {/* Navigation Menu */}
        <nav className="og-admin-sidebar__nav" style={{ marginTop: '8px' }}>
          {/* 1. Dashboard doanh thu & các biểu đồ báo cáo */}
          <NavLink
            to="/admin"
            end
            title="Tổng quan doanh thu"
            className={({ isActive }) =>
              `og-admin-sidebar__item ${isActive ? 'og-admin-sidebar__item--active' : ''}`
            }
          >
            <ConsoleIcons.BarChart size={19} />
            <span className="og-admin-sidebar__label" style={{ flex: 1 }}>Tổng quan doanh thu</span>
          </NavLink>

          {/* 2. Quản lý tài khoản KTV */}
          <NavLink
            to="/admin/ktv-accounts"
            title="Quản lý tài khoản KTV"
            className={({ isActive }) =>
              `og-admin-sidebar__item ${isActive ? 'og-admin-sidebar__item--active' : ''}`
            }
          >
            <ConsoleIcons.UserCog size={19} />
            <span className="og-admin-sidebar__label" style={{ flex: 1 }}>Quản lý tài khoản KTV</span>
          </NavLink>

          {/* 3. Nhận thông báo (sửa lại từ "Thông báo khẩn từ KTV") */}
          <NavLink
            to="/admin/emergency-alerts"
            title="Thông báo"
            className={({ isActive }) =>
              `og-admin-sidebar__item ${isActive ? 'og-admin-sidebar__item--active' : ''}`
            }
          >
            <ConsoleIcons.BellRing size={19} />
            <span className="og-admin-sidebar__label" style={{ flex: 1 }}>Thông báo</span>
            {unreadAlertsCount > 0 && (
              <span
                className="og-admin-sidebar__badge"
                style={{
                  background: 'var(--og-color-danger-bg)',
                  color: 'var(--og-color-danger)',
                  border: '1px solid #fca5a5',
                  fontSize: '0.72rem',
                  padding: '1px 7px',
                  borderRadius: '10px',
                  fontWeight: 700,
                }}
              >
                {unreadAlertsCount}
              </span>
            )}
          </NavLink>

          {/* 4. Xem lịch sử thao tác hệ thống (Audit log) */}
          <NavLink
            to="/admin/audit-logs"
            title="Lịch sử thao tác hệ thống"
            className={({ isActive }) =>
              `og-admin-sidebar__item ${isActive ? 'og-admin-sidebar__item--active' : ''}`
            }
          >
            <ConsoleIcons.FileText size={19} />
            <span className="og-admin-sidebar__label" style={{ flex: 1 }}>Lịch sử thao tác hệ thống</span>
          </NavLink>

          {/* 5. Quản lý phí hệ thống */}
          <NavLink
            to="/admin/system-fees"
            title="Quản lý phí hệ thống"
            className={({ isActive }) =>
              `og-admin-sidebar__item ${isActive ? 'og-admin-sidebar__item--active' : ''}`
            }
          >
            <ConsoleIcons.Sliders size={19} />
            <span className="og-admin-sidebar__label" style={{ flex: 1 }}>Quản lý phí hệ thống</span>
          </NavLink>

          {/* 6. Tạo và quản lý voucher */}
          <NavLink
            to="/admin/vouchers"
            title="Tạo & Quản lý Voucher"
            className={({ isActive }) =>
              `og-admin-sidebar__item ${isActive ? 'og-admin-sidebar__item--active' : ''}`
            }
          >
            <ConsoleIcons.Ticket size={19} />
            <span className="og-admin-sidebar__label" style={{ flex: 1 }}>Tạo & Quản lý Voucher</span>
          </NavLink>

          {/* 7. Nhận thông báo và điều hướng thông báo */}
          <NavLink
            to="/admin/broadcasts"
            title="Điều hướng thông báo"
            className={({ isActive }) =>
              `og-admin-sidebar__item ${isActive ? 'og-admin-sidebar__item--active' : ''}`
            }
          >
            <ConsoleIcons.Megaphone size={19} />
            <span className="og-admin-sidebar__label" style={{ flex: 1 }}>Điều hướng thông báo</span>
          </NavLink>
        </nav>

        {/* Sidebar Footer with Logout (NO 'Về sàn mua sắm') */}
        <div style={{ marginTop: 'auto', paddingTop: '16px', borderTop: '1px solid #e2e8f0' }}>
          <button
            type="button"
            className="og-admin-sidebar__logout-btn"
            onClick={() => setShowLogoutConfirm(true)}
            title="Đăng xuất hệ thống"
            style={{
              width: '100%',
              display: 'flex',
              alignItems: 'center',
              gap: isExpanded ? '10px' : '0',
              justifyContent: isExpanded ? 'flex-start' : 'center',
              color: 'var(--og-color-danger)',
              background: 'transparent',
              border: '1px solid transparent',
              padding: isExpanded ? '9px 12px' : '9px 0',
              borderRadius: '6px',
              cursor: 'pointer',
              fontSize: '0.88rem',
              fontWeight: 600,
              textAlign: 'left',
              transition: 'all 0.15s ease',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.background = 'var(--og-color-danger-bg)';
              e.currentTarget.style.borderColor = 'var(--og-color-danger-border)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = 'transparent';
              e.currentTarget.style.borderColor = 'transparent';
            }}
          >
            <ConsoleIcons.Logout size={19} />
            <span className="og-admin-sidebar__logout-text">Đăng xuất hệ thống</span>
          </button>
        </div>
      </aside>

      {/* Main Admin Content */}
      <main className="og-admin-content">
        <div className="og-console-utilities"><ThemeSwitch /><NotificationBell /></div>
        <div className="og-console-page"><Outlet /></div>
      </main>

      {/* Logout Confirmation */}
      <ConfirmDialog
        isOpen={showLogoutConfirm}
        title="Xác nhận đăng xuất Quản Trị Viên"
        message="Bạn có chắc chắn muốn đăng xuất khỏi bảng điều khiển Quản Trị Viên?"
        confirmText="Đăng xuất"
        cancelText="Ở lại"
        variant="danger"
        onConfirm={async () => {
          setShowLogoutConfirm(false);
          await logout();
          navigate('/login');
        }}
        onCancel={() => setShowLogoutConfirm(false)}
      />
    </div>
  );
};
