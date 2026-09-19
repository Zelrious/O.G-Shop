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
        <Card title="Xác minh Người bán">
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <Badge variant="verified">Đã xác minh</Badge>
            <span style={{ color: 'var(--og-color-text-secondary)' }}>
              Quyền SELLER được tải từ backend. Hồ sơ không lưu face embedding hoặc ảnh CCCD.
            </span>
          </div>
        </Card>
      ) : (
        <Card title="Trở thành Người bán trên O.G Shop">
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <p style={{ margin: 0, color: 'var(--og-color-text-secondary)' }}>
              Để đăng bán sản phẩm và mở gian hàng trên nền tảng O.G Shop, bạn cần hoàn thành quy trình xác thực sinh trắc học eKYC (quét thẻ CCCD và khuôn mặt).
            </p>
            <div style={{ display: 'flex', gap: '12px', marginTop: '8px' }}>
              <Button variant="gold" onClick={() => navigate('/seller-verification')}>
                Bắt đầu Xác thực eKYC ngay
              </Button>
            </div>
          </div>
        </Card>
      )}
    </div>
  );
};
