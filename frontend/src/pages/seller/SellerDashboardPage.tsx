import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Card, Button, StatusBadge } from '../../shared/components';
import { sellerSettingsApi, SellerSettings } from '../../features/seller/sellerSettingsApi';

export const SellerDashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const [settings, setSettings] = useState<SellerSettings | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    let ignore = false;
    sellerSettingsApi
      .getSettings()
      .then((data) => {
        if (!ignore) {
          setSettings(data);
          setLoading(false);
        }
      })
      .catch(() => {
        if (!ignore) {
          setLoading(false);
        }
      });
    return () => {
      ignore = true;
    };
  }, []);

  const isVerified = settings?.verifiedSeller ?? false;
  const hasStoreInfo = settings?.hasStoreInfo ?? (
    !!settings?.storeName &&
    !!settings?.pickupAddressId &&
    !!settings?.returnAddressId &&
    !!settings?.bankAccountNumberMasked
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', margin: '0 0 6px', color: 'var(--og-color-text-primary)' }}>
            Trung Tâm Bán Hàng
          </h1>
          <p style={{ margin: 0, color: 'var(--og-color-text-secondary)' }}>
            Theo dõi dòng tiền ký quỹ, đơn hàng cần đóng gói và tình trạng các món đồ đang niêm yết.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '10px' }}>
          <Link to="/seller/settings">
            <Button variant="outline" size="md">
              Thiết lập gian hàng
            </Button>
          </Link>
          <Button
            variant="gold"
            size="md"
            onClick={() => {
              if (!isVerified) {
                navigate('/ekyc/verification');
              } else if (!hasStoreInfo) {
                navigate('/seller/settings');
              } else {
                navigate('/seller/products/new');
              }
            }}
          >
            Đăng tin bán mới
          </Button>
        </div>
      </div>

      {/* Onboarding Guidance Banners */}
      {!loading && !isVerified && (
        <div
          style={{
            padding: '18px 22px',
            borderRadius: '16px',
            border: '1.5px solid var(--og-color-warning, #f59e0b)',
            backgroundColor: 'rgba(245, 158, 11, 0.08)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: '14px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <span style={{ fontSize: '1.5rem' }}>🛡️</span>
            <div>
              <div style={{ fontWeight: 700, fontSize: '0.98rem', color: 'var(--og-color-text-primary)' }}>
                Yêu cầu hoàn tất định danh eKYC trước khi bán hàng
              </div>
              <div style={{ fontSize: '0.86rem', color: 'var(--og-color-text-secondary)', marginTop: '2px' }}>
                Để đảm bảo an toàn giao dịch, bạn cần xác thực CCCD và khuôn mặt trước khi đăng tin bán và nhận thanh toán.
              </div>
            </div>
          </div>
          <Link to="/ekyc/verification">
            <Button variant="gold" size="sm">
              Xác thực danh tính ngay (eKYC)
            </Button>
          </Link>
        </div>
      )}

      {!loading && isVerified && !hasStoreInfo && (
        <div
          style={{
            padding: '18px 22px',
            borderRadius: '16px',
            border: '1.5px solid var(--og-color-primary, #3b82f6)',
            backgroundColor: 'rgba(59, 130, 246, 0.08)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: '14px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <span style={{ fontSize: '1.5rem' }}>🏪</span>
            <div>
              <div style={{ fontWeight: 700, fontSize: '0.98rem', color: 'var(--og-color-text-primary)' }}>
                Bước cuối cùng: Đăng ký thông tin gian hàng Người bán
              </div>
              <div style={{ fontSize: '0.86rem', color: 'var(--og-color-text-secondary)', marginTop: '2px' }}>
                Vui lòng điền Kho gửi hàng GHN và Tài khoản ngân hàng nhận tiền quyết toán để mở khóa toàn bộ tính năng bán hàng.
              </div>
            </div>
          </div>
          <Link to="/seller/settings">
            <Button variant="primary" size="sm">
              Hoàn tất thiết lập gian hàng
            </Button>
          </Link>
        </div>
      )}

      {/* KPI Overview Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '16px' }}>
        <Card>
          <div style={{ fontSize: '0.85rem', color: 'var(--og-color-text-muted)', marginBottom: '6px' }}>
             Tiền đang bảo vệ tại Escrow (Held)
          </div>
          <div style={{ fontSize: '1.6rem', fontWeight: 800, color: 'var(--og-color-primary)' }}>
            14.850.000₫
          </div>
          <div style={{ fontSize: '0.78rem', color: 'var(--og-color-text-secondary)', marginTop: '4px' }}>
            Sẽ giải ngân khi người mua kiểm tra hàng
          </div>
        </Card>

        <Card>
          <div style={{ fontSize: '0.85rem', color: 'var(--og-color-text-muted)', marginBottom: '6px' }}>
             Đơn hàng cần đóng gói
          </div>
          <div style={{ fontSize: '1.6rem', fontWeight: 800, color: 'var(--og-color-warning)' }}>
            2 đơn
          </div>
          <Link to="/seller/orders" style={{ fontSize: '0.82rem', color: 'var(--og-color-primary)', fontWeight: 600, marginTop: '4px', display: 'inline-block' }}>
            Xem đơn cần xử lý →
          </Link>
        </Card>

        <Card>
          <div style={{ fontSize: '0.85rem', color: 'var(--og-color-text-muted)', marginBottom: '6px' }}>
             Tin đang hiển thị trên sàn
          </div>
          <div style={{ fontSize: '1.6rem', fontWeight: 800, color: 'var(--og-color-text-primary)' }}>
            5 tin
          </div>
          <Link to="/seller/products" style={{ fontSize: '0.82rem', color: 'var(--og-color-primary)', fontWeight: 600, marginTop: '4px', display: 'inline-block' }}>
            Quản lý kho tin đăng →
          </Link>
        </Card>

        <Card>
          <div style={{ fontSize: '0.85rem', color: 'var(--og-color-text-muted)', marginBottom: '6px' }}>
            ⭐ Điểm uy tín người bán
          </div>
          <div style={{ fontSize: '1.6rem', fontWeight: 800, color: 'var(--og-color-gold-text)' }}>
            4.9 / 5.0
          </div>
          <div style={{ fontSize: '0.78rem', color: 'var(--og-color-text-secondary)', marginTop: '4px' }}>
            Dựa trên 28 giao dịch hoàn tất
          </div>
        </Card>
      </div>

      {/* Quick Action Table Placeholder */}
      <Card title="Đơn hàng gần nhất cần gửi bưu tá">
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.9rem' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid var(--og-color-border)', color: 'var(--og-color-text-muted)' }}>
                <th style={{ padding: '12px 8px' }}>Mã đơn</th>
                <th style={{ padding: '12px 8px' }}>Sản phẩm</th>
                <th style={{ padding: '12px 8px' }}>Số tiền</th>
                <th style={{ padding: '12px 8px' }}>Trạng thái thanh toán</th>
                <th style={{ padding: '12px 8px' }}>Hành động</th>
              </tr>
            </thead>
            <tbody>
              <tr style={{ borderBottom: '1px solid var(--og-color-border)' }}>
                <td style={{ padding: '12px 8px', fontWeight: 600 }}>#OG-ORD-8821</td>
                <td style={{ padding: '12px 8px' }}>Máy ảnh Fujifilm X-T30 Body (95%)</td>
                <td style={{ padding: '12px 8px', fontWeight: 700 }}>14.200.000₫</td>
                <td style={{ padding: '12px 8px' }}>
                  <StatusBadge category="payment" status="HELD" />
                </td>
                <td style={{ padding: '12px 8px' }}>
                  <Link to="/seller/orders" className="og-button og-button--outline og-button--sm">
                    In phiếu gửi hàng
                  </Link>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
};
