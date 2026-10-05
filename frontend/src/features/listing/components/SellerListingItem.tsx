import React, { useState, useRef, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { CONDITION_LABELS } from '../../marketplace/types';
import { LISTING_STATUS_LABELS, LISTING_STATUS_VARIANTS, SellerProductSummary } from '../types';
import { Badge } from '../../../shared/components';

interface SellerListingItemProps {
  product: SellerProductSummary;
  onPublish: (productId: number) => Promise<void>;
  onHide: (productId: number) => Promise<void>;
  onSubmitForReview?: (productId: number) => Promise<void>;
  isActionLoading: boolean;
}

export const SellerListingItem: React.FC<SellerListingItemProps> = ({
  product,
  onPublish,
  onHide,
  onSubmitForReview,
  isActionLoading,
}) => {
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setIsMenuOpen(false);
      }
    };
    if (isMenuOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isMenuOpen]);

  const formattedPrice = new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
  }).format(product.listedPrice);

  const statusVariant = LISTING_STATUS_VARIANTS[product.status] || 'neutral';
  const statusLabel = LISTING_STATUS_LABELS[product.status] || product.status;
  const conditionLabel = CONDITION_LABELS[product.condition] || product.condition;

  const canEdit = ['DRAFT', 'ACTIVE', 'HIDDEN', 'REJECTED'].includes(product.status);
  const canPublish = product.status === 'HIDDEN';
  const canHide = product.status === 'ACTIVE';
  const canSubmit = ['DRAFT', 'HIDDEN', 'REJECTED'].includes(product.status);

  const handleHideClick = () => {
    setIsMenuOpen(false);
    if (window.confirm(`Bạn có chắc chắn muốn ẩn tin đăng "${product.title}"? Người mua sẽ không còn thấy tin này trên chợ.`)) {
      onHide(product.productId);
    }
  };

  const categoriesList = (product.categories?.length ? product.categories : [product.category])
    .map((c) => c.categoryName)
    .join(' · ');

  return (
    <article className="og-seller-listing-item" aria-labelledby={`product-title-${product.productId}`}>
      <div className="og-seller-listing-item__thumb-wrap">
        {product.thumbnailUrl ? (
          <img
            src={product.thumbnailUrl}
            alt={product.title}
            className="og-seller-listing-item__thumb"
            loading="lazy"
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
          {product.requiresBuyerEkyc && (
            <span
              style={{
                fontSize: '0.75rem',
                fontWeight: 600,
                padding: '2px 8px',
                borderRadius: '12px',
                background: '#e0f2fe',
                color: '#0284c7',
                border: '1px solid #bae6fd',
              }}
            >
              🛡️ Yêu cầu eKYC
            </span>
          )}
          <span className="og-seller-listing-item__category">{categoriesList}</span>
          <span className="og-seller-listing-item__condition">({conditionLabel})</span>
        </div>

        <h3 id={`product-title-${product.productId}`} className="og-seller-listing-item__title">
          {product.title}
        </h3>

        <div className="og-seller-listing-item__price-row">
          <span className="og-seller-listing-item__price">{formattedPrice}</span>
          <span className="og-seller-listing-item__price-note">Giá niêm yết</span>
          {product.location && (
            <span className="og-seller-listing-item__location">📍 {product.location}</span>
          )}
        </div>

        {product.status === 'PENDING' && (
          <div className="og-seller-listing-item__alert og-seller-listing-item__alert--pending">
            <span aria-hidden="true">ⓘ</span>
            <span>Tin đang được KTV kiểm duyệt nội dung và video cận cảnh. Bạn sẽ nhận được thông báo khi hoàn tất.</span>
          </div>
        )}

        {product.status === 'REJECTED' && (
          <div className="og-seller-listing-item__alert og-seller-listing-item__alert--rejected">
            <span aria-hidden="true">⚠️</span>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
              <div>
                <strong>Lý do từ chối kiểm duyệt: </strong>
                {product.rejectionReason ? (
                  <span>&ldquo;{product.rejectionReason}&rdquo;</span>
                ) : (
                  <span style={{ fontStyle: 'italic', opacity: 0.8 }}>(chưa có chi tiết lý do từ KTV)</span>
                )}
              </div>
              {product.reviewedAt && (
                <div style={{ fontSize: '0.8rem', opacity: 0.8 }}>
                  Thời gian xét duyệt: {new Date(product.reviewedAt).toLocaleString('vi-VN')}
                </div>
              )}
              <div style={{ fontSize: '0.85rem', marginTop: '2px' }}>
                Vui lòng bấm &ldquo;Sửa&rdquo; để cập nhật lại thông tin/video cận cảnh và gửi duyệt lại.
              </div>
            </div>
          </div>
        )}
      </div>

      <div className="og-seller-listing-item__actions">
        {canEdit && (
          <Link
            to={`/seller/listings/${product.productId}/edit`}
            className="og-button og-button--outline og-button--sm"
          >
            ✏️ Sửa
          </Link>
        )}

        {product.status === 'ACTIVE' && (
          <Link
            to={`/products/${product.productId}`}
            className="og-button og-button--ghost og-button--sm"
            target="_blank"
            rel="noopener noreferrer"
          >
            👁️ Xem
          </Link>
        )}

        {canSubmit && onSubmitForReview && (
          <button
            type="button"
            className="og-button og-button--primary og-button--sm"
            onClick={() => onSubmitForReview(product.productId)}
            disabled={isActionLoading}
          >
            📤 Gửi duyệt
          </button>
        )}

        {canPublish && (
          <button
            type="button"
            className="og-button og-button--primary og-button--sm"
            onClick={() => onPublish(product.productId)}
            disabled={isActionLoading}
          >
            🚀 Đăng lại
          </button>
        )}

        {/* More actions dropdown for secondary operations like Hide */}
        {canHide && (
          <div className="og-seller-actions-dropdown-wrap" ref={menuRef}>
            <button
              type="button"
              className="og-button og-button--ghost og-button--sm"
              onClick={() => setIsMenuOpen(!isMenuOpen)}
              aria-label="Thao tác khác"
              aria-haspopup="true"
              aria-expanded={isMenuOpen}
              style={{ padding: '4px 8px', minWidth: '32px' }}
            >
              •••
            </button>

            {isMenuOpen && (
              <div className="og-seller-dropdown-menu" role="menu">
                <button
                  type="button"
                  className="og-seller-dropdown-item og-seller-dropdown-item--danger"
                  role="menuitem"
                  onClick={handleHideClick}
                  disabled={isActionLoading}
                >
                  <span>👁️</span>
                  <span>Ẩn tin đăng</span>
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </article>
  );
};
