import React, { useEffect, useState, useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth';
import { listingApi, SellerListingItem, SellerProductSummary } from '../features/listing';
import { Alert } from '../shared/components';

type FilterTabKey = 'ALL' | 'ACTIVE' | 'PENDING' | 'DRAFT' | 'SOLD' | 'REJECTED';

export const SellerListingsPage: React.FC = () => {
  const { user } = useAuth();
  const navigate = useNavigate();

  const isSeller = user?.roles.includes('SELLER') ?? false;

  const [products, setProducts] = useState<SellerProductSummary[]>([]);
  const [isLoading, setIsLoading] = useState(isSeller);
  const [actionLoadingId, setActionLoadingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  // Filter & Search states
  const [activeTab, setActiveTab] = useState<FilterTabKey>('ALL');
  const [searchTerm, setSearchTerm] = useState('');
  const [sortBy, setSortBy] = useState<'newest' | 'oldest' | 'priceAsc' | 'priceDesc'>('newest');

  useEffect(() => {
    if (!isSeller) {
      return;
    }
    let ignore = false;
    listingApi
      .getSellerProducts(0, 50)
      .then((data) => {
        if (!ignore) {
          setProducts(data.items);
          setError(null);
          setIsLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          setError(err instanceof Error ? err.message : 'Không thể tải danh sách tin đăng.');
          setIsLoading(false);
        }
      });
    return () => {
      ignore = true;
    };
  }, [isSeller, refreshKey]);

  const handlePublish = async (productId: number) => {
    setActionLoadingId(productId);
    setError(null);
    setSuccessMessage(null);
    try {
      await listingApi.publishProduct(productId);
      setSuccessMessage('Đăng bán sản phẩm thành công! Tin hiện đã hiển thị trên chợ.');
      setRefreshKey((k) => k + 1);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Không thể đăng bán sản phẩm.');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleHide = async (productId: number) => {
    setActionLoadingId(productId);
    setError(null);
    setSuccessMessage(null);
    try {
      await listingApi.hideProduct(productId);
      setSuccessMessage('Đã ẩn tin đăng thành công.');
      setRefreshKey((k) => k + 1);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Không thể ẩn tin đăng.');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleSubmitForReview = async (productId: number) => {
    setActionLoadingId(productId);
    setError(null);
    setSuccessMessage(null);
    try {
      await listingApi.submitProduct(productId);
      setSuccessMessage('Đã gửi tin đăng cho KTV kiểm duyệt thành công!');
      setRefreshKey((k) => k + 1);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Không thể gửi duyệt tin đăng. Hãy chắc chắn tin có đủ ít nhất 1 ảnh và 1 video.');
    } finally {
      setActionLoadingId(null);
    }
  };

  // Status counts for filter tabs
  const tabCounts = useMemo(() => {
    return {
      ALL: products.length,
      ACTIVE: products.filter((p) => p.status === 'ACTIVE').length,
      PENDING: products.filter((p) => p.status === 'PENDING').length,
      DRAFT: products.filter((p) => p.status === 'DRAFT' || p.status === 'HIDDEN').length,
      SOLD: products.filter((p) => p.status === 'SOLD').length,
      REJECTED: products.filter((p) => p.status === 'REJECTED').length,
    };
  }, [products]);

  // Filtered & sorted products list
  const filteredProducts = useMemo(() => {
    let result = [...products];

    // Filter by tab
    if (activeTab === 'ACTIVE') {
      result = result.filter((p) => p.status === 'ACTIVE');
    } else if (activeTab === 'PENDING') {
      result = result.filter((p) => p.status === 'PENDING');
    } else if (activeTab === 'DRAFT') {
      result = result.filter((p) => p.status === 'DRAFT' || p.status === 'HIDDEN');
    } else if (activeTab === 'SOLD') {
      result = result.filter((p) => p.status === 'SOLD');
    } else if (activeTab === 'REJECTED') {
      result = result.filter((p) => p.status === 'REJECTED');
    }

    // Filter by search query
    if (searchTerm.trim()) {
      const q = searchTerm.toLowerCase().trim();
      result = result.filter(
        (p) =>
          p.title.toLowerCase().includes(q) ||
          (p.location && p.location.toLowerCase().includes(q))
      );
    }

    // Sort
    result.sort((a, b) => {
      if (sortBy === 'newest') return b.productId - a.productId;
      if (sortBy === 'oldest') return a.productId - b.productId;
      if (sortBy === 'priceAsc') return a.listedPrice - b.listedPrice;
      if (sortBy === 'priceDesc') return b.listedPrice - a.listedPrice;
      return 0;
    });

    return result;
  }, [products, activeTab, searchTerm, sortBy]);

  // If user does not have SELLER role
  if (!isSeller) {
    return (
      <div className="og-seller-upgrade-prompt">
        <div className="og-seller-upgrade-prompt__icon">🏪</div>
        <h1 className="og-seller-upgrade-prompt__title">Kích hoạt quyền Người bán</h1>
        <p className="og-seller-upgrade-prompt__desc">
          Tài khoản của bạn hiện là Người mua (Buyer). Để đăng bán món đồ cũ và quản lý tin đăng trên O.G Shop,
          bạn cần kích hoạt quyền Người bán.
        </p>
        <Link to="/seller-verification" className="og-button og-button--primary">
          ✨ Kích hoạt quyền Người bán ngay
        </Link>
      </div>
    );
  }

  return (
    <div className="og-seller-listings-page">
      {/* 1. Page Header */}
      <div className="og-seller-listings-page__header">
        <div>
          <h1 className="og-seller-listings-page__title">Tin đăng của tôi</h1>
          <p className="og-seller-listings-page__subtitle">
            Quản lý các sản phẩm bạn đang bán, bản nháp và tin đã ẩn.
          </p>
        </div>
        <button
          type="button"
          className="og-button og-button--primary og-seller-btn-primary-cta"
          onClick={() => navigate('/seller/listings/new')}
        >
          <span aria-hidden="true">➕</span>
          <span>Đăng tin mới</span>
        </button>
      </div>

      {successMessage && (
        <div style={{ marginBottom: '16px' }}>
          <Alert type="success">{successMessage}</Alert>
        </div>
      )}

      {error && (
        <div style={{ marginBottom: '16px' }}>
          <Alert type="danger">{error}</Alert>
        </div>
      )}

      {/* 2. Filter Tabs Strip */}
      <div className="og-seller-filter-tabs" role="tablist" aria-label="Lọc theo trạng thái tin">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'ALL'}
          className={`og-seller-tab-btn ${activeTab === 'ALL' ? 'og-seller-tab-btn--active' : ''}`}
          onClick={() => setActiveTab('ALL')}
        >
          <span>Tất cả</span>
          <span className="og-seller-tab-count">{tabCounts.ALL}</span>
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'ACTIVE'}
          className={`og-seller-tab-btn ${activeTab === 'ACTIVE' ? 'og-seller-tab-btn--active' : ''}`}
          onClick={() => setActiveTab('ACTIVE')}
        >
          <span>Đang bán</span>
          <span className="og-seller-tab-count">{tabCounts.ACTIVE}</span>
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'PENDING'}
          className={`og-seller-tab-btn ${activeTab === 'PENDING' ? 'og-seller-tab-btn--active' : ''}`}
          onClick={() => setActiveTab('PENDING')}
        >
          <span>Chờ duyệt</span>
          <span className="og-seller-tab-count">{tabCounts.PENDING}</span>
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'DRAFT'}
          className={`og-seller-tab-btn ${activeTab === 'DRAFT' ? 'og-seller-tab-btn--active' : ''}`}
          onClick={() => setActiveTab('DRAFT')}
        >
          <span>Bản nháp & Ẩn</span>
          <span className="og-seller-tab-count">{tabCounts.DRAFT}</span>
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'SOLD'}
          className={`og-seller-tab-btn ${activeTab === 'SOLD' ? 'og-seller-tab-btn--active' : ''}`}
          onClick={() => setActiveTab('SOLD')}
        >
          <span>Đã bán</span>
          <span className="og-seller-tab-count">{tabCounts.SOLD}</span>
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'REJECTED'}
          className={`og-seller-tab-btn ${activeTab === 'REJECTED' ? 'og-seller-tab-btn--active' : ''}`}
          onClick={() => setActiveTab('REJECTED')}
        >
          <span>Bị từ chối</span>
          <span className="og-seller-tab-count">{tabCounts.REJECTED}</span>
        </button>
      </div>

      {/* 3. Search & Sort Toolbar */}
      {products.length > 0 && (
        <div className="og-seller-toolbar">
          <div className="og-seller-search-box">
            <span className="og-seller-search-icon" aria-hidden="true">
              🔍
            </span>
            <input
              type="text"
              className="og-seller-search-input"
              placeholder="Tìm kiếm theo tên sản phẩm hoặc địa điểm..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              aria-label="Tìm kiếm tin đăng"
            />
          </div>

          <div className="og-seller-sort-wrap">
            <label htmlFor="seller-sort-select" style={{ fontSize: '13px', color: 'var(--og-color-text-muted)' }}>
              Sắp xếp:
            </label>
            <select
              id="seller-sort-select"
              className="og-seller-sort-select"
              value={sortBy}
              onChange={(e) =>
                setSortBy(
                  e.target.value as 'newest' | 'oldest' | 'priceAsc' | 'priceDesc'
                )
              }
            >
              <option value="newest">Mới nhất</option>
              <option value="oldest">Cũ nhất</option>
              <option value="priceAsc">Giá tăng dần</option>
              <option value="priceDesc">Giá giảm dần</option>
            </select>
          </div>
        </div>
      )}

      {/* 4. Product Listings List / Empty State */}
      {isLoading ? (
        <div className="og-detail-loading" aria-busy="true">
          <div className="og-spinner" />
          <p>Đang tải danh sách tin đăng...</p>
        </div>
      ) : products.length === 0 ? (
        <div className="og-empty-state">
          <div className="og-empty-state__icon">📝</div>
          <h2 className="og-empty-state__title">Bạn chưa có tin đăng nào</h2>
          <p className="og-empty-state__desc">
            Hãy bắt đầu đăng bán những món đồ bạn không còn dùng tới để kết nối với người mua trên O.G Shop!
          </p>
          <button
            type="button"
            className="og-button og-button--primary og-seller-btn-primary-cta"
            onClick={() => navigate('/seller/listings/new')}
          >
            <span aria-hidden="true">➕</span>
            <span>Đăng tin đầu tiên</span>
          </button>
        </div>
      ) : filteredProducts.length === 0 ? (
        <div className="og-empty-state" style={{ padding: '40px 20px' }}>
          <div className="og-empty-state__icon" style={{ fontSize: '2.5rem' }}>🔍</div>
          <h2 className="og-empty-state__title" style={{ fontSize: '1.1rem' }}>Không tìm thấy tin đăng phù hợp</h2>
          <p className="og-empty-state__desc">
            Thử thay đổi từ khóa tìm kiếm hoặc chọn tab trạng thái khác.
          </p>
          <button
            type="button"
            className="og-button og-button--secondary og-button--sm"
            onClick={() => {
              setActiveTab('ALL');
              setSearchTerm('');
            }}
          >
            Xem tất cả tin đăng
          </button>
        </div>
      ) : (
        <div className="og-seller-listings-list">
          {filteredProducts.map((p) => (
            <SellerListingItem
              key={p.productId}
              product={p}
              onPublish={handlePublish}
              onHide={handleHide}
              onSubmitForReview={handleSubmitForReview}
              isActionLoading={actionLoadingId === p.productId}
            />
          ))}
        </div>
      )}
    </div>
  );
};
