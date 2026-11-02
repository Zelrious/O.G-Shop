import { Icon } from '../../../shared/components/Icon/Icon';
import React from 'react';
import { Link } from 'react-router-dom';
import { ProductSummary } from '../types';

interface ProductCardProps {
  product: ProductSummary;
}

// Concise Vietnamese condition labels matching modern second-hand platforms (Image 3)
const CONDITION_TAGS: Record<string, string> = {
  LIKE_NEW: 'Như mới',
  GOOD: 'Đã dùng tốt',
  FAIR: 'Có hao mòn',
  POOR: 'Cũ',
  FOR_PARTS: 'Rã xác',
};

export const ProductCard: React.FC<ProductCardProps> = ({ product }) => {
  // Format price matching Image 3: e.g. "3.000.000 đ"
  const formattedPrice = `${new Intl.NumberFormat('vi-VN').format(product.listedPrice)} đ`;

  // Check if buyer has an active accepted agreed price
  const hasAgreedPrice = typeof product.agreedPrice === 'number' && product.agreedPrice > 0;
  const formattedAgreedPrice = hasAgreedPrice
    ? `${new Intl.NumberFormat('vi-VN').format(product.agreedPrice!)} đ`
    : null;

  // Concise condition label
  const conditionText = CONDITION_TAGS[product.condition] || 'Đã dùng';

  // Format location to short city/province name (e.g. "Hà Nội", "TP. HCM")
  const shortLocation = React.useMemo(() => {
    if (!product.location) return 'Toàn quốc';
    const parts = product.location.split(',');
    const lastPart = parts[parts.length - 1].trim();
    if (lastPart.includes('Hồ Chí Minh')) return 'TP. HCM';
    return lastPart || 'Toàn quốc';
  }, [product.location]);

  return (
    <article className={`og-product-card${hasAgreedPrice ? ' og-product-card--agreed' : ''}`}>
      <Link
        to={`/products/${product.productId}`}
        className="og-product-card__link"
        aria-label={`Xem chi tiết ${product.title}`}
      >
        {/* 1:1 Aspect Ratio Image Container */}
        <div className="og-product-card__image-container">
          {product.thumbnailUrl ? (
            <img
              src={product.thumbnailUrl}
              alt={product.title}
              className="og-product-card__image"
              loading="lazy"
            />
          ) : (
            <div className="og-product-card__image-placeholder" aria-hidden="true">
              <span className="og-product-card__placeholder-icon"><Icon name="package" /></span>
              <span>Chưa có ảnh</span>
            </div>
          )}

          {hasAgreedPrice && (
            <span className="og-product-card__ribbon-agreed" title="Giá đã thương lượng dành riêng cho bạn">
              Đã thỏa thuận
            </span>
          )}

          {product.requiresBuyerEkyc && (
            <span
              style={{
                position: 'absolute',
                top: '8px',
                right: '8px',
                zIndex: 2,
                display: 'inline-flex',
                alignItems: 'center',
                gap: '4px',
                fontSize: '0.7rem',
                fontWeight: 600,
                color: '#1d4ed8',
                backgroundColor: 'rgba(239, 246, 255, 0.95)',
                border: '1px solid #bfdbfe',
                padding: '2px 7px',
                borderRadius: '6px',
                backdropFilter: 'blur(4px)',
                boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
              }}
              title="Món đồ này yêu cầu Người mua phải hoàn tất eKYC để đặt mua"
            >
              <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
              </svg>
              <span>Cần eKYC</span>
            </span>
          )}

          {/* Accessible Category Label */}
          {product.category && (
            <span className="og-visually-hidden">
              {(product.categories?.length ? product.categories : [product.category]).map((category) => category.categoryName).join(' · ')}
            </span>
          )}
        </div>

        {/* Card Body */}
        <div className="og-product-card__body">
          {/* Top Line: Seller / Shop Info */}
          <div className="og-product-card__seller" title={product.seller.displayName}>
            <span className="og-product-card__seller-name">
              {product.seller.displayName}
            </span>
            {product.seller.trustLabel === 'Đã xác minh eKYC' ? (
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '3px',
                  fontSize: '0.72rem',
                  fontWeight: 600,
                  color: '#059669',
                  backgroundColor: 'rgba(16, 185, 129, 0.1)',
                  border: '1px solid rgba(16, 185, 129, 0.3)',
                  padding: '1px 6px',
                  borderRadius: '10px',
                  lineHeight: 1.2,
                }}
                title="Người bán đã xác thực danh tính CCCD (eKYC)"
              >
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                  <polyline points="9 12 11 14 15 10" />
                </svg>
                <span>Đã eKYC</span>
              </span>
            ) : product.seller.trustLabel ? (
              <span
                style={{
                  fontSize: '0.72rem',
                  color: 'var(--og-color-text-muted)',
                }}
              >
                {product.seller.trustLabel}
              </span>
            ) : null}
          </div>

          {/* Title: Strict 2 lines clamp */}
          <h3 className="og-product-card__title" title={product.title}>
            {product.title}
          </h3>

          {/* Price */}
          <div className="og-product-card__price-row">
            {hasAgreedPrice ? (
              <div className="og-product-card__agreed-price-wrap">
                <div className="og-product-card__agreed-main">
                  <span className="og-product-card__price og-product-card__price--agreed">
                    {formattedAgreedPrice}
                  </span>
                  <span
                    className="og-product-card__agreed-badge"
                    title="Giá thương lượng đã được người bán chấp thuận cho riêng bạn"
                  >
                    Giá của bạn
                  </span>
                </div>
                <del className="og-product-card__price-original">{formattedPrice}</del>
              </div>
            ) : (
              <span className="og-product-card__price">{formattedPrice}</span>
            )}
          </div>

          {/* Bottom Footer: Location & Condition Pill */}
          <div className="og-product-card__footer">
            <span className="og-product-card__location" title={product.location || 'Toàn quốc'}>
              <svg
                width="12"
                height="12"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
                style={{ flexShrink: 0, opacity: 0.7 }}
              >
                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
                <circle cx="12" cy="10" r="3" />
              </svg>
              <span>{shortLocation}</span>
            </span>
            <span className="og-product-card__condition-pill">
              {conditionText}
            </span>
          </div>
        </div>
      </Link>
    </article>
  );
};
