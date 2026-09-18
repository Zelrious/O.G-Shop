import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth';
import { Button, Card, Badge } from '../shared/components';

export const HomePage: React.FC = () => {
  const { isAuthenticated, user } = useAuth();
  const navigate = useNavigate();

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '36px', margin: '20px 0' }}>
      {/* Hero Section */}
      <section
        style={{
          textAlign: 'center',
          padding: '48px 24px',
          background: 'linear-gradient(180deg, var(--og-color-surface-subtle) 0%, var(--og-color-bg) 100%)',
          borderRadius: 'var(--og-radius-lg)',
          border: '1px solid var(--og-color-border)',
        }}
      >
        <Badge variant="seller" style={{ marginBottom: '16px' }}>
          Nền tảng mua bán đồ cũ C2C đáng tin cậy
        </Badge>
        <h1
          style={{
            fontFamily: 'var(--og-font-serif)',
            fontSize: 'clamp(2.4rem, 6vw, 4rem)',
            lineHeight: 1.1,
            margin: '0 0 16px',
            color: 'var(--og-color-text-primary)',
          }}
        >
          Old but Gold
        </h1>
        <p
          style={{
            fontSize: 'clamp(1rem, 2.5vw, 1.25rem)',
            color: 'var(--og-color-text-secondary)',
            maxWidth: '680px',
            margin: '0 auto 28px',
          }}
        >
          Mua bán đồ đã qua sử dụng an tâm tuyệt đối với xác minh danh tính người bán sinh trắc học eKYC, thanh toán giữ tiền bảo đảm và kiểm tra hàng trước khi nhận.
        </p>

        <div style={{ display: 'flex', gap: '14px', justifyContent: 'center', flexWrap: 'wrap' }}>
          {isAuthenticated ? (
            <>
              <Button variant="primary" size="lg" onClick={() => navigate('/profile')}>
                Xem hồ sơ của tôi ({user?.fullName})
              </Button>
              {!user?.roles.includes('SELLER') && (
                <Button variant="gold" size="lg" onClick={() => navigate('/seller-verification')}>
                  Đăng ký Bán hàng qua eKYC →
                </Button>
              )}
            </>
          ) : (
            <>
              <Button variant="primary" size="lg" onClick={() => navigate('/register')}>
                Đăng ký tài khoản miễn phí
              </Button>
              <Button variant="outline" size="lg" onClick={() => navigate('/login')}>
                Đăng nhập
              </Button>
            </>
          )}
        </div>
      </section>

      {/* Trọng tâm cốt lõi (Trust Invariants) */}
      <section style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '20px' }}>
        <Card title="🛡️ Xác minh eKYC Sinh trắc học">
          <p style={{ margin: 0, color: 'var(--og-color-text-secondary)', fontSize: '0.92rem' }}>
            Người bán phải xác minh thẻ Căn cước công dân và so khớp khuôn mặt qua AI trước khi được cấp quyền đăng bán sản phẩm.
          </p>
        </Card>

        <Card title="🔒 Thanh toán giữ tiền an toàn">
          <p style={{ margin: 0, color: 'var(--og-color-text-secondary)', fontSize: '0.92rem' }}>
            Tiền thanh toán được hệ thống tạm giữ an toàn. Chỉ giải ngân cho người bán khi người mua đã nhận và kiểm tra hàng hợp lệ.
          </p>
        </Card>

        <Card title="⚖️ Phân xử tranh chấp công bằng">
          <p style={{ margin: 0, color: 'var(--og-color-text-secondary)', fontSize: '0.92rem' }}>
            Quy trình khiếu nại minh bạch, hỗ trợ đóng băng thanh toán tức thì và quản trị viên phân xử dựa trên bằng chứng kiểm tra.
          </p>
        </Card>
      </section>
    </div>
  );
};
