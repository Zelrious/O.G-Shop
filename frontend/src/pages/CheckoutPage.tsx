import React from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { CheckoutView } from '../features/checkout';

export const CheckoutPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const productIdStr = searchParams.get('productId');
  const productId = productIdStr ? Number(productIdStr) : null;

  if (!productId || isNaN(productId)) {
    return (
      <div style={{ maxWidth: 640, margin: '60px auto', padding: 32, background: '#fff', borderRadius: 16, textAlign: 'center', boxShadow: '0 4px 20px rgba(0,0,0,0.06)' }}>
        <div style={{ fontSize: 48, marginBottom: 16 }}>⚠️</div>
        <h2 style={{ fontSize: 20, fontWeight: 700, color: '#1f2937', marginBottom: 8 }}>
          Không tìm thấy sản phẩm cần mua
        </h2>
        <p style={{ color: '#6b7280', marginBottom: 24 }}>
          Vui lòng chọn sản phẩm từ Chợ đồ cũ để tiến hành Mua ngay.
        </p>
        <Link
          to="/marketplace"
          style={{ display: 'inline-block', padding: '10px 20px', background: '#e25b29', color: '#fff', borderRadius: 8, textDecoration: 'none', fontWeight: 600 }}
        >
          Khám phá Chợ đồ cũ
        </Link>
      </div>
    );
  }

  return <CheckoutView productId={productId} />;
};

export default CheckoutPage;
