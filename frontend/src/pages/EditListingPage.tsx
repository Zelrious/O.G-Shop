import React, { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../features/auth';
import { Category, marketplaceApi } from '../features/marketplace';
import { CreateOrUpdateProductPayload, ListingForm, listingApi, SellerProductDetail } from '../features/listing';
import { Alert } from '../shared/components';

export const EditListingPage: React.FC = () => {
  const { productId } = useParams<{ productId: string }>();
  const { user } = useAuth();
  const navigate = useNavigate();

  const isSeller = user?.roles.includes('SELLER') ?? false;

  const [categories, setCategories] = useState<Category[]>([]);
  const [initialData, setInitialData] = useState<SellerProductDetail | null>(null);
  const [isLoading, setIsLoading] = useState(isSeller && Boolean(productId));
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const loadData = () => {
    if (!productId || !isSeller) return;
    setIsLoading(true);
    setLoadError(null);

    Promise.all([
      marketplaceApi.getCategories(),
      listingApi.getSellerProduct(productId),
    ])
      .then(([cats, product]) => {
        setCategories(cats);
        setInitialData(product);
        setLoadError(null);
      })
      .catch((err: unknown) => {
        setLoadError(err instanceof Error ? err.message : 'Không thể tải thông tin tin đăng.');
      })
      .finally(() => {
        setIsLoading(false);
      });
  };

  useEffect(() => {
    if (!productId || !isSeller) return;
    let ignore = false;
    Promise.all([
      marketplaceApi.getCategories(),
      listingApi.getSellerProduct(productId),
    ])
      .then(([cats, product]) => {
        if (!ignore) {
          setCategories(cats);
          setInitialData(product);
          setLoadError(null);
          setIsLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          setLoadError(err instanceof Error ? err.message : 'Không thể tải thông tin tin đăng.');
          setIsLoading(false);
        }
      });
    return () => {
      ignore = true;
    };
  }, [productId, isSeller]);

  const [isMediaLoading, setIsMediaLoading] = useState(false);

  const handleUpdate = async (payload: CreateOrUpdateProductPayload) => {
    if (!productId) return;
    setIsSubmitting(true);
    setErrorMessage(null);
    try {
      await listingApi.updateProduct(productId, payload);
      navigate('/seller/listings');
    } catch (err: unknown) {
      setErrorMessage(err instanceof Error ? err.message : 'Cập nhật tin đăng thất bại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleUploadMedia = async (file: File, mediaType: 'IMAGE' | 'VIDEO') => {
    if (!productId) return;
    setIsMediaLoading(true);
    setErrorMessage(null);
    try {
      await listingApi.uploadMedia(productId, file, mediaType);
      const updated = await listingApi.getSellerProduct(productId);
      setInitialData(updated);
    } catch (err: unknown) {
      setErrorMessage(err instanceof Error ? err.message : 'Tải media thất bại.');
      throw err;
    } finally {
      setIsMediaLoading(false);
    }
  };

  const handleDeleteMedia = async (mediaId: number) => {
    if (!productId) return;
    setIsMediaLoading(true);
    setErrorMessage(null);
    try {
      await listingApi.deleteMedia(productId, mediaId);
      const updated = await listingApi.getSellerProduct(productId);
      setInitialData(updated);
    } catch (err: unknown) {
      setErrorMessage(err instanceof Error ? err.message : 'Xóa media thất bại.');
    } finally {
      setIsMediaLoading(false);
    }
  };

  const handleSubmitForReview = async () => {
    if (!productId) return;
    setIsSubmitting(true);
    setErrorMessage(null);
    try {
      await listingApi.submitProduct(productId);
      navigate('/seller/listings');
    } catch (err: unknown) {
      setErrorMessage(err instanceof Error ? err.message : 'Gửi duyệt tin đăng thất bại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!isSeller) {
    return (
      <div className="og-seller-upgrade-prompt">
        <h1 className="og-seller-upgrade-prompt__title">Kích hoạt quyền Người bán</h1>
        <p className="og-seller-upgrade-prompt__desc">
          Bạn cần kích hoạt quyền Người bán trước khi quản lý tin đăng.
        </p>
        <Link to="/seller-verification" className="og-button og-button--primary">
          Kích hoạt ngay
        </Link>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="og-detail-loading" aria-busy="true">
        <div className="og-spinner" />
        <p>Đang tải thông tin tin đăng...</p>
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="og-detail-error" role="alert">
        <Alert type="danger">{loadError}</Alert>
        <div style={{ marginTop: '16px', display: 'flex', gap: '8px' }}>
          <button type="button" className="og-button og-button--primary" onClick={loadData}>
            🔄 Thử lại
          </button>
          <Link to="/seller/listings" className="og-button og-button--ghost">
            ← Quay lại danh sách tin
          </Link>
        </div>
      </div>
    );
  }

  if (!initialData) {
    return null;
  }

  return (
    <div className="og-listing-form-page">
      <div className="og-listing-form-page__header">
        <nav aria-label="Breadcrumb" className="og-listing-form-page__breadcrumb">
          <Link to="/seller/listings" className="og-listing-form-page__breadcrumb-link">
            <span aria-hidden="true">←</span>
            <span>Quay lại danh sách tin đăng</span>
          </Link>
        </nav>
        <h1 className="og-listing-form-page__title">Chỉnh sửa tin đăng</h1>
        <p className="og-listing-form-page__subtitle">
          Cập nhật thông tin chi tiết cho món đồ &ldquo;{initialData.title}&rdquo;.
        </p>
        <div className="og-listing-form-page__required-note">
          Trường có dấu <span className="og-form-required">*</span> là bắt buộc.
        </div>
      </div>

      <ListingForm
        categories={categories}
        initialData={initialData}
        onSubmit={handleUpdate}
        isSubmitting={isSubmitting}
        submitButtonLabel="Cập nhật tin đăng"
        errorMessage={errorMessage}
        mediaList={initialData.media}
        onUploadMedia={handleUploadMedia}
        onDeleteMedia={handleDeleteMedia}
        onSubmitForReview={handleSubmitForReview}
        isMediaActionLoading={isMediaLoading}
      />
    </div>
  );
};
