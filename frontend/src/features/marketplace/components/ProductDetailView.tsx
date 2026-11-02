import React, { useState, useContext } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { CONDITION_LABELS, ProductDetail } from '../types';
import { AuthContext } from '../../auth/context';
import { ConditionBadge } from '../../../shared/components';
import { ChatContext } from '../../chat-offer/chatContext';
import { Icon } from '../../../shared/components/Icon/Icon';
import { useCart } from '../../../shared/context';

interface ProductDetailViewProps {
  product: ProductDetail;
}

export const ProductDetailView: React.FC<ProductDetailViewProps> = ({ product }) => {
  const authContext = useContext(AuthContext);
  const isAuthenticated = Boolean(authContext?.isAuthenticated);
  const navigate = useNavigate();
  const chat = useContext(ChatContext);
  const { addToCart } = useCart();
  const productCategories = product.categories?.length ? product.categories : [product.category];

  const [activeMediaIndex, setActiveMediaIndex] = useState(0);
  const [showAuthModal, setShowAuthModal] = useState(false);
  const [authActionName, setAuthActionName] = useState<string>('');
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

  const hasAgreedPrice = typeof product.agreedPrice === 'number' && product.agreedPrice > 0;
  const formattedAgreedPrice = hasAgreedPrice
    ? `${new Intl.NumberFormat('vi-VN').format(product.agreedPrice!)} ₫`
    : null;

  const handleBuyNow = (useOffer = true) => {
    requireAuth('Mua ngay và Thanh toán Ký quỹ', () => {
      if (useOffer && hasAgreedPrice && product.acceptedOfferId) {
        navigate(`/checkout?productId=${product.productId}&offerId=${product.acceptedOfferId}`);
      } else {
        navigate(`/checkout?productId=${product.productId}`);
      }
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
      if (chat?.enabled) chat.open({ productId: product.productId }); else navigate(`/messages?productId=${product.productId}`);
    });
  };

  const handleOpenOffer = () => {
    requireAuth('Gửi đề xuất Trả giá món đồ này', () => {
      if (chat?.enabled) chat.open({ productId: product.productId, offer: true }); else navigate(`/messages?productId=${product.productId}&offer=1`);
    });
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
            border: '1px solid var(--og-color-success-border, var(--og-color-border))',
            color: 'var(--og-color-success, #065f46)',
            borderRadius: '8px',
            marginBottom: '16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <span>{cartSuccessMsg}</span>
          <Link to="/cart" style={{ fontWeight: 700, color: '#065f46', textDecoration: 'none' }}>
            Xem giỏ hàng →
          </Link>
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
                  color: 'var(--og-color-text-muted)',
                }}
                aria-hidden="true"
              >
                <span style={{ fontSize: '2.5rem' }}><Icon name="package" /></span>
                <span>Chưa có ảnh cho sản phẩm này</span>
              </div>
            )}

            {product.media.length > 0 && (
              <span className="og-product-detail__image-badge">
                {isCurrentVideo ? '1 Video cận cảnh' : `${activeMediaIndex + 1} / ${product.media.length} media`}
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
                    <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--og-color-text-primary)', color: '#fff', fontSize: '1.2rem' }}>

                    </div>
                  ) : (
                    <img src={media.mediaUrl} alt={`Ảnh nhỏ ${idx + 1}`} />
                  )}
                </button>
              ))}
            </div>
          )}

          {/* Utility Action Bar under Gallery (Oreka style with outline icons) */}
          <div className="og-product-detail__gallery-actions">
            <div>
              <Link
                to="/seller/listings/new"
                className="og-product-detail__action-link"
                style={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <circle cx="12" cy="12" r="10" />
                  <line x1="12" y1="8" x2="12" y2="16" />
                  <line x1="8" y1="12" x2="16" y2="12" />
                </svg>
                <span>Đăng bán đồ tương tự</span>
              </Link>
            </div>
            <div className="og-product-detail__action-buttons">
              <button
                type="button"
                className="og-product-detail__icon-btn"
                title="Báo cáo tin đăng vi phạm"
                onClick={() => alert('Đã ghi nhận yêu cầu báo cáo tin đăng vi phạm.')}
                style={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z" />
                  <line x1="4" y1="22" x2="4" y2="15" />
                </svg>
                <span>Báo cáo</span>
              </button>
              <button
                type="button"
                className="og-product-detail__icon-btn"
                title="Chia sẻ tin này"
                onClick={() => {
                  navigator.clipboard?.writeText(window.location.href);
                  alert('Đã sao chép liên kết sản phẩm vào bộ nhớ tạm!');
                }}
                style={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <circle cx="18" cy="5" r="3" />
                  <circle cx="6" cy="12" r="3" />
                  <circle cx="18" cy="19" r="3" />
                  <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
                  <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
                </svg>
                <span>Chia sẻ</span>
              </button>
              <button
                type="button"
                className="og-product-detail__icon-btn"
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '5px',
                  color: isFavorited ? '#ef4444' : undefined,
                }}
                title="Lưu tin yêu thích"
                onClick={() => setIsFavorited(!isFavorited)}
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill={isFavorited ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z" />
                </svg>
                <span>{isFavorited ? 'Đã lưu' : 'Lưu tin'}</span>
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
              <span
                style={{
                  fontSize: '0.75rem',
                  fontWeight: 600,
                  padding: '3px 10px',
                  borderRadius: '12px',
                  background: 'rgba(59, 130, 246, 0.12)',
                  color: 'var(--og-color-primary, #2563eb)',
                  border: '1px solid rgba(59, 130, 246, 0.35)',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '4px',
                }}
                title="Sản phẩm giá trị cao yêu cầu Người mua phải hoàn tất định danh eKYC trước khi đặt mua"
              >
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                </svg>
                <span>Yêu cầu Người mua eKYC</span>
              </span>
            )}
          </div>

          {/* Title */}
          <h1 className="og-product-detail__title">{product.title}</h1>

          {/* Price */}
          <div className={`og-product-detail__price-card${hasAgreedPrice ? ' og-product-detail__price-card--agreed' : ''}`}>
            {hasAgreedPrice ? (
              <div className="og-product-detail__agreed-box">
                <div className="og-product-detail__agreed-header">
                  <div className="og-product-detail__price-value og-product-detail__price-value--agreed">
                    {formattedAgreedPrice}
                  </div>
                  <span className="og-product-detail__agreed-badge">
                    Giá đã thỏa thuận
                  </span>
                </div>
                <div className="og-product-detail__agreed-original-row">
                  <span className="og-product-detail__agreed-original-label">Giá niêm yết:</span>
                  <del className="og-product-detail__agreed-original-val">{formattedPrice}</del>
                </div>
                <div className="og-product-detail__price-note og-product-detail__price-note--agreed">
                  Đề xuất trả giá #{product.acceptedOfferId} đã được người bán chấp thuận. Mức giá này chỉ áp dụng riêng cho tài khoản của bạn.
                </div>
              </div>
            ) : (
              <>
                <div className="og-product-detail__price-value">{formattedPrice}</div>
                <div className="og-product-detail__price-note">
                  Giá niêm yết do Người bán đưa ra (được bảo vệ bởi cơ chế Giữ tiền Escrow của O.G Shop).
                </div>
              </>
            )}
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
              <span style={{ color: 'var(--og-color-primary)', fontWeight: 600 }}>
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
            <span style={{ fontSize: '1.3rem', flexShrink: 0 }}><Icon name="shield" /></span>
            <div>
              <strong>Old but Gold cam kết:</strong> Nhận sản phẩm chuẩn như mô tả hoặc nhận hoàn tiền 100%. Tiền mua của bạn được bảo mật và tạm giữ an toàn trong suốt 48h kiểm tra hàng.
            </div>
          </div>

          {/* Compact Action Buttons Grid: 2 Rows (Thao tác mua hàng & Trao đổi) */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginTop: '16px' }}>
            {/* Hàng 1: Nút Mua hàng chính (Thêm vào giỏ 38% + Mua ngay 62%) */}
            <div style={{ display: 'grid', gridTemplateColumns: 'minmax(130px, 38%) 1fr', gap: '10px' }}>
              <button
                type="button"
                className="og-btn-add-cart"
                onClick={handleAddToCart}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '8px',
                  height: '46px',
                  borderRadius: '10px',
                  fontSize: '0.94rem',
                  fontWeight: 600,
                  padding: '0 12px',
                }}
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <circle cx="9" cy="21" r="1" />
                  <circle cx="20" cy="21" r="1" />
                  <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
                </svg>
                <span>Thêm vào giỏ</span>
              </button>

              <button
                type="button"
                className={`og-btn-buy-now${hasAgreedPrice ? ' og-btn-buy-now--agreed' : ''}`}
                onClick={() => handleBuyNow(true)}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '8px',
                  height: '46px',
                  borderRadius: '10px',
                  fontSize: '1rem',
                  fontWeight: 700,
                  padding: '0 16px',
                }}
                title={hasAgreedPrice ? `Mua ngay với giá thỏa thuận ${formattedAgreedPrice}` : 'Mua ngay'}
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
                </svg>
                <span>{hasAgreedPrice ? `Mua ngay (${formattedAgreedPrice})` : 'Mua ngay'}</span>
              </button>
            </div>

            {/* Hàng 2: Trao đổi & Thương lượng (Nhắn tin 50% + Trả giá 50%) */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
              <button
                type="button"
                className="og-btn-quick-chat"
                onClick={handleChat}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '8px',
                  height: '42px',
                  borderRadius: '10px',
                  fontSize: '0.88rem',
                  fontWeight: 600,
                  padding: '0 10px',
                  width: '100%',
                }}
                aria-label="Nhắn tin bảo mật với người bán"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                </svg>
                <span>Nhắn tin với shop</span>
              </button>

              <button
                type="button"
                className="og-btn-make-offer"
                onClick={handleOpenOffer}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '8px',
                  height: '42px',
                  borderRadius: '10px',
                  fontSize: '0.88rem',
                  fontWeight: 600,
                  padding: '0 10px',
                  margin: 0,
                  width: '100%',
                }}
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z" />
                  <circle cx="7" cy="7" r="1" fill="currentColor" />
                </svg>
                <span>{hasAgreedPrice ? 'Xem đề xuất trả giá' : 'Đề xuất Trả giá'}</span>
              </button>
            </div>
          </div>
        </section>
      </div>

      {/* 3. Middle Section: Seller Profile (Oreka Style) */}
      <section className="og-seller-section" aria-label="Thông tin người bán">
        <div className="og-seller-left">
          <div className="og-seller-avatar-wrap">
            <span>{product.seller.displayName.charAt(0).toUpperCase()}</span>
            {product.seller.trustLabel === 'Đã xác minh eKYC' && (
              <span className="og-seller-avatar-badge" title="Đã xác thực eKYC">Đã eKYC</span>
            )}
          </div>
          <div className="og-seller-info">
            <div className="og-seller-name-row" style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
              <span className="og-seller-name">{product.seller.displayName}</span>
              {product.seller.trustLabel === 'Đã xác minh eKYC' ? (
                <span
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '4px',
                    fontSize: '0.78rem',
                    fontWeight: 600,
                    color: '#059669',
                    backgroundColor: 'rgba(16, 185, 129, 0.1)',
                    border: '1px solid rgba(16, 185, 129, 0.3)',
                    padding: '2px 8px',
                    borderRadius: '12px',
                  }}
                  title="Người bán đã hoàn tất xác thực danh tính căn cước công dân (eKYC)"
                >
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                    <polyline points="9 12 11 14 15 10" />
                  </svg>
                  <span>Đã xác minh eKYC</span>
                </span>
              ) : (
                <span
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '4px',
                    fontSize: '0.78rem',
                    color: 'var(--og-color-text-secondary)',
                    backgroundColor: 'var(--og-color-surface-subtle)',
                    border: '1px solid var(--og-color-border)',
                    padding: '2px 8px',
                    borderRadius: '12px',
                  }}
                >
                  <span>Chưa xác minh eKYC</span>
                </span>
              )}
            </div>
            <div className="og-seller-stats">
              <span><Icon name="star" size={15} /> 5.0 (18 đánh giá)</span>
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
            <span className="og-seller-trust-icon"><Icon name="shield" /></span>
            <span className="og-seller-trust-text">Đáng tin cậy</span>
            <span>Xác thực CCCD</span>
          </div>
          <div className="og-seller-trust-item">
            <span className="og-seller-trust-icon"><Icon name="clock" /></span>
            <span className="og-seller-trust-text">Thành viên từ</span>
            <span>Năm 2024</span>
          </div>
          <div className="og-seller-trust-item">
            <span className="og-seller-trust-icon"><Icon name="message" /></span>
            <span className="og-seller-trust-text">Phản hồi nhanh</span>
            <span>100% trong 5 phút</span>
          </div>
          <div className="og-seller-trust-item">
            <span className="og-seller-trust-icon"><Icon name="truck" /></span>
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
              <span className="og-specs-value">{product.location}</span>
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
              <h3 className="og-modal-dialog__title">Đăng nhập để tiếp tục</h3>
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
                  color: 'var(--og-color-text-secondary)',
                }}
              >
                <strong>Lợi ích khi đăng nhập:</strong> Mọi giao dịch được bảo vệ bởi thanh toán giữ tiền Escrow, trò chuyện trực tiếp và mở khiếu nại nếu hàng không đúng mô tả.
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

    </article>
  );
};
