import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { ordersApi } from '../ordersApi';
import { OrderDetail } from '../types';

interface SellerOrderDetailViewProps {
  orderId: number;
}

export const SellerOrderDetailView: React.FC<SellerOrderDetailViewProps> = ({ orderId }) => {
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);

  const loadOrder = useCallback(() => {
    setLoading(true);
    setErrorMsg(null);
    ordersApi.getSellerOrderDetail(orderId)
      .then((data) => {
        setOrder(data);
      })
      .catch((err) => {
        setErrorMsg(err.message || 'Không thể tải chi tiết đơn bán.');
      })
      .finally(() => {
        setLoading(false);
      });
  }, [orderId]);

  useEffect(() => {
    loadOrder();
  }, [loadOrder]);

  const handleConfirmOrder = async () => {
    if (!window.confirm(`Xác nhận tiếp nhận và chuẩn bị đơn hàng #${orderId}? Sản phẩm sẽ được chuyển sang trạng thái ĐÃ BÁN.`)) {
      return;
    }

    setConfirming(true);
    try {
      const res = await ordersApi.confirmSellerOrder(orderId);
      alert(res.message || 'Đã xác nhận đơn hàng thành công.');
      loadOrder();
    } catch (err: unknown) {
      const error = err as Error;
      alert(error.message || 'Không thể xác nhận đơn hàng.');
    } finally {
      setConfirming(false);
    }
  };

  const formatVnd = (amount: number) => {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
  };

  const formatDate = (isoStr?: string | null) => {
    if (!isoStr) return '--';
    return new Date(isoStr).toLocaleString('vi-VN');
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '60px 0' }}>
        <div className="og-spinner" style={{ margin: '0 auto 12px' }} />
        <p style={{ color: 'var(--og-color-text-secondary)' }}>Đang tải thông tin đơn bán #{orderId}...</p>
      </div>
    );
  }

  if (errorMsg || !order) {
    return (
      <div style={{ maxWidth: 640, margin: '40px auto', padding: '0 16px' }}>
        <div style={{ background: '#fef2f2', border: '1px solid #fecaca', borderRadius: 12, padding: 24, textAlign: 'center' }}>
          <h2 style={{ color: '#991b1b', margin: '0 0 8px' }}>Không tìm thấy đơn hàng</h2>
          <p style={{ color: '#7f1d1d', marginBottom: 20 }}>{errorMsg || 'Đơn hàng không tồn tại.'}</p>
          <Link to="/seller/orders" className="og-button og-button--primary">
            Quay lại danh sách Đơn bán
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 960, margin: '20px auto', padding: '0 16px 60px' }}>
      {/* Top navigation */}
      <div style={{ marginBottom: 20, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Link to="/seller/orders" style={{ textDecoration: 'none', color: 'var(--og-color-text-secondary)', fontSize: '0.9rem' }}>
          &larr; Quay lại danh sách Đơn bán
        </Link>
        <div style={{ fontSize: '0.88rem', color: 'var(--og-color-text-secondary)' }}>
          Ngày tạo: {formatDate(order.createdAt)}
        </div>
      </div>

      {/* Header status */}
      <div style={{
        background: 'var(--og-color-surface)',
        border: '1px solid var(--og-color-border)',
        borderRadius: 12,
        padding: 24,
        marginBottom: 20,
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <h1 style={{ fontSize: '1.4rem', fontWeight: 700, margin: '0 0 4px', color: 'var(--og-color-text-primary)' }}>
              Đơn bán #{order.orderId} (SL-10)
            </h1>
            <div style={{ fontSize: '0.88rem', color: 'var(--og-color-text-secondary)' }}>
              Người mua: <strong>{order.buyerName}</strong>
            </div>
          </div>

          <div style={{ textAlign: 'right' }}>
            <span style={{
              background: order.status === 'PAID_HELD' ? '#dcfce7' : order.status === 'SELLER_CONFIRMED' ? '#f3e8ff' : '#fef3c7',
              color: order.status === 'PAID_HELD' ? '#15803d' : order.status === 'SELLER_CONFIRMED' ? '#6b21a8' : '#b45309',
              padding: '6px 14px',
              borderRadius: 16,
              fontWeight: 700,
              fontSize: '0.88rem',
            }}>
              {order.status}
            </span>
          </div>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 360px', gap: 20 }}>
        {/* Left Column */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          {/* Product details */}
          <div style={{ background: 'var(--og-color-surface)', border: '1px solid var(--og-color-border)', borderRadius: 12, padding: 20 }}>
            <h3 style={{ margin: '0 0 16px', fontSize: '1rem', fontWeight: 600, color: 'var(--og-color-text-primary)' }}>
              Món đồ được đặt mua
            </h3>
            <div style={{ display: 'flex', gap: 16 }}>
              {order.productThumbnail ? (
                <img
                  src={order.productThumbnail}
                  alt={order.productTitle}
                  style={{ width: 84, height: 84, borderRadius: 8, objectFit: 'cover', border: '1px solid var(--og-color-border)' }}
                />
              ) : (
                <div style={{ width: 84, height: 84, borderRadius: 8, background: '#f3f4f6', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 32 }}>
                  📦
                </div>
              )}
              <div style={{ flex: 1 }}>
                <h4 style={{ margin: '0 0 6px', fontSize: '1.05rem', color: 'var(--og-color-text-primary)' }}>
                  {order.productTitle}
                </h4>
                <div style={{ fontSize: '0.88rem', color: 'var(--og-color-text-secondary)', marginBottom: 8 }}>
                  Số lượng: x{order.quantity}
                </div>
                <div style={{ fontSize: '1.1rem', fontWeight: 700, color: 'var(--og-color-primary)' }}>
                  {formatVnd(order.unitPrice)}
                </div>
              </div>
            </div>
          </div>

          {/* Recipient Address */}
          <div style={{ background: 'var(--og-color-surface)', border: '1px solid var(--og-color-border)', borderRadius: 12, padding: 20 }}>
            <h3 style={{ margin: '0 0 12px', fontSize: '1rem', fontWeight: 600, color: 'var(--og-color-text-primary)' }}>
              Thông tin người nhận & Địa chỉ giao hàng
            </h3>
            <div style={{ fontSize: '0.9rem', lineHeight: 1.6 }}>
              <div><strong>{order.shippingRecipientName}</strong> ({order.shippingPhoneNumber})</div>
              <div style={{ color: 'var(--og-color-text-secondary)' }}>{order.fullShippingAddress}</div>
            </div>
          </div>
        </div>

        {/* Right Column: Seller Proceeds & Action */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          <div style={{ background: 'var(--og-color-surface)', border: '1px solid var(--og-color-border)', borderRadius: 12, padding: 20 }}>
            <h3 style={{ margin: '0 0 16px', fontSize: '1rem', fontWeight: 600, color: 'var(--og-color-text-primary)' }}>
              Doanh thu của Người bán
            </h3>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 10, fontSize: '0.88rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--og-color-text-secondary)' }}>Giá bán sản phẩm:</span>
                <span>{formatVnd(order.subtotal)}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--og-color-text-secondary)' }}>Phí sàn người bán:</span>
                <span style={{ color: '#dc2626' }}>-{formatVnd(order.sellerSystemFee)}</span>
              </div>

              <div style={{
                borderTop: '1px dashed var(--og-color-border)',
                paddingTop: 12,
                marginTop: 6,
                display: 'flex',
                justifyContent: 'space-between',
                fontSize: '1.05rem',
              }}>
                <strong style={{ color: 'var(--og-color-text-primary)' }}>Thực nhận sau khi hoàn tất:</strong>
                <strong style={{ color: '#16a34a', fontSize: '1.2rem' }}>
                  {formatVnd(order.sellerProceeds)}
                </strong>
              </div>
            </div>

            <div style={{
              background: '#f0fdf4',
              border: '1px solid #bbf7d0',
              borderRadius: 8,
              padding: 12,
              marginTop: 16,
              fontSize: '0.82rem',
              color: '#166534',
              lineHeight: 1.5,
            }}>
              🪙 <strong>Tiền đã giữ trong Escrow:</strong> Tiền được bảo vệ bởi sàn và sẽ giải ngân vào ví người bán sau khi đơn hàng hoàn tất.
            </div>

            {order.status === 'PAID_HELD' && (
              <div style={{ marginTop: 20 }}>
                <button
                  type="button"
                  className="og-button og-button--primary"
                  style={{ width: '100%', padding: '12px 16px', fontWeight: 700, fontSize: '0.95rem' }}
                  disabled={confirming}
                  onClick={handleConfirmOrder}
                >
                  {confirming ? 'Đang xác nhận...' : '⚡ Xác nhận Đơn hàng'}
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
