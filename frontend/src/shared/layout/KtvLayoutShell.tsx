import React, { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../features/auth';
import { ConsoleIcons } from './ConsoleIcons';
import { ConfirmDialog } from '../components';
import { useManagementStore } from '../../features/management/useManagementStore';

export const KtvLayoutShell: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
  const { complaints, kycList, disputes } = useManagementStore();
  const [isHovered, setIsHovered] = useState(false);
  const [isPinned, setIsPinned] = useState(false);

  const isExpanded = isPinned || isHovered;

  const pendingComplaintsCount = complaints.filter((c) => c.status === 'PENDING').length;
  const pendingKycCount = kycList.filter((k) => k.status === 'PENDING').length;
  const openDisputesCount = disputes.filter((d) => d.status === 'OPEN').length;

  return (
    <div className="og-admin-shell">
      {/* KTV Sidebar */}
      <aside
        className={`og-admin-sidebar og-admin-sidebar--collapsible ${
          isExpanded ? 'og-admin-sidebar--expanded' : 'og-admin-sidebar--collapsed'
        }`}
        aria-label="Bảng điều khiển kiểm duyệt & vận hành KTV"
        onMouseEnter={() => setIsHovered(true)}
        onMouseLeave={() => setIsHovered(false)}
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
              color: '#0f172a',
              letterSpacing: '-0.02em',
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              transition: 'opacity 0.2s ease',
            }}
          >
            Kỹ thuật viên
          </span>
          {isExpanded && (
            <button
              type="button"
              onClick={() => setIsPinned(!isPinned)}
              title={isPinned ? 'Bỏ ghim (tự động thu gọn khi rê chuột ra)' : 'Ghim mở rộng cố định'}
              style={{
                background: isPinned ? '#ecfdf5' : 'transparent',
                border: isPinned ? '1px solid #a7f3d0' : '1px solid transparent',
                borderRadius: '6px',
                padding: '4px 6px',
                cursor: 'pointer',
                color: isPinned ? '#047857' : '#94a3b8',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                transition: 'all 0.15s ease',
              }}
              onMouseEnter={(e) => {
                if (!isPinned) e.currentTarget.style.color = '#0f172a';
              }}
              onMouseLeave={(e) => {
                if (!isPinned) e.currentTarget.style.color = '#94a3b8';
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
            background: isExpanded ? '#f8fafc' : 'transparent',
            borderRadius: '8px',
            border: isExpanded ? '1px solid #e2e8f0' : '1px solid transparent',
            justifyContent: isExpanded ? 'flex-start' : 'center',
            transition: 'all 0.2s ease',
          }}
          title={user?.fullName || user?.email || 'Kỹ thuật viên'}
        >
          <div
            style={{
              width: '36px',
              height: '36px',
              borderRadius: '50%',
              background: '#047857',
              color: '#ffffff',
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
              (user?.fullName || user?.email || 'K').charAt(0).toUpperCase()
            )}
          </div>
          <div className="og-admin-sidebar__user-text" style={{ minWidth: 0, flex: 1 }}>
            <div
              style={{
                fontWeight: 600,
                fontSize: '0.88rem',
                color: '#0f172a',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
              title={user?.fullName || user?.email || 'Kỹ thuật viên'}
            >
              {user?.fullName || 'Kỹ thuật viên'}
            </div>
            <div
              style={{
                fontSize: '0.74rem',
                color: '#64748b',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {user?.email || 'ktv@ogshop.vn'}
            </div>
          </div>
        </div>

        {/* Navigation Menu */}
        <nav className="og-admin-sidebar__nav" style={{ marginTop: '8px' }}>
          {/* 1. Kiểm duyệt tin */}
          <NavLink
            to="/ktv"
            end
            title="Kiểm duyệt tin"
            className={({ isActive }) =>
              `og-admin-sidebar__item ${isActive ? 'og-admin-sidebar__item--active' : ''}`
            }
          >
            <ConsoleIcons.FileCheck size={19} />
            <span className="og-admin-sidebar__label" style={{ flex: 1 }}>Kiểm duyệt tin</span>
          </NavLink>

          {/* 2. Quản lý dòng tiền escrow */}
          <NavLink
            to="/ktv/escrow"
            title="Dòng tiền Escrow"
            className={({ isActive }) =>
              `og-admin-sidebar__item ${isActive ? 'og-admin-sidebar__item--active' : ''}`
            }
          >
            <ConsoleIcons.CreditCard size={19} />
            <span className="og-admin-sidebar__label" style={{ flex: 1 }}>Dòng tiền Escrow</span>
          </NavLink>

          {/* 3. Quản lý tài khoản user bình thường */}
          <NavLink
            to="/ktv/users"
            title="Quản lý người dùng"
            className={({ isActive }) =>
              `og-admin-sidebar__item ${isActive ? 'og-admin-sidebar__item--active' : ''}`
            }
          >
            <ConsoleIcons.Users size={19} />
            <span className="og-admin-sidebar__label" style={{ flex: 1 }}>Quản lý người dùng</span>
          </NavLink>

          {/* 4. Phân xử tranh chấp */}
          <NavLink
            to="/ktv/disputes"
            title="Phân xử tranh chấp"
            className={({ isActive }) =>
              `og-admin-sidebar__item ${isActive ? 'og-admin-sidebar__item--active' : ''}`
            }
          >
            <ConsoleIcons.Scale size={19} />
            <span className="og-admin-sidebar__label" style={{ flex: 1 }}>Phân xử tranh chấp</span>
            {openDisputesCount > 0 && (
              <span
                className="og-admin-sidebar__badge"
                style={{
                  background: '#fee2e2',
                  color: '#dc2626',
                  border: '1px solid #fca5a5',
                  fontSize: '0.72rem',
                  padding: '1px 6px',
                  borderRadius: '10px',
                  fontWeight: 700,
                }}
              >
                {openDisputesCount}
              </span>
            )}
          </NavLink>

          {/* 5. Kiểm duyệt eKYC */}
          <NavLink
            to="/ktv/kyc"
            title="Kiểm duyệt eKYC"
            className={({ isActive }) =>
              `og-admin-sidebar__item ${isActive ? 'og-admin-sidebar__item--active' : ''}`
            }
          >
            <ConsoleIcons.IdCard size={19} />
            <span className="og-admin-sidebar__label" style={{ flex: 1 }}>Kiểm duyệt eKYC</span>
            {pendingKycCount > 0 && (
              <span
                className="og-admin-sidebar__badge"
                style={{
                  background: '#fef3c7',
                  color: '#d97706',
                  border: '1px solid #fcd34d',
                  fontSize: '0.72rem',
                  padding: '1px 6px',
                  borderRadius: '10px',
                  fontWeight: 700,
                }}
              >
                {pendingKycCount}
              </span>
            )}
          </NavLink>

          {/* 6. Nhận thông báo khiếu nại, xử lý, đẩy lên admin */}
          <NavLink
            to="/ktv/complaints"
            title="Xử lý khiếu nại"
            className={({ isActive }) =>
              `og-admin-sidebar__item ${isActive ? 'og-admin-sidebar__item--active' : ''}`
            }
          >
            <ConsoleIcons.MessageAlert size={19} />
            <span className="og-admin-sidebar__label" style={{ flex: 1 }}>Xử lý khiếu nại</span>
            {pendingComplaintsCount > 0 && (
              <span
                className="og-admin-sidebar__badge"
                style={{
                  background: '#fee2e2',
                  color: '#dc2626',
                  border: '1px solid #fca5a5',
                  fontSize: '0.72rem',
                  padding: '1px 6px',
                  borderRadius: '10px',
                  fontWeight: 700,
                }}
              >
                {pendingComplaintsCount}
              </span>
            )}
          </NavLink>

          {/* 7. Tặng/thu hồi voucher cho user */}
          <NavLink
            to="/ktv/vouchers"
            title="Tặng & Thu hồi Voucher"
            className={({ isActive }) =>
              `og-admin-sidebar__item ${isActive ? 'og-admin-sidebar__item--active' : ''}`
            }
          >
            <ConsoleIcons.Gift size={19} />
            <span className="og-admin-sidebar__label" style={{ flex: 1 }}>Tặng & Thu hồi Voucher</span>
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
              color: '#dc2626',
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
              e.currentTarget.style.background = '#fef2f2';
              e.currentTarget.style.borderColor = '#fecaca';
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

      {/* Main KTV Content */}
      <main className="og-admin-content">
        <Outlet />
      </main>

      {/* Logout Confirmation */}
      <ConfirmDialog
        isOpen={showLogoutConfirm}
        title="Xác nhận đăng xuất KTV"
        message="Bạn có chắc chắn muốn đăng xuất khỏi phiên làm việc Kỹ Thuật Viên?"
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
