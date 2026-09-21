import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth';
import { listingApi, SellerListingItem, SellerProductSummary } from '../features/listing';
import { Alert } from '../shared/components';

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
      <div className="og-seller-listings-page__header">
        <div>
          <h1 className="og-seller-listings-page__title">Tin đăng của tôi</h1>
          <p className="og-seller-listings-page__subtitle">
            Quản lý các sản phẩm bạn đang bán, bản nháp và tin đã ẩn.
          </p>
        </div>
        <button
          type="button"
          className="og-button og-button--primary"
          onClick={() => navigate('/seller/listings/new')}
        >
          ➕ Đăng tin mới
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

      {isLoading ? (
        <div className="og-detail-loading" aria-busy="true">
          <div className="og-spinner" />
          <p>Đang tải danh sách tin đăng...</p>
        </div>
      ) : products.length > 0 ? (
        <div className="og-seller-listings-list">
          {products.map((p) => (
            <SellerListingItem
              key={p.productId}
              product={p}
              onPublish={handlePublish}
              onHide={handleHide}
              isActionLoading={actionLoadingId === p.productId}
            />
          ))}
        </div>
      ) : (
        <div className="og-seller-listings-empty">
          <div className="og-seller-listings-empty__icon">📝</div>
          <h2 className="og-seller-listings-empty__title">Bạn chưa có tin đăng nào</h2>
          <p className="og-seller-listings-empty__desc">
            Hãy bắt đầu đăng bán những món đồ bạn không còn dùng tới để kết nối với người mua trên O.G Shop!
          </p>
          <button
            type="button"
            className="og-button og-button--primary"
            onClick={() => navigate('/seller/listings/new')}
          >
            Đăng tin đầu tiên
          </button>
        </div>
      )}
    </div>
  );
};
