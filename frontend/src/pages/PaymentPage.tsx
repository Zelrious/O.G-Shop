import React from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { PaymentView } from '../features/payment';

export const PaymentPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const orderIdStr = searchParams.get('orderId');
  const orderId = orderIdStr ? parseInt(orderIdStr, 10) : null;

  if (!orderId) {
    return (
      <div style={{ maxWidth: 600, margin: '60px auto', padding: '0 16px', textAlign: 'center' }}>
        <h2 style={{ color: '#991b1b', marginBottom: 12 }}>Thiếu thông tin đơn hàng</h2>
        <p style={{ color: 'var(--og-color-text-secondary)', marginBottom: 20 }}>
          Không tìm thấy mã đơn hàng để tiến hành thanh toán.
        </p>
        <Link to="/orders" className="og-button og-button--primary">
          Về danh sách Đơn mua
        </Link>
      </div>
    );
  }

  return <PaymentView orderId={orderId} />;
};
