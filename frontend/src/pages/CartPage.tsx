import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useCart } from '../shared/context';

const CONDITION_MAP: Record<string, string> = {
  LIKE_NEW: '✨ Như mới',
  GOOD: '👌 Khá tốt',
  FAIR: '👍 Chấp nhận được',
  POOR: '⚠️ Kém',
  VINTAGE: '🏺 Đồ cổ',
};

export const CartPage: React.FC = () => {
  const { items, totalCount, totalAmount, removeFromCart, clearCart } = useCart();
  const navigate = useNavigate();

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
            <span>🛒 Giỏ Hàng Của Bạn</span>
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
          <span>📦</span>
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
        <span style={{ fontSize: '1.5rem' }}>🛡️</span>
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
          <div style={{ fontSize: '4rem', marginBottom: '16px' }}>🛒</div>
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
              🔍 Khám phá Chợ Đồ Cũ
            </Link>
            <Link
              to="/orders"
              className="og-button og-button--ghost"
              style={{ textDecoration: 'none', padding: '10px 20px' }}
            >
              📦 Xem đơn hàng đã mua
            </Link>
          </div>
        </div>
      ) : (
        /* Cart Contents */
        <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '24px' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 340px', gap: '24px', alignItems: 'start' }}>
            {/* Items Column */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
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
                    border: '1px solid var(--og-color-border)',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.03)',
                  }}
                >
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
                      <span style={{ fontSize: '2rem' }}>📦</span>
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
                          📍 {item.location}
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
                      ⚡ Mua ngay
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
                <span>Tổng số lượng:</span>
                <span style={{ fontWeight: 600, color: 'var(--og-color-text-primary)' }}>{totalCount} món đồ</span>
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
                  {formatPrice(totalAmount)}
                </span>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                <p style={{ margin: 0, fontSize: '0.8rem', color: 'var(--og-color-text-muted)', lineHeight: 1.4 }}>
                  💡 Lưu ý: Do mỗi món đồ cũ thuộc một người bán khác nhau, bạn sẽ tiến hành thanh toán từng đơn hàng riêng biệt.
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
    </div>
  );
};

export default CartPage;
