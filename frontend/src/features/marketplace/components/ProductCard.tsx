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
    <article className="og-product-card">
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
              <span className="og-product-card__placeholder-icon">📦</span>
              <span>Chưa có ảnh</span>
            </div>
          )}

          {/* Accessible Category Label */}
          {product.category && (
            <span className="og-visually-hidden">
              {product.category.categoryName}
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
            {product.seller.trustLabel && (
              <span
                className="og-product-card__verified-badge"
                title={product.seller.trustLabel}
              >
                <svg
                  width="13"
                  height="13"
                  viewBox="0 0 24 24"
                  fill="#0284c7"
                  aria-hidden="true"
                >
                  <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" />
                </svg>
                <span className="og-visually-hidden">
                  {product.seller.trustLabel}
                </span>
              </span>
            )}
          </div>

          {/* Title: Strict 2 lines clamp */}
          <h3 className="og-product-card__title" title={product.title}>
            {product.title}
          </h3>

          {/* Price */}
          <div className="og-product-card__price-row">
            <span className="og-product-card__price">{formattedPrice}</span>
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
