import React from 'react';
import { Link } from 'react-router-dom';
import { Card, Button, StatusBadge } from '../../shared/components';

export const SellerDashboardPage: React.FC = () => {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', margin: '0 0 6px', color: 'var(--og-color-text-primary)' }}>
            Trung Tâm Bán Hàng (SL-01)
          </h1>
          <p style={{ margin: 0, color: 'var(--og-color-text-secondary)' }}>
            Theo dõi dòng tiền ký quỹ, đơn hàng cần đóng gói và tình trạng các món đồ đang niêm yết.
          </p>
        </div>
        <Link to="/seller/products/new">
          <Button variant="gold" size="md">
            ➕ Đăng tin bán mới (SL-02)
          </Button>
        </Link>
      </div>

      {/* KPI Overview Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '16px' }}>
        <Card>
          <div style={{ fontSize: '0.85rem', color: 'var(--og-color-text-muted)', marginBottom: '6px' }}>
            🔒 Tiền đang bảo vệ tại Escrow (Held)
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
            📦 Đơn hàng cần đóng gói
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
            🏷️ Tin đang hiển thị trên sàn
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
                    In phiếu gửi hàng (SL-11)
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
