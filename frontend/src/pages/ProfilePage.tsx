import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth';
import { Card, Badge, Button } from '../shared/components';

export const ProfilePage: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  if (!user) return null;

  const isSeller = user.roles.includes('SELLER');

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px', maxWidth: '800px', margin: '20px auto' }}>
      {/* Thông tin tài khoản */}
      <Card title="Hồ sơ người dùng">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
              <h3 style={{ margin: 0, fontSize: '1.3rem' }}>{user.fullName}</h3>
              {user.roles.map((role) => (
                <Badge key={role} variant={role === 'SELLER' ? 'seller' : 'buyer'}>
                  {role}
                </Badge>
              ))}
            </div>
            <p style={{ margin: '0 0 4px', color: 'var(--og-color-text-secondary)', fontSize: '0.92rem' }}>
              ✉️ {user.email}
            </p>
            {user.phoneNumber && (
              <p style={{ margin: 0, color: 'var(--og-color-text-secondary)', fontSize: '0.92rem' }}>
                📞 {user.phoneNumber}
              </p>
            )}
          </div>

          <Button variant="outline" size="sm" onClick={handleLogout}>
            Đăng xuất
          </Button>
        </div>
      </Card>

      {/* Quản lý quyền Người bán & eKYC */}
      {isSeller ? (
        <Card title="Quyền Người bán">
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <Badge variant="seller">Đã kích hoạt Người bán</Badge>
            <span style={{ color: 'var(--og-color-text-secondary)' }}>
              Quyền SELLER đã được cấp từ backend. Bạn có thể tham gia đăng tin bán hàng.
            </span>
          </div>
        </Card>
      ) : (
        <Card title="Trở thành Người bán trên O.G Shop">
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <p style={{ margin: 0, color: 'var(--og-color-text-secondary)' }}>
              Để đăng bán sản phẩm và mở gian hàng trên nền tảng O.G Shop, bạn cần kích hoạt quyền Người bán (chế độ MVP hỗ trợ kích hoạt trực tiếp).
            </p>
            <div style={{ display: 'flex', gap: '12px', marginTop: '8px' }}>
              <Button variant="gold" onClick={() => navigate('/seller-verification')}>
                Kích hoạt quyền Người bán
              </Button>
            </div>
          </div>
        </Card>
      )}
    </div>
  );
};
