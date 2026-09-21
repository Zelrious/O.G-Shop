import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth';
import { Badge, Button } from '../shared/components';

export const Navbar: React.FC = () => {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const isSeller = isAuthenticated && user?.roles.includes('SELLER');

  return (
    <header className="og-navbar">
      <Link to="/" className="og-navbar-brand">
        <span>Old but Gold</span>
        <span className="gold-dot" />
      </Link>

      <nav aria-label="Main Navigation">
        <ul className="og-navbar-nav">
          <li>
            <Link to="/" className="og-nav-link">
              Trang chủ
            </Link>
          </li>
          <li>
            <Link to="/marketplace" className="og-nav-link">
              Mua sắm
            </Link>
          </li>
          <li>
            <Link
              to="/showcase"
              className="og-nav-link"
              style={{
                background: 'var(--og-color-accent-gold-light)',
                color: 'var(--og-color-gold-text)',
                fontWeight: 700,
                padding: '4px 12px',
                borderRadius: 'var(--og-radius-full)',
                border: '1px solid var(--og-color-accent-gold)'
              }}
            >
              🗺️ 44 Màn Hình Prototype
            </Link>
          </li>

          {isAuthenticated && user ? (
            <>
              {isSeller ? (
                <>
                  <li>
                    <Link to="/seller/listings" className="og-nav-link">
                      Tin đăng của tôi
                    </Link>
                  </li>
                  <li>
                    <Badge variant="seller">Seller Shop</Badge>
                  </li>
                </>
              ) : (
                <li>
                  <Link
                    to="/seller-verification"
                    className="og-nav-link"
                    style={{ color: 'var(--og-color-accent-gold-dark)' }}
                  >
                    ✨ Đăng ký Bán hàng
                  </Link>
                </li>
              )}

              <li>
                <Link to="/profile" className="og-nav-link" style={{ fontWeight: 700 }}>
                  👤 {user.fullName}
                </Link>
              </li>

              <li>
                <Button variant="ghost" size="sm" onClick={handleLogout}>
                  Đăng xuất
                </Button>
              </li>
            </>
          ) : (
            <>
              <li>
                <Link to="/login" className="og-nav-link">
                  Đăng nhập
                </Link>
              </li>
              <li>
                <Button variant="primary" size="sm" onClick={() => navigate('/register')}>
                  Đăng ký
                </Button>
              </li>
            </>
          )}
        </ul>
      </nav>
    </header>
  );
};
