import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Category,
  MarketplaceFilters,
  ProductCard,
  ProductFilterParams,
  ProductSummary,
  marketplaceApi,
} from '../features/marketplace';
import { Alert } from '../shared/components';

// Preview items shown in the frosted peek row (Requirement 3: các sản phẩm chưa hiển thị phủ sương làm mờ)
const PEEK_CARDS = [
  {
    title: 'Túi da bò thật đựng laptop thủ công',
    price: 650000,
    condition: 'Như mới',
    sellerName: 'Thành Long',
    location: 'TP. HCM',
    thumbnailUrl: 'https://images.unsplash.com/photo-1548036328-c9fa89d128fa?w=400&q=80',
  },
  {
    title: 'Giày sneaker thể thao cao cấp êm chân',
    price: 480000,
    condition: 'Đã dùng tốt',
    sellerName: 'Minh Hoàng',
    location: 'Hà Nội',
    thumbnailUrl: 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=400&q=80',
  },
  {
    title: 'Tai nghe Bluetooth không dây bass trầm',
    price: 520000,
    condition: 'Đã dùng tốt',
    sellerName: 'Audio Pro',
    location: 'Đà Nẵng',
    thumbnailUrl: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=400&q=80',
  },
  {
    title: 'Đồng hồ cổ mạ vàng phong cách vintage',
    price: 950000,
    condition: 'Đồ cổ',
    sellerName: 'Vintage Store',
    location: 'TP. HCM',
    thumbnailUrl: 'https://images.unsplash.com/photo-1524805444758-089113d48a6d?w=400&q=80',
  },
  {
    title: 'Máy ảnh cơ film cổ điển cho người mới chơi',
    price: 1650000,
    condition: 'Có hao mòn',
    sellerName: 'Film Camera',
    location: 'Cần Thơ',
    thumbnailUrl: 'https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?w=400&q=80',
  },
  {
    title: 'Hoa thược dược đỏ rực rỡ decor sân vườn',
    price: 180000,
    condition: 'Như mới',
    sellerName: 'Garden Art',
    location: 'Đà Lạt',
    thumbnailUrl: 'https://images.unsplash.com/photo-1508615070457-7baeba4003ab?w=400&q=80',
  },
];

export const MarketplacePage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const [categories, setCategories] = useState<Category[]>([]);
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [currentPage, setCurrentPage] = useState<number>(0);
  const [hasNext, setHasNext] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Parse filters from URL search params (ignore pagination page in URL)
  const currentFilters = useMemo<ProductFilterParams>(() => {
    const rawCat = searchParams.get('categoryIds') || searchParams.get('categoryId');
    let parsedCatIds: number[] | undefined;
    if (rawCat) {
      const parts = rawCat.split(',').map((s) => Number(s.trim())).filter((n) => !isNaN(n) && n > 0);
      if (parts.length > 0) parsedCatIds = parts;
    }

    const rawCond = searchParams.get('conditions') || searchParams.get('condition');
    let parsedConds: string[] | undefined;
    if (rawCond) {
      const parts = rawCond.split(',').map((s) => s.trim().toUpperCase()).filter(Boolean);
      if (parts.length > 0) parsedConds = parts;
    }

    return {
      query: searchParams.get('query') || undefined,
      categoryId: parsedCatIds && parsedCatIds.length === 1 ? parsedCatIds[0] : undefined,
      categoryIds: parsedCatIds,
      condition: parsedConds && parsedConds.length === 1 ? parsedConds[0] : undefined,
      conditions: parsedConds,
      minPrice: searchParams.get('minPrice') ? Number(searchParams.get('minPrice')) : undefined,
      maxPrice: searchParams.get('maxPrice') ? Number(searchParams.get('maxPrice')) : undefined,
      size: 12,
      sort: searchParams.get('sort') || 'newest',
    };
  }, [searchParams]);

  // Fetch categories on mount
  useEffect(() => {
    marketplaceApi.getCategories()
      .then(setCategories)
      .catch((err) => console.error('Không thể tải categories:', err));
  }, []);

  // Fetch initial batch (page 0) whenever filters change
  useEffect(() => {
    let ignore = false;
    setIsLoading(true);
    marketplaceApi
      .getProducts({ ...currentFilters, page: 0, size: 12 })
      .then((data) => {
        if (!ignore) {
          setProducts(data.items);
          setTotalElements(data.totalElements);
          setHasNext(data.hasNext || data.items.length < data.totalElements);
          setCurrentPage(0);
          setError(null);
          setIsLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          setError(err instanceof Error ? err.message : 'Không thể tải danh sách sản phẩm. Vui lòng thử lại.');
          setIsLoading(false);
        }
      });
    return () => {
      ignore = true;
    };
  }, [currentFilters]);

  // Load more products handler (Requirement 2 & 3: Infinite scroll with load more button)
  const handleLoadMore = useCallback(() => {
    if (isLoadingMore || !hasNext) return;
    setIsLoadingMore(true);
    const nextPage = currentPage + 1;

    marketplaceApi
      .getProducts({ ...currentFilters, page: nextPage, size: 12 })
      .then((data) => {
        setProducts((prev) => [...prev, ...data.items]);
        setCurrentPage(nextPage);
        setTotalElements(data.totalElements);
        const nextHasMore =
          data.hasNext !== undefined
            ? data.hasNext
            : products.length + data.items.length < data.totalElements;
        setHasNext(nextHasMore);
      })
      .catch((err) => {
        console.error('Lỗi khi tải thêm sản phẩm:', err);
      })
      .finally(() => {
        setIsLoadingMore(false);
      });
  }, [currentFilters, currentPage, hasNext, isLoadingMore, products.length]);

  const handleApplyFilters = (newFilters: ProductFilterParams) => {
    setIsLoading(true);
    const params: Record<string, string> = {};
    if (newFilters.query) params.query = newFilters.query;

    const catIds = newFilters.categoryIds && newFilters.categoryIds.length > 0
      ? newFilters.categoryIds
      : newFilters.categoryId ? [newFilters.categoryId] : [];
    if (catIds.length > 0) {
      params.categoryIds = catIds.join(',');
      if (catIds.length === 1) {
        params.categoryId = String(catIds[0]);
      }
    }

    const conds = newFilters.conditions && newFilters.conditions.length > 0
      ? newFilters.conditions
      : newFilters.condition ? [newFilters.condition] : [];
    if (conds.length > 0) {
      params.conditions = conds.join(',');
      if (conds.length === 1) {
        params.condition = conds[0];
      }
    }

    if (newFilters.minPrice !== undefined && newFilters.minPrice !== null && !isNaN(newFilters.minPrice)) {
      params.minPrice = String(newFilters.minPrice);
    }
    if (newFilters.maxPrice !== undefined && newFilters.maxPrice !== null && !isNaN(newFilters.maxPrice)) {
      params.maxPrice = String(newFilters.maxPrice);
    }
    if (newFilters.sort) params.sort = newFilters.sort;
    setSearchParams(params);
  };

  const handleResetFilters = () => {
    setIsLoading(true);
    setSearchParams({});
  };

  const handleRetry = () => {
    setIsLoading(true);
    setError(null);
    marketplaceApi
      .getProducts({ ...currentFilters, page: 0, size: 12 })
      .then((data) => {
        setProducts(data.items);
        setTotalElements(data.totalElements);
        setHasNext(data.hasNext || data.items.length < data.totalElements);
        setCurrentPage(0);
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Không thể tải danh sách sản phẩm. Vui lòng thử lại.');
      })
      .finally(() => {
        setIsLoading(false);
      });
  };

  const isFiltered = Boolean(
    currentFilters.query ||
    (currentFilters.categoryIds && currentFilters.categoryIds.length > 0) ||
    currentFilters.categoryId ||
    (currentFilters.conditions && currentFilters.conditions.length > 0) ||
    currentFilters.condition ||
    currentFilters.minPrice !== undefined ||
    currentFilters.maxPrice !== undefined ||
    (currentFilters.sort && currentFilters.sort !== 'newest')
  );

  const remainingCount = Math.max(0, totalElements - products.length);

  return (
    <div className="og-marketplace-page">
      <div className="og-marketplace-page__header">
        <h1 className="og-marketplace-page__title">Chợ Đồ Cũ O.G Shop</h1>
        <p className="og-marketplace-page__subtitle">
          Khám phá đồ công nghệ, thời trang và đồ dùng đã qua sử dụng với nguồn gốc minh bạch.
        </p>
      </div>

      <MarketplaceFilters
        key={searchParams.toString()}
        categories={categories}
        filters={currentFilters}
        onApplyFilters={handleApplyFilters}
        onResetFilters={handleResetFilters}
        isLoading={isLoading}
      />

      {error ? (
        <div className="og-marketplace-page__error" role="alert">
          <Alert type="danger">{error}</Alert>
          <button
            type="button"
            className="og-button og-button--primary"
            style={{ marginTop: '16px' }}
            onClick={handleRetry}
          >
            🔄 Thử lại
          </button>
        </div>
      ) : isLoading ? (
        <div className="og-marketplace-grid-skeleton" aria-busy="true" aria-label="Đang tải sản phẩm...">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="og-product-card-skeleton" />
          ))}
        </div>
      ) : products && products.length > 0 ? (
        <>
          <div className="og-marketplace-page__meta">
            <span>
              Tìm thấy <strong>{totalElements}</strong> sản phẩm (đang hiển thị {products.length})
            </span>
          </div>

          <div className="og-marketplace-grid" role="feed" aria-label="Danh sách sản phẩm">
            {products.map((product: ProductSummary) => (
              <ProductCard key={product.productId} product={product} />
            ))}
          </div>

          {/* Requirement 2 & 3: Load More Button with Frosted Peek Row */}
          {hasNext ? (
            <div className="og-load-more-section">
              <div className="og-load-more-btn-wrap">
                <button
                  type="button"
                  className="og-load-more-btn"
                  onClick={handleLoadMore}
                  disabled={isLoadingMore}
                  aria-label="Tải thêm sản phẩm"
                >
                  {isLoadingMore ? (
                    <>
                      <span className="og-spinner" aria-hidden="true" /> Đang tải thêm sản phẩm...
                    </>
                  ) : (
                    <>
                      <span className="og-load-more-icon" aria-hidden="true">⬇</span>
                      <span>Tải thêm sản phẩm</span>
                      {remainingCount > 0 && (
                        <span className="og-load-more-badge">
                          (còn {remainingCount} món)
                        </span>
                      )}
                    </>
                  )}
                </button>
              </div>

              {/* Frosted peek row extending down to touch footer */}
              <div className="og-peek-row-wrap" aria-hidden="true">
                <div className="og-marketplace-grid og-peek-grid">
                  {PEEK_CARDS.map((card, idx) => (
                    <article key={idx} className="og-product-card og-peek-card">
                      <div className="og-product-card__link">
                        <div className="og-product-card__image-container">
                          <img
                            src={card.thumbnailUrl}
                            alt=""
                            className="og-product-card__image"
                            loading="lazy"
                            onError={(e) => {
                              (e.target as HTMLElement).style.display = 'none';
                            }}
                          />
                        </div>
                        <div className="og-product-card__body">
                          <div className="og-product-card__seller">
                            <span className="og-product-card__seller-name">{card.sellerName}</span>
                            <span className="og-product-card__verified-badge">
                              <svg width="13" height="13" viewBox="0 0 24 24" fill="#0284c7" aria-hidden="true">
                                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" />
                              </svg>
                            </span>
                          </div>
                          <h3 className="og-product-card__title">{card.title}</h3>
                          <div className="og-product-card__price-row">
                            <span className="og-product-card__price">
                              {new Intl.NumberFormat('vi-VN').format(card.price)} đ
                            </span>
                          </div>
                          <div className="og-product-card__footer">
                            <span className="og-product-card__location">{card.location}</span>
                            <span className="og-product-card__condition">{card.condition}</span>
                          </div>
                        </div>
                      </div>
                    </article>
                  ))}
                </div>
                {/* Frosted mist overlay extending all the way down to footer */}
                <div className="og-peek-row-mist" />
              </div>
            </div>
          ) : (
            /* End of list banner */
            <div className="og-marketplace-end-banner">
              <span className="og-marketplace-end-icon" aria-hidden="true">✨</span>
              <span>Bạn đã xem hết tất cả <strong>{products.length}</strong> sản phẩm hiện có</span>
            </div>
          )}
        </>
      ) : (
        <div className="og-marketplace-empty">
          <div className="og-marketplace-empty__icon">🔍</div>
          <h2 className="og-marketplace-empty__title">
            {isFiltered ? 'Không tìm thấy sản phẩm phù hợp' : 'Hiện chưa có sản phẩm nào đang đăng bán'}
          </h2>
          <p className="og-marketplace-empty__desc">
            {isFiltered
              ? 'Hãy thử thay đổi từ khóa tìm kiếm, mức giá hoặc gỡ bỏ các tiêu chí lọc.'
              : 'Hãy quay lại sau hoặc là người đầu tiên đăng bán món đồ của bạn!'}
          </p>
          {isFiltered && (
            <button
              type="button"
              className="og-button og-button--ghost"
              onClick={handleResetFilters}
            >
              Xóa bộ lọc
            </button>
          )}
        </div>
      )}
    </div>
  );
};

export default MarketplacePage;
