import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../features/auth';
import { Button } from '../components/Button/Button';
import { ConfirmDialog } from '../components/Dialog/ConfirmDialog';

export const AppHeader: React.FC = () => {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchQuery, setSearchQuery] = useState('');
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      navigate(`/marketplace?query=${encodeURIComponent(searchQuery.trim())}`);
    } else {
      navigate('/marketplace');
    }
  };

  const isSeller = isAuthenticated && user?.roles.includes('SELLER');
  const isAdmin = isAuthenticated && user?.roles.includes('ADMIN');

  return (
    <>
      <header className="og-header" role="banner">
        <div className="og-header__inner">
          {/* Brand */}
          <Link to="/" className="og-header__brand" aria-label="Old but Gold Trang chủ">
            <span className="og-header__brand-logo" aria-hidden="true">🪙</span>
            <span>Old but Gold</span>
            <span className="og-header__brand-dot" aria-hidden="true" />
          </Link>

          {/* Search Bar */}
          <form className="og-header__search-wrap" onSubmit={handleSearchSubmit} role="search">
            <span className="og-header__search-icon" aria-hidden="true">🔍</span>
            <input
              type="search"
              className="og-header__search-input"
              placeholder="Tìm đồ cũ chất lượng: máy ảnh, đồ sưu tầm, công nghệ..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              aria-label="Tìm kiếm sản phẩm trên O.G Shop"
            />
          </form>

          {/* Actions & Navigation Links */}
          <div className="og-header__actions">
            <Link
              to="/marketplace"
              className={`og-header__nav-link ${location.pathname === '/marketplace' ? 'og-header__nav-link--active' : ''}`}
            >
              <span>Mua sắm</span>
            </Link>

            {isAuthenticated ? (
              <>
                {/* Seller Shortcut */}
                {isSeller ? (
                  <Link to="/seller/products" className="og-header__seller-btn">
                    <span>🏪 Kênh Người Bán</span>
                  </Link>
                ) : (
                  <Link to="/seller-verification" className="og-header__seller-btn">
                    <span>✨ Bán đồ cũ</span>
                  </Link>
                )}

                {/* Admin Shortcut if Admin */}
                {isAdmin && (
                  <Link
                    to="/admin"
                    className="og-header__nav-link"
                    style={{ color: 'var(--og-color-gold-text)', fontWeight: 700 }}
                  >
                    <span>🛡️ Quản trị</span>
                  </Link>
                )}

                {/* Profile Link */}
                <Link
                  to="/profile"
                  className={`og-header__nav-link ${location.pathname.startsWith('/profile') ? 'og-header__nav-link--active' : ''}`}
                  title={user?.fullName}
                >
                  <span aria-hidden="true">👤</span>
                  <span>{user?.fullName?.split(' ')[0] || 'Tài khoản'}</span>
                </Link>

                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setShowLogoutConfirm(true)}
                  aria-label="Đăng xuất"
                >
                  Đăng xuất
                </Button>
              </>
            ) : (
              <>
                <Link to="/login" className="og-header__nav-link">
                  Đăng nhập
                </Link>
                <Button variant="primary" size="sm" onClick={() => navigate('/register')}>
                  Đăng ký
                </Button>
              </>
            )}
          </div>
        </div>
      </header>

      {/* Confirmation Dialog for Logout (SY-02) */}
      <ConfirmDialog
        isOpen={showLogoutConfirm}
        title="Xác nhận đăng xuất"
        message="Bạn có chắc chắn muốn đăng xuất khỏi tài khoản Old but Gold?"
        confirmText="Đăng xuất"
        cancelText="Ở lại"
        variant="primary"
        onConfirm={async () => {
          setShowLogoutConfirm(false);
          await logout();
          navigate('/login');
        }}
        onCancel={() => setShowLogoutConfirm(false)}
      />
    </>
  );
};
