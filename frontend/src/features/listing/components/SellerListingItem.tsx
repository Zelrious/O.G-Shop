import React from 'react';
import { Link } from 'react-router-dom';
import { CONDITION_LABELS } from '../../marketplace/types';
import { LISTING_STATUS_LABELS, LISTING_STATUS_VARIANTS, SellerProductSummary } from '../types';
import { Badge } from '../../../shared/components';

interface SellerListingItemProps {
  product: SellerProductSummary;
  onPublish: (productId: number) => Promise<void>;
  onHide: (productId: number) => Promise<void>;
  isActionLoading: boolean;
}

export const SellerListingItem: React.FC<SellerListingItemProps> = ({
  product,
  onPublish,
  onHide,
  isActionLoading,
}) => {
  const formattedPrice = new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
  }).format(product.listedPrice);

  const statusVariant = LISTING_STATUS_VARIANTS[product.status] || 'neutral';
  const statusLabel = LISTING_STATUS_LABELS[product.status] || product.status;
  const conditionLabel = CONDITION_LABELS[product.condition] || product.condition;

  const canEdit = ['DRAFT', 'ACTIVE', 'HIDDEN'].includes(product.status);
  const canPublish = ['DRAFT', 'HIDDEN'].includes(product.status);
  const canHide = product.status === 'ACTIVE';

  const handleHideClick = () => {
    if (window.confirm(`Bạn có chắc chắn muốn ẩn tin đăng "${product.title}"? Người mua sẽ không còn thấy tin này trên chợ.`)) {
      onHide(product.productId);
    }
  };

  return (
    <div className="og-seller-listing-item">
      <div className="og-seller-listing-item__thumb-wrap">
        {product.thumbnailUrl ? (
          <img
            src={product.thumbnailUrl}
            alt={product.title}
            className="og-seller-listing-item__thumb"
          />
        ) : (
          <div className="og-seller-listing-item__thumb-placeholder" aria-hidden="true">
            📦
          </div>
        )}
      </div>

      <div className="og-seller-listing-item__info">
        <div className="og-seller-listing-item__header">
          <Badge variant={statusVariant}>{statusLabel}</Badge>
          <span className="og-seller-listing-item__category">{product.category.categoryName}</span>
          <span className="og-seller-listing-item__condition">({conditionLabel})</span>
        </div>

        <h3 className="og-seller-listing-item__title">{product.title}</h3>

        <div className="og-seller-listing-item__price-row">
          <span className="og-seller-listing-item__price">{formattedPrice}</span>
          <span className="og-seller-listing-item__price-note">Giá niêm yết</span>
          {product.location && (
            <span className="og-seller-listing-item__location">📍 {product.location}</span>
          )}
        </div>
      </div>

      <div className="og-seller-listing-item__actions">
        {canEdit && (
          <Link
            to={`/seller/listings/${product.productId}/edit`}
            className="og-button og-button--ghost og-button--sm"
          >
            ✏️ Sửa
          </Link>
        )}

        {canPublish && (
          <button
            type="button"
            className="og-button og-button--primary og-button--sm"
            onClick={() => onPublish(product.productId)}
            disabled={isActionLoading}
          >
            🚀 Đăng bán
          </button>
        )}

        {canHide && (
          <button
            type="button"
            className="og-button og-button--danger og-button--sm"
            onClick={handleHideClick}
            disabled={isActionLoading}
          >
            👁️ Ẩn tin
          </button>
        )}

        {product.status === 'ACTIVE' && (
          <Link
            to={`/products/${product.productId}`}
            className="og-button og-button--ghost og-button--sm"
            target="_blank"
            rel="noopener noreferrer"
          >
            🔗 Xem trang
          </Link>
        )}
      </div>
    </div>
  );
};
