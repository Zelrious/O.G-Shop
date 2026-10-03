import React from 'react';
import { useParams, Link } from 'react-router-dom';
import { BuyerOrderDetailView } from '../features/orders';

export const BuyerOrderDetailPage: React.FC = () => {
  const { orderId } = useParams<{ orderId: string }>();
  const parsedId = orderId ? parseInt(orderId, 10) : null;

  if (!parsedId) {
    return (
      <div style={{ maxWidth: 600, margin: '60px auto', padding: '0 16px', textAlign: 'center' }}>
        <h2 style={{ color: '#991b1b', marginBottom: 12 }}>Mã đơn hàng không hợp lệ</h2>
        <Link to="/orders" className="og-button og-button--primary">
          Về danh sách Đơn mua
        </Link>
      </div>
    );
  }

  return <BuyerOrderDetailView orderId={parsedId} />;
};
