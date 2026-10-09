import React, { useState, useContext } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { CONDITION_LABELS, ProductDetail } from '../types';
import { AuthContext } from '../../auth/context';
import { ConditionBadge, VerificationBadge } from '../../../shared/components';
import { useCart } from '../../../shared/context';

interface ProductDetailViewProps {
  product: ProductDetail;
}

export const ProductDetailView: React.FC<ProductDetailViewProps> = ({ product }) => {
  const authContext = useContext(AuthContext);
  const isAuthenticated = Boolean(authContext?.isAuthenticated);
  const navigate = useNavigate();
  const { addToCart } = useCart();
  const productCategories = product.categories?.length ? product.categories : [product.category];

  const [activeMediaIndex, setActiveMediaIndex] = useState(0);
  const [showAuthModal, setShowAuthModal] = useState(false);
  const [authActionName, setAuthActionName] = useState<string>('');
  const [offerModalOpen, setOfferModalOpen] = useState(false);
  const [offerPriceInput, setOfferPriceInput] = useState<string>('');
  const [offerSuccessMsg, setOfferSuccessMsg] = useState<string | null>(null);
  const [cartSuccessMsg, setCartSuccessMsg] = useState<string | null>(null);
  const [isFavorited, setIsFavorited] = useState(false);

  const formattedPrice = `${new Intl.NumberFormat('vi-VN').format(product.listedPrice)} đ`;
  const conditionText = CONDITION_LABELS[product.condition] || product.condition;

  const currentMedia = product.media.length > 0 ? product.media[activeMediaIndex] : null;
  const isCurrentVideo = currentMedia?.mediaType === 'VIDEO';
  const currentMediaUrl = currentMedia ? currentMedia.mediaUrl : product.thumbnailUrl;

  const formattedDate = new Date(product.createdAt).toLocaleDateString('vi-VN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });

  // Auth Guard Handler
  const requireAuth = (actionName: string, onAuthenticated: () => void) => {
    if (!isAuthenticated) {
      setAuthActionName(actionName);
      setShowAuthModal(true);
    } else {
      onAuthenticated();
    }
  };

  const handleBuyNow = () => {
    requireAuth('Mua ngay và Thanh toán Ký quỹ', () => {
      navigate(`/checkout?productId=${product.productId}`);
    });
  };

  const handleAddToCart = () => {
    requireAuth('Thêm sản phẩm vào giỏ hàng', () => {
      const result = addToCart({
        productId: product.productId,
        title: product.title,
        price: product.listedPrice,
        thumbnailUrl: product.thumbnailUrl || (product.media[0]?.mediaUrl ?? null),
        condition: product.condition,
        sellerId: product.seller.sellerId,
        sellerName: product.seller.displayName,
        location: product.location,
      });
      setCartSuccessMsg(result.message);
      setTimeout(() => setCartSuccessMsg(null), 4000);
    });
  };

  const handleChat = () => {
    requireAuth('Nhắn tin trao đổi với người bán', () => {
      navigate(`/messages?sellerId=${product.seller.sellerId}&productId=${product.productId}`);
    });
  };

  const handleOpenOffer = () => {
    requireAuth('Gửi đề xuất Trả giá món đồ này', () => {
      setOfferPriceInput(String(Math.round(product.listedPrice * 0.9)));
      setOfferModalOpen(true);
    });
  };

  const handleSubmitOffer = (e: React.FormEvent) => {
    e.preventDefault();
    setOfferModalOpen(false);
    setOfferSuccessMsg(`Đã gửi đề xuất trả giá ${new Intl.NumberFormat('vi-VN').format(Number(offerPriceInput))} đ tới ${product.seller.displayName}.`);
    setTimeout(() => setOfferSuccessMsg(null), 5000);
  };

  return (
    <article className="og-product-detail">
      {/* 1. Breadcrumb (Oreka style) */}
      <nav aria-label="Breadcrumb" className="og-product-detail__breadcrumb">
        <Link to="/">Trang chủ</Link>
        <span className="og-product-detail__breadcrumb-sep">&gt;</span>
        <Link to="/marketplace">Đồ cũ</Link>
        <span className="og-product-detail__breadcrumb-sep">&gt;</span>
        <Link to={`/marketplace?categoryId=${product.category.categoryId}`}>
          {product.category.categoryName}
        </Link>
        <span className="og-product-detail__breadcrumb-sep">&gt;</span>
        <span className="og-product-detail__breadcrumb-current" aria-current="page">
          {product.title}
        </span>
      </nav>

      {/* Notifications / Feedback Toasts */}
      {cartSuccessMsg && (
        <div
          style={{
            padding: '12px 20px',
            background: 'var(--og-color-success-bg, #ecfdf5)',
            border: '1px solid var(--og-color-success-border, #a7f3d0)',
            color: 'var(--og-color-success, #065f46)',
            borderRadius: '8px',
            marginBottom: '16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <span>🛒 {cartSuccessMsg}</span>
          <Link to="/cart" style={{ fontWeight: 700, color: '#065f46', textDecoration: 'none' }}>
            Xem giỏ hàng →
          </Link>
        </div>
      )}

      {offerSuccessMsg && (
        <div
          style={{
            padding: '12px 20px',
            background: '#f0f9ff',
            border: '1px solid #bae6fd',
            color: '#0369a1',
            borderRadius: '8px',
            marginBottom: '16px',
          }}
        >
          🏷️ {offerSuccessMsg}
        </div>
      )}

      {/* 2. Main Top Card: Gallery & Product Info (Oreka Layout) */}
      <div className="og-product-detail__main-card">
        {/* Left: Gallery & Photos */}
        <section className="og-product-detail__gallery" aria-label="Hình ảnh sản phẩm">
          <div className="og-product-detail__main-image-wrap">
            {isCurrentVideo && currentMedia ? (
              <video
                src={currentMedia.mediaUrl}
                controls
                className="og-product-detail__main-video"
                style={{ width: '100%', height: '100%', maxHeight: '420px', objectFit: 'contain', background: '#000', borderRadius: '8px' }}
              />
            ) : currentMediaUrl ? (
              <img
                src={currentMediaUrl}
                alt={product.title}
                className="og-product-detail__main-image"
              />
            ) : (
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  gap: '8px',
                  color: '#94a3b8',
                }}
                aria-hidden="true"
              >
                <span style={{ fontSize: '2.5rem' }}>📦</span>
                <span>Chưa có ảnh cho sản phẩm này</span>
              </div>
            )}

            {product.media.length > 0 && (
              <span className="og-product-detail__image-badge">
                {isCurrentVideo ? '🎥 1 Video cận cảnh' : `📷 ${activeMediaIndex + 1} / ${product.media.length} media`}
              </span>
            )}
          </div>

          {/* Thumbnails Row */}
          {product.media.length > 1 && (
            <div className="og-product-detail__thumbnails" role="tablist" aria-label="Danh sách ảnh & video">
              {product.media.map((media, idx) => (
                <button
                  key={media.mediaId}
                  type="button"
                  role="tab"
                  aria-selected={idx === activeMediaIndex}
                  aria-label={media.mediaType === 'VIDEO' ? 'Video cận cảnh' : `Ảnh ${idx + 1}`}
                  className={`og-product-detail__thumb-btn ${idx === activeMediaIndex ? 'og-product-detail__thumb-btn--active' : ''}`}
                  onClick={() => setActiveMediaIndex(idx)}
                  style={{ position: 'relative' }}
                >
                  {media.mediaType === 'VIDEO' ? (
                    <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#1e293b', color: '#fff', fontSize: '1.2rem' }}>
                      🎥
                    </div>
                  ) : (
                    <img src={media.mediaUrl} alt={`Ảnh nhỏ ${idx + 1}`} />
                  )}
                </button>
              ))}
            </div>
          )}

          {/* Utility Action Bar under Gallery (Oreka style) */}
          <div className="og-product-detail__gallery-actions">
            <div>
              <span>Bạn có sản phẩm tương tự? </span>
              <Link to="/seller/listings/new" className="og-product-detail__action-link">
                Đăng bán
              </Link>
            </div>
            <div className="og-product-detail__action-buttons">
              <button
                type="button"
                className="og-product-detail__icon-btn"
                title="Báo cáo tin đăng"
                onClick={() => alert('Đã ghi nhận yêu cầu báo cáo tin đăng vi phạm.')}
              >
                🚩 <span>Báo cáo</span>
              </button>
              <button
                type="button"
                className="og-product-detail__icon-btn"
                title="Chia sẻ tin này"
                onClick={() => {
                  navigator.clipboard?.writeText(window.location.href);
                  alert('Đã sao chép liên kết sản phẩm vào bộ nhớ tạm!');
                }}
              >
                🔗 <span>Chia sẻ</span>
              </button>
              <button
                type="button"
                className="og-product-detail__icon-btn"
                style={{ color: isFavorited ? '#ef4444' : undefined }}
                title="Lưu tin yêu thích"
                onClick={() => setIsFavorited(!isFavorited)}
              >
                <span>{isFavorited ? '❤️ Đã lưu' : '🤍 Lưu tin'}</span>
              </button>
            </div>
          </div>
        </section>

        {/* Right: Info & Actions */}
        <section className="og-product-detail__info" aria-label="Thông tin sản phẩm">
          {/* Badge row */}
          <div className="og-product-detail__badge-row" style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
            {productCategories.map((category) => (
              <Link key={category.categoryId} className="og-badge og-badge--neutral" to={`/marketplace?categoryId=${category.categoryId}`}>
                {category.categoryName}
              </Link>
            ))}
            <ConditionBadge condition={product.condition} />
            {product.requiresBuyerEkyc && (
              <span style={{ fontSize: '0.75rem', fontWeight: 600, padding: '3px 10px', borderRadius: '12px', background: 'rgba(59, 130, 246, 0.2)', color: '#60a5fa', border: '1px solid rgba(59, 130, 246, 0.4)' }}>
                🛡️ Yêu cầu xác thực eKYC
              </span>
            )}
          </div>

          {/* Title */}
          <h1 className="og-product-detail__title">{product.title}</h1>

          {/* Price */}
          <div className="og-product-detail__price-card">
            <div className="og-product-detail__price-value">{formattedPrice}</div>
            <div className="og-product-detail__price-note">
              Giá niêm yết do Người bán đưa ra (được bảo vệ bởi cơ chế Giữ tiền Escrow của O.G Shop).
            </div>
          </div>

          {/* Shipping Info (Oreka style) */}
          <div className="og-product-detail__shipping-box">
            <div className="og-product-detail__shipping-row">
              <span className="og-product-detail__shipping-label">Vận chuyển:</span>
              <span className="og-product-detail__shipping-val">
                Từ {product.location ? product.location.split(',').pop()?.trim() || product.location : 'Toàn quốc'} đến vị trí của bạn
              </span>
            </div>
            <div className="og-product-detail__shipping-row">
              <span className="og-product-detail__shipping-label">Phí ship:</span>
              <span style={{ color: '#059669', fontWeight: 600 }}>
                30.000 ₫ (Hỗ trợ đồng kiểm tận nơi trước khi thanh toán)
              </span>
            </div>
            <div className="og-product-detail__shipping-row">
              <span className="og-product-detail__shipping-label">Số lượng:</span>
              <span>1 sản phẩm duy nhất (Đồ sưu tầm / Đồ cũ)</span>
            </div>
          </div>

          {/* Escrow Guarantee Banner (Oreka green guarantee) */}
          <div className="og-product-detail__guarantee-box">
            <span style={{ fontSize: '1.3rem', flexShrink: 0 }}>🛡️</span>
            <div>
              <strong>Old but Gold cam kết:</strong> Nhận sản phẩm chuẩn như mô tả hoặc nhận hoàn tiền 100%. Tiền mua của bạn được bảo mật và tạm giữ an toàn trong suốt 48h kiểm tra hàng.
            </div>
          </div>

          {/* Contact Row (Bảo mật thông tin cá nhân & Trao đổi chính thức) */}
          <div className="og-quick-contact-row" aria-label="Liên hệ nhanh với người bán">
            <button
              type="button"
              className="og-btn-quick-chat"
              onClick={handleChat}
              style={{ width: '100%', justifyContent: 'center' }}
              aria-label="Nhắn tin trao đổi bảo mật với người bán"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" />
              </svg>
              <span>💬 Nhắn tin bảo mật với {product.seller.displayName}</span>
            </button>
          </div>

          {/* Primary Purchase Buttons (Add to cart & Buy now) */}
          <div className="og-purchase-row">
            <button
              type="button"
              className="og-btn-add-cart"
              onClick={handleAddToCart}
            >
              <span>🛒</span>
              <span>Thêm vào giỏ</span>
            </button>

            <button
              type="button"
              className="og-btn-buy-now"
              onClick={handleBuyNow}
            >
              <span>⚡ Mua ngay</span>
            </button>
          </div>

          {/* Make Offer Button */}
          <button
            type="button"
            className="og-btn-make-offer"
            onClick={handleOpenOffer}
          >
            <span>🏷️</span>
            <span>Đề xuất Trả giá (Make an Offer)</span>
          </button>
        </section>
      </div>

      {/* 3. Middle Section: Seller Profile (Oreka Style) */}
      <section className="og-seller-section" aria-label="Thông tin người bán">
        <div className="og-seller-left">
          <div className="og-seller-avatar-wrap">
            <span>{product.seller.displayName.charAt(0).toUpperCase()}</span>
            <span className="og-seller-avatar-badge">Đã xác minh</span>
          </div>
          <div className="og-seller-info">
            <div className="og-seller-name-row">
              <span className="og-seller-name">{product.seller.displayName}</span>
              <VerificationBadge status="VERIFIED" showLabel={false} />
            </div>
            <div className="og-seller-stats">
              <span>⭐ 5.0 (18 đánh giá)</span>
              <span>•</span>
              <span>12 sản phẩm đang bán</span>
              <span>•</span>
              <span>8 đã giao dịch</span>
            </div>
          </div>
        </div>

        {/* Trust statistics row */}
        <div className="og-seller-trust-grid">
          <div className="og-seller-trust-item">
            <span className="og-seller-trust-icon">🛡️</span>
            <span className="og-seller-trust-text">Đáng tin cậy</span>
            <span>Xác thực CCCD</span>
          </div>
          <div className="og-seller-trust-item">
            <span className="og-seller-trust-icon">📅</span>
            <span className="og-seller-trust-text">Thành viên từ</span>
            <span>Năm 2024</span>
          </div>
          <div className="og-seller-trust-item">
            <span className="og-seller-trust-icon">💬</span>
            <span className="og-seller-trust-text">Phản hồi nhanh</span>
            <span>100% trong 5 phút</span>
          </div>
          <div className="og-seller-trust-item">
            <span className="og-seller-trust-icon">🚚</span>
            <span className="og-seller-trust-text">Giao hàng nhanh</span>
            <span>Gửi bưu cục 24h</span>
          </div>
        </div>
      </section>

      {/* 4. Specifications Card (Thông tin nổi bật - Oreka style) */}
      <section className="og-specs-card" aria-label="Thông tin chi tiết đồ cũ">
        <h2 className="og-specs-title">Thông tin nổi bật</h2>
        <div className="og-specs-table">
          <div className="og-specs-row">
            <span className="og-specs-label">Danh mục</span>
            <span className="og-specs-value">{productCategories.map((category) => category.categoryName).join(' · ')}</span>
          </div>
          <div className="og-specs-row">
            <span className="og-specs-label">Phí vận chuyển</span>
            <span className="og-specs-value">Người mua trả (hỗ trợ đồng kiểm tận tay)</span>
          </div>
          <div className="og-specs-row">
            <span className="og-specs-label">Tình trạng món đồ</span>
            <span className="og-specs-value" style={{ fontWeight: 600, color: '#e25b29' }}>
              {conditionText}
            </span>
          </div>
          {product.usageDuration && (
            <div className="og-specs-row">
              <span className="og-specs-label">Thời gian đã sử dụng</span>
              <span className="og-specs-value">{product.usageDuration}</span>
            </div>
          )}
          {product.defects && (
            <div className="og-specs-row">
              <span className="og-specs-label">Khuyết điểm / Hao mòn</span>
              <span className="og-specs-value" style={{ color: '#b91c1c' }}>
                {product.defects}
              </span>
            </div>
          )}
          {product.repairHistory && (
            <div className="og-specs-row">
              <span className="og-specs-label">Lịch sử sửa chữa</span>
              <span className="og-specs-value">{product.repairHistory}</span>
            </div>
          )}
          {product.includedAccessories && (
            <div className="og-specs-row">
              <span className="og-specs-label">Phụ kiện kèm theo</span>
              <span className="og-specs-value">{product.includedAccessories}</span>
            </div>
          )}
          {product.location && (
            <div className="og-specs-row">
              <span className="og-specs-label">Khu vực đăng bán</span>
              <span className="og-specs-value">📍 {product.location}</span>
            </div>
          )}
          <div className="og-specs-row">
            <span className="og-specs-label">Ngày đăng bán</span>
            <span className="og-specs-value">{formattedDate}</span>
          </div>
        </div>
      </section>

      {/* 5. Description Card */}
      <section className="og-desc-card" aria-label="Mô tả sản phẩm">
        <h2>Mô tả sản phẩm</h2>
        <div className="og-desc-text">
          {product.description}
        </div>
      </section>

      {/* 6. Guest Auth Guard Modal */}
      {showAuthModal && (
        <div
          className="og-modal-backdrop"
          role="dialog"
          aria-modal="true"
          onClick={() => setShowAuthModal(false)}
        >
          <div className="og-modal-dialog" onClick={(e) => e.stopPropagation()}>
            <div className="og-modal-dialog__header">
              <h3 className="og-modal-dialog__title">🔒 Đăng nhập để tiếp tục</h3>
            </div>
            <div className="og-modal-dialog__body">
              <p style={{ margin: '0 0 12px' }}>
                Bạn đang duyệt sàn dưới tư cách <strong>Khách</strong>. Để <strong>{authActionName}</strong>, vui lòng đăng nhập tài khoản Old but Gold.
              </p>
              <div
                style={{
                  background: 'var(--og-color-surface-subtle, #f8fafc)',
                  padding: '12px',
                  borderRadius: '8px',
                  fontSize: '0.86rem',
                  color: '#475569',
                }}
              >
                🛡️ <strong>Lợi ích khi đăng nhập:</strong> Mọi giao dịch được bảo vệ bởi thanh toán giữ tiền Escrow, trò chuyện trực tiếp và mở khiếu nại nếu hàng không đúng mô tả.
              </div>
            </div>
            <div className="og-modal-dialog__footer" style={{ justifyContent: 'space-between' }}>
              <button
                type="button"
                className="og-button og-button--ghost og-button--sm"
                onClick={() => setShowAuthModal(false)}
              >
                Để sau
              </button>
              <div style={{ display: 'flex', gap: '8px' }}>
                <button
                  type="button"
                  className="og-button og-button--outline og-button--sm"
                  onClick={() => navigate('/register')}
                >
                  Đăng ký
                </button>
                <button
                  type="button"
                  className="og-button og-button--primary og-button--sm"
                  onClick={() => navigate(`/login?redirect=/products/${product.productId}`)}
                >
                  Đăng nhập ngay →
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 7. Modal Trả Giá (Make Offer CH-03) */}
      {offerModalOpen && (
        <div
          className="og-modal-backdrop"
          role="dialog"
          aria-modal="true"
          onClick={() => setOfferModalOpen(false)}
        >
          <div className="og-modal-dialog" onClick={(e) => e.stopPropagation()}>
            <form onSubmit={handleSubmitOffer}>
              <div className="og-modal-dialog__header">
                <h3 className="og-modal-dialog__title">🏷️ Đề xuất Trả giá món đồ</h3>
              </div>
              <div className="og-modal-dialog__body">
                <p style={{ margin: '0 0 12px', fontSize: '0.9rem', color: '#64748b' }}>
                  Gửi mức giá bạn mong muốn tới <strong>{product.seller.displayName}</strong>. Nếu người bán chấp nhận, bạn sẽ được mua với mức giá này.
                </p>

                <div style={{ marginBottom: '16px' }}>
                  <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '6px' }}>
                    Giá niêm yết hiện tại:
                  </label>
                  <div style={{ fontSize: '1.2rem', fontWeight: 700, color: '#e25b29' }}>
                    {formattedPrice}
                  </div>
                </div>

                <div style={{ marginBottom: '16px' }}>
                  <label htmlFor="offer-price-input" style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '6px' }}>
                    Mức giá đề xuất của bạn (VNĐ):
                  </label>
                  <input
                    id="offer-price-input"
                    type="number"
                    className="og-input"
                    value={offerPriceInput}
                    onChange={(e) => setOfferPriceInput(e.target.value)}
                    min="10000"
                    max={product.listedPrice}
                    required
                    style={{ fontSize: '1.1rem', fontWeight: 700 }}
                  />
                  <span style={{ fontSize: '0.78rem', color: '#94a3b8', marginTop: '4px', display: 'block' }}>
                    💡 Gợi ý: Các đề xuất trong khoảng 85% - 95% giá niêm yết thường dễ được chấp nhận.
                  </span>
                </div>
              </div>
              <div className="og-modal-dialog__footer">
                <button
                  type="button"
                  className="og-button og-button--ghost og-button--sm"
                  onClick={() => setOfferModalOpen(false)}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  className="og-button og-button--primary og-button--sm"
                  style={{ background: '#d97706', borderColor: '#d97706' }}
                >
                  Gửi đề xuất trả giá
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </article>
  );
};
