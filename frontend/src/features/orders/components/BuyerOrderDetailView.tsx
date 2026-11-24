import { Icon } from '../../../shared/components/Icon/Icon';
import { ChatLink } from '../../chat-offer/ChatLink';
import React, { useState, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ordersApi } from '../ordersApi';
import { OrderDetail } from '../types';
import { TrackingResponse, resolveOutboundGhnDisplayStatus } from '../../fulfillment/fulfillmentApi';
import { BuyerDisputeModal } from '../../disputes/BuyerDisputeModal';
import { OrderReviewModal } from '../../disputes/OrderReviewModal';
import { ReturnShipmentCard, getReturnShipmentStatusMeta } from '../../disputes/ReturnShipmentCard';
import { Uc83BuyerOrderPanel } from '../../disputes/Uc83BuyerOrderPanel';
import { disputeApi } from '../../disputes/disputeApi';
import { reviewApi } from '../../disputes/reviewApi';
import { buildOrderTimelineSteps } from '../orderTimeline';
import type { DisputeDetail, OrderReviewsResponse } from '../../disputes/types';

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
  const [showCompleteModal, setShowCompleteModal] = useState(false);
  const [completing, setCompleting] = useState(false);
  const [tracking, setTracking] = useState<TrackingResponse | null>(null);
  const [showDisputeModal, setShowDisputeModal] = useState(false);
  const [showReviewModal, setShowReviewModal] = useState(false);
  const [reviews, setReviews] = useState<OrderReviewsResponse | null>(null);
  const [dispute, setDispute] = useState<DisputeDetail | null>(null);
  const [creatingReturnShipment, setCreatingReturnShipment] = useState(false);
  const [confirmingHandover, setConfirmingHandover] = useState(false);

  const loadOrder = useCallback(() => {
    setLoading(true);
    setErrorMsg(null);
    ordersApi.getBuyerOrderDetail(orderId)
      .then((data) => {
        setOrder(data);
        ordersApi.getTracking(orderId)
          .then((res) => setTracking(res))
          .catch(() => setTracking(null));
        reviewApi.getOrderReviews(orderId)
          .then((res) => setReviews(res))
          .catch(() => setReviews(null));
        if (['DISPUTED', 'REFUNDED', 'COMPLETED'].includes(data.status)) {
          disputeApi.getDisputeByOrder(orderId)
            .then((res) => setDispute(res))
            .catch(() => setDispute(null));
        } else {
          setDispute(null);
        }
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

  const handleCompleteConfirm = async () => {
    setCompleting(true);
    try {
      const res = await ordersApi.completeBuyerOrder(orderId);
      setShowCompleteModal(false);
      loadOrder();
      alert(res.message || 'Xác nhận nhận hàng thành công. Giao dịch đã hoàn tất và tiền ký quỹ đã được giải ngân.');
    } catch (err: unknown) {
      const error = err as Error;
      alert(error.message || 'Không thể hoàn tất đơn hàng.');
    } finally {
      setCompleting(false);
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

  // Authoritative timeline preserving outbound delivery milestones & return shipment lifecycle
  const steps = buildOrderTimelineSteps(order, tracking, dispute);
  const outboundGhnMeta = resolveOutboundGhnDisplayStatus(order, tracking);


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
                  {step.done ? '' : idx + 1}
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

          {/* GHN Shipping Information Card */}
          {((tracking && tracking.trackingNumber) || order.trackingNumber) && (
            <div style={{
              background: 'var(--og-color-surface)',
              border: '1px solid var(--og-color-border)',
              borderRadius: 12,
              padding: 20,
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: 600, color: 'var(--og-color-text-primary)', display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span>🚚</span> Thông tin vận chuyển (GHN)
                </h3>
                {(tracking?.rawGhnStatus || outboundGhnMeta.isDeliveredConfirmed) && (
                  <span
                    data-testid="outbound-ghn-status-badge"
                    title={outboundGhnMeta.rawDiagnosticStatus ? `GHN raw status: ${outboundGhnMeta.rawDiagnosticStatus}` : undefined}
                    style={{
                      background: outboundGhnMeta.bg,
                      color: outboundGhnMeta.color,
                      padding: '4px 12px',
                      borderRadius: 12,
                      fontWeight: 600,
                      fontSize: '0.82rem',
                    }}
                  >
                    {outboundGhnMeta.label}
                  </span>
                )}
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontSize: '0.88rem' }}>
                <div>
                  <span style={{ color: 'var(--og-color-text-secondary)' }}>Mã vận đơn: </span>
                  <strong style={{ color: 'var(--og-color-primary)', fontSize: '1rem' }}>{tracking?.trackingNumber || order.trackingNumber}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--og-color-text-secondary)' }}>Đơn vị vận chuyển: </span>
                  <strong>{tracking?.carrier || order.carrier || 'Giao Hàng Nhanh (GHN)'}</strong>
                </div>
                {tracking?.expectedDeliveryAt && (
                  <div>
                    <span style={{ color: 'var(--og-color-text-secondary)' }}>Dự kiến giao hàng: </span>
                    <strong>{formatDate(tracking.expectedDeliveryAt)}</strong>
                  </div>
                )}
                {order.shippedAt && (
                  <div>
                    <span style={{ color: 'var(--og-color-text-secondary)' }}>Thời gian gửi hàng: </span>
                    <span>{formatDate(order.shippedAt)}</span>
                  </div>
                )}
                {order.deliveredAt && (
                  <div>
                    <span style={{ color: 'var(--og-color-text-secondary)' }}>Thời gian phát thành công: </span>
                    <span style={{ color: '#15803d', fontWeight: 600 }}>{formatDate(order.deliveredAt)}</span>
                  </div>
                )}
                {tracking?.fromAddress && (
                  <div style={{ fontSize: '0.82rem', color: 'var(--og-color-text-secondary)', marginTop: 4 }}>
                    Gửi từ: {tracking.fromAddress}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Return Shipment Info Card (GHN - Return Leg) */}
          <ReturnShipmentCard
            dispute={dispute}
            buyerSenderName={order.shippingRecipientName}
            buyerSenderAddress={order.fullShippingAddress}
          />

          {/* Escrow Status Card */}
          <div style={{
            background: 'linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%)',
            border: '1px solid #bbf7d0',
            borderRadius: 12,
            padding: 20,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
              <span style={{ fontSize: 24 }}><Icon name="shield" /></span>
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
                    Thanh toán ngay
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

              {order.status === 'DELIVERED' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  <div style={{
                    background: '#eff6ff',
                    border: '1px solid #bfdbfe',
                    borderRadius: 8,
                    padding: 12,
                    fontSize: '0.85rem',
                    color: '#1e40af',
                    lineHeight: 1.5,
                  }}>
                    <strong>Đơn hàng đã được giao!</strong> Bạn có thời gian đồng kiểm sản phẩm. Nếu sản phẩm đúng mô tả và bạn hài lòng, hãy nhấn nút dưới đây để hoàn tất và giải ngân cho người bán.
                  </div>
                  <button
                    type="button"
                    className="og-button og-button--primary"
                    style={{
                      width: '100%',
                      padding: '12px 16px',
                      fontWeight: 700,
                      fontSize: '0.95rem',
                      background: '#16a34a',
                      borderColor: '#16a34a',
                    }}
                    onClick={() => setShowCompleteModal(true)}
                  >
                    Đã nhận được hàng (Hoàn tất)
                  </button>
                  <button
                    type="button"
                    style={{
                      width: '100%',
                      padding: '10px 16px',
                      background: 'transparent',
                      border: '1px solid #f87171',
                      color: '#dc2626',
                      borderRadius: 8,
                      fontWeight: 600,
                      cursor: 'pointer',
                    }}
                    onClick={() => setShowDisputeModal(true)}
                  >
                    Yêu cầu Trả hàng / Khiếu nại (UC62)
                  </button>
                  <Uc83BuyerOrderPanel
                    orderId={order.orderId}
                    orderStatus={order.status}
                    onUpdated={loadOrder}
                  />
                </div>
              )}

              {order.status === 'DISPUTED' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {dispute?.status === 'RETURN_APPROVED' ? (
                    <div style={{
                      background: '#eff6ff',
                      border: '1px solid #93c5fd',
                      borderRadius: 8,
                      padding: 14,
                      fontSize: '0.85rem',
                      color: '#1e3a8a',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 10,
                      lineHeight: 1.5,
                    }}>
                      <div>
                        <strong>✅ KTV đã chấp thuận yêu cầu trả hàng &amp; hoàn tiền (RETURN_APPROVED):</strong>
                        {dispute.verdictNotes && (
                          <div style={{ marginTop: 4 }}>
                            Ghi chú phán quyết của KTV: <em>"{dispute.verdictNotes}"</em>
                          </div>
                        )}
                        <div style={{ marginTop: 4 }}>
                          Số tiền hoàn dự kiến: <strong>{formatVnd(dispute.refundAmount || order.totalAmount)}</strong>
                        </div>
                      </div>

                      {dispute.returnTrackingCode ? (
                        <div style={{
                          background: '#fff',
                          border: '1px solid #bfdbfe',
                          borderRadius: 8,
                          padding: 10,
                          color: '#0f172a',
                          display: 'flex',
                          flexDirection: 'column',
                          gap: 6,
                        }}>
                          <div style={{ fontWeight: 700 }}>
                            🔄 Trạng thái Vận đơn Hoàn trả (Chiều về)
                          </div>
                          <div>Đơn vị vận chuyển: <strong>{dispute.returnCarrier || 'GHN'}</strong></div>
                          <div>Mã vận đơn hoàn trả: <strong style={{ color: '#2563eb' }}>{dispute.returnTrackingCode}</strong></div>
                          <div>
                            Trạng thái:{' '}
                            <strong style={{ color: getReturnShipmentStatusMeta(dispute.returnShipmentStatus).color }}>
                              {getReturnShipmentStatusMeta(dispute.returnShipmentStatus).label}
                            </strong>
                          </div>

                          {dispute.returnShipmentStatus === 'RETURN_CREATED' && (
                            <button
                              type="button"
                              disabled={confirmingHandover}
                              onClick={async () => {
                                if (!window.confirm(`Xác nhận bạn đã đóng gói và bàn giao kiện hàng hoàn trả (${dispute.returnTrackingCode}) cho shipper GHN?`)) {
                                  return;
                                }
                                setConfirmingHandover(true);
                                try {
                                  const updated = await disputeApi.confirmReturnHandover(order.orderId);
                                  setDispute(updated);
                                } catch (err) {
                                  alert(err instanceof Error ? err.message : 'Không thể xác nhận bàn giao kiện hàng hoàn trả.');
                                } finally {
                                  setConfirmingHandover(false);
                                }
                              }}
                              style={{
                                marginTop: 4,
                                width: '100%',
                                padding: '10px 14px',
                                background: '#ea580c',
                                border: 'none',
                                color: '#fff',
                                borderRadius: 8,
                                fontWeight: 700,
                                cursor: confirmingHandover ? 'not-allowed' : 'pointer',
                              }}
                            >
                              {confirmingHandover
                                ? 'Đang xác nhận bàn giao...'
                                : '🚚 Xác nhận đã bàn giao kiện hàng hoàn trả cho GHN'}
                            </button>
                          )}

                          {dispute.returnShipmentStatus === 'RETURN_DELIVERED' && (
                            <div style={{ fontSize: '0.82rem', color: '#15803d', background: '#f0fdf4', padding: 8, borderRadius: 6 }}>
                              Kiện hàng hoàn trả đã giao tới Người bán. Đang chờ Người bán kiểm tra tình trạng hàng và hoàn tất quy trình hoàn tiền.
                            </div>
                          )}
                        </div>
                      ) : (
                        <button
                          type="button"
                          disabled={creatingReturnShipment}
                          onClick={async () => {
                            setCreatingReturnShipment(true);
                            try {
                              const updated = await disputeApi.submitReturnShipment(order.orderId);
                              setDispute(updated);
                            } catch (err) {
                              alert(err instanceof Error ? err.message : 'Không thể tạo vận đơn hoàn trả.');
                            } finally {
                              setCreatingReturnShipment(false);
                            }
                          }}
                          style={{
                            width: '100%',
                            padding: '10px 16px',
                            background: '#2563eb',
                            border: 'none',
                            color: '#fff',
                            borderRadius: 8,
                            fontWeight: 700,
                            cursor: creatingReturnShipment ? 'not-allowed' : 'pointer',
                          }}
                        >
                          {creatingReturnShipment
                            ? 'Đang tạo vận đơn hoàn trả...'
                            : '📦 Tạo vận đơn GHN Hoàn trả'}
                        </button>
                      )}
                    </div>
                  ) : (
                    <div style={{
                      background: '#fef3c7',
                      border: '1px solid #fde68a',
                      borderRadius: 8,
                      padding: 12,
                      fontSize: '0.85rem',
                      color: '#b45309',
                      lineHeight: 1.5,
                    }}>
                      <strong>Đang xử lý tranh chấp (UC62/UC75):</strong> Hồ sơ khiếu nại của bạn đã được chuyển đến Kỹ thuật viên đối soát 2 chiều để ra phán quyết hoàn tiền hoặc trả hàng.
                      {dispute?.sellerResponse && (
                        <div style={{ marginTop: 6, padding: 8, background: '#fff', borderRadius: 6, color: '#334155' }}>
                          <strong>Phản hồi của Người bán:</strong> "{dispute.sellerResponse}"
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}

              {order.status === 'REFUNDED' && (
                <div style={{
                  background: '#eff6ff',
                  border: '1px solid #bfdbfe',
                  borderRadius: 8,
                  padding: 12,
                  fontSize: '0.85rem',
                  color: '#1d4ed8',
                  lineHeight: 1.5,
                }}>
                  <strong>Đã hoàn tiền (UC83):</strong> Đơn hàng đã được hoàn tiền hàng về cho bạn. Tiền ký quỹ Escrow đã được giải phóng.
                </div>
              )}

              {order.status === 'COMPLETED' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  <div style={{
                    background: '#dcfce7',
                    border: '1px solid #86efac',
                    borderRadius: 8,
                    padding: 12,
                    fontSize: '0.85rem',
                    color: '#166534',
                    lineHeight: 1.5,
                  }}>
                    <strong>Đơn hàng đã hoàn tất!</strong> Cảm ơn bạn đã mua sắm tại O.G-Shop. Tiền ký quỹ đã được giải ngân cho người bán.
                  </div>
                  {reviews?.buyerReview ? (
                    <div style={{
                      background: '#f8fafc',
                      border: '1px solid #e2e8f0',
                      borderRadius: 8,
                      padding: '10px 12px',
                      fontSize: '0.84rem',
                      color: '#334155',
                    }}>
                      <div><strong>Đánh giá của bạn:</strong> {'★'.repeat(reviews.buyerReview.rating)}{'☆'.repeat(5 - reviews.buyerReview.rating)} ({reviews.buyerReview.rating}/5 sao)</div>
                      {reviews.buyerReview.comment && (
                        <div style={{ marginTop: 4, color: '#64748b', fontStyle: 'italic' }}>
                          "{reviews.buyerReview.comment}"
                        </div>
                      )}
                    </div>
                  ) : (
                    <button
                      type="button"
                      className="og-button og-button--primary"
                      style={{
                        width: '100%',
                        padding: '10px 16px',
                        fontWeight: 600,
                        fontSize: '0.9rem',
                      }}
                      onClick={() => setShowReviewModal(true)}
                    >
                      ⭐ Đánh giá Người bán (+500 xu thưởng)
                    </button>
                  )}
                </div>
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
              <strong>Lưu ý:</strong> Voucher giảm giá đã áp dụng sẽ <strong>không được hoàn lại</strong> lượt dùng.
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

      {/* Complete Order Modal */}
      {showCompleteModal && (
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
            <h3 style={{ margin: '0 0 8px', color: '#166534', fontSize: '1.2rem' }}>
              Xác nhận Đã Nhận Hàng #{order.orderId}?
            </h3>
            <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '0.88rem', margin: '0 0 16px', lineHeight: 1.5 }}>
              Bạn xác nhận đã nhận được món đồ và hoàn toàn hài lòng với tình trạng sản phẩm.
              Sau khi xác nhận, đơn hàng sẽ chuyển sang trạng thái <strong>HOÀN TẤT (COMPLETED)</strong> và số tiền ký quỹ <strong>{formatVnd(order.totalAmount)}</strong> sẽ được giải ngân ngay lập tức cho người bán.
            </p>

            <div style={{
              background: '#f0fdf4',
              border: '1px solid #bbf7d0',
              borderRadius: 8,
              padding: '10px 14px',
              fontSize: '0.82rem',
              color: '#15803d',
              marginBottom: 20,
            }}>
              <strong>Bảo vệ O.G Shield:</strong> Thao tác này không thể hoàn tác sau khi đã thực hiện.
            </div>

            <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
              <button
                type="button"
                className="og-button og-button--secondary"
                disabled={completing}
                onClick={() => setShowCompleteModal(false)}
              >
                Kiểm tra lại
              </button>
              <button
                type="button"
                style={{
                  padding: '10px 18px',
                  background: '#16a34a',
                  color: '#fff',
                  border: 'none',
                  borderRadius: 8,
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
                disabled={completing}
                onClick={handleCompleteConfirm}
              >
                {completing ? 'Đang hoàn tất...' : 'Xác nhận & Giải ngân'}
              </button>
            </div>
          </div>
        </div>
      )}
      {/* Buyer Return/Dispute Modal (UC62) */}
      {showDisputeModal && (
        <BuyerDisputeModal
          isOpen={showDisputeModal}
          orderId={order.orderId}
          productTitle={order.productTitle}
          onClose={() => setShowDisputeModal(false)}
          onSuccess={() => loadOrder()}
        />
      )}

      {/* Buyer Review Modal (UC61) */}
      {showReviewModal && (
        <OrderReviewModal
          isOpen={showReviewModal}
          orderId={order.orderId}
          partnerName={order.sellerName}
          isBuyer={true}
          onClose={() => setShowReviewModal(false)}
          onSuccess={() => loadOrder()}
        />
      )}
    </div>
  );
};
