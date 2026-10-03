import React, { useState, useEffect, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Category,
  MarketplaceFilters,
  PageResponse,
  ProductCard,
  ProductFilterParams,
  ProductSummary,
  marketplaceApi,
} from '../features/marketplace';
import { Alert } from '../shared/components';

export const MarketplacePage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const [categories, setCategories] = useState<Category[]>([]);
  const [productsData, setProductsData] = useState<PageResponse<ProductSummary> | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Parse filters from URL search params
  const currentFilters = useMemo<ProductFilterParams>(
    () => ({
      query: searchParams.get('query') || undefined,
      categoryId: searchParams.get('categoryId') ? Number(searchParams.get('categoryId')) : undefined,
      condition: searchParams.get('condition') || undefined,
      minPrice: searchParams.get('minPrice') ? Number(searchParams.get('minPrice')) : undefined,
      maxPrice: searchParams.get('maxPrice') ? Number(searchParams.get('maxPrice')) : undefined,
      page: searchParams.get('page') ? Number(searchParams.get('page')) : 0,
      size: 12,
      sort: searchParams.get('sort') || 'newest',
    }),
    [searchParams]
  );

  // Fetch categories on mount
  useEffect(() => {
    marketplaceApi.getCategories()
      .then(setCategories)
      .catch((err) => console.error('Không thể tải categories:', err));
  }, []);

  useEffect(() => {
    let ignore = false;
    marketplaceApi
      .getProducts(currentFilters)
      .then((data) => {
        if (!ignore) {
          setProductsData(data);
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

  const handleApplyFilters = (newFilters: ProductFilterParams) => {
    setIsLoading(true);
    const params: Record<string, string> = {};
    if (newFilters.query) params.query = newFilters.query;
    if (newFilters.categoryId) params.categoryId = String(newFilters.categoryId);
    if (newFilters.condition) params.condition = newFilters.condition;
    if (newFilters.minPrice !== undefined && newFilters.minPrice !== null) params.minPrice = String(newFilters.minPrice);
    if (newFilters.maxPrice !== undefined && newFilters.maxPrice !== null) params.maxPrice = String(newFilters.maxPrice);
    if (newFilters.sort) params.sort = newFilters.sort;
    params.page = '0';
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
      .getProducts(currentFilters)
      .then((data) => {
        setProductsData(data);
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Không thể tải danh sách sản phẩm. Vui lòng thử lại.');
      })
      .finally(() => {
        setIsLoading(false);
      });
  };

  const handlePageChange = (newPage: number) => {
    const params = new URLSearchParams(searchParams);
    params.set('page', String(newPage));
    setSearchParams(params);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const isFiltered = Boolean(
    currentFilters.query ||
    currentFilters.categoryId ||
    currentFilters.condition ||
    currentFilters.minPrice !== undefined ||
    currentFilters.maxPrice !== undefined ||
    (currentFilters.sort && currentFilters.sort !== 'newest')
  );

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
      ) : productsData && productsData.items.length > 0 ? (
        <>
          <div className="og-marketplace-page__meta">
            <span>Tìm thấy <strong>{productsData.totalElements}</strong> sản phẩm</span>
          </div>

          <div className="og-marketplace-grid" role="feed" aria-label="Danh sách sản phẩm">
            {productsData.items.map((product: ProductSummary) => (
              <ProductCard key={product.productId} product={product} />
            ))}
          </div>

          {/* Pagination */}
          {productsData.totalPages > 1 && (
            <nav className="og-pagination" aria-label="Phân trang sản phẩm">
              <button
                type="button"
                className="og-button og-button--ghost og-button--sm"
                disabled={productsData.page === 0}
                onClick={() => handlePageChange(productsData.page - 1)}
              >
                ← Trang trước
              </button>

              <span className="og-pagination__info">
                Trang {productsData.page + 1} / {productsData.totalPages}
              </span>

              <button
                type="button"
                className="og-button og-button--ghost og-button--sm"
                disabled={!productsData.hasNext}
                onClick={() => handlePageChange(productsData.page + 1)}
              >
                Trang sau →
              </button>
            </nav>
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
