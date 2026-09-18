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

          {isAuthenticated && user ? (
            <>
              {user.roles.includes('SELLER') ? (
                <li>
                  <Badge variant="seller">Seller Shop</Badge>
                </li>
              ) : (
                <li>
                  <Link to="/seller-verification" className="og-nav-link" style={{ color: 'var(--og-color-accent-gold-dark)' }}>
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
