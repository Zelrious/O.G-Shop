import { ChatLink } from '../../chat-offer/ChatLink';
import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { ordersApi } from '../ordersApi';
import { OrderDetail } from '../types';
import { TrackingResponse, formatGhnStatus } from '../../fulfillment/fulfillmentApi';

interface SellerOrderDetailViewProps {
  orderId: number;
}

export const SellerOrderDetailView: React.FC<SellerOrderDetailViewProps> = ({ orderId }) => {
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);

  // GHN Fulfillment state
  const [tracking, setTracking] = useState<TrackingResponse | null>(null);
  const [loadingTracking, setLoadingTracking] = useState(false);
  const [showShipModal, setShowShipModal] = useState(false);
  const [weight, setWeight] = useState(500);
  const [length, setLength] = useState(20);
  const [width, setWidth] = useState(15);
  const [height, setHeight] = useState(10);
  const [shipmentNote, setShipmentNote] = useState('Cho xem hàng không cho thử');
  const [creatingShipment, setCreatingShipment] = useState(false);
  const [shipmentError, setShipmentError] = useState<string | null>(null);
  const [printingWaybill, setPrintingWaybill] = useState(false);

  const loadTracking = useCallback(() => {
    setLoadingTracking(true);
    ordersApi.getTracking(orderId)
      .then((res) => {
        setTracking(res);
      })
      .catch(() => {
        setTracking(null);
      })
      .finally(() => {
        setLoadingTracking(false);
      });
  }, [orderId]);

  const loadOrder = useCallback(() => {
    setLoading(true);
    setErrorMsg(null);
    ordersApi.getSellerOrderDetail(orderId)
      .then((data) => {
        setOrder(data);
        loadTracking();
      })
      .catch((err) => {
        setErrorMsg(err.message || 'Không thể tải chi tiết đơn bán.');
      })
      .finally(() => {
        setLoading(false);
      });
  }, [orderId, loadTracking]);

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

  const handleCreateShipment = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreatingShipment(true);
    setShipmentError(null);

    try {
      const res = await ordersApi.createShipment(orderId, {
        weight,
        length,
        width,
        height,
        note: shipmentNote,
      });
      alert(`Đã tạo vận đơn GHN thành công! Mã vận đơn: ${res.trackingNumber}`);
      setShowShipModal(false);
      loadOrder();
    } catch (err: unknown) {
      const error = err as Error;
      setShipmentError(error.message || 'Không thể tạo vận đơn GHN.');
    } finally {
      setCreatingShipment(false);
    }
  };

  const handlePrintWaybill = async () => {
    setPrintingWaybill(true);
    try {
      const res = await ordersApi.printWaybill(orderId);
      if (res.printUrl) {
        window.open(res.printUrl, '_blank');
      } else {
        alert('Không nhận được đường dẫn in phiếu giao hàng từ GHN.');
      }
    } catch (err: unknown) {
      const error = err as Error;
      alert(error.message || 'Không thể lấy thông tin in phiếu giao hàng.');
    } finally {
      setPrintingWaybill(false);
    }
  };

  const formatVnd = (amount?: number | null) => {
    if (amount == null) return '0 ₫';
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

  const hasTracking = Boolean(tracking && tracking.trackingNumber);
  const ghnStatusInfo = hasTracking ? formatGhnStatus(tracking?.rawGhnStatus) : null;

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
                {order.productId && <ChatLink className="og-button og-button--outline" to={`/messages?productId=${order.productId}&orderId=${order.orderId}`}>Trao đổi về đơn hàng</ChatLink>}
                <div style={{ fontSize: '0.88rem', color: 'var(--og-color-text-secondary)', marginBottom: 8, marginTop: 8 }}>
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

          {/* GHN Fulfillment Section */}
          <div style={{ background: 'var(--og-color-surface)', border: '1px solid var(--og-color-border)', borderRadius: 12, padding: 20 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
              <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: 600, color: 'var(--og-color-text-primary)', display: 'flex', alignItems: 'center', gap: 8 }}>
                <span>🚚</span> Vận chuyển Giao Hàng Nhanh (GHN)
              </h3>
              {loadingTracking && <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)' }}>Đang cập nhật...</span>}
            </div>

            {hasTracking ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12, fontSize: '0.9rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 14px', background: 'var(--og-color-surface-subtle, #f8fafc)', borderRadius: 8 }}>
                  <div>
                    <span style={{ color: 'var(--og-color-text-secondary)', marginRight: 8 }}>Mã vận đơn:</span>
                    <strong style={{ fontSize: '1.05rem', color: 'var(--og-color-primary)' }}>{tracking?.trackingNumber}</strong>
                  </div>
                  {ghnStatusInfo && (
                    <span style={{
                      background: ghnStatusInfo.bg,
                      color: ghnStatusInfo.color,
                      padding: '4px 12px',
                      borderRadius: 12,
                      fontWeight: 600,
                      fontSize: '0.82rem',
                    }}>
                      {ghnStatusInfo.label}
                    </span>
                  )}
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, fontSize: '0.85rem' }}>
                  <div>
                    <span style={{ color: 'var(--og-color-text-secondary)' }}>Đơn vị vận chuyển: </span>
                    <strong>{tracking?.carrier || 'GHN'}</strong>
                  </div>
                  <div>
                    <span style={{ color: 'var(--og-color-text-secondary)' }}>Cước vận chuyển: </span>
                    <strong>{formatVnd(tracking?.shippingFee || order.shippingFee)}</strong>
                  </div>
                  <div style={{ gridColumn: 'span 2' }}>
                    <span style={{ color: 'var(--og-color-text-secondary)' }}>Dự kiến giao hàng: </span>
                    <strong>{formatDate(tracking?.expectedDeliveryAt)}</strong>
                  </div>
                </div>

                <div style={{ marginTop: 10, display: 'flex', gap: 10 }}>
                  <button
                    type="button"
                    className="og-button og-button--primary"
                    style={{ padding: '8px 16px', fontSize: '0.88rem', fontWeight: 600 }}
                    disabled={printingWaybill}
                    onClick={handlePrintWaybill}
                  >
                    {printingWaybill ? 'Đang tạo link in...' : '🖨️ In Phiếu Giao Hàng A5 (GHN)'}
                  </button>
                  <button
                    type="button"
                    className="og-button og-button--secondary"
                    style={{ padding: '8px 14px', fontSize: '0.88rem' }}
                    onClick={loadTracking}
                  >
                    Tra cứu lại
                  </button>
                </div>
              </div>
            ) : order.status === 'SELLER_CONFIRMED' ? (
              <div>
                <p style={{ margin: '0 0 14px', fontSize: '0.88rem', color: 'var(--og-color-text-secondary)', lineHeight: 1.5 }}>
                  Đơn hàng đã được xác nhận. Hãy đóng gói hàng và tạo vận đơn GHN để shipper tới lấy hàng theo địa chỉ kho của bạn.
                </p>
                <button
                  type="button"
                  className="og-button og-button--primary"
                  style={{ padding: '10px 18px', fontWeight: 600 }}
                  onClick={() => {
                    setShipmentError(null);
                    setShowShipModal(true);
                  }}
                >
                  📦 Tạo vận đơn GHN
                </button>
              </div>
            ) : (
              <div style={{ fontSize: '0.88rem', color: 'var(--og-color-text-secondary)' }}>
                Chưa có thông tin vận đơn GHN cho đơn hàng này. Vận đơn sẽ được tạo sau khi người bán xác nhận đơn.
              </div>
            )}
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
              <strong>Tiền đã giữ trong Escrow:</strong> Tiền được bảo vệ bởi sàn và sẽ giải ngân vào ví người bán sau khi đơn hàng hoàn tất.
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
                  {confirming ? 'Đang xác nhận...' : 'Xác nhận Đơn hàng'}
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Modal Tạo Vận Đơn GHN */}
      {showShipModal && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: 'rgba(0, 0, 0, 0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1000,
          padding: 16,
        }}>
          <div style={{
            background: 'var(--og-color-surface)',
            borderRadius: 12,
            padding: 24,
            maxWidth: 520,
            width: '100%',
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
            maxHeight: '90vh',
            overflowY: 'auto',
          }}>
            <h3 style={{ margin: '0 0 12px', fontSize: '1.2rem', color: 'var(--og-color-text-primary)' }}>
              Tạo vận đơn Giao Hàng Nhanh (GHN)
            </h3>
            <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '0.88rem', margin: '0 0 16px' }}>
              Xác nhận thông số đóng gói để gửi yêu cầu lấy hàng tới Giao Hàng Nhanh.
            </p>

            {shipmentError && (
              <div style={{
                background: '#fef2f2',
                border: '1px solid #fecaca',
                color: '#991b1b',
                padding: '10px 14px',
                borderRadius: 8,
                fontSize: '0.85rem',
                marginBottom: 16,
              }}>
                {shipmentError}
              </div>
            )}

            <form onSubmit={handleCreateShipment}>
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: 4 }}>
                  Khối lượng kiện hàng (gram) *
                </label>
                <input
                  type="number"
                  min="50"
                  max="50000"
                  required
                  value={weight}
                  onChange={(e) => setWeight(Number(e.target.value))}
                  style={{
                    width: '100%',
                    padding: '8px 12px',
                    borderRadius: 6,
                    border: '1px solid var(--og-color-border)',
                  }}
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 10, marginBottom: 14 }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.82rem', fontWeight: 600, marginBottom: 4 }}>
                    Dài (cm) *
                  </label>
                  <input
                    type="number"
                    min="1"
                    max="200"
                    required
                    value={length}
                    onChange={(e) => setLength(Number(e.target.value))}
                    style={{
                      width: '100%',
                      padding: '8px 12px',
                      borderRadius: 6,
                      border: '1px solid var(--og-color-border)',
                    }}
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '0.82rem', fontWeight: 600, marginBottom: 4 }}>
                    Rộng (cm) *
                  </label>
                  <input
                    type="number"
                    min="1"
                    max="200"
                    required
                    value={width}
                    onChange={(e) => setWidth(Number(e.target.value))}
                    style={{
                      width: '100%',
                      padding: '8px 12px',
                      borderRadius: 6,
                      border: '1px solid var(--og-color-border)',
                    }}
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '0.82rem', fontWeight: 600, marginBottom: 4 }}>
                    Cao (cm) *
                  </label>
                  <input
                    type="number"
                    min="1"
                    max="200"
                    required
                    value={height}
                    onChange={(e) => setHeight(Number(e.target.value))}
                    style={{
                      width: '100%',
                      padding: '8px 12px',
                      borderRadius: 6,
                      border: '1px solid var(--og-color-border)',
                    }}
                  />
                </div>
              </div>

              <div style={{ marginBottom: 16 }}>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: 4 }}>
                  Ghi chú cho shipper
                </label>
                <input
                  type="text"
                  value={shipmentNote}
                  onChange={(e) => setShipmentNote(e.target.value)}
                  placeholder="Ví dụ: Cho xem hàng không cho thử..."
                  style={{
                    width: '100%',
                    padding: '8px 12px',
                    borderRadius: 6,
                    border: '1px solid var(--og-color-border)',
                  }}
                />
              </div>

              <div style={{
                background: 'var(--og-color-surface-subtle, #f8fafc)',
                padding: '12px 14px',
                borderRadius: 8,
                fontSize: '0.82rem',
                color: 'var(--og-color-text-secondary)',
                marginBottom: 20,
              }}>
                <div><strong>Người nhận:</strong> {order.shippingRecipientName} ({order.shippingPhoneNumber})</div>
                <div style={{ marginTop: 2 }}><strong>Địa chỉ:</strong> {order.fullShippingAddress}</div>
              </div>

              <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
                <button
                  type="button"
                  className="og-button og-button--secondary"
                  disabled={creatingShipment}
                  onClick={() => setShowShipModal(false)}
                >
                  Đóng
                </button>
                <button
                  type="submit"
                  className="og-button og-button--primary"
                  disabled={creatingShipment}
                >
                  {creatingShipment ? 'Đang tạo vận đơn...' : 'Xác nhận tạo vận đơn GHN'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
