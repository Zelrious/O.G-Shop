import React, { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import {
  publicSellerApi,
  PublicSellerProfile,
  PublicSellerProductSummary,
  PublicSellerReviewItem,
} from '../features/seller/publicSellerApi';
import { useAuth } from '../features/auth';
import { resolveReturnMediaUrl } from '../features/disputes/mediaUrl';
import { Alert, Button } from '../shared/components';

const CONDITION_LABELS: Record<string, string> = {
  LIKE_NEW: 'Như mới',
  GOOD: 'Tốt',
  FAIR: 'Khá',
  POOR: 'Cũ',
  FOR_PARTS: 'Lấy linh kiện',
};

const formatVnd = (amount: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);

const formatDate = (iso?: string | null) => {
  if (!iso) return '—';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleDateString('vi-VN');
};

export const PublicSellerProfilePage: React.FC = () => {
  const { sellerId } = useParams<{ sellerId: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const { user } = useAuth();

  const activeTab: 'products' | 'reviews' =
    searchParams.get('tab') === 'reviews' ? 'reviews' : 'products';

  const [profile, setProfile] = useState<PublicSellerProfile | null>(null);
  const [loadingProfile, setLoadingProfile] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [profileError, setProfileError] = useState<string | null>(null);

  // Products tab state
  const [products, setProducts] = useState<PublicSellerProductSummary[]>([]);
  const [productsPage, setProductsPage] = useState(0);
  const [productsTotalPages, setProductsTotalPages] = useState(0);
  const [productsTotal, setProductsTotal] = useState(0);
  const [productsSort, setProductsSort] = useState<'newest' | 'price_asc' | 'price_desc'>('newest');
  const [loadingProducts, setLoadingProducts] = useState(false);
  const [productsError, setProductsError] = useState<string | null>(null);

  // Reviews tab state
  const [reviews, setReviews] = useState<PublicSellerReviewItem[]>([]);
  const [reviewsPage, setReviewsPage] = useState(0);
  const [reviewsTotalPages, setReviewsTotalPages] = useState(0);
  const [reviewsTotal, setReviewsTotal] = useState(0);
  const [ratingFilter, setRatingFilter] = useState<number | null>(null);
  const [loadingReviews, setLoadingReviews] = useState(false);
  const [reviewsError, setReviewsError] = useState<string | null>(null);

  useEffect(() => {
    if (!sellerId) return;
    let ignore = false;
    setLoadingProfile(true);
    setNotFound(false);
    setProfileError(null);
    setProductsPage(0);
    setReviewsPage(0);
    setRatingFilter(null);

    publicSellerApi
      .getPublicProfile(sellerId)
      .then((data) => {
        if (!ignore) {
          setProfile(data);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          if (err instanceof Error && err.message === 'SELLER_PROFILE_NOT_FOUND') {
            setNotFound(true);
          } else {
            setProfileError(err instanceof Error ? err.message : 'Không thể tải hồ sơ người bán.');
          }
        }
      })
      .finally(() => {
        if (!ignore) setLoadingProfile(false);
      });

    return () => {
      ignore = true;
    };
  }, [sellerId]);

  useEffect(() => {
    if (!sellerId || !profile || activeTab !== 'products') return;
    let ignore = false;
    setLoadingProducts(true);
    setProductsError(null);

    publicSellerApi
      .getPublicProducts(sellerId, productsPage, 12, productsSort)
      .then((res) => {
        if (!ignore) {
          setProducts(res.content || []);
          setProductsTotalPages(res.totalPages || 0);
          setProductsTotal(res.totalElements || 0);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          setProductsError(err instanceof Error ? err.message : 'Không thể tải danh sách sản phẩm.');
        }
      })
      .finally(() => {
        if (!ignore) setLoadingProducts(false);
      });

    return () => {
      ignore = true;
    };
  }, [sellerId, profile, activeTab, productsPage, productsSort]);

  useEffect(() => {
    if (!sellerId || !profile || activeTab !== 'reviews') return;
    let ignore = false;
    setLoadingReviews(true);
    setReviewsError(null);

    publicSellerApi
      .getPublicReviews(sellerId, reviewsPage, 10, ratingFilter)
      .then((res) => {
        if (!ignore) {
          setReviews(res.content || []);
          setReviewsTotalPages(res.totalPages || 0);
          setReviewsTotal(res.totalElements || 0);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          setReviewsError(err instanceof Error ? err.message : 'Không thể tải danh sách đánh giá.');
        }
      })
      .finally(() => {
        if (!ignore) setLoadingReviews(false);
      });

    return () => {
      ignore = true;
    };
  }, [sellerId, profile, activeTab, reviewsPage, ratingFilter]);

  const switchTab = (tab: 'products' | 'reviews') => {
    setSearchParams(tab === 'reviews' ? { tab: 'reviews' } : {});
  };

  if (loadingProfile) {
    return (
      <div style={{ maxWidth: 1080, margin: '48px auto', textAlign: 'center', padding: '0 16px' }}>
        <div className="og-spinner" style={{ margin: '0 auto 12px' }} />
        <p style={{ color: 'var(--og-color-text-secondary)' }}>Đang tải hồ sơ người bán...</p>
      </div>
    );
  }

  if (notFound) {
    return (
      <div style={{ maxWidth: 640, margin: '64px auto', textAlign: 'center', padding: '32px 24px', background: 'var(--og-color-surface)', borderRadius: 16, border: '1px solid var(--og-color-border)' }}>
        <div style={{ fontSize: '2.5rem', marginBottom: 12 }}>🏪</div>
        <h1 style={{ fontSize: '1.4rem', margin: '0 0 8px', color: 'var(--og-color-text-primary)' }}>
          Hồ sơ người bán không khả dụng
        </h1>
        <p style={{ color: 'var(--og-color-text-secondary)', marginBottom: 24 }}>
          Gian hàng này không tồn tại hoặc hiện không thể hiển thị công khai.
        </p>
        <Link to="/marketplace" className="og-button og-button--primary">
          Khám phá Chợ O.G
        </Link>
      </div>
    );
  }

  if (profileError || !profile) {
    return (
      <div style={{ maxWidth: 640, margin: '48px auto', padding: '0 16px' }}>
        <Alert type="danger">{profileError || 'Không thể tải hồ sơ người bán.'}</Alert>
      </div>
    );
  }

  const isOwnPreview = Boolean(user && Number(user.userId) === profile.sellerId);
  const avatarSrc = profile.avatarUrl ? resolveReturnMediaUrl(profile.avatarUrl) : null;
  const monogram = (profile.displayName || 'S').trim().charAt(0).toUpperCase();

  return (
    <div style={{ maxWidth: 1100, margin: '24px auto 64px', padding: '0 16px' }}>
      {isOwnPreview && (
        <div
          style={{
            background: '#eff6ff',
            border: '1px solid #bfdbfe',
            borderRadius: 10,
            padding: '10px 16px',
            marginBottom: 16,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 10,
            fontSize: '0.88rem',
            color: '#1e40af',
          }}
        >
          <span>👁️ Bạn đang xem trước <strong>Hồ sơ người bán công khai</strong> của mình (giống góc nhìn của Khách/Người mua).</span>
          <Link to="/seller/settings" style={{ fontWeight: 600, color: '#1d4ed8', textDecoration: 'underline' }}>
            Quay lại Thiết lập gian hàng
          </Link>
        </div>
      )}

      {/* Header Card */}
      <section
        aria-label="Thông tin công khai của người bán"
        style={{
          background: 'var(--og-color-surface)',
          border: '1px solid var(--og-color-border)',
          borderRadius: 16,
          padding: 24,
          marginBottom: 20,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 20,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 18 }}>
          {avatarSrc ? (
            <img
              data-testid="public-seller-avatar-img"
              src={avatarSrc}
              alt={profile.displayName}
              style={{
                width: 72,
                height: 72,
                borderRadius: '50%',
                objectFit: 'cover',
                border: '2px solid var(--og-color-primary)',
              }}
            />
          ) : (
            <div
              data-testid="public-seller-avatar-monogram"
              style={{
                width: 72,
                height: 72,
                borderRadius: '50%',
                background: 'var(--og-color-primary)',
                color: '#fff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '1.8rem',
                fontWeight: 700,
              }}
            >
              {monogram}
            </div>
          )}

          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
              <h1 style={{ margin: 0, fontSize: '1.45rem', fontWeight: 700, color: 'var(--og-color-text-primary)' }}>
                {profile.displayName}
              </h1>
              {profile.ekycVerified ? (
                <span
                  style={{
                    fontSize: '0.78rem',
                    fontWeight: 600,
                    color: '#059669',
                    background: 'rgba(16, 185, 129, 0.12)',
                    border: '1px solid rgba(16, 185, 129, 0.35)',
                    padding: '3px 10px',
                    borderRadius: 12,
                  }}
                >
                  ✓ Đã xác minh eKYC
                </span>
              ) : (
                <span
                  style={{
                    fontSize: '0.78rem',
                    color: 'var(--og-color-text-secondary)',
                    background: 'var(--og-color-surface-subtle)',
                    border: '1px solid var(--og-color-border)',
                    padding: '3px 10px',
                    borderRadius: 12,
                  }}
                >
                  Chưa xác minh eKYC
                </span>
              )}
            </div>

            {profile.joinedAt && (
              <div style={{ fontSize: '0.84rem', color: 'var(--og-color-text-secondary)', marginTop: 4 }}>
                Tham gia O.G-Shop từ {formatDate(profile.joinedAt)}
              </div>
            )}
          </div>
        </div>

        {/* Verified Public Stats */}
        <div style={{ display: 'flex', gap: 20, flexWrap: 'wrap' }}>
          <div style={{ textAlign: 'center', minWidth: 110, padding: '8px 14px', background: 'var(--og-color-surface-subtle, #f8fafc)', borderRadius: 10 }}>
            <div style={{ fontSize: '1.2rem', fontWeight: 700, color: '#d97706' }} data-testid="seller-stat-rating">
              {profile.stats.averageRating !== null ? `★ ${profile.stats.averageRating.toFixed(1)}` : 'Chưa có'}
            </div>
            <div style={{ fontSize: '0.78rem', color: 'var(--og-color-text-secondary)' }}>
              {profile.stats.totalReviews} đánh giá
            </div>
          </div>

          <div style={{ textAlign: 'center', minWidth: 110, padding: '8px 14px', background: 'var(--og-color-surface-subtle, #f8fafc)', borderRadius: 10 }}>
            <div style={{ fontSize: '1.2rem', fontWeight: 700, color: 'var(--og-color-primary)' }} data-testid="seller-stat-active-products">
              {profile.stats.activeProductsCount}
            </div>
            <div style={{ fontSize: '0.78rem', color: 'var(--og-color-text-secondary)' }}>
              Sản phẩm đang bán
            </div>
          </div>

          <div style={{ textAlign: 'center', minWidth: 110, padding: '8px 14px', background: 'var(--og-color-surface-subtle, #f8fafc)', borderRadius: 10 }}>
            <div style={{ fontSize: '1.2rem', fontWeight: 700, color: '#15803d' }} data-testid="seller-stat-completed-tx">
              {profile.stats.completedTransactionsCount}
            </div>
            <div style={{ fontSize: '0.78rem', color: 'var(--og-color-text-secondary)' }}>
              Giao dịch hoàn tất
            </div>
          </div>
        </div>
      </section>

      {/* Tabs */}
      <div
        role="tablist"
        aria-label="Nội dung hồ sơ người bán"
        style={{
          display: 'flex',
          gap: 10,
          borderBottom: '1px solid var(--og-color-border)',
          marginBottom: 20,
        }}
      >
        <button
          role="tab"
          type="button"
          aria-selected={activeTab === 'products'}
          onClick={() => switchTab('products')}
          style={{
            padding: '12px 20px',
            border: 'none',
            background: 'transparent',
            cursor: 'pointer',
            fontWeight: activeTab === 'products' ? 700 : 500,
            color: activeTab === 'products' ? 'var(--og-color-primary)' : 'var(--og-color-text-secondary)',
            borderBottom: activeTab === 'products' ? '3px solid var(--og-color-primary)' : '3px solid transparent',
            fontSize: '0.95rem',
          }}
        >
          🛍️ Sản phẩm đang bán ({profile.stats.activeProductsCount})
        </button>

        <button
          role="tab"
          type="button"
          aria-selected={activeTab === 'reviews'}
          onClick={() => switchTab('reviews')}
          style={{
            padding: '12px 20px',
            border: 'none',
            background: 'transparent',
            cursor: 'pointer',
            fontWeight: activeTab === 'reviews' ? 700 : 500,
            color: activeTab === 'reviews' ? 'var(--og-color-primary)' : 'var(--og-color-text-secondary)',
            borderBottom: activeTab === 'reviews' ? '3px solid var(--og-color-primary)' : '3px solid transparent',
            fontSize: '0.95rem',
          }}
        >
          ⭐ Đánh giá từ người mua ({profile.stats.totalReviews})
        </button>
      </div>

      {/* Tab 1: Active Products */}
      {activeTab === 'products' && (
        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16, flexWrap: 'wrap', gap: 10 }}>
            <span style={{ fontSize: '0.9rem', color: 'var(--og-color-text-secondary)' }}>
              Hiển thị {products.length} / {productsTotal} sản phẩm đang mở bán
            </span>
            <select
              aria-label="Sắp xếp sản phẩm"
              value={productsSort}
              onChange={(e) => {
                setProductsSort(e.target.value as 'newest' | 'price_asc' | 'price_desc');
                setProductsPage(0);
              }}
              style={{
                padding: '6px 12px',
                borderRadius: 8,
                border: '1px solid var(--og-color-border)',
                background: 'var(--og-color-surface)',
                color: 'var(--og-color-text-primary)',
                fontSize: '0.88rem',
              }}
            >
              <option value="newest">Mới đăng nhất</option>
              <option value="price_asc">Giá: Thấp đến Cao</option>
              <option value="price_desc">Giá: Cao đến Thấp</option>
            </select>
          </div>

          {productsError && <Alert type="danger">{productsError}</Alert>}

          {loadingProducts ? (
            <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--og-color-text-secondary)' }}>
              Đang tải danh sách sản phẩm...
            </div>
          ) : products.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '48px 16px', background: 'var(--og-color-surface)', borderRadius: 12, border: '1px solid var(--og-color-border)', color: 'var(--og-color-text-secondary)' }}>
              Người bán hiện chưa có sản phẩm nào đang mở bán.
            </div>
          ) : (
            <>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 16 }}>
                {products.map((p) => (
                  <Link
                    key={p.productId}
                    to={`/products/${p.productId}`}
                    style={{
                      textDecoration: 'none',
                      color: 'inherit',
                      background: 'var(--og-color-surface)',
                      border: '1px solid var(--og-color-border)',
                      borderRadius: 12,
                      overflow: 'hidden',
                      display: 'flex',
                      flexDirection: 'column',
                    }}
                  >
                    <div style={{ width: '100%', aspectRatio: '4 / 3', background: '#f3f4f6', overflow: 'hidden' }}>
                      {p.thumbnailUrl ? (
                        <img
                          src={resolveReturnMediaUrl(p.thumbnailUrl)}
                          alt={p.title}
                          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                        />
                      ) : (
                        <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '2rem' }}>
                          📦
                        </div>
                      )}
                    </div>
                    <div style={{ padding: 12, display: 'flex', flexDirection: 'column', gap: 6, flex: 1 }}>
                      <div style={{ fontSize: '0.76rem', color: 'var(--og-color-text-secondary)' }}>
                        {p.categoryName} • {CONDITION_LABELS[p.condition] || p.condition}
                      </div>
                      <div style={{ fontWeight: 600, fontSize: '0.94rem', color: 'var(--og-color-text-primary)', lineHeight: 1.35 }}>
                        {p.title}
                      </div>
                      <div style={{ marginTop: 'auto', fontWeight: 700, fontSize: '1.05rem', color: 'var(--og-color-primary)' }}>
                        {formatVnd(p.listedPrice)}
                      </div>
                    </div>
                  </Link>
                ))}
              </div>

              {productsTotalPages > 1 && (
                <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 12, marginTop: 24 }}>
                  <Button
                    variant="secondary"
                    disabled={productsPage <= 0}
                    onClick={() => setProductsPage((p) => Math.max(0, p - 1))}
                  >
                    Trước
                  </Button>
                  <span style={{ fontSize: '0.88rem', color: 'var(--og-color-text-secondary)' }}>
                    Trang {productsPage + 1} / {productsTotalPages}
                  </span>
                  <Button
                    variant="secondary"
                    disabled={productsPage + 1 >= productsTotalPages}
                    onClick={() => setProductsPage((p) => p + 1)}
                  >
                    Sau
                  </Button>
                </div>
              )}
            </>
          )}
        </div>
      )}

      {/* Tab 2: Buyer Reviews */}
      {activeTab === 'reviews' && (
        <div>
          {/* Rating distribution & star filter */}
          <div
            style={{
              background: 'var(--og-color-surface)',
              border: '1px solid var(--og-color-border)',
              borderRadius: 12,
              padding: 16,
              marginBottom: 16,
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: 12,
            }}
          >
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <button
                type="button"
                onClick={() => {
                  setRatingFilter(null);
                  setReviewsPage(0);
                }}
                style={{
                  padding: '6px 14px',
                  borderRadius: 16,
                  border: '1px solid var(--og-color-border)',
                  background: ratingFilter === null ? 'var(--og-color-primary)' : 'transparent',
                  color: ratingFilter === null ? '#fff' : 'var(--og-color-text-primary)',
                  cursor: 'pointer',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                }}
              >
                Tất cả ({profile.stats.totalReviews})
              </button>
              {[5, 4, 3, 2, 1].map((star) => {
                const count = profile.stats.ratingDistribution?.[String(star)] ?? 0;
                const active = ratingFilter === star;
                return (
                  <button
                    key={star}
                    type="button"
                    onClick={() => {
                      setRatingFilter(star);
                      setReviewsPage(0);
                    }}
                    style={{
                      padding: '6px 12px',
                      borderRadius: 16,
                      border: '1px solid var(--og-color-border)',
                      background: active ? 'var(--og-color-primary)' : 'transparent',
                      color: active ? '#fff' : 'var(--og-color-text-primary)',
                      cursor: 'pointer',
                      fontSize: '0.85rem',
                    }}
                  >
                    {star} ★ ({count})
                  </button>
                );
              })}
            </div>
          </div>

          {reviewsError && <Alert type="danger">{reviewsError}</Alert>}

          {loadingReviews ? (
            <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--og-color-text-secondary)' }}>
              Đang tải đánh giá...
            </div>
          ) : reviews.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '48px 16px', background: 'var(--og-color-surface)', borderRadius: 12, border: '1px solid var(--og-color-border)', color: 'var(--og-color-text-secondary)' }}>
              {ratingFilter ? `Chưa có đánh giá ${ratingFilter} sao nào.` : 'Người bán hiện chưa có đánh giá nào từ người mua.'}
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {reviews.map((rev) => (
                <div
                  key={rev.reviewId}
                  style={{
                    background: 'var(--og-color-surface)',
                    border: '1px solid var(--og-color-border)',
                    borderRadius: 12,
                    padding: 16,
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                      <strong style={{ fontSize: '0.92rem', color: 'var(--og-color-text-primary)' }}>
                        {rev.reviewerDisplayName}
                      </strong>
                      <span style={{ color: '#d97706', fontWeight: 700, fontSize: '0.9rem' }}>
                        {'★'.repeat(rev.rating)}{'☆'.repeat(Math.max(0, 5 - rev.rating))}
                      </span>
                    </div>
                    <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)' }}>
                      {formatDate(rev.createdAt)}
                    </span>
                  </div>
                  <p style={{ margin: 0, fontSize: '0.92rem', color: 'var(--og-color-text-primary)', lineHeight: 1.5 }}>
                    {rev.comment || 'Người mua không để lại nhận xét chi tiết.'}
                  </p>
                  {(rev.productId || rev.productTitle) && (
                    <div
                      data-testid={`review-product-${rev.reviewId}`}
                      style={{
                        marginTop: 12,
                        paddingTop: 10,
                        borderTop: '1px solid var(--og-color-border)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: 12,
                        flexWrap: 'wrap',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 0 }}>
                        {rev.productThumbnailUrl ? (
                          <img
                            src={resolveReturnMediaUrl(rev.productThumbnailUrl)}
                            alt={rev.productTitle || `Sản phẩm #${rev.productId}`}
                            style={{
                              width: 40,
                              height: 40,
                              borderRadius: 8,
                              objectFit: 'cover',
                              border: '1px solid var(--og-color-border)',
                              flexShrink: 0,
                            }}
                          />
                        ) : (
                          <div
                            style={{
                              width: 40,
                              height: 40,
                              borderRadius: 8,
                              background: 'var(--og-color-surface-subtle, #f1f5f9)',
                              border: '1px solid var(--og-color-border)',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              fontSize: '1.1rem',
                              flexShrink: 0,
                            }}
                          >
                            📦
                          </div>
                        )}
                        <div style={{ minWidth: 0 }}>
                          <div style={{ fontSize: '0.75rem', color: 'var(--og-color-text-secondary)' }}>
                            Sản phẩm đã mua
                          </div>
                          <div style={{ fontSize: '0.88rem', fontWeight: 600, color: 'var(--og-color-text-primary)' }}>
                            {rev.productTitle || `Sản phẩm #${rev.productId}`}
                          </div>
                        </div>
                      </div>
                      {rev.productId && (
                        <Link
                          to={`/products/${rev.productId}`}
                          style={{
                            fontSize: '0.84rem',
                            fontWeight: 600,
                            color: 'var(--og-color-primary)',
                            textDecoration: 'none',
                            whiteSpace: 'nowrap',
                          }}
                        >
                          Xem sản phẩm &rarr;
                        </Link>
                      )}
                    </div>
                  )}
                </div>
              ))}

              {reviewsTotalPages > 1 && (
                <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 12, marginTop: 12 }}>
                  <Button
                    variant="secondary"
                    disabled={reviewsPage <= 0}
                    onClick={() => setReviewsPage((p) => Math.max(0, p - 1))}
                  >
                    Trước
                  </Button>
                  <span style={{ fontSize: '0.88rem', color: 'var(--og-color-text-secondary)' }}>
                    Trang {reviewsPage + 1} / {reviewsTotalPages} ({reviewsTotal} đánh giá)
                  </span>
                  <Button
                    variant="secondary"
                    disabled={reviewsPage + 1 >= reviewsTotalPages}
                    onClick={() => setReviewsPage((p) => p + 1)}
                  >
                    Sau
                  </Button>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
};
