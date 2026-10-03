import React, { useState, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ordersApi } from '../ordersApi';
import { OrderDetail } from '../types';

interface BuyerOrderDetailViewProps {
  orderId: number;
}

export const BuyerOrderDetailView: React.FC<BuyerOrderDetailViewProps> = ({ orderId }) => {
  const navigate = useNavigate();
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [cancelReason, setCancelReason] = useState('Tôi đổi ý, không muốn mua nữa');
  const [cancelling, setCancelling] = useState(false);

  const loadOrder = useCallback(() => {
    setLoading(true);
    setErrorMsg(null);
    ordersApi.getBuyerOrderDetail(orderId)
      .then((data) => {
        setOrder(data);
      })
      .catch((err) => {
        setErrorMsg(err.message || 'Không thể tải chi tiết đơn hàng.');
      })
      .finally(() => {
        setLoading(false);
      });
  }, [orderId]);

  useEffect(() => {
    loadOrder();
  }, [loadOrder]);

  const handleCancelConfirm = async () => {
    setCancelling(true);
    try {
      await ordersApi.cancelBuyerOrder(orderId, cancelReason);
      setShowCancelModal(false);
      loadOrder();
    } catch (err: unknown) {
      const error = err as Error;
      alert(error.message || 'Không thể hủy đơn hàng.');
    } finally {
      setCancelling(false);
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
        <p style={{ color: 'var(--og-color-text-secondary)' }}>Đang tải chi tiết đơn hàng #{orderId}...</p>
      </div>
    );
  }

  if (errorMsg || !order) {
    return (
      <div style={{ maxWidth: 640, margin: '40px auto', padding: '0 16px' }}>
        <div style={{ background: '#fef2f2', border: '1px solid #fecaca', borderRadius: 12, padding: 24, textAlign: 'center' }}>
          <h2 style={{ color: '#991b1b', margin: '0 0 8px' }}>Không tìm thấy đơn hàng</h2>
          <p style={{ color: '#7f1d1d', marginBottom: 20 }}>{errorMsg || 'Đơn hàng không tồn tại.'}</p>
          <Link to="/orders" className="og-button og-button--primary">
            Quay lại danh sách Đơn mua
          </Link>
        </div>
      </div>
    );
  }

  // Determine timeline steps
  const steps = [
    { key: 'CREATED', label: 'Đặt hàng', done: true, time: order.createdAt },
    { key: 'PAID', label: 'Ký quỹ Escrow', done: order.status !== 'PAYMENT_PENDING' && order.status !== 'CANCELLED', time: order.heldAt || order.paidAt },
    { key: 'CONFIRMED', label: 'Người bán xác nhận', done: ['SELLER_CONFIRMED', 'SHIPPED', 'DELIVERED', 'COMPLETED'].includes(order.status) },
    { key: 'SHIPPED', label: 'Đang vận chuyển', done: ['SHIPPED', 'DELIVERED', 'COMPLETED'].includes(order.status) },
    { key: 'DELIVERED', label: 'Hoàn tất giao dịch', done: order.status === 'COMPLETED', time: order.completedAt },
  ];

  return (
    <div style={{ maxWidth: 900, margin: '24px auto', padding: '0 16px 60px' }}>
      {/* Top navigation */}
      <div style={{ marginBottom: 20, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Link to="/orders" style={{ textDecoration: 'none', color: 'var(--og-color-text-secondary)', fontSize: '0.9rem' }}>
          &larr; Quay lại danh sách Đơn mua
        </Link>
        <div style={{ fontSize: '0.88rem', color: 'var(--og-color-text-secondary)' }}>
          Ngày tạo: {formatDate(order.createdAt)}
        </div>
      </div>

      {/* Header card with status */}
      <div style={{
        background: 'var(--og-color-surface)',
        border: '1px solid var(--og-color-border)',
        borderRadius: 12,
        padding: 24,
        marginBottom: 20,
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
          <div>
            <h1 style={{ fontSize: '1.4rem', fontWeight: 700, margin: '0 0 4px', color: 'var(--og-color-text-primary)' }}>
              Đơn hàng #{order.orderId}
            </h1>
            <div style={{ fontSize: '0.85rem', color: 'var(--og-color-text-secondary)' }}>
              Người bán: <strong>{order.sellerName}</strong>
            </div>
          </div>

          <div style={{ textAlign: 'right' }}>
            <span style={{
              background: order.status === 'PAID_HELD' ? '#e0e7ff' : order.status === 'CANCELLED' ? '#fee2e2' : '#fef3c7',
              color: order.status === 'PAID_HELD' ? '#3730a3' : order.status === 'CANCELLED' ? '#b91c1c' : '#b45309',
              padding: '6px 14px',
              borderRadius: 16,
              fontWeight: 700,
              fontSize: '0.88rem',
            }}>
              {order.status}
            </span>
          </div>
        </div>

        {/* Timeline */}
        {order.status !== 'CANCELLED' ? (
          <div style={{ display: 'flex', justifyContent: 'space-between', position: 'relative', marginTop: 12 }}>
            {steps.map((step, idx) => (
              <div key={step.key} style={{ textAlign: 'center', flex: 1, position: 'relative' }}>
                <div style={{
                  width: 32,
                  height: 32,
                  borderRadius: '50%',
                  background: step.done ? 'var(--og-color-primary)' : 'var(--og-color-border)',
                  color: '#fff',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  margin: '0 auto 8px',
                  fontWeight: 700,
                  fontSize: 14,
                }}>
                  {step.done ? '✓' : idx + 1}
                </div>
                <div style={{ fontSize: '0.82rem', fontWeight: step.done ? 600 : 400, color: step.done ? 'var(--og-color-text-primary)' : 'var(--og-color-text-secondary)' }}>
                  {step.label}
                </div>
                {step.time && (
                  <div style={{ fontSize: '0.7rem', color: 'var(--og-color-text-tertiary)', marginTop: 2 }}>
                    {formatDate(step.time)}
                  </div>
                )}
              </div>
            ))}
          </div>
        ) : (
          <div style={{ background: '#fee2e2', borderRadius: 8, padding: '12px 16px', color: '#991b1b', fontSize: '0.9rem' }}>
            <strong>Đơn hàng đã hủy:</strong> {order.cancellationReason || 'Đã hủy theo yêu cầu.'} ({formatDate(order.cancelledAt)})
          </div>
        )}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 360px', gap: 20 }}>
        {/* Left Column: Product & Shipping Address */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          {/* Product Card */}
          <div style={{ background: 'var(--og-color-surface)', border: '1px solid var(--og-color-border)', borderRadius: 12, padding: 20 }}>
            <h3 style={{ margin: '0 0 16px', fontSize: '1rem', fontWeight: 600, color: 'var(--og-color-text-primary)' }}>
              Sản phẩm
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

          {/* Shipping Address Snapshot */}
          <div style={{ background: 'var(--og-color-surface)', border: '1px solid var(--og-color-border)', borderRadius: 12, padding: 20 }}>
            <h3 style={{ margin: '0 0 12px', fontSize: '1rem', fontWeight: 600, color: 'var(--og-color-text-primary)' }}>
              Địa chỉ nhận hàng
            </h3>
            <div style={{ fontSize: '0.9rem', lineHeight: 1.6 }}>
              <div><strong>{order.shippingRecipientName}</strong> ({order.shippingPhoneNumber})</div>
              <div style={{ color: 'var(--og-color-text-secondary)' }}>{order.fullShippingAddress}</div>
            </div>
          </div>

          {/* Escrow Status Card */}
          <div style={{
            background: 'linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%)',
            border: '1px solid #bbf7d0',
            borderRadius: 12,
            padding: 20,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
              <span style={{ fontSize: 24 }}>🛡️</span>
              <strong style={{ color: '#166534', fontSize: '1rem' }}>Bảo Vệ Ký Quỹ Escrow (O.G Shield)</strong>
            </div>
            <p style={{ margin: '0 0 12px', fontSize: '0.85rem', color: '#14532d', lineHeight: 1.5 }}>
              Số tiền <strong>{formatVnd(order.totalAmount)}</strong> được lưu giữ an toàn. Tiền chỉ được giải ngân cho người bán sau khi bạn nhận đồ và đồng kiểm xác nhận.
            </p>
            {order.transactionCode && (
              <div style={{ fontSize: '0.82rem', color: '#166534' }}>
                Mã ký quỹ: <code>{order.transactionCode}</code>
              </div>
            )}
          </div>
        </div>

        {/* Right Column: Financial Breakdown & Actions */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          <div style={{ background: 'var(--og-color-surface)', border: '1px solid var(--og-color-border)', borderRadius: 12, padding: 20 }}>
            <h3 style={{ margin: '0 0 16px', fontSize: '1rem', fontWeight: 600, color: 'var(--og-color-text-primary)' }}>
              Chi tiết thanh toán
            </h3>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 10, fontSize: '0.88rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--og-color-text-secondary)' }}>Tiền hàng:</span>
                <span>{formatVnd(order.subtotal)}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--og-color-text-secondary)' }}>Phí vận chuyển:</span>
                <span>{formatVnd(order.shippingFee)}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--og-color-text-secondary)' }}>Phí bảo vệ người mua:</span>
                <span>{formatVnd(order.buyerSystemFee)}</span>
              </div>

              {order.voucherDiscountAmount > 0 && (
                <div style={{ display: 'flex', justifyContent: 'space-between', color: '#16a34a' }}>
                  <span>Voucher giảm giá ({order.appliedVoucherCode}):</span>
                  <span>-{formatVnd(order.voucherDiscountAmount)}</span>
                </div>
              )}

              {order.shippingDiscountAmount > 0 && (
                <div style={{ display: 'flex', justifyContent: 'space-between', color: '#16a34a' }}>
                  <span>Giảm phí ship ({order.appliedVoucherCode}):</span>
                  <span>-{formatVnd(order.shippingDiscountAmount)}</span>
                </div>
              )}

              <div style={{
                borderTop: '1px dashed var(--og-color-border)',
                paddingTop: 12,
                marginTop: 6,
                display: 'flex',
                justifyContent: 'space-between',
                fontSize: '1.05rem',
              }}>
                <strong style={{ color: 'var(--og-color-text-primary)' }}>Tổng cộng:</strong>
                <strong style={{ color: 'var(--og-color-primary)', fontSize: '1.2rem' }}>
                  {formatVnd(order.totalAmount)}
                </strong>
              </div>
            </div>

            {/* Action Buttons */}
            <div style={{ marginTop: 24, display: 'flex', flexDirection: 'column', gap: 10 }}>
              {order.status === 'PAYMENT_PENDING' && (
                <>
                  <button
                    type="button"
                    className="og-button og-button--primary"
                    style={{ width: '100%', padding: '12px 16px', fontWeight: 600 }}
                    onClick={() => navigate(`/checkout/payment?orderId=${order.orderId}`)}
                  >
                    ⚡ Thanh toán ngay
                  </button>
                  <button
                    type="button"
                    style={{
                      width: '100%',
                      padding: '10px 16px',
                      background: 'transparent',
                      border: '1px solid #fca5a5',
                      color: '#dc2626',
                      borderRadius: 8,
                      fontWeight: 600,
                      cursor: 'pointer',
                    }}
                    onClick={() => setShowCancelModal(true)}
                  >
                    Hủy đơn hàng
                  </button>
                </>
              )}

              {order.status === 'PAID_HELD' && (
                <button
                  type="button"
                  style={{
                    width: '100%',
                    padding: '10px 16px',
                    background: 'transparent',
                    border: '1px solid #fca5a5',
                    color: '#dc2626',
                    borderRadius: 8,
                    fontWeight: 600,
                    cursor: 'pointer',
                  }}
                  onClick={() => setShowCancelModal(true)}
                >
                  Hủy đơn & Hoàn tiền
                </button>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Cancel Order Modal */}
      {showCancelModal && (
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
            maxWidth: 480,
            width: '100%',
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
          }}>
            <h3 style={{ margin: '0 0 8px', color: '#991b1b', fontSize: '1.2rem' }}>
              Xác nhận Hủy Đơn Hàng #{order.orderId}?
            </h3>
            <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '0.88rem', margin: '0 0 16px' }}>
              {order.status === 'PAID_HELD'
                ? 'Đơn hàng đã được Ký quỹ. Khi hủy, toàn bộ số tiền thanh toán sẽ được hoàn lại.'
                : 'Đơn hàng sẽ bị hủy và sản phẩm sẽ được mở lại cho người mua khác.'}
            </p>

            <div style={{
              background: '#fffbeb',
              border: '1px solid #fef3c7',
              borderRadius: 8,
              padding: '10px 14px',
              fontSize: '0.82rem',
              color: '#92400e',
              marginBottom: 16,
            }}>
              ⚠️ <strong>Lưu ý:</strong> Voucher giảm giá đã áp dụng sẽ <strong>không được hoàn lại</strong> lượt dùng.
            </div>

            <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: 6 }}>
              Lý do hủy:
            </label>
            <select
              style={{
                width: '100%',
                padding: '10px 12px',
                borderRadius: 8,
                border: '1px solid var(--og-color-border)',
                marginBottom: 20,
                fontSize: '0.9rem',
              }}
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
            >
              <option value="Tôi đổi ý, không muốn mua nữa">Tôi đổi ý, không muốn mua nữa</option>
              <option value="Tôi muốn thay đổi địa chỉ nhận hàng">Tôi muốn thay đổi địa chỉ nhận hàng</option>
              <option value="Tôi tìm thấy sản phẩm khác giá tốt hơn">Tôi tìm thấy sản phẩm khác giá tốt hơn</option>
              <option value="Người bán không phản hồi trao đổi">Người bán không phản hồi trao đổi</option>
              <option value="Lý do khác">Lý do khác</option>
            </select>

            <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
              <button
                type="button"
                className="og-button og-button--secondary"
                disabled={cancelling}
                onClick={() => setShowCancelModal(false)}
              >
                Đóng
              </button>
              <button
                type="button"
                style={{
                  padding: '10px 18px',
                  background: '#dc2626',
                  color: '#fff',
                  border: 'none',
                  borderRadius: 8,
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
                disabled={cancelling}
                onClick={handleCancelConfirm}
              >
                {cancelling ? 'Đang hủy...' : 'Đồng ý Hủy Đơn'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
