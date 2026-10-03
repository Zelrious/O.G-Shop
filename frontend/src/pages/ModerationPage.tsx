import React, { useEffect, useState, useCallback, useRef } from 'react';
import { ApiError, listingApi, ModerationProduct } from '../features/listing';
import { Alert, Badge } from '../shared/components';

function isConflictError(err: unknown): boolean {
  if (err instanceof ApiError) {
    if (err.status === 409) return true;
    if (
      err.code === 'PRODUCT_VERSION_CONFLICT' ||
      err.code === 'COMMAND_KEY_CONFLICT' ||
      err.code === 'PRODUCT_STATE_CONFLICT' ||
      err.code === 'DATA_CONFLICT'
    ) {
      return true;
    }
  }
  return false;
}

export const ModerationPage: React.FC = () => {
  const [items, setItems] = useState<ModerationProduct[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [actionLoadingId, setActionLoadingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isConflict, setIsConflict] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [rejectReasonId, setRejectReasonId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  // Stable command keys map to guarantee same key reuse during retry
  const commandKeysRef = useRef<Map<string, string>>(new Map());

  const getOrCreateCommandKey = useCallback((prefix: string, productId: number, version: number, payloadSuffix = ''): string => {
    const mapKey = `${prefix}:${productId}:${version}:${payloadSuffix}`;
    const existing = commandKeysRef.current.get(mapKey);
    if (existing) {
      return existing;
    }
    const newKey = `ck-${prefix}-${productId}-${version}-${Math.random().toString(36).substring(2, 10)}`;
    commandKeysRef.current.set(mapKey, newKey);
    return newKey;
  }, []);

  const clearCommandKey = useCallback((prefix: string, productId: number, version: number, payloadSuffix = ''): void => {
    const mapKey = `${prefix}:${productId}:${version}:${payloadSuffix}`;
    commandKeysRef.current.delete(mapKey);
  }, []);

  // Pagination states
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  const loadPending = useCallback(async (targetPage = page, targetSize = size) => {
    setIsLoading(true);
    setError(null);
    setIsConflict(false);
    commandKeysRef.current.clear();
    try {
      const res = await listingApi.getPendingProducts(targetPage, targetSize);
      setItems(res.items || []);
      setPage(res.page ?? targetPage);
      setSize(res.size ?? targetSize);
      setTotalPages(res.totalPages ?? 1);
      setTotalElements(res.totalElements ?? (res.items?.length || 0));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Không thể tải danh sách tin chờ duyệt.');
    } finally {
      setIsLoading(false);
    }
  }, [page, size]);

  useEffect(() => {
    loadPending(page, size);
  }, [page, size, loadPending]);

  const handleApprove = async (item: ModerationProduct) => {
    setActionLoadingId(item.productId);
    setError(null);
    setIsConflict(false);
    setSuccessMessage(null);
    try {
      const commandKey = getOrCreateCommandKey('app', item.productId, item.version);
      const res = await listingApi.approveProduct(item.productId, {
        expectedVersion: item.version,
        commandKey,
      });
      clearCommandKey('app', item.productId, item.version);
      setSuccessMessage(res.message || `Phê duyệt tin #${item.productId} thành công! Tin đã chuyển sang trạng thái ACTIVE.`);
      // Remove approved item from current view
      setItems((prev) => prev.filter((i) => i.productId !== item.productId));
      setTotalElements((prev) => Math.max(0, prev - 1));
    } catch (err: unknown) {
      const errMsg = err instanceof Error ? err.message : 'Phê duyệt tin thất bại.';
      setError(errMsg);
      if (isConflictError(err)) {
        setIsConflict(true);
      }
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleRejectSubmit = async (item: ModerationProduct) => {
    const trimmed = rejectReason.trim();
    if (!trimmed) {
      setError('Vui lòng nhập lý do từ chối tin đăng (không được để trống hoặc chỉ có khoảng trắng).');
      return;
    }
    if (trimmed.length > 500) {
      setError(`Lý do từ chối không được vượt quá 500 ký tự (hiện tại: ${trimmed.length} ký tự).`);
      return;
    }

    setActionLoadingId(item.productId);
    setError(null);
    setIsConflict(false);
    setSuccessMessage(null);
    try {
      const commandKey = getOrCreateCommandKey('rej', item.productId, item.version, trimmed);
      const res = await listingApi.rejectProduct(item.productId, {
        reason: trimmed,
        expectedVersion: item.version,
        commandKey,
      });
      clearCommandKey('rej', item.productId, item.version, trimmed);
      setSuccessMessage(res.message || `Đã từ chối tin #${item.productId}. Tin đã chuyển sang trạng thái REJECTED.`);
      setRejectReasonId(null);
      setRejectReason('');
      setItems((prev) => prev.filter((i) => i.productId !== item.productId));
      setTotalElements((prev) => Math.max(0, prev - 1));
    } catch (err: unknown) {
      const errMsg = err instanceof Error ? err.message : 'Từ chối tin thất bại.';
      setError(errMsg);
      if (isConflictError(err)) {
        setIsConflict(true);
      }
    } finally {
      setActionLoadingId(null);
    }
  };

  return (
    <div className="og-moderation-page" style={{ maxWidth: '1000px', margin: '0 auto', padding: '24px 16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 700, margin: '0 0 8px 0' }}>
            🛡️ Kiểm duyệt tin đăng đồ cũ (KTV - UC70)
          </h1>
          <p style={{ margin: 0, color: 'rgba(255,255,255,0.7)', fontSize: '0.95rem' }}>
            Kiểm tra tính trung thực, chất lượng hình ảnh và video quay cận cảnh (Rule V6) trước khi cấp phép bán công khai trên chợ O.G Shop.
          </p>
        </div>
        <button
          type="button"
          className="og-button og-button--outline og-button--sm"
          onClick={() => loadPending(page, size)}
          disabled={isLoading}
          style={{ whiteSpace: 'nowrap' }}
        >
          🔄 Làm mới danh sách
        </button>
      </div>

      {successMessage && (
        <div style={{ marginBottom: '16px' }}>
          <Alert type="success">{successMessage}</Alert>
        </div>
      )}

      {error && (
        <div style={{ marginBottom: '16px' }}>
          <Alert type="danger">
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <span>{error}</span>
              {isConflict && (
                <div>
                  <button
                    type="button"
                    className="og-button og-button--primary og-button--sm"
                    onClick={() => loadPending(page, size)}
                    style={{ marginTop: '4px' }}
                  >
                    🔄 Tải lại dữ liệu mới nhất (HTTP 409)
                  </button>
                </div>
              )}
            </div>
          </Alert>
        </div>
      )}

      {isLoading ? (
        <div className="og-detail-loading" aria-busy="true">
          <div className="og-spinner" />
          <p>Đang tải danh sách tin chờ duyệt...</p>
        </div>
      ) : items.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '60px 20px', background: 'rgba(255,255,255,0.02)', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.06)' }}>
          <div style={{ fontSize: '3rem', marginBottom: '16px' }}>🎉</div>
          <h2 style={{ fontSize: '1.25rem', marginBottom: '8px' }}>Không có tin nào đang chờ duyệt</h2>
          <p style={{ margin: 0, color: 'rgba(255,255,255,0.6)' }}>Tất cả tin đăng đồ cũ đều đã được xử lý xong.</p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
          {items.map((item) => {
            const images = item.media.filter((m) => m.mediaType === 'IMAGE');
            const videos = item.media.filter((m) => m.mediaType === 'VIDEO');
            const hasRequiredMedia = images.length >= 1 && videos.length >= 1;
            const categoriesList = (item.categories && item.categories.length > 0 ? item.categories : (item.category ? [item.category] : []))
              .map((c) => c.categoryName)
              .join(' · ');

            return (
              <div
                key={item.productId}
                style={{
                  background: 'rgba(255,255,255,0.03)',
                  border: '1px solid rgba(255,255,255,0.1)',
                  borderRadius: '12px',
                  padding: '20px',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '12px', marginBottom: '16px' }}>
                  <div>
                    <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap', marginBottom: '6px' }}>
                      <Badge variant="pending">PENDING</Badge>
                      {item.requiresBuyerEkyc ? (
                        <span style={{ background: 'rgba(59,130,246,0.2)', color: '#60a5fa', fontSize: '0.75rem', fontWeight: 600, padding: '2px 8px', borderRadius: '4px' }}>
                          🛡️ Yêu cầu eKYC Người mua
                        </span>
                      ) : (
                        <span style={{ background: 'rgba(255,255,255,0.06)', color: 'rgba(255,255,255,0.6)', fontSize: '0.75rem', padding: '2px 8px', borderRadius: '4px' }}>
                          eKYC tùy chọn
                        </span>
                      )}
                      <span
                        style={{ background: 'rgba(156,163,175,0.15)', color: '#9ca3af', fontSize: '0.75rem', fontWeight: 600, padding: '2px 8px', borderRadius: '4px' }}
                        title="AI tự động tạm hoãn theo chính sách DP-22"
                      >
                        🤖 Chưa kiểm tra AI
                      </span>
                      <span style={{ fontSize: '0.85rem', color: 'rgba(255,255,255,0.6)' }}>
                        Mã tin: #{item.productId} | Người bán: #{item.sellerId} | Bản: v{item.version}
                      </span>
                    </div>
                    <h2 style={{ fontSize: '1.25rem', margin: '4px 0' }}>{item.title}</h2>
                    {categoriesList && (
                      <div style={{ fontSize: '0.85rem', color: '#f59e0b', marginTop: '2px' }}>
                        Danh mục: {categoriesList}
                      </div>
                    )}
                  </div>

                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: '1.2rem', fontWeight: 700, color: '#f59e0b' }}>
                      {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: item.currency || 'VND' }).format(item.listedPrice)}
                    </div>
                    <div style={{ fontSize: '0.85rem', color: 'rgba(255,255,255,0.6)' }}>
                      Tình trạng: <strong>{item.condition}</strong>
                    </div>
                    {item.location && (
                      <div style={{ fontSize: '0.8rem', color: 'rgba(255,255,255,0.5)', marginTop: '2px' }}>
                        📍 {item.location}
                      </div>
                    )}
                  </div>
                </div>

                {/* Description */}
                <div style={{ marginBottom: '16px', background: 'rgba(0,0,0,0.2)', padding: '12px', borderRadius: '8px' }}>
                  <h4 style={{ margin: '0 0 6px 0', fontSize: '0.9rem', color: 'rgba(255,255,255,0.8)' }}>Mô tả sản phẩm:</h4>
                  <p style={{ margin: 0, fontSize: '0.9rem', whiteSpace: 'pre-wrap', color: 'rgba(255,255,255,0.9)' }}>
                    {item.description}
                  </p>
                </div>

                {/* Inspection checklist details */}
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
                    gap: '12px',
                    marginBottom: '16px',
                    background: 'rgba(255,255,255,0.02)',
                    padding: '12px',
                    borderRadius: '8px',
                    border: '1px solid rgba(255,255,255,0.05)',
                  }}
                >
                  <div>
                    <span style={{ fontSize: '0.8rem', color: 'rgba(255,255,255,0.5)', display: 'block' }}>Thời gian đã dùng:</span>
                    <strong style={{ fontSize: '0.9rem' }}>{item.usageDuration || 'Chưa cung cấp'}</strong>
                  </div>
                  <div>
                    <span style={{ fontSize: '0.8rem', color: 'rgba(255,255,255,0.5)', display: 'block' }}>Khuyết điểm / trầy xước:</span>
                    <strong style={{ fontSize: '0.9rem' }}>{item.defects || 'Không ghi nhận'}</strong>
                  </div>
                  <div>
                    <span style={{ fontSize: '0.8rem', color: 'rgba(255,255,255,0.5)', display: 'block' }}>Lịch sử sửa chữa:</span>
                    <strong style={{ fontSize: '0.9rem' }}>{item.repairHistory || 'Chưa từng sửa chữa'}</strong>
                  </div>
                  <div>
                    <span style={{ fontSize: '0.8rem', color: 'rgba(255,255,255,0.5)', display: 'block' }}>Phụ kiện kèm theo:</span>
                    <strong style={{ fontSize: '0.9rem' }}>{item.includedAccessories || 'Không có phụ kiện'}</strong>
                  </div>
                </div>

                {/* Media Inspector */}
                <div style={{ marginBottom: '20px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
                    <h4 style={{ margin: 0, fontSize: '0.95rem' }}>
                      📸 Media kiểm định theo Rule V6 ({images.length} ảnh, {videos.length} video):
                    </h4>
                    {!hasRequiredMedia && (
                      <span style={{ color: '#ef4444', fontSize: '0.85rem', fontWeight: 600 }}>
                        ⚠️ Chưa đủ điều kiện duyệt: cần ≥ 1 ảnh và ≥ 1 video cận cảnh!
                      </span>
                    )}
                  </div>

                  {/* Images */}
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))', gap: '12px', marginBottom: '16px' }}>
                    {images.map((img) => (
                      <a
                        key={img.mediaId}
                        href={img.mediaUrl}
                        target="_blank"
                        rel="noreferrer"
                        title="Bấm để xem ảnh kích thước gốc"
                        style={{ display: 'block', borderRadius: '8px', overflow: 'hidden', border: '1px solid rgba(255,255,255,0.15)' }}
                      >
                        <img src={img.mediaUrl} alt="Product media" style={{ width: '100%', height: '110px', objectFit: 'cover', display: 'block' }} />
                      </a>
                    ))}
                    {images.length === 0 && (
                      <div style={{ padding: '16px', background: 'rgba(239,68,68,0.08)', borderRadius: '8px', border: '1px dashed #ef4444', color: '#ef4444', fontSize: '0.85rem' }}>
                        Thiếu hình ảnh sản phẩm
                      </div>
                    )}
                  </div>

                  {/* Video */}
                  {videos.length > 0 ? (
                    <div>
                      <span style={{ fontSize: '0.85rem', color: 'rgba(255,255,255,0.7)', display: 'block', marginBottom: '6px' }}>
                        🎥 Video quay cận cảnh góc cạnh máy:
                      </span>
                      <video src={videos[0].mediaUrl} controls style={{ maxWidth: '440px', width: '100%', borderRadius: '8px', border: '1px solid rgba(255,255,255,0.2)' }} />
                    </div>
                  ) : (
                    <div style={{ color: '#ef4444', fontSize: '0.85rem', fontWeight: 600 }}>
                      ⚠️ Tin này thiếu video cận cảnh sản phẩm!
                    </div>
                  )}
                </div>

                {/* Reject dialog inline */}
                {rejectReasonId === item.productId && (
                  <div style={{ background: 'rgba(239, 68, 68, 0.1)', border: '1px solid rgba(239, 68, 68, 0.3)', padding: '16px', borderRadius: '8px', marginBottom: '16px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                      <label htmlFor={`reject-reason-${item.productId}`} style={{ fontWeight: 600, fontSize: '0.9rem' }}>
                        Lý do từ chối duyệt (bắt buộc, tối đa 500 ký tự):
                      </label>
                      <span style={{ fontSize: '0.8rem', color: rejectReason.length > 500 ? '#ef4444' : 'rgba(255,255,255,0.6)' }}>
                        {rejectReason.length}/500
                      </span>
                    </div>
                    <textarea
                      id={`reject-reason-${item.productId}`}
                      className="og-input"
                      rows={3}
                      placeholder="Ví dụ: Video không quay cận cảnh góc máy có vết nứt, vui lòng quay lại video rõ ràng hơn."
                      value={rejectReason}
                      onChange={(e) => setRejectReason(e.target.value)}
                      maxLength={500}
                      style={{ marginBottom: '12px', width: '100%', resize: 'vertical' }}
                    />
                    <div style={{ display: 'flex', gap: '8px' }}>
                      <button
                        type="button"
                        className="og-button og-button--danger og-button--sm"
                        onClick={() => handleRejectSubmit(item)}
                        disabled={actionLoadingId === item.productId || !rejectReason.trim()}
                      >
                        {actionLoadingId === item.productId ? 'Đang gửi...' : 'Xác nhận từ chối'}
                      </button>
                      <button
                        type="button"
                        className="og-button og-button--ghost og-button--sm"
                        onClick={() => { setRejectReasonId(null); setRejectReason(''); }}
                      >
                        Hủy
                      </button>
                    </div>
                  </div>
                )}

                {/* Actions */}
                <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end', alignItems: 'center' }}>
                  <button
                    type="button"
                    className="og-button og-button--danger"
                    onClick={() => {
                      setRejectReasonId(item.productId);
                      setRejectReason('');
                    }}
                    disabled={actionLoadingId === item.productId}
                  >
                    ❌ Từ chối tin
                  </button>
                  <button
                    type="button"
                    className="og-button og-button--primary"
                    onClick={() => handleApprove(item)}
                    disabled={actionLoadingId === item.productId || !hasRequiredMedia}
                    style={{ background: '#10b981', color: '#fff' }}
                    title={!hasRequiredMedia ? 'Không thể duyệt tin khi thiếu ảnh hoặc video cận cảnh' : 'Phê duyệt tin'}
                  >
                    {actionLoadingId === item.productId ? 'Đang duyệt...' : '✅ Phê duyệt (ACTIVE)'}
                  </button>
                </div>
              </div>
            );
          })}

          {/* Pagination bar */}
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: '12px',
              padding: '16px 20px',
              background: 'rgba(255,255,255,0.02)',
              borderRadius: '8px',
              border: '1px solid rgba(255,255,255,0.06)',
              marginTop: '12px',
            }}
          >
            <div style={{ fontSize: '0.9rem', color: 'rgba(255,255,255,0.7)' }}>
              Trang <strong>{page + 1}</strong> / {Math.max(1, totalPages)} (Tổng <strong>{totalElements}</strong> tin chờ duyệt)
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <label style={{ fontSize: '0.85rem', color: 'rgba(255,255,255,0.6)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                Hiển thị:
                <select
                  value={size}
                  onChange={(e) => setSize(Number(e.target.value))}
                  style={{
                    background: '#1e293b',
                    color: '#fff',
                    border: '1px solid rgba(255,255,255,0.2)',
                    borderRadius: '4px',
                    padding: '4px 8px',
                    fontSize: '0.85rem',
                  }}
                >
                  <option value={10}>10 tin</option>
                  <option value={20}>20 tin</option>
                  <option value={50}>50 tin</option>
                </select>
              </label>

              <div style={{ display: 'flex', gap: '6px' }}>
                <button
                  type="button"
                  className="og-button og-button--outline og-button--sm"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0 || isLoading}
                >
                  « Trang trước
                </button>
                <button
                  type="button"
                  className="og-button og-button--outline og-button--sm"
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1 || isLoading}
                >
                  Trang sau »
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
