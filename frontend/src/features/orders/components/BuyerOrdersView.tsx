import React, { useState, useEffect, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ordersApi } from '../ordersApi';
import { OrderSummary } from '../types';

export const BuyerOrdersView: React.FC = () => {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<string>('ALL');
  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [page, setPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [loading, setLoading] = useState(true);
  const [hasLoaded, setHasLoaded] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [cancellingId, setCancellingId] = useState<number | null>(null);
  const [showCancelModal, setShowCancelModal] = useState<number | null>(null);
  const [cancelReason, setCancelReason] = useState<string>('Tôi đổi ý, không muốn mua nữa');
  const reqSeqRef = useRef<number>(0);
  const [reloadVersion, setReloadVersion] = useState(0);

  const PAGE_SIZE = 10;

  const tabs = [
    { id: 'ALL', label: 'Tất cả' },
    { id: 'PAYMENT_PENDING', label: 'Chờ thanh toán' },
    { id: 'PAID_HELD', label: 'Đã giữ tiền (Ký quỹ)' },
    { id: 'SELLER_CONFIRMED', label: 'Người bán chuẩn bị' },
    { id: 'COMPLETED', label: 'Hoàn thành' },
    { id: 'CANCELLED', label: 'Đã hủy' },
  ];

  const loadOrders = (statusTab: string, pageIndex: number) => {
    const seq = ++reqSeqRef.current;
    setLoading(true);
    setErrorMsg(null);
    ordersApi.getBuyerOrders(statusTab, pageIndex, PAGE_SIZE)
      .then((res) => {
        if (seq !== reqSeqRef.current) return;
        setOrders(res.content || []);
        const totalP = res.totalPages || 0;
        const totalE = res.totalElements || 0;
        setTotalPages(totalP);
        setTotalElements(totalE);
        setHasLoaded(true);

        // Clamp if page is beyond new totalPages
        if (totalP > 0 && pageIndex >= totalP) {
          setPage(totalP - 1);
        }
      })
      .catch((err) => {
        if (seq !== reqSeqRef.current) return;
        setOrders([]);
        setErrorMsg(err.message || 'Không thể tải danh sách đơn hàng.');
        setHasLoaded(true);
      })
      .finally(() => {
        if (seq === reqSeqRef.current) {
          setLoading(false);
        }
      });
  };

  useEffect(() => {
    loadOrders(activeTab, page);
  }, [activeTab, page, reloadVersion]);

  const handleTabChange = (newTab: string) => {
    if (newTab === activeTab) return;
    setActiveTab(newTab);
    setPage(0);
  };

  const handleCancelConfirm = async () => {
    if (!showCancelModal) return;
    setCancellingId(showCancelModal);
    try {
      await ordersApi.cancelBuyerOrder(showCancelModal, cancelReason);
      setShowCancelModal(null);
      setReloadVersion(version => version + 1);
    } catch (err: unknown) {
      const error = err as Error;
      alert(error.message || 'Không thể hủy đơn hàng.');
    } finally {
      setCancellingId(null);
    }
  };

  const formatVnd = (amount: number) => {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
  };

  const renderStatusBadge = (status: string) => {
    switch (status) {
      case 'PAYMENT_PENDING':
        return (
          <span style={{ background: '#fef3c7', color: '#b45309', padding: '3px 10px', borderRadius: 12, fontSize: '0.8rem', fontWeight: 600 }}>
            ⏱️ Chờ thanh toán
          </span>
        );
      case 'PAID_HELD':
        return (
          <span style={{ background: '#e0e7ff', color: '#3730a3', padding: '3px 10px', borderRadius: 12, fontSize: '0.8rem', fontWeight: 600 }}>
            🪙 Đã giữ tiền (Ký quỹ)
          </span>
        );
      case 'SELLER_CONFIRMED':
        return (
          <span style={{ background: '#f3e8ff', color: '#6b21a8', padding: '3px 10px', borderRadius: 12, fontSize: '0.8rem', fontWeight: 600 }}>
            📦 Người bán đang chuẩn bị
          </span>
        );
      case 'SHIPPED':
        return (
          <span style={{ background: '#e0f2fe', color: '#0369a1', padding: '3px 10px', borderRadius: 12, fontSize: '0.8rem', fontWeight: 600 }}>
            🚚 Đang vận chuyển
          </span>
        );
      case 'DELIVERED':
        return (
          <span style={{ background: '#ccfbf1', color: '#0f766e', padding: '3px 10px', borderRadius: 12, fontSize: '0.8rem', fontWeight: 600 }}>
            📬 Đã giao hàng
          </span>
        );
      case 'COMPLETED':
        return (
          <span style={{ background: '#dcfce7', color: '#16a34a', padding: '3px 10px', borderRadius: 12, fontSize: '0.8rem', fontWeight: 600 }}>
            ✓ Hoàn thành
          </span>
        );
      case 'CANCELLED':
        return (
          <span style={{ background: '#fee2e2', color: '#b91c1c', padding: '3px 10px', borderRadius: 12, fontSize: '0.8rem', fontWeight: 600 }}>
            ✕ Đã hủy
          </span>
        );
      default:
        return (
          <span style={{ background: '#f3f4f6', color: '#4b5563', padding: '3px 10px', borderRadius: 12, fontSize: '0.8rem', fontWeight: 600 }}>
            {status}
          </span>
        );
    }
  };

  return (
    <div style={{ maxWidth: 960, margin: '24px auto', padding: '0 16px 60px' }}>
      <div style={{ marginBottom: 20 }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, margin: '0 0 6px', color: 'var(--og-color-text-primary)' }}>
          📦 Đơn Mua Của Bạn (OR-01)
        </h1>
        <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '0.9rem', margin: 0 }}>
          Theo dõi trạng thái ký quỹ, tiến độ giao hàng và xác nhận nhận hàng an toàn.
        </p>
      </div>

      {/* Tabs */}
      <div className="og-seller-tabs-bar" aria-label="Bộ lọc trạng thái đơn mua">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            type="button"
            className={`og-seller-tab-btn-line ${activeTab === tab.id ? 'og-seller-tab-btn-line--active' : ''}`}
            style={{
              fontWeight: activeTab === tab.id ? 700 : 600,
            }}
            onClick={() => handleTabChange(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Error display */}
      {errorMsg && (
        <div style={{
          background: '#fef2f2',
          border: '1px solid #fecaca',
          color: '#991b1b',
          padding: '12px 16px',
          borderRadius: 8,
          marginBottom: 20,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          gap: 12,
        }}>
          <span>{errorMsg}</span>
          <button
            type="button"
            className="og-button og-button--outline"
            style={{ padding: '6px 12px', fontSize: '0.85rem', cursor: 'pointer' }}
            onClick={() => loadOrders(activeTab, page)}
          >
            Thử lại
          </button>
        </div>
      )}

      {/* Main Content Area with Stable Min-Height & Anti-Jitter Loading */}
      <div
        className="og-seller-orders-container"
        style={{
          opacity: loading && hasLoaded ? 0.5 : 1,
          pointerEvents: loading && hasLoaded ? 'none' : 'auto',
          transition: 'opacity 0.2s ease',
        }}
      >
        {/* Sleek top loading progress bar when refetching / switching tabs */}
        {loading && hasLoaded && (
          <div className="og-seller-tab-loader" role="progressbar" aria-label="Đang cập nhật danh sách đơn mua...">
            <div className="og-seller-tab-loader__bar" />
          </div>
        )}

        {loading && !hasLoaded ? (
          <div style={{ textAlign: 'center', padding: '64px 0', minHeight: 460, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
            <div className="og-spinner" style={{ margin: '0 auto 12px' }} />
            <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '0.9rem' }}>Đang tải danh sách đơn mua...</p>
          </div>
        ) : orders.length === 0 ? (
          <div style={{
            background: 'var(--og-color-surface)',
            border: '1px dashed var(--og-color-border)',
            borderRadius: 12,
            padding: '56px 16px',
            textAlign: 'center',
            minHeight: 380,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
          }}>
            <div style={{ fontSize: 48, marginBottom: 12 }}>🛒</div>
            <h3 style={{ margin: '0 0 8px', color: 'var(--og-color-text-primary)' }}>Chưa có đơn hàng nào</h3>
            <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '0.9rem', marginBottom: 20 }}>
              Bạn chưa có đơn hàng nào trong mục này. Hãy khám phá chợ đồ cũ để săn món đồ yêu thích!
            </p>
            <Link to="/marketplace" className="og-button og-button--primary">
              Khám phá Chợ Đồ Cũ
            </Link>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {orders.map((order) => (
            <div
              key={order.orderId}
              style={{
                background: 'var(--og-color-surface)',
                border: '1px solid var(--og-color-border)',
                borderRadius: 12,
                padding: '20px',
                boxShadow: '0 2px 4px rgba(0,0,0,0.02)',
              }}
            >
              {/* Card Header */}
              <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                borderBottom: '1px solid var(--og-color-border-subtle, #f3f4f6)',
                paddingBottom: 12,
                marginBottom: 16,
                fontSize: '0.88rem',
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <span style={{ fontWeight: 700, color: 'var(--og-color-text-primary)' }}>
                    Đơn hàng #{order.orderId}
                  </span>
                  <span style={{ color: 'var(--og-color-text-secondary)' }}>•</span>
                  <span style={{ color: 'var(--og-color-text-secondary)' }}>
                    Người bán: <strong>{order.partnerName}</strong>
                  </span>
                </div>
                <div>{renderStatusBadge(order.status)}</div>
              </div>

              {/* Card Body */}
              <div style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
                {order.productThumbnail ? (
                  <img
                    src={order.productThumbnail}
                    alt={order.productTitle}
                    style={{ width: 80, height: 80, borderRadius: 8, objectFit: 'cover', border: '1px solid var(--og-color-border)' }}
                  />
                ) : (
                  <div style={{
                    width: 80,
                    height: 80,
                    borderRadius: 8,
                    background: '#f3f4f6',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 28,
                  }}>
                    📦
                  </div>
                )}

                <div style={{ flex: 1 }}>
                  <h4 style={{ margin: '0 0 6px', fontSize: '1rem', color: 'var(--og-color-text-primary)' }}>
                    {order.productTitle}
                  </h4>
                  <div style={{ fontSize: '0.85rem', color: 'var(--og-color-text-secondary)' }}>
                    Số lượng: x{order.quantity} • Giá niêm yết: {formatVnd(order.unitPrice)}
                  </div>
                </div>

                <div style={{ textAlign: 'right' }}>
                  <div style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)' }}>
                    Tổng thanh toán Ký quỹ:
                  </div>
                  <div style={{ fontSize: '1.2rem', fontWeight: 700, color: 'var(--og-color-primary)', marginTop: 2 }}>
                    {formatVnd(order.totalAmount)}
                  </div>
                </div>
              </div>

              {/* Card Footer Actions */}
              <div style={{
                display: 'flex',
                justifyContent: 'flex-end',
                alignItems: 'center',
                gap: 12,
                borderTop: '1px solid var(--og-color-border-subtle, #f3f4f6)',
                paddingTop: 14,
                marginTop: 16,
              }}>
                {order.status === 'PAYMENT_PENDING' && (
                  <>
                    <button
                      type="button"
                      className="og-button og-button--primary"
                      style={{ padding: '8px 16px', fontSize: '0.88rem', fontWeight: 600 }}
                      onClick={() => navigate(`/checkout/payment?orderId=${order.orderId}`)}
                    >
                      ⚡ Thanh toán ngay
                    </button>
                    <button
                      type="button"
                      style={{
                        padding: '8px 14px',
                        fontSize: '0.85rem',
                        background: 'transparent',
                        border: '1px solid #fca5a5',
                        color: '#dc2626',
                        borderRadius: 6,
                        cursor: 'pointer',
                      }}
                      onClick={() => setShowCancelModal(order.orderId)}
                    >
                      Hủy đơn
                    </button>
                  </>
                )}

                {order.status === 'PAID_HELD' && (
                  <button
                    type="button"
                    style={{
                      padding: '8px 14px',
                      fontSize: '0.85rem',
                      background: 'transparent',
                      border: '1px solid #fca5a5',
                      color: '#dc2626',
                      borderRadius: 6,
                      cursor: 'pointer',
                    }}
                    onClick={() => setShowCancelModal(order.orderId)}
                  >
                    Hủy đơn & Hoàn tiền
                  </button>
                )}

                <button
                  type="button"
                  className="og-button og-button--secondary"
                  style={{ padding: '8px 16px', fontSize: '0.88rem' }}
                  onClick={() => navigate(`/orders/${order.orderId}`)}
                >
                  Chi tiết đơn hàng
                </button>
              </div>
            </div>
          ))}

          {/* Pagination Controls */}
          {totalPages > 0 && (
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                marginTop: 20,
                padding: '12px 16px',
                background: 'var(--og-color-surface)',
                border: '1px solid var(--og-color-border)',
                borderRadius: 8,
                flexWrap: 'wrap',
                gap: 12,
              }}
            >
              <div style={{ fontSize: '0.88rem', color: 'var(--og-color-text-secondary)' }}>
                Trang <strong>{page + 1}</strong> / {totalPages} (Tổng <strong>{totalElements}</strong> đơn hàng)
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                <button
                  type="button"
                  className="og-button og-button--secondary"
                  style={{ padding: '6px 14px', fontSize: '0.85rem' }}
                  disabled={page <= 0 || loading}
                  onClick={() => setPage((prev) => Math.max(0, prev - 1))}
                >
                  Trước
                </button>
                <button
                  type="button"
                  className="og-button og-button--secondary"
                  style={{ padding: '6px 14px', fontSize: '0.85rem' }}
                  disabled={page >= totalPages - 1 || loading}
                  onClick={() => setPage((prev) => Math.min(totalPages - 1, prev + 1))}
                >
                  Sau
                </button>
              </div>
            </div>
          )}
        </div>
      )}
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
              Xác nhận Hủy Đơn Hàng #{showCancelModal}?
            </h3>
            <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '0.88rem', margin: '0 0 16px' }}>
              Bạn có chắc chắn muốn hủy đơn hàng này không? Sản phẩm sẽ được mở lại cho người mua khác.
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
              ⚠️ <strong>Lưu ý:</strong> Voucher giảm giá đã áp dụng cho đơn hàng này sẽ <strong>không được hoàn lại</strong> lượt dùng.
            </div>

            <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: 6 }}>
              Lý do hủy đơn:
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
                disabled={cancellingId !== null}
                onClick={() => setShowCancelModal(null)}
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
                disabled={cancellingId !== null}
                onClick={handleCancelConfirm}
              >
                {cancellingId ? 'Đang hủy...' : 'Đồng ý Hủy Đơn'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
