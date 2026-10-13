import { Icon } from '../shared/components/Icon/Icon';
import React from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { CheckoutView } from '../features/checkout';

export const CheckoutPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const productIdStr = searchParams.get('productId');
  const productId = productIdStr ? Number(productIdStr) : null;
  const offerId = Number(searchParams.get('offerId')) || undefined;

  if (!productId || isNaN(productId)) {
    return (
      <div style={{ maxWidth: 640, margin: '60px auto', padding: 32, background: 'var(--og-color-surface)', borderRadius: 16, textAlign: 'center', boxShadow: '0 4px 20px rgba(0,0,0,0.06)' }}>
        <div style={{ fontSize: 48, marginBottom: 16 }}><Icon name="alert" style={{ width: '1em', height: '1em' }} /></div>
        <h2 style={{ fontSize: 20, fontWeight: 700, color: 'var(--og-color-text-primary)', marginBottom: 8 }}>
          Không tìm thấy sản phẩm cần mua
        </h2>
        <p style={{ color: 'var(--og-color-text-secondary)', marginBottom: 24 }}>
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

  return <CheckoutView key={`${productId}:${offerId ?? ''}`} productId={productId} offerId={offerId} />;
};

export default CheckoutPage;
