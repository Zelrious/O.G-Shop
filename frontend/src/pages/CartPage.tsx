import { Icon } from '../shared/components/Icon/Icon';
import React, { useState, useEffect, useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useCart } from '../shared/context';

const CONDITION_MAP: Record<string, string> = {
  LIKE_NEW: 'Như mới',
  GOOD: 'Khá tốt',
  FAIR: 'Chấp nhận được',
  POOR: 'Cũ',
  VINTAGE: 'Đồ cổ',
};

export const CartPage: React.FC = () => {
  const { items, totalCount, removeFromCart, clearCart } = useCart();
  const navigate = useNavigate();

  // State: các sản phẩm được chọn (mặc định chọn tất cả)
  const [selectedProductIds, setSelectedProductIds] = useState<Set<number>>(() => new Set(items.map((i) => i.productId)));
  const [showMultiSellerNotice, setShowMultiSellerNotice] = useState(false);

  // Đồng bộ khi số lượng items thay đổi
  useEffect(() => {
    setSelectedProductIds((prev) => {
      const validIds = new Set<number>();
      for (const item of items) {
        if (prev.has(item.productId)) {
          validIds.add(item.productId);
        } else if (prev.size === 0) {
          validIds.add(item.productId);
        }
      }
      return validIds.size > 0 ? validIds : new Set(items.map((i) => i.productId));
    });
  }, [items]);

  const selectedItems = useMemo(
    () => items.filter((item) => selectedProductIds.has(item.productId)),
    [items, selectedProductIds]
  );

  const selectedCount = selectedItems.length;
  const selectedAmount = useMemo(
    () => selectedItems.reduce((sum, item) => sum + (item.price || 0), 0),
    [selectedItems]
  );

  const isAllSelected = totalCount > 0 && selectedCount === totalCount;

  const handleToggleSelectAll = () => {
    if (isAllSelected) {
      setSelectedProductIds(new Set());
    } else {
      setSelectedProductIds(new Set(items.map((i) => i.productId)));
    }
  };

  const handleToggleItem = (productId: number) => {
    setSelectedProductIds((prev) => {
      const next = new Set(prev);
      if (next.has(productId)) {
        next.delete(productId);
      } else {
        next.add(productId);
      }
      return next;
    });
  };

  const handleDeleteSelected = () => {
    if (selectedCount === 0) return;
    if (window.confirm(`Bạn có chắc muốn xóa ${selectedCount} món đồ đã chọn khỏi giỏ hàng?`)) {
      selectedItems.forEach((i) => removeFromCart(i.productId));
      setSelectedProductIds(new Set());
    }
  };

  const handleCheckoutSelected = () => {
    if (selectedCount === 0) return;
    if (selectedCount === 1) {
      navigate(`/checkout?productId=${selectedItems[0].productId}`);
    } else {
      // Khi chọn nhiều món khác nhau, thông báo cho người dùng
      setShowMultiSellerNotice(true);
    }
  };

  const formatPrice = (price: number) =>
    new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(price);

  return (
    <div className="og-cart-page" style={{ maxWidth: '1140px', margin: '0 auto', padding: '24px 16px 60px' }}>
      {/* Breadcrumb & Navigation */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.88rem', color: 'var(--og-color-text-muted)', marginBottom: '16px' }}>
        <Link to="/" style={{ color: 'var(--og-color-text-secondary)', textDecoration: 'none' }}>
          Trang chủ
        </Link>
        <span>/</span>
        <span style={{ color: 'var(--og-color-text-primary)', fontWeight: 600 }}>Giỏ hàng</span>
      </div>

      {/* Page Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 800, margin: '0 0 6px', color: 'var(--og-color-text-primary)', display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span> Giỏ Hàng Của Bạn</span>
            {totalCount > 0 && (
              <span style={{ fontSize: '0.9rem', fontWeight: 700, padding: '2px 10px', borderRadius: '12px', background: 'var(--og-color-primary-light)', color: 'var(--og-color-primary)' }}>
                {totalCount} món
              </span>
            )}
          </h1>
          <p style={{ margin: 0, color: 'var(--og-color-text-secondary)', fontSize: '0.92rem' }}>
            Quản lý những món đồ bạn đang dự định mua. Mỗi sản phẩm second-hand là độc bản!
          </p>
        </div>

        {/* Link to Orders (Đơn đã mua) to clearly distinguish */}
        <Link
          to="/orders"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '8px',
            padding: '8px 16px',
            borderRadius: 'var(--og-radius-md)',
            border: '1px solid var(--og-color-border)',
            background: 'var(--og-color-surface)',
            color: 'var(--og-color-text-primary)',
            fontSize: '0.88rem',
            fontWeight: 600,
            textDecoration: 'none',
            boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
          }}
        >
          <span><Icon name="package" style={{ width: '1em', height: '1em' }} /></span>
          <span>Xem các đơn đã mua</span>
        </Link>
      </div>

      {/* Escrow Guarantee Banner */}
      <div
        style={{
          background: 'linear-gradient(135deg, rgba(29, 77, 56, 0.08) 0%, rgba(212, 160, 23, 0.08) 100%)',
          border: '1px solid rgba(29, 77, 56, 0.2)',
          borderRadius: 'var(--og-radius-md)',
          padding: '14px 18px',
          marginBottom: '24px',
          display: 'flex',
          alignItems: 'center',
          gap: '12px',
        }}
      >
        <span style={{ fontSize: '1.5rem' }}><Icon name="shield" style={{ width: '1em', height: '1em' }} /></span>
        <div style={{ fontSize: '0.88rem', color: 'var(--og-color-text-primary)', lineHeight: 1.45 }}>
          <strong>Bảo vệ ký quỹ 100% O.G Escrow:</strong> Tiền của bạn chỉ được chuyển cho người bán sau khi bạn đã nhận hàng và xác nhận món đồ đúng mô tả.
        </div>
      </div>

      {totalCount === 0 ? (
        /* Empty State */
        <div
          style={{
            textAlign: 'center',
            padding: '80px 20px',
            background: 'var(--og-color-surface)',
            borderRadius: 'var(--og-radius-lg)',
            border: '1px dashed var(--og-color-border)',
          }}
        >
          <div style={{ fontSize: '4rem', marginBottom: '16px' }}><Icon name="cart" style={{ width: '1em', height: '1em' }} /></div>
          <h2 style={{ fontSize: '1.4rem', fontWeight: 700, margin: '0 0 8px', color: 'var(--og-color-text-primary)' }}>
            Giỏ hàng của bạn đang trống
          </h2>
          <p style={{ margin: '0 0 24px', color: 'var(--og-color-text-secondary)', fontSize: '0.95rem', maxWidth: '480px', marginLeft: 'auto', marginRight: 'auto' }}>
            Bạn chưa thêm món đồ nào vào giỏ. Hãy ghé qua Chợ Đồ Cũ để tìm kiếm những món đồ chất lượng với giá tốt!
          </p>
          <div style={{ display: 'flex', justifyContent: 'center', gap: '12px', flexWrap: 'wrap' }}>
            <Link
              to="/marketplace"
              className="og-button og-button--primary"
              style={{ textDecoration: 'none', padding: '10px 24px' }}
            >
               Khám phá Chợ Đồ Cũ
            </Link>
            <Link
              to="/orders"
              className="og-button og-button--ghost"
              style={{ textDecoration: 'none', padding: '10px 20px' }}
            >
               Xem đơn hàng đã mua
            </Link>
          </div>
        </div>
      ) : (
        /* Cart Contents */
        <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '24px' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 340px', gap: '24px', alignItems: 'start' }}>
            {/* Items Column */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {/* Select All & Batch Actions Bar */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '12px 16px',
                  background: 'var(--og-color-surface)',
                  borderRadius: 'var(--og-radius-md)',
                  border: '1px solid var(--og-color-border)',
                }}
              >
                <label style={{ display: 'flex', alignItems: 'center', gap: '10px', cursor: 'pointer', fontSize: '0.92rem', fontWeight: 600, color: 'var(--og-color-text-primary)' }}>
                  <input
                    type="checkbox"
                    checked={isAllSelected}
                    onChange={handleToggleSelectAll}
                    style={{ width: '18px', height: '18px', accentColor: 'var(--og-color-primary)', cursor: 'pointer' }}
                  />
                  <span>Chọn tất cả ({totalCount} món)</span>
                </label>

                {selectedCount > 0 && (
                  <button
                    type="button"
                    onClick={handleDeleteSelected}
                    style={{
                      background: 'none',
                      border: 'none',
                      color: '#ef4444',
                      fontSize: '0.85rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '4px',
                    }}
                  >
                    <span>Xóa các món đã chọn ({selectedCount})</span>
                  </button>
                )}
              </div>

              {items.map((item) => (
                <div
                  key={item.productId}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '16px',
                    padding: '16px',
                    background: 'var(--og-color-surface)',
                    borderRadius: 'var(--og-radius-md)',
                    border: selectedProductIds.has(item.productId)
                      ? '1px solid var(--og-color-primary)'
                      : '1px solid var(--og-color-border)',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.03)',
                    transition: 'border-color 0.15s ease',
                  }}
                >
                  {/* Item Selection Checkbox */}
                  <label style={{ display: 'flex', alignItems: 'center', cursor: 'pointer', flexShrink: 0 }}>
                    <input
                      type="checkbox"
                      checked={selectedProductIds.has(item.productId)}
                      onChange={() => handleToggleItem(item.productId)}
                      aria-label={`Chọn sản phẩm ${item.title}`}
                      style={{ width: '18px', height: '18px', accentColor: 'var(--og-color-primary)', cursor: 'pointer' }}
                    />
                  </label>

                  {/* Thumbnail */}
                  <Link
                    to={`/products/${item.productId}`}
                    style={{
                      width: '90px',
                      height: '90px',
                      borderRadius: 'var(--og-radius-sm)',
                      overflow: 'hidden',
                      flexShrink: 0,
                      background: 'var(--og-color-bg-subtle)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    {item.thumbnailUrl ? (
                      <img
                        src={item.thumbnailUrl}
                        alt={item.title}
                        style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                      />
                    ) : (
                      <span style={{ fontSize: '2rem' }}><Icon name="package" style={{ width: '1em', height: '1em' }} /></span>
                    )}
                  </Link>

                  {/* Details */}
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                      <span
                        style={{
                          fontSize: '0.75rem',
                          padding: '2px 6px',
                          borderRadius: '4px',
                          background: 'var(--og-color-primary-light)',
                          color: 'var(--og-color-primary)',
                          fontWeight: 600,
                        }}
                      >
                        {CONDITION_MAP[item.condition] || item.condition}
                      </span>
                      {item.location && (
                        <span style={{ fontSize: '0.78rem', color: 'var(--og-color-text-muted)' }}>
                           {item.location}
                        </span>
                      )}
                    </div>

                    <Link
                      to={`/products/${item.productId}`}
                      style={{
                        fontSize: '1rem',
                        fontWeight: 700,
                        color: 'var(--og-color-text-primary)',
                        textDecoration: 'none',
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                        marginBottom: '6px',
                      }}
                    >
                      {item.title}
                    </Link>

                    {item.sellerName && (
                      <div style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', marginBottom: '8px' }}>
                        Người bán: <strong>{item.sellerName}</strong>
                      </div>
                    )}

                    <div style={{ fontSize: '1.15rem', fontWeight: 800, color: 'var(--og-color-primary)' }}>
                      {formatPrice(item.price)}
                    </div>
                  </div>

                  {/* Item Actions */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', flexShrink: 0 }}>
                    <button
                      type="button"
                      className="og-button og-button--primary og-button--sm"
                      onClick={() => navigate(`/checkout?productId=${item.productId}`)}
                      style={{ whiteSpace: 'nowrap' }}
                    >
                       Mua ngay
                    </button>
                    <button
                      type="button"
                      className="og-button og-button--ghost og-button--sm"
                      onClick={() => removeFromCart(item.productId)}
                      style={{ color: '#ef4444', borderColor: 'transparent' }}
                      title="Xóa khỏi giỏ hàng"
                    >
                      ✕ Bỏ món này
                    </button>
                  </div>
                </div>
              ))}

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '8px' }}>
                <Link
                  to="/marketplace"
                  style={{ fontSize: '0.88rem', color: 'var(--og-color-primary)', textDecoration: 'none', fontWeight: 600 }}
                >
                  ← Tiếp tục tìm kiếm đồ cũ
                </Link>
                <button
                  type="button"
                  onClick={clearCart}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: 'var(--og-color-text-muted)',
                    fontSize: '0.85rem',
                    cursor: 'pointer',
                    textDecoration: 'underline',
                  }}
                >
                  Xóa tất cả ({totalCount})
                </button>
              </div>
            </div>

            {/* Cart Summary Card */}
            <div
              style={{
                background: 'var(--og-color-surface)',
                borderRadius: 'var(--og-radius-lg)',
                border: '1px solid var(--og-color-border)',
                padding: '20px',
                boxShadow: '0 4px 14px rgba(0,0,0,0.04)',
                position: 'sticky',
                top: '90px',
              }}
            >
              <h2 style={{ fontSize: '1.15rem', fontWeight: 700, margin: '0 0 16px', color: 'var(--og-color-text-primary)' }}>
                Tóm tắt giỏ hàng
              </h2>

              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '10px', fontSize: '0.9rem', color: 'var(--og-color-text-secondary)' }}>
                <span>Đã chọn:</span>
                <span style={{ fontWeight: 700, color: selectedCount > 0 ? 'var(--og-color-primary)' : 'var(--og-color-text-muted)' }}>
                  {selectedCount} / {totalCount} món đồ
                </span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '16px', fontSize: '0.9rem', color: 'var(--og-color-text-secondary)' }}>
                <span>Phí vận chuyển:</span>
                <span style={{ color: 'var(--og-color-text-muted)' }}>Tính khi thanh toán</span>
              </div>

              <div
                style={{
                  borderTop: '1px solid var(--og-color-border)',
                  paddingTop: '14px',
                  marginBottom: '20px',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'baseline',
                }}
              >
                <span style={{ fontSize: '1rem', fontWeight: 700, color: 'var(--og-color-text-primary)' }}>Tổng tiền tạm tính:</span>
                <span style={{ fontSize: '1.35rem', fontWeight: 800, color: 'var(--og-color-primary)' }}>
                  {formatPrice(selectedAmount)}
                </span>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                <button
                  type="button"
                  className="og-button og-button--primary"
                  disabled={selectedCount === 0}
                  onClick={handleCheckoutSelected}
                  style={{
                    width: '100%',
                    height: '46px',
                    fontSize: '1rem',
                    fontWeight: 700,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '8px',
                    boxShadow: selectedCount > 0 ? '0 4px 12px rgba(0,0,0,0.15)' : 'none',
                    cursor: selectedCount > 0 ? 'pointer' : 'not-allowed',
                    opacity: selectedCount > 0 ? 1 : 0.6,
                  }}
                >
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
                  </svg>
                  <span>Mua hàng ({selectedCount} món đã chọn)</span>
                </button>

                <p style={{ margin: 0, fontSize: '0.8rem', color: 'var(--og-color-text-muted)', lineHeight: 1.4 }}>
                   Lưu ý: Do mỗi món đồ cũ thuộc một người bán độc lập, bạn sẽ tiến hành thanh toán từng đơn hàng riêng biệt.
                </p>
                <Link
                  to="/marketplace"
                  className="og-button og-button--outline"
                  style={{ textDecoration: 'none', textAlign: 'center', width: '100%', boxSizing: 'border-box' }}
                >
                  Mua thêm đồ khác
                </Link>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Multi-Seller Sequential Checkout Notice Modal */}
      {showMultiSellerNotice && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 10000,
            backgroundColor: 'rgba(0,0,0,0.7)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '16px',
            backdropFilter: 'blur(4px)',
          }}
        >
          <div
            style={{
              background: 'var(--og-color-surface)',
              borderRadius: '16px',
              maxWidth: '480px',
              width: '100%',
              padding: '24px',
              border: '1px solid var(--og-color-border)',
              boxShadow: '0 20px 40px rgba(0,0,0,0.3)',
            }}
          >
            <h3 style={{ margin: '0 0 10px', fontSize: '1.2rem', fontWeight: 700, color: 'var(--og-color-text-primary)' }}>
              Thanh toán {selectedCount} món đồ đã chọn
            </h3>
            <p style={{ fontSize: '0.92rem', color: 'var(--og-color-text-secondary)', lineHeight: 1.5, margin: '0 0 16px' }}>
              Do mỗi món đồ cũ thuộc một người bán độc lập (với địa chỉ kho và phí giao hàng riêng), hệ thống sẽ mở thanh toán tuần tự cho từng món đồ.
            </p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '20px', maxHeight: '200px', overflowY: 'auto' }}>
              {selectedItems.map((item, idx) => (
                <div
                  key={item.productId}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '8px 12px',
                    borderRadius: '8px',
                    background: 'var(--og-color-surface-subtle)',
                    fontSize: '0.88rem',
                  }}
                >
                  <span style={{ fontWeight: 600, color: 'var(--og-color-text-primary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '280px' }}>
                    {idx + 1}. {item.title}
                  </span>
                  <span style={{ fontWeight: 700, color: 'var(--og-color-primary)', flexShrink: 0 }}>
                    {formatPrice(item.price)}
                  </span>
                </div>
              ))}
            </div>
            <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end' }}>
              <button
                type="button"
                className="og-button og-button--outline og-button--sm"
                onClick={() => setShowMultiSellerNotice(false)}
              >
                Đóng
              </button>
              <button
                type="button"
                className="og-button og-button--primary og-button--sm"
                onClick={() => {
                  setShowMultiSellerNotice(false);
                  navigate(`/checkout?productId=${selectedItems[0].productId}`);
                }}
              >
                Tiến hành mua món 1 →
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default CartPage;
