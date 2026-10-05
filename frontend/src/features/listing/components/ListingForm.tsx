import React, { useState, useRef } from 'react';
import { Category, CONDITION_LABELS, ProductMedia } from '../../marketplace/types';
import { CreateOrUpdateProductPayload, SellerProductDetail } from '../types';

interface ListingFormProps {
  categories: Category[];
  initialData?: SellerProductDetail;
  onSubmit: (payload: CreateOrUpdateProductPayload) => Promise<void>;
  isSubmitting: boolean;
  submitButtonLabel?: string;
  errorMessage?: string | null;
  // Media & Review props
  mediaList?: ProductMedia[];
  onUploadMedia?: (file: File, mediaType: 'IMAGE' | 'VIDEO') => Promise<void>;
  onDeleteMedia?: (mediaId: number) => Promise<void>;
  onSubmitForReview?: () => Promise<void>;
  isMediaActionLoading?: boolean;
}

export const ListingForm: React.FC<ListingFormProps> = ({
  categories,
  initialData,
  onSubmit,
  isSubmitting,
  submitButtonLabel = 'Lưu tin đăng',
  errorMessage,
  mediaList = initialData?.media || [],
  onUploadMedia,
  onDeleteMedia,
  onSubmitForReview,
  isMediaActionLoading = false,
}) => {
  const [title, setTitle] = useState(initialData?.title || '');
  const [categoryIds, setCategoryIds] = useState<number[]>(() =>
    initialData
      ? (initialData.categories?.length ? initialData.categories : [initialData.category]).map((category) => category.categoryId)
      : []
  );
  const [condition, setCondition] = useState(initialData?.condition || 'GOOD');
  const [listedPrice, setListedPrice] = useState<string>(initialData?.listedPrice ? String(initialData.listedPrice) : '');
  const [description, setDescription] = useState(initialData?.description || '');
  const [usageDuration, setUsageDuration] = useState(initialData?.usageDuration || '');
  const [defects, setDefects] = useState(initialData?.defects || '');
  const [repairHistory, setRepairHistory] = useState(initialData?.repairHistory || '');
  const [includedAccessories, setIncludedAccessories] = useState(initialData?.includedAccessories || '');
  const [location, setLocation] = useState(initialData?.location || '');
  const [requiresBuyerEkyc, setRequiresBuyerEkyc] = useState(Boolean(initialData?.requiresBuyerEkyc));

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [mediaError, setMediaError] = useState<string | null>(null);
  const [isDragOver, setIsDragOver] = useState(false);

  const imageInputRef = useRef<HTMLInputElement>(null);
  const videoInputRef = useRef<HTMLInputElement>(null);

  const images = mediaList.filter((m) => m.mediaType === 'IMAGE');
  const videos = mediaList.filter((m) => m.mediaType === 'VIDEO');

  const hasMinMedia = images.length >= 1 && videos.length >= 1;
  const canSubmitForReview = Boolean(initialData?.productId) && ['DRAFT', 'HIDDEN', 'REJECTED'].includes(initialData?.status || '') && hasMinMedia;

  const validate = (): boolean => {
    const errors: Record<string, string> = {};

    if (!title.trim()) {
      errors.title = 'Vui lòng nhập tiêu đề tin đăng.';
    } else if (title.trim().length > 200) {
      errors.title = 'Tiêu đề không được vượt quá 200 ký tự.';
    }

    if (categoryIds.length === 0) {
      errors.categoryIds = 'Vui lòng chọn ít nhất một danh mục sản phẩm.';
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
      categoryIds,
      condition,
      listedPrice: Number(listedPrice),
      description: description.trim(),
      usageDuration: usageDuration.trim() || undefined,
      defects: defects.trim() || undefined,
      repairHistory: repairHistory.trim() || undefined,
      includedAccessories: includedAccessories.trim() || undefined,
      location: location.trim() || undefined,
      requiresBuyerEkyc,
      version: initialData?.version,
    });
  };

  const processUploadedFile = async (file: File) => {
    if (!onUploadMedia) return;
    setMediaError(null);

    const isImage = file.type.startsWith('image/') || /\.(jpe?g|png|webp)$/i.test(file.name);
    const isVideo = file.type.startsWith('video/') || /\.(mp4|webm)$/i.test(file.name);

    if (isImage) {
      if (images.length >= 5) {
        setMediaError('Đã đạt giới hạn tối đa 5 hình ảnh.');
        return;
      }
      if (file.size > 5 * 1024 * 1024) {
        setMediaError('Kích thước ảnh tối đa là 5 MB.');
        return;
      }
      try {
        await onUploadMedia(file, 'IMAGE');
      } catch (err: unknown) {
        setMediaError(err instanceof Error ? err.message : 'Tải ảnh thất bại.');
      }
    } else if (isVideo) {
      if (videos.length >= 1) {
        setMediaError('Đã có 1 video. Vui lòng xóa video hiện tại trước khi tải lên video mới.');
        return;
      }
      if (file.size > 50 * 1024 * 1024) {
        setMediaError('Kích thước video tối đa là 50 MB (thời lượng khuyến nghị ≤ 60s).');
        return;
      }
      try {
        await onUploadMedia(file, 'VIDEO');
      } catch (err: unknown) {
        setMediaError(err instanceof Error ? err.message : 'Tải video thất bại.');
      }
    } else {
      setMediaError('Định dạng tệp không được hỗ trợ. Vui lòng tải ảnh (JPG, PNG, WEBP) hoặc video (MP4, WEBM).');
    }
  };

  const handleImageFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      await processUploadedFile(file);
    }
    e.target.value = '';
  };

  const handleVideoFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      await processUploadedFile(file);
    }
    e.target.value = '';
  };

  const handleDrop = async (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
    if (!initialData?.productId || !onUploadMedia) return;

    const files = Array.from(e.dataTransfer.files);
    for (const file of files) {
      await processUploadedFile(file);
    }
  };

  return (
    <form className="og-listing-form" onSubmit={handleSubmit} noValidate>
      {errorMessage && (
        <div className="og-alert og-alert--danger" role="alert" tabIndex={0}>
          ⚠️ {errorMessage}
        </div>
      )}

      {/* 01. Thông tin cơ bản */}
      <section className="og-form-section" aria-labelledby="section-01-heading">
        <div className="og-form-section__header">
          <div className="og-form-section__title-group">
            <span className="og-form-section__number" aria-hidden="true">01</span>
            <h2 id="section-01-heading" className="og-form-section__title">
              Thông tin cơ bản
            </h2>
          </div>
        </div>

        {/* Tiêu đề tin đăng */}
        <div className="og-form-group">
          <label htmlFor="listing-title" className="og-form-label">
            <span>Tiêu đề tin đăng</span>
            <span className="og-form-required">*</span>
          </label>
          <input
            id="listing-title"
            type="text"
            className={`og-input ${fieldErrors.title ? 'og-input--error' : ''}`}
            placeholder="Ví dụ: iPhone 13 Pro Max 128GB Xanh Sierra, nguyên zin 99%"
            value={title}
            maxLength={200}
            onChange={(e) => setTitle(e.target.value)}
            disabled={isSubmitting}
            required
            aria-describedby="listing-title-hint listing-title-error"
          />
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            {fieldErrors.title ? (
              <span id="listing-title-error" className="og-form-error">{fieldErrors.title}</span>
            ) : (
              <span id="listing-title-hint" className="og-form-hint">Nên đặt tên rõ ràng gồm: Hãng + Tên máy + Dung lượng / Màu sắc</span>
            )}
            <span className="og-char-counter">{title.length} / 200</span>
          </div>
        </div>

        {/* Danh mục (Selectable Chips Grid) */}
        <div className="og-form-group" style={{ marginTop: '16px' }}>
          <fieldset
            style={{ border: 'none', padding: 0, margin: 0 }}
            disabled={isSubmitting}
            aria-describedby="listing-categories-hint listing-categories-error"
            aria-invalid={Boolean(fieldErrors.categoryIds)}
          >
            <legend className="og-form-label" style={{ marginBottom: '4px' }}>
              <span>Danh mục sản phẩm</span>
              <span className="og-form-required">*</span>
            </legend>
            <span id="listing-categories-hint" className="og-form-hint">
              Chọn một hoặc nhiều danh mục phù hợp nhất với sản phẩm của bạn.
            </span>

            <div className="og-category-chips-grid">
              {categories.map((c) => {
                const isSelected = categoryIds.includes(c.categoryId);
                return (
                  <label
                    key={c.categoryId}
                    className={`og-category-chip ${isSelected ? 'og-category-chip--selected' : ''}`}
                  >
                    <input
                      type="checkbox"
                      checked={isSelected}
                      disabled={isSubmitting}
                      onChange={(event) =>
                        setCategoryIds((selected) =>
                          event.target.checked
                            ? [...selected, c.categoryId]
                            : selected.filter((id) => id !== c.categoryId)
                        )
                      }
                    />
                    <span className="og-category-chip__name">{c.categoryName}</span>
                  </label>
                );
              })}
            </div>
            {categories.length === 0 && (
              <span className="og-form-hint">Đang tải danh mục...</span>
            )}
          </fieldset>
          <span
            id="listing-categories-error"
            className="og-form-error"
            role={fieldErrors.categoryIds ? 'alert' : undefined}
          >
            {fieldErrors.categoryIds}
          </span>
        </div>

        {/* Tình trạng & Giá niêm yết (2 Columns) */}
        <div className="og-form-grid-2" style={{ marginTop: '16px' }}>
          <div className="og-form-group">
            <label htmlFor="listing-condition" className="og-form-label">
              <span>Tình trạng máy</span>
              <span className="og-form-required">*</span>
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
            <span className="og-form-hint">Đánh giá trung thực tình trạng ngoại quan và linh kiện.</span>
          </div>

          <div className="og-form-group">
            <label htmlFor="listing-price" className="og-form-label">
              <span>Giá niêm yết (₫)</span>
              <span className="og-form-required">*</span>
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
        </div>

        {/* Khu vực giao dịch */}
        <div className="og-form-group" style={{ marginTop: '16px' }}>
          <label htmlFor="listing-location" className="og-form-label">
            <span>Khu vực giao dịch</span>
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
          <span className="og-form-hint">Địa điểm thuận tiện để xem đồ hoặc chuyển phát nhanh.</span>
        </div>
      </section>

      {/* 02. Tình trạng & chất lượng */}
      <section className="og-form-section" aria-labelledby="section-02-heading">
        <div className="og-form-section__header">
          <div className="og-form-section__title-group">
            <span className="og-form-section__number" aria-hidden="true">02</span>
            <h2 id="section-02-heading" className="og-form-section__title">
              Tình trạng & chất lượng
            </h2>
          </div>
        </div>

        <div className="og-form-group">
          <label htmlFor="listing-usage" className="og-form-label">
            <span>Thời gian đã sử dụng</span>
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
          <span className="og-form-hint">Cho biết thời gian bạn đã sở hữu hoặc tình trạng bảo hành.</span>
        </div>

        <div className="og-form-group">
          <label htmlFor="listing-defects" className="og-form-label">
            <span>Khuyết điểm / Vết xước / Lỗi (nếu có)</span>
          </label>
          <textarea
            id="listing-defects"
            rows={3}
            style={{ minHeight: '96px' }}
            className={`og-input ${fieldErrors.defects ? 'og-input--error' : ''}`}
            placeholder="Ví dụ: Viền góc dưới có chấm xước dăm nhỏ, màn hình dán cường lực từ lúc mua không vết xước."
            value={defects}
            maxLength={2000}
            onChange={(e) => setDefects(e.target.value)}
            disabled={isSubmitting}
          />
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            {fieldErrors.defects ? (
              <span className="og-form-error">{fieldErrors.defects}</span>
            ) : (
              <span className="og-form-hint">Mô tả trung thực lỗi giúp người mua tin tưởng và tránh khiếu nại sau này.</span>
            )}
            <span className="og-char-counter">{defects.length} / 2000</span>
          </div>
        </div>

        <div className="og-form-grid-2">
          <div className="og-form-group">
            <label htmlFor="listing-repairs" className="og-form-label">
              <span>Lịch sử sửa chữa / Thay thế</span>
            </label>
            <input
              id="listing-repairs"
              type="text"
              className={`og-input ${fieldErrors.repairHistory ? 'og-input--error' : ''}`}
              placeholder="Ví dụ: Nguyên bản chưa sửa / Đã thay pin chính hãng"
              value={repairHistory}
              maxLength={2000}
              onChange={(e) => setRepairHistory(e.target.value)}
              disabled={isSubmitting}
            />
            {fieldErrors.repairHistory && <span className="og-form-error">{fieldErrors.repairHistory}</span>}
          </div>

          <div className="og-form-group">
            <label htmlFor="listing-accessories" className="og-form-label">
              <span>Phụ kiện đi kèm</span>
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
      </section>

      {/* 03. Giao dịch & xác thực */}
      <section className="og-form-section" aria-labelledby="section-03-heading">
        <div className="og-form-section__header">
          <div className="og-form-section__title-group">
            <span className="og-form-section__number" aria-hidden="true">03</span>
            <h2 id="section-03-heading" className="og-form-section__title">
              Giao dịch & xác thực
            </h2>
          </div>
        </div>

        {/* eKYC Verification Card */}
        <div className="og-verification-card">
          <div className="og-verification-card__icon-wrap" aria-hidden="true">
            🛡️
          </div>
          <div className="og-verification-card__body">
            <h3 className="og-verification-card__title">Xác minh người mua an toàn</h3>
            <p className="og-verification-card__desc">
              Xác thực căn cước công dân (eKYC) giúp tăng độ tin cậy của giao dịch và phòng ngừa rủi ro gian lận.
              Khi kích hoạt, chỉ người mua đã hoàn tất eKYC mới có thể đặt mua món hàng này.
            </p>
            <label htmlFor="listing-requires-ekyc" className="og-verification-card__checkbox-label">
              <input
                id="listing-requires-ekyc"
                type="checkbox"
                className="og-verification-card__checkbox"
                checked={requiresBuyerEkyc}
                onChange={(e) => setRequiresBuyerEkyc(e.target.checked)}
                disabled={isSubmitting}
              />
              <span>Yêu cầu Người mua phải hoàn tất xác thực CCCD (eKYC) để đặt mua món hàng này</span>
            </label>
          </div>
        </div>
      </section>

      {/* 04. Mô tả chi tiết */}
      <section className="og-form-section" aria-labelledby="section-04-heading">
        <div className="og-form-section__header">
          <div className="og-form-section__title-group">
            <span className="og-form-section__number" aria-hidden="true">04</span>
            <h2 id="section-04-heading" className="og-form-section__title">
              Mô tả sản phẩm
            </h2>
          </div>
        </div>

        <div className="og-form-group">
          <label htmlFor="listing-description" className="og-form-label">
            <span>Nội dung bài đăng</span>
            <span className="og-form-required">*</span>
          </label>
          <textarea
            id="listing-description"
            rows={6}
            style={{ minHeight: '150px' }}
            className={`og-input ${fieldErrors.description ? 'og-input--error' : ''}`}
            placeholder="Mô tả kỹ hơn về tính năng nổi bật, xuất xứ, lý do bán, các lưu ý khi kiểm tra hàng..."
            value={description}
            maxLength={5000}
            onChange={(e) => setDescription(e.target.value)}
            disabled={isSubmitting}
            required
            aria-describedby="listing-description-hint listing-description-error"
          />
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            {fieldErrors.description ? (
              <span id="listing-description-error" className="og-form-error">{fieldErrors.description}</span>
            ) : (
              <span id="listing-description-hint" className="og-form-hint">Mô tả càng chi tiết và trung thực sẽ giúp sản phẩm được duyệt và bán nhanh hơn.</span>
            )}
            <span className="og-char-counter">{description.length} / 5000 ký tự</span>
          </div>
        </div>
      </section>

      {/* 05. Hình ảnh & Video */}
      <section className="og-form-section" aria-labelledby="section-05-heading">
        <div className="og-form-section__header">
          <div className="og-form-section__title-group">
            <span className="og-form-section__number" aria-hidden="true">05</span>
            <h2 id="section-05-heading" className="og-form-section__title">
              Hình ảnh & Video
            </h2>
          </div>
          <div style={{ fontSize: '0.85rem', fontWeight: 600, color: hasMinMedia ? '#166534' : '#b45309' }}>
            {hasMinMedia ? '✅ Đã đủ điều kiện gửi duyệt' : '⚠️ Cần ít nhất 1 ảnh và 1 video'}
          </div>
        </div>

        {mediaError && (
          <div className="og-alert og-alert--danger" role="alert" style={{ marginBottom: '16px' }}>
            ⚠️ {mediaError}
          </div>
        )}

        {initialData?.productId ? (
          <div>
            {/* Upload Zone */}
            <div
              className={`og-media-dropzone ${isDragOver ? 'og-media-dropzone--dragover' : ''}`}
              onDragOver={(e) => {
                e.preventDefault();
                setIsDragOver(true);
              }}
              onDragLeave={() => setIsDragOver(false)}
              onDrop={handleDrop}
            >
              <div className="og-media-dropzone__icon" aria-hidden="true">
                🖼️
              </div>
              <div className="og-media-dropzone__title">
                Thêm hình ảnh hoặc video cận cảnh
              </div>
              <div className="og-media-dropzone__desc">
                Kéo thả file vào đây hoặc chọn tệp từ thiết bị của bạn
              </div>

              <div style={{ display: 'flex', gap: '10px', marginTop: '6px', flexWrap: 'wrap', justifyContent: 'center' }}>
                {images.length < 5 && onUploadMedia && (
                  <button
                    type="button"
                    className="og-button og-button--primary og-media-dropzone__btn"
                    onClick={() => imageInputRef.current?.click()}
                    disabled={isMediaActionLoading}
                  >
                    <span>📸 Chọn ảnh ({images.length}/5)</span>
                  </button>
                )}

                {videos.length < 1 && onUploadMedia && (
                  <button
                    type="button"
                    className="og-button og-button--secondary og-media-dropzone__btn"
                    onClick={() => videoInputRef.current?.click()}
                    disabled={isMediaActionLoading}
                  >
                    <span>🎥 Chọn video cận cảnh ({videos.length}/1)</span>
                  </button>
                )}
              </div>

              <div className="og-media-dropzone__hint">
                JPG, PNG, WEBP (tối đa 5 MB) • Video MP4, WEBM (tối đa 50 MB, ≤ 60s)
              </div>
            </div>

            {/* Hidden file inputs */}
            <input
              ref={imageInputRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              style={{ display: 'none' }}
              onChange={handleImageFileChange}
              disabled={isMediaActionLoading}
            />
            <input
              ref={videoInputRef}
              type="file"
              accept="video/mp4,video/webm"
              style={{ display: 'none' }}
              onChange={handleVideoFileChange}
              disabled={isMediaActionLoading}
            />

            {/* Images Preview Grid */}
            <div style={{ marginBottom: '24px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                <span style={{ fontWeight: 600, fontSize: '14px', color: 'var(--og-color-text-primary)' }}>
                  Hình ảnh đã tải lên ({images.length}/5 - tối thiểu 1):
                </span>
              </div>

              {images.length > 0 ? (
                <div className="og-media-grid">
                  {images.map((img) => (
                    <div key={img.mediaId} className="og-media-card">
                      <img src={img.mediaUrl} alt="Product media" />
                      {onDeleteMedia && (
                        <button
                          type="button"
                          className="og-media-card__delete"
                          onClick={() => onDeleteMedia(img.mediaId)}
                          disabled={isMediaActionLoading}
                          title="Xóa ảnh này"
                          aria-label="Xóa ảnh này"
                        >
                          ✕
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              ) : (
                <p style={{ fontSize: '13px', color: 'var(--og-color-text-muted)', margin: 0, fontStyle: 'italic' }}>
                  Vui lòng thêm ít nhất 1 hình ảnh rõ nét chụp các góc cạnh của sản phẩm.
                </p>
              )}
            </div>

            {/* Video Preview */}
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                <span style={{ fontWeight: 600, fontSize: '14px', color: 'var(--og-color-text-primary)' }}>
                  Video quay cận cảnh ({videos.length}/1 - bắt buộc 1 video ≤ 60s):
                </span>
              </div>

              {videos.length > 0 ? (
                <div>
                  {videos.map((vid) => (
                    <div
                      key={vid.mediaId}
                      style={{
                        position: 'relative',
                        maxWidth: '380px',
                        borderRadius: '10px',
                        overflow: 'hidden',
                        border: '1px solid var(--og-color-border)',
                        background: '#000',
                      }}
                    >
                      <video src={vid.mediaUrl} controls style={{ width: '100%', maxHeight: '220px', display: 'block' }} />
                      {onDeleteMedia && (
                        <button
                          type="button"
                          onClick={() => onDeleteMedia(vid.mediaId)}
                          disabled={isMediaActionLoading}
                          style={{
                            position: 'absolute',
                            top: '8px',
                            right: '8px',
                            background: 'rgba(15, 23, 42, 0.8)',
                            border: 'none',
                            borderRadius: '6px',
                            color: '#ef4444',
                            padding: '4px 10px',
                            cursor: 'pointer',
                            fontSize: '12px',
                            fontWeight: 600,
                          }}
                        >
                          🗑️ Xóa video
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              ) : (
                <p style={{ fontSize: '13px', color: 'var(--og-color-text-muted)', margin: 0, fontStyle: 'italic' }}>
                  Quy định kiểm duyệt yêu cầu mỗi tin đăng cần có 1 video quay cận cảnh các góc món đồ trước khi gửi duyệt.
                </p>
              )}
            </div>
          </div>
        ) : (
          <div
            style={{
              padding: '24px 20px',
              background: 'var(--og-color-bg)',
              borderRadius: '10px',
              border: '1px dashed var(--og-color-border-strong)',
              textAlign: 'center',
            }}
          >
            <div style={{ fontSize: '2rem', marginBottom: '6px' }}>📸</div>
            <p style={{ margin: '0 0 6px', color: 'var(--og-color-text-primary)', fontWeight: 600, fontSize: '14.5px' }}>
              Tải lên hình ảnh & video ở bước tiếp theo
            </p>
            <p style={{ margin: 0, color: 'var(--og-color-text-muted)', fontSize: '13px', lineHeight: 1.4 }}>
              Sau khi bấm <strong>&ldquo;Tạo bản nháp&rdquo;</strong>, bạn sẽ có thể tải lên từ 1 đến 5 hình ảnh và 1 video quay cận cảnh món đồ để gửi kiểm duyệt.
            </p>
          </div>
        )}
      </section>

      {/* 11. Sticky Action Bar */}
      <div className="og-sticky-action-bar">
        <div className="og-sticky-action-bar__inner">
          <button
            type="submit"
            className="og-button og-button--primary"
            disabled={isSubmitting || isMediaActionLoading}
            style={{ height: '42px', padding: '0 24px', fontSize: '14.5px' }}
          >
            {isSubmitting ? 'Đang lưu...' : submitButtonLabel}
          </button>

          {canSubmitForReview && onSubmitForReview && (
            <button
              type="button"
              className="og-button og-button--gold"
              onClick={onSubmitForReview}
              disabled={isSubmitting || isMediaActionLoading}
              style={{ height: '42px', padding: '0 20px', fontSize: '14.5px' }}
            >
              🚀 Gửi kiểm duyệt (PENDING)
            </button>
          )}
        </div>
      </div>
    </form>
  );
};
