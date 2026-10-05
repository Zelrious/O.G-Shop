import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { ordersApi } from '../ordersApi';
import { OrderSummary } from '../types';

export const SellerOrdersView: React.FC = () => {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<string>('PAID_HELD');
  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [page, setPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [confirmingId, setConfirmingId] = useState<number | null>(null);
  const [reloadVersion, setReloadVersion] = useState(0);
  const reqSeqRef = useRef<number>(0);

  const PAGE_SIZE = 10;

  const tabs = [
    { id: 'PAID_HELD', label: '⚡ Cần xử lý (Đã Ký quỹ)' },
    { id: 'SELLER_CONFIRMED', label: 'Đã xác nhận' },
    { id: 'PAYMENT_PENDING', label: 'Chờ thanh toán' },
    { id: 'COMPLETED', label: 'Hoàn thành' },
    { id: 'CANCELLED', label: 'Đã hủy' },
    { id: 'ALL', label: 'Tất cả' },
  ];

  const loadOrders = (statusTab: string, pageIndex: number) => {
    const seq = ++reqSeqRef.current;
    setLoading(true);
    setErrorMsg(null);
    ordersApi.getSellerOrders(statusTab, pageIndex, PAGE_SIZE)
      .then((res) => {
        if (seq !== reqSeqRef.current) return;
        setOrders(res.content || []);
        const totalP = res.totalPages || 0;
        const totalE = res.totalElements || 0;
        setTotalPages(totalP);
        setTotalElements(totalE);

        // Clamp if page is beyond new totalPages
        if (totalP > 0 && pageIndex >= totalP) {
          setPage(totalP - 1);
        }
      })
      .catch((err) => {
        if (seq !== reqSeqRef.current) return;
        setOrders([]);
        setErrorMsg(err.message || 'Không thể tải danh sách đơn bán.');
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

  const handleConfirmOrder = async (orderId: number) => {
    if (!window.confirm(`Xác nhận tiếp nhận và chuẩn bị đơn hàng #${orderId}? Sản phẩm sẽ được đánh dấu là ĐÃ BÁN.`)) {
      return;
    }

    setConfirmingId(orderId);
    try {
      const res = await ordersApi.confirmSellerOrder(orderId);
      alert(res.message || 'Đã xác nhận đơn hàng thành công.');
      setReloadVersion(version => version + 1);
    } catch (err: unknown) {
      const error = err as Error;
      alert(error.message || 'Không thể xác nhận đơn hàng.');
    } finally {
      setConfirmingId(null);
    }
  };

  const formatVnd = (amount: number) => {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
  };

  const renderStatusBadge = (status: string) => {
    switch (status) {
      case 'PAID_HELD':
        return (
          <span style={{ background: '#dcfce7', color: '#15803d', padding: '3px 10px', borderRadius: 12, fontSize: '0.8rem', fontWeight: 700 }}>
            🪙 Tiền đã Ký quỹ — Cần xác nhận
          </span>
        );
      case 'SELLER_CONFIRMED':
        return (
          <span style={{ background: '#f3e8ff', color: '#6b21a8', padding: '3px 10px', borderRadius: 12, fontSize: '0.8rem', fontWeight: 600 }}>
            ✓ Đã xác nhận — Chuẩn bị hàng
          </span>
        );
      case 'PAYMENT_PENDING':
        return (
          <span style={{ background: '#fef3c7', color: '#b45309', padding: '3px 10px', borderRadius: 12, fontSize: '0.8rem', fontWeight: 600 }}>
            ⏱️ Người mua chưa thanh toán
          </span>
        );
      case 'COMPLETED':
        return (
          <span style={{ background: '#dbeafe', color: '#1e40af', padding: '3px 10px', borderRadius: 12, fontSize: '0.8rem', fontWeight: 600 }}>
            ✓ Giao dịch thành công
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
    <div style={{ maxWidth: 1000, margin: '20px auto', padding: '0 16px 60px' }}>
      <div style={{ marginBottom: 20 }}>
        <h1 style={{ fontSize: '1.45rem', fontWeight: 700, margin: '0 0 6px', color: 'var(--og-color-text-primary)' }}>
          📋 Quản Lý Đơn Hàng Cần Gửi (SL-09)
        </h1>
        <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '0.88rem', margin: 0 }}>
          Xem các đơn hàng đã được giữ tiền qua Escrow, xác nhận chuẩn bị đơn và in vận đơn giao hàng.
        </p>
      </div>

      {/* Tabs */}
      <div style={{
        display: 'flex',
        borderBottom: '1px solid var(--og-color-border)',
        overflowX: 'auto',
        marginBottom: 20,
        gap: 8,
      }}>
        {tabs.map((tab) => (
          <button
            key={tab.id}
            type="button"
            style={{
              padding: '12px 16px',
              border: 'none',
              background: 'transparent',
              borderBottom: activeTab === tab.id ? '2px solid var(--og-color-primary)' : '2px solid transparent',
              color: activeTab === tab.id ? 'var(--og-color-primary)' : 'var(--og-color-text-secondary)',
              fontWeight: activeTab === tab.id ? 700 : 500,
              fontSize: '0.9rem',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
            }}
            onClick={() => handleTabChange(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {errorMsg && (
        <div style={{
          background: '#fef2f2',
          border: '1px solid #fecaca',
          color: '#991b1b',
          padding: '12px 16px',
          borderRadius: 8,
          marginBottom: 16,
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

      {loading ? (
        <div style={{ textAlign: 'center', padding: '48px 0' }}>
          <div className="og-spinner" style={{ margin: '0 auto 12px' }} />
          <p style={{ color: 'var(--og-color-text-secondary)' }}>Đang tải danh sách đơn bán...</p>
        </div>
      ) : orders.length === 0 ? (
        <div style={{
          background: 'var(--og-color-surface)',
          border: '1px dashed var(--og-color-border)',
          borderRadius: 12,
          padding: '48px 16px',
          textAlign: 'center',
        }}>
          <div style={{ fontSize: 40, marginBottom: 8 }}>📦</div>
          <h3 style={{ margin: '0 0 6px', color: 'var(--og-color-text-primary)' }}>Không có đơn hàng nào</h3>
          <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '0.88rem' }}>
            Không tìm thấy đơn hàng nào ở mục này.
          </p>
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
                fontSize: '0.85rem',
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <span style={{ fontWeight: 700, color: 'var(--og-color-text-primary)' }}>
                    Đơn hàng #{order.orderId}
                  </span>
                  <span style={{ color: 'var(--og-color-text-secondary)' }}>•</span>
                  <span style={{ color: 'var(--og-color-text-secondary)' }}>
                    Người mua: <strong>{order.partnerName}</strong>
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
                    style={{ width: 72, height: 72, borderRadius: 8, objectFit: 'cover', border: '1px solid var(--og-color-border)' }}
                  />
                ) : (
                  <div style={{
                    width: 72,
                    height: 72,
                    borderRadius: 8,
                    background: '#f3f4f6',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 24,
                  }}>
                    📦
                  </div>
                )}

                <div style={{ flex: 1 }}>
                  <h4 style={{ margin: '0 0 4px', fontSize: '0.98rem', color: 'var(--og-color-text-primary)' }}>
                    {order.productTitle}
                  </h4>
                  <div style={{ fontSize: '0.82rem', color: 'var(--og-color-text-secondary)' }}>
                    Số lượng: x{order.quantity} • Giá bán: {formatVnd(order.unitPrice)}
                  </div>
                </div>

                <div style={{ textAlign: 'right' }}>
                  <div style={{ fontSize: '0.78rem', color: 'var(--og-color-text-secondary)' }}>
                    Tổng tiền đơn hàng:
                  </div>
                  <div style={{ fontSize: '1.1rem', fontWeight: 700, color: 'var(--og-color-text-primary)' }}>
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
                paddingTop: 12,
                marginTop: 16,
              }}>
                {order.status === 'PAID_HELD' && (
                  <button
                    type="button"
                    className="og-button og-button--primary"
                    style={{ padding: '8px 18px', fontSize: '0.88rem', fontWeight: 700 }}
                    disabled={confirmingId === order.orderId}
                    onClick={() => handleConfirmOrder(order.orderId)}
                  >
                    {confirmingId === order.orderId ? 'Đang xác nhận...' : '⚡ Xác nhận đơn hàng'}
                  </button>
                )}

                <button
                  type="button"
                  className="og-button og-button--secondary"
                  style={{ padding: '8px 16px', fontSize: '0.88rem' }}
                  onClick={() => navigate(`/seller/orders/${order.orderId}`)}
                >
                  Xem chi tiết
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
                Trang <strong>{page + 1}</strong> / {totalPages} (Tổng <strong>{totalElements}</strong> đơn bán)
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
  );
};
