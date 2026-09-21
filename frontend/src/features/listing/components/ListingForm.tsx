import React, { useState } from 'react';
import { Category, CONDITION_LABELS } from '../../marketplace/types';
import { CreateOrUpdateProductPayload, SellerProductDetail } from '../types';

interface ListingFormProps {
  categories: Category[];
  initialData?: SellerProductDetail;
  onSubmit: (payload: CreateOrUpdateProductPayload) => Promise<void>;
  isSubmitting: boolean;
  submitButtonLabel?: string;
  errorMessage?: string | null;
}

export const ListingForm: React.FC<ListingFormProps> = ({
  categories,
  initialData,
  onSubmit,
  isSubmitting,
  submitButtonLabel = 'Lưu tin đăng',
  errorMessage,
}) => {
  const [title, setTitle] = useState(initialData?.title || '');
  const [categoryId, setCategoryId] = useState<string>(initialData?.category.categoryId ? String(initialData.category.categoryId) : '');
  const [condition, setCondition] = useState(initialData?.condition || 'GOOD');
  const [listedPrice, setListedPrice] = useState<string>(initialData?.listedPrice ? String(initialData.listedPrice) : '');
  const [description, setDescription] = useState(initialData?.description || '');
  const [usageDuration, setUsageDuration] = useState(initialData?.usageDuration || '');
  const [defects, setDefects] = useState(initialData?.defects || '');
  const [repairHistory, setRepairHistory] = useState(initialData?.repairHistory || '');
  const [includedAccessories, setIncludedAccessories] = useState(initialData?.includedAccessories || '');
  const [location, setLocation] = useState(initialData?.location || '');

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const validate = (): boolean => {
    const errors: Record<string, string> = {};

    if (!title.trim()) {
      errors.title = 'Vui lòng nhập tiêu đề tin đăng.';
    } else if (title.trim().length > 200) {
      errors.title = 'Tiêu đề không được vượt quá 200 ký tự.';
    }

    if (!categoryId) {
      errors.categoryId = 'Vui lòng chọn danh mục sản phẩm.';
    }

    if (!condition) {
      errors.condition = 'Vui lòng chọn tình trạng sản phẩm.';
    }

    const priceNum = Number(listedPrice);
    if (!listedPrice || isNaN(priceNum) || priceNum <= 0) {
      errors.listedPrice = 'Giá niêm yết phải lớn hơn 0.';
    }

    if (!description.trim()) {
      errors.description = 'Vui lòng nhập mô tả chi tiết sản phẩm.';
    } else if (description.trim().length > 5000) {
      errors.description = 'Mô tả không được vượt quá 5000 ký tự.';
    }

    if (usageDuration.length > 100) {
      errors.usageDuration = 'Thời gian sử dụng không được vượt quá 100 ký tự.';
    }
    if (defects.length > 2000) {
      errors.defects = 'Khuyết điểm/lỗi không được vượt quá 2000 ký tự.';
    }
    if (repairHistory.length > 2000) {
      errors.repairHistory = 'Lịch sử sửa chữa không được vượt quá 2000 ký tự.';
    }
    if (includedAccessories.length > 2000) {
      errors.includedAccessories = 'Phụ kiện kèm theo không được vượt quá 2000 ký tự.';
    }
    if (location.length > 255) {
      errors.location = 'Khu vực không được vượt quá 255 ký tự.';
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate() || isSubmitting) {
      return;
    }

    await onSubmit({
      title: title.trim(),
      categoryId: Number(categoryId),
      condition,
      listedPrice: Number(listedPrice),
      description: description.trim(),
      usageDuration: usageDuration.trim() || undefined,
      defects: defects.trim() || undefined,
      repairHistory: repairHistory.trim() || undefined,
      includedAccessories: includedAccessories.trim() || undefined,
      location: location.trim() || undefined,
      version: initialData?.version,
    });
  };

  return (
    <form className="og-listing-form" onSubmit={handleSubmit} noValidate>
      {errorMessage && (
        <div className="og-alert og-alert--danger" role="alert" tabIndex={0}>
          ⚠️ {errorMessage}
        </div>
      )}

      {/* Basic info */}
      <div className="og-form-section">
        <h2 className="og-form-section__title">Thông tin cơ bản</h2>

        <div className="og-form-group">
          <label htmlFor="listing-title" className="og-form-label">
            Tiêu đề tin đăng <span className="og-form-required">*</span>
          </label>
          <input
            id="listing-title"
            type="text"
            className={`og-input ${fieldErrors.title ? 'og-input--error' : ''}`}
            placeholder="Ví dụ: iPhone 13 Pro Max 128GB Xanh dương"
            value={title}
            maxLength={200}
            onChange={(e) => setTitle(e.target.value)}
            disabled={isSubmitting}
            required
          />
          {fieldErrors.title && <span className="og-form-error">{fieldErrors.title}</span>}
          <span className="og-form-hint">{title.length}/200 ký tự</span>
        </div>

        <div className="og-form-grid-2">
          <div className="og-form-group">
            <label htmlFor="listing-category" className="og-form-label">
              Danh mục <span className="og-form-required">*</span>
            </label>
            <select
              id="listing-category"
              className={`og-input ${fieldErrors.categoryId ? 'og-input--error' : ''}`}
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
              disabled={isSubmitting}
              required
            >
              <option value="">-- Chọn danh mục --</option>
              {categories.map((c) => (
                <option key={c.categoryId} value={c.categoryId}>
                  {c.categoryName}
                </option>
              ))}
            </select>
            {fieldErrors.categoryId && <span className="og-form-error">{fieldErrors.categoryId}</span>}
          </div>

          <div className="og-form-group">
            <label htmlFor="listing-condition" className="og-form-label">
              Tình trạng máy <span className="og-form-required">*</span>
            </label>
            <select
              id="listing-condition"
              className={`og-input ${fieldErrors.condition ? 'og-input--error' : ''}`}
              value={condition}
              onChange={(e) => setCondition(e.target.value)}
              disabled={isSubmitting}
              required
            >
              {(Object.entries(CONDITION_LABELS) as [string, string][]).map(([val, label]) => (
                <option key={val} value={val}>
                  {label}
                </option>
              ))}
            </select>
            {fieldErrors.condition && <span className="og-form-error">{fieldErrors.condition}</span>}
          </div>
        </div>

        <div className="og-form-grid-2">
          <div className="og-form-group">
            <label htmlFor="listing-price" className="og-form-label">
              Giá niêm yết (₫) <span className="og-form-required">*</span>
            </label>
            <input
              id="listing-price"
              type="number"
              min="1"
              step="10000"
              className={`og-input ${fieldErrors.listedPrice ? 'og-input--error' : ''}`}
              placeholder="Ví dụ: 12500000"
              value={listedPrice}
              onChange={(e) => setListedPrice(e.target.value)}
              disabled={isSubmitting}
              required
            />
            {fieldErrors.listedPrice && <span className="og-form-error">{fieldErrors.listedPrice}</span>}
            <span className="og-form-hint">Giá người mua sẽ thấy trên thị trường (VND).</span>
          </div>

          <div className="og-form-group">
            <label htmlFor="listing-location" className="og-form-label">
              Khu vực giao dịch
            </label>
            <input
              id="listing-location"
              type="text"
              className={`og-input ${fieldErrors.location ? 'og-input--error' : ''}`}
              placeholder="Ví dụ: Quận 1, TP. Hồ Chí Minh"
              value={location}
              maxLength={255}
              onChange={(e) => setLocation(e.target.value)}
              disabled={isSubmitting}
            />
            {fieldErrors.location && <span className="og-form-error">{fieldErrors.location}</span>}
          </div>
        </div>
      </div>

      {/* Detailed specs & transparency */}
      <div className="og-form-section">
        <h2 className="og-form-section__title">Minh bạch chất lượng & Tình trạng</h2>

        <div className="og-form-group">
          <label htmlFor="listing-usage" className="og-form-label">
            Thời gian đã sử dụng
          </label>
          <input
            id="listing-usage"
            type="text"
            className={`og-input ${fieldErrors.usageDuration ? 'og-input--error' : ''}`}
            placeholder="Ví dụ: 8 tháng, Hết bảo hành Apple 2 tháng"
            value={usageDuration}
            maxLength={100}
            onChange={(e) => setUsageDuration(e.target.value)}
            disabled={isSubmitting}
          />
          {fieldErrors.usageDuration && <span className="og-form-error">{fieldErrors.usageDuration}</span>}
        </div>

        <div className="og-form-group">
          <label htmlFor="listing-defects" className="og-form-label">
            Khuyết điểm / Vết xước / Lỗi (nếu có)
          </label>
          <textarea
            id="listing-defects"
            rows={2}
            className={`og-input ${fieldErrors.defects ? 'og-input--error' : ''}`}
            placeholder="Ví dụ: Viền góc dưới có chấm xước dăm nhỏ, màn hình dán cường lực từ lúc mua."
            value={defects}
            maxLength={2000}
            onChange={(e) => setDefects(e.target.value)}
            disabled={isSubmitting}
          />
          {fieldErrors.defects && <span className="og-form-error">{fieldErrors.defects}</span>}
          <span className="og-form-hint">Mô tả trung thực lỗi giúp người mua tin tưởng và tránh khiếu nại sau này.</span>
        </div>

        <div className="og-form-grid-2">
          <div className="og-form-group">
            <label htmlFor="listing-repairs" className="og-form-label">
              Lịch sử sửa chữa / Thay thế
            </label>
            <input
              id="listing-repairs"
              type="text"
              className={`og-input ${fieldErrors.repairHistory ? 'og-input--error' : ''}`}
              placeholder="Ví dụ: Chưa qua sửa chữa / Đã thay pin chính hãng"
              value={repairHistory}
              maxLength={2000}
              onChange={(e) => setRepairHistory(e.target.value)}
              disabled={isSubmitting}
            />
            {fieldErrors.repairHistory && <span className="og-form-error">{fieldErrors.repairHistory}</span>}
          </div>

          <div className="og-form-group">
            <label htmlFor="listing-accessories" className="og-form-label">
              Phụ kiện đi kèm
            </label>
            <input
              id="listing-accessories"
              type="text"
              className={`og-input ${fieldErrors.includedAccessories ? 'og-input--error' : ''}`}
              placeholder="Ví dụ: Hộp zin, Cáp sạc Type-C, 2 ốp lưng"
              value={includedAccessories}
              maxLength={2000}
              onChange={(e) => setIncludedAccessories(e.target.value)}
              disabled={isSubmitting}
            />
            {fieldErrors.includedAccessories && <span className="og-form-error">{fieldErrors.includedAccessories}</span>}
          </div>
        </div>
      </div>

      {/* Full Description */}
      <div className="og-form-section">
        <h2 className="og-form-section__title">Mô tả chi tiết</h2>

        <div className="og-form-group">
          <label htmlFor="listing-description" className="og-form-label">
            Nội dung bài đăng <span className="og-form-required">*</span>
          </label>
          <textarea
            id="listing-description"
            rows={5}
            className={`og-input ${fieldErrors.description ? 'og-input--error' : ''}`}
            placeholder="Mô tả kỹ hơn về tính năng, xuất xứ, lý do bán..."
            value={description}
            maxLength={5000}
            onChange={(e) => setDescription(e.target.value)}
            disabled={isSubmitting}
            required
          />
          {fieldErrors.description && <span className="og-form-error">{fieldErrors.description}</span>}
          <span className="og-form-hint">{description.length}/5000 ký tự</span>
        </div>
      </div>

      {/* Form Actions */}
      <div className="og-form-actions">
        <button
          type="submit"
          className="og-button og-button--primary"
          disabled={isSubmitting}
        >
          {isSubmitting ? 'Đang lưu...' : submitButtonLabel}
        </button>
      </div>
    </form>
  );
};
