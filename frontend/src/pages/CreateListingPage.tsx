import React, { useEffect, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../features/auth';
import { Category, marketplaceApi } from '../features/marketplace';
import { CreateOrUpdateProductPayload, ListingForm, listingApi } from '../features/listing';

export const CreateListingPage: React.FC = () => {
  const { user } = useAuth();
  const navigate = useNavigate();

  const isSeller = user?.roles.includes('SELLER') ?? false;

  const [categories, setCategories] = useState<Category[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    marketplaceApi.getCategories()
      .then(setCategories)
      .catch((err) => console.error('Không thể tải categories:', err));
  }, []);

  const handleCreate = async (payload: CreateOrUpdateProductPayload) => {
    setIsSubmitting(true);
    setErrorMessage(null);
    try {
      await listingApi.createProduct(payload);
      navigate('/seller/listings');
    } catch (err: unknown) {
      setErrorMessage(err instanceof Error ? err.message : 'Tạo tin đăng thất bại. Vui lòng thử lại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!isSeller) {
    return (
      <div className="og-seller-upgrade-prompt">
        <h1 className="og-seller-upgrade-prompt__title">Kích hoạt quyền Người bán</h1>
        <p className="og-seller-upgrade-prompt__desc">
          Bạn cần kích hoạt quyền Người bán trước khi đăng tin sản phẩm.
        </p>
        <Link to="/seller-verification" className="og-button og-button--primary">
          Kích hoạt ngay
        </Link>
      </div>
    );
  }

  return (
    <div className="og-listing-form-page">
      <div className="og-listing-form-page__header">
        <nav aria-label="Breadcrumb">
          <Link to="/seller/listings" className="og-link">
            ← Quay lại danh sách tin đăng
          </Link>
        </nav>
        <h1 className="og-listing-form-page__title">Đăng tin bán đồ cũ mới</h1>
        <p className="og-listing-form-page__subtitle">
          Điền thông tin và mô tả trung thực về món đồ của bạn để tạo bản nháp (DRAFT). Bạn có thể đăng bán bất cứ lúc nào.
        </p>
      </div>

      <ListingForm
        categories={categories}
        onSubmit={handleCreate}
        isSubmitting={isSubmitting}
        submitButtonLabel="Tạo bản nháp"
        errorMessage={errorMessage}
      />
    </div>
  );
};
