import { ChatLink } from '../../chat-offer/ChatLink';
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
  const [shipping, setShipping] = useState(false);
  const [delivering, setDelivering] = useState(false);
  const [printing, setPrinting] = useState(false);

  const isSimulationAllowed = import.meta.env.DEV || import.meta.env.VITE_SIMULATION_ENABLED === 'true';

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

  const handleShipOrder = async () => {
    if (!window.confirm(`Bàn giao đơn hàng #${orderId} cho GHN để tạo vận đơn chính thức?`)) {
      return;
    }

    setShipping(true);
    try {
      const res = await ordersApi.shipSellerOrder(orderId);
      alert(res.message || `Tạo vận đơn GHN thành công! Mã vận đơn: ${res.trackingNumber}`);
      loadOrder();
    } catch (err: unknown) {
      const error = err as Error;
      alert(error.message || 'Không thể tạo vận đơn GHN.');
    } finally {
      setShipping(false);
    }
  };

  const handleDeliverOrder = async () => {
    if (!window.confirm(`[TEST DEMO] Bạn muốn mô phỏng GHN xác nhận đã giao hàng thành công cho đơn #${orderId}?`)) {
      return;
    }

    setDelivering(true);
    try {
      const res = await ordersApi.deliverSellerOrder(orderId);
      alert(res.message || 'Mô phỏng GHN giao hàng thành công!');
      loadOrder();
    } catch (err: unknown) {
      const error = err as Error;
      alert(error.message || 'Không thể thực hiện mô phỏng giao hàng.');
    } finally {
      setDelivering(false);
    }
  };

  const handlePrintWaybill = async () => {
    setPrinting(true);
    try {
      const res = await ordersApi.getPrintWaybill(orderId);
      if (res.printUrl) {
        window.open(res.printUrl, '_blank');
      } else {
        alert('Không tìm thấy liên kết in phiếu gửi hàng.');
      }
    } catch (err: unknown) {
      const error = err as Error;
      alert(error.message || 'Không thể lấy liên kết in phiếu gửi hàng.');
    } finally {
      setPrinting(false);
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

                </div>
              )}
              <div style={{ flex: 1 }}>
                <h4 style={{ margin: '0 0 6px', fontSize: '1.05rem', color: 'var(--og-color-text-primary)' }}>
                  {order.productTitle}
                </h4>
                {order.productId && <ChatLink className="og-button og-button--outline" to={`/messages?productId=${order.productId}&orderId=${order.orderId}`}>Trao đổi về đơn hàng</ChatLink>}
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

          {/* GHN Fulfillment & Shipping Info */}
          {order.trackingNumber && (
            <div style={{ background: 'var(--og-color-surface)', border: '1px solid #bfdbfe', borderRadius: 12, padding: 20 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: 600, color: '#1e40af' }}>
                  Thông tin vận chuyển GHN
                </h3>
                <span style={{ fontSize: '0.8rem', background: '#dbeafe', color: '#1d4ed8', padding: '3px 8px', borderRadius: 4, fontWeight: 600 }}>
                  {order.carrier || 'GHN'}
                </span>
              </div>
              <div style={{ fontSize: '0.9rem', lineHeight: 1.8 }}>
                <div>Mã vận đơn: <strong style={{ color: '#0369a1', fontFamily: 'monospace', fontSize: '1rem' }}>{order.trackingNumber}</strong></div>
                {order.shippedAt && <div>Thời gian gửi hàng: <span style={{ color: 'var(--og-color-text-secondary)' }}>{formatDate(order.shippedAt)}</span></div>}
                {order.deliveredAt && <div>Thời gian phát thành công: <span style={{ color: '#15803d', fontWeight: 600 }}>{formatDate(order.deliveredAt)}</span></div>}
              </div>
              <div style={{ marginTop: 12 }}>
                <button
                  type="button"
                  className="og-button og-button--outline"
                  style={{ fontSize: '0.88rem', padding: '6px 14px' }}
                  disabled={printing}
                  onClick={handlePrintWaybill}
                >
                  {printing ? 'Đang tải phiếu in...' : 'In phiếu gửi hàng (A5)'}
                </button>
              </div>
            </div>
          )}
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
              <strong>Tiền đã giữ trong Escrow:</strong> Tiền được bảo vệ bởi sàn và sẽ giải ngân vào ví người bán sau khi đơn hàng hoàn tất.
            </div>

            {/* Action buttons based on status */}
            <div style={{ marginTop: 20, display: 'flex', flexDirection: 'column', gap: 10 }}>
              {order.status === 'PAID_HELD' && (
                <button
                  type="button"
                  className="og-button og-button--primary"
                  style={{ width: '100%', padding: '12px 16px', fontWeight: 700, fontSize: '0.95rem' }}
                  disabled={confirming}
                  onClick={handleConfirmOrder}
                >
                  {confirming ? 'Đang xác nhận...' : 'Xác nhận Đơn hàng'}
                </button>
              )}

              {order.status === 'SELLER_CONFIRMED' && (
                <button
                  type="button"
                  className="og-button og-button--primary"
                  style={{ width: '100%', padding: '12px 16px', fontWeight: 700, fontSize: '0.95rem', background: '#2563eb', borderColor: '#2563eb' }}
                  disabled={shipping}
                  onClick={handleShipOrder}
                >
                  {shipping ? 'Đang tạo vận đơn GHN...' : 'Bàn giao cho GHN (Tạo vận đơn)'}
                </button>
              )}

              {order.status === 'SHIPPED' && (
                <>
                  <button
                    type="button"
                    className="og-button og-button--outline"
                    style={{ width: '100%', padding: '10px 16px', fontWeight: 600 }}
                    disabled={printing}
                    onClick={handlePrintWaybill}
                  >
                    {printing ? 'Đang mở phiếu in...' : 'In phiếu gửi hàng (A5)'}
                  </button>

                  {isSimulationAllowed && (
                    <button
                      type="button"
                      style={{
                        width: '100%',
                        padding: '10px 16px',
                        background: '#fef3c7',
                        border: '1px dashed #d97706',
                        color: '#92400e',
                        borderRadius: 8,
                        fontWeight: 700,
                        fontSize: '0.88rem',
                        cursor: 'pointer',
                      }}
                      disabled={delivering}
                      onClick={handleDeliverOrder}
                    >
                      {delivering ? 'Đang mô phỏng giao hàng...' : '[Test] Mô phỏng GHN giao hàng (DELIVERED)'}
                    </button>
                  )}
                </>
              )}

              {order.status === 'DELIVERED' && (
                <div style={{
                  background: '#fef9c3',
                  border: '1px solid #fde047',
                  borderRadius: 8,
                  padding: 12,
                  fontSize: '0.85rem',
                  color: '#854d0e',
                  lineHeight: 1.5,
                }}>
                  <strong>Đã giao hàng thành công!</strong> Đang chờ người mua xác nhận hoặc hệ thống sẽ tự động hoàn tất sau thời gian đồng kiểm.
                </div>
              )}

              {order.status === 'COMPLETED' && (
                <div style={{
                  background: '#dcfce7',
                  border: '1px solid #86efac',
                  borderRadius: 8,
                  padding: 12,
                  fontSize: '0.85rem',
                  color: '#166534',
                  lineHeight: 1.5,
                }}>
                  <strong>Đơn hàng đã hoàn tất!</strong> Tiền bán hàng đã được giải ngân thành công.
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
