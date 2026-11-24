import React, { useState } from 'react';
import { disputeApi } from './disputeApi';
import { resolveReturnMediaUrl } from './mediaUrl';
import { returnCaseApi } from './returnCaseApi';

interface BuyerDisputeModalProps {
  isOpen: boolean;
  orderId: number;
  productTitle: string;
  onClose: () => void;
  onSuccess: () => void;
}

export const BuyerDisputeModal: React.FC<BuyerDisputeModalProps> = ({
  isOpen,
  orderId,
  productTitle,
  onClose,
  onSuccess,
}) => {
  const [reason, setReason] = useState('Hàng không đúng mô tả');
  const [description, setDescription] = useState('');
  const [videoUnboxingUrl, setVideoUnboxingUrl] = useState('');
  const [evidenceInput, setEvidenceInput] = useState('');
  const [uploadingMedia, setUploadingMedia] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleUploadFiles = async (files: FileList | null, target: 'VIDEO' | 'IMAGE') => {
    if (!files || files.length === 0) return;
    setError(null);
    setUploadingMedia(true);
    try {
      for (const file of Array.from(files)) {
        const dto = await returnCaseApi.uploadEvidence(file, target, target === 'VIDEO' ? 15 : undefined);
        const resolvedUrl = resolveReturnMediaUrl(dto.objectKey || '');
        if (target === 'VIDEO') {
          setVideoUnboxingUrl(resolvedUrl);
        } else {
          setEvidenceInput((prev) => (prev.trim() ? `${prev.trim()}\n${resolvedUrl}` : resolvedUrl));
        }
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể tải tệp bằng chứng lên.');
    } finally {
      setUploadingMedia(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reason.trim()) {
      setError('Vui lòng chọn lý do khiếu nại.');
      return;
    }

    const buyerEvidenceUrls = evidenceInput
      .split('\n')
      .map((s) => s.trim())
      .filter((s) => s.length > 0);

    setError(null);
    setSubmitting(true);
    try {
      await disputeApi.createBuyerDispute(orderId, {
        reason,
        description: description.trim(),
        videoUnboxingUrl: videoUnboxingUrl.trim() || undefined,
        buyerEvidenceUrls: buyerEvidenceUrls.length > 0 ? buyerEvidenceUrls : undefined,
      });
      onSuccess();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể gửi yêu cầu khiếu nại.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(15, 23, 42, 0.6)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1100,
        padding: '16px',
        backdropFilter: 'blur(2px)',
      }}
    >
      <div
        style={{
          background: 'var(--og-color-surface, #fff)',
          borderRadius: '16px',
          border: '1px solid var(--og-color-border, #e2e8f0)',
          padding: '24px',
          width: '100%',
          maxWidth: '540px',
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.2)',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '14px' }}>
          <div>
            <h3 style={{ margin: '0 0 4px', fontSize: '1.2rem', fontWeight: 700, color: 'var(--og-color-text-primary, #0f172a)' }}>
              Yêu cầu Trả hàng / Khiếu nại (UC62)
            </h3>
            <p style={{ margin: 0, fontSize: '0.84rem', color: 'var(--og-color-text-secondary, #64748b)' }}>
              Đơn hàng #{orderId} - {productTitle}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            style={{ background: 'transparent', border: 'none', fontSize: '1.2rem', cursor: 'pointer', color: '#64748b' }}
          >
            ✕
          </button>
        </div>

        {error && (
          <div
            style={{
              padding: '10px 14px',
              borderRadius: '8px',
              background: '#fef2f2',
              border: '1px solid #fca5a5',
              color: '#b91c1c',
              fontSize: '0.84rem',
              marginBottom: '14px',
            }}
          >
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <div>
            <label style={{ display: 'block', fontSize: '0.84rem', fontWeight: 600, marginBottom: '6px', color: '#334155' }}>
              Lý do khiếu nại *
            </label>
            <select
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              style={{
                width: '100%',
                padding: '9px 12px',
                borderRadius: '8px',
                border: '1px solid #cbd5e1',
                fontSize: '0.88rem',
              }}
            >
              <option value="Hàng không đúng mô tả">Hàng không đúng mô tả</option>
              <option value="Hàng bị hỏng/vỡ trong quá trình vận chuyển">Hàng bị hỏng/vỡ trong quá trình vận chuyển</option>
              <option value="Hàng giả / hàng nhái">Hàng giả / hàng nhái</option>
              <option value="Thiếu phụ kiện hoặc thiếu sản phẩm">Thiếu phụ kiện hoặc thiếu sản phẩm</option>
              <option value="Khác">Khác</option>
            </select>
          </div>

          <div>
            <label style={{ display: 'block', fontSize: '0.84rem', fontWeight: 600, marginBottom: '6px', color: '#334155' }}>
              Mô tả chi tiết tình trạng hàng *
            </label>
            <textarea
              rows={3}
              required
              placeholder="Mô tả cụ thể lỗi sản phẩm, mức độ sai lệch so với mô tả của người bán..."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              style={{
                width: '100%',
                padding: '9px 12px',
                borderRadius: '8px',
                border: '1px solid #cbd5e1',
                fontSize: '0.88rem',
                boxSizing: 'border-box',
                fontFamily: 'inherit',
              }}
            />
          </div>

          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
              <label style={{ display: 'block', fontSize: '0.84rem', fontWeight: 600, color: '#334155' }}>
                Link Video mở kiện hàng (Unboxing Video)
              </label>
              <label
                style={{
                  fontSize: '0.78rem',
                  fontWeight: 600,
                  color: '#2563eb',
                  cursor: uploadingMedia ? 'not-allowed' : 'pointer',
                }}
              >
                {uploadingMedia ? 'Đang tải lên...' : '+ Chọn video từ máy'}
                <input
                  type="file"
                  accept="video/mp4,video/webm"
                  disabled={uploadingMedia}
                  onChange={(e) => {
                    void handleUploadFiles(e.target.files, 'VIDEO');
                    e.target.value = '';
                  }}
                  style={{ display: 'none' }}
                />
              </label>
            </div>
            <input
              type="text"
              placeholder="Chọn video từ máy hoặc dán link video (https://...)"
              value={videoUnboxingUrl}
              onChange={(e) => setVideoUnboxingUrl(e.target.value)}
              style={{
                width: '100%',
                padding: '8px 12px',
                borderRadius: '8px',
                border: '1px solid #cbd5e1',
                fontSize: '0.86rem',
                boxSizing: 'border-box',
              }}
            />
            <span style={{ fontSize: '0.76rem', color: '#64748b' }}>
              Khuyến nghị video liên tục không cắt ghép từ lúc kiểm tra mã vận đơn đến khi mở hộp đồ.
            </span>
          </div>

          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
              <label style={{ display: 'block', fontSize: '0.84rem', fontWeight: 600, color: '#334155' }}>
                Link hình ảnh bằng chứng (Mỗi link một dòng)
              </label>
              <label
                style={{
                  fontSize: '0.78rem',
                  fontWeight: 600,
                  color: '#2563eb',
                  cursor: uploadingMedia ? 'not-allowed' : 'pointer',
                }}
              >
                {uploadingMedia ? 'Đang tải lên...' : '+ Chọn ảnh từ máy'}
                <input
                  type="file"
                  multiple
                  accept="image/jpeg,image/png,image/webp"
                  disabled={uploadingMedia}
                  onChange={(e) => {
                    void handleUploadFiles(e.target.files, 'IMAGE');
                    e.target.value = '';
                  }}
                  style={{ display: 'none' }}
                />
              </label>
            </div>
            <textarea
              rows={2}
              placeholder="Chọn ảnh từ máy hoặc nhập mỗi link ảnh một dòng..."
              value={evidenceInput}
              onChange={(e) => setEvidenceInput(e.target.value)}
              style={{
                width: '100%',
                padding: '8px 12px',
                borderRadius: '8px',
                border: '1px solid #cbd5e1',
                fontSize: '0.86rem',
                boxSizing: 'border-box',
                fontFamily: 'monospace',
              }}
            />
            {evidenceInput.trim() && (
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 6 }}>
                {evidenceInput
                  .split('\n')
                  .map((s) => s.trim())
                  .filter(Boolean)
                  .map((url, idx) => (
                    <img
                      key={`${url}-${idx}`}
                      src={resolveReturnMediaUrl(url)}
                      alt={`Bằng chứng #${idx + 1}`}
                      style={{ width: 64, height: 64, objectFit: 'cover', borderRadius: 6, border: '1px solid #cbd5e1' }}
                    />
                  ))}
              </div>
            )}
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px' }}>
            <button
              type="button"
              onClick={onClose}
              style={{
                padding: '9px 16px',
                background: '#f1f5f9',
                border: '1px solid #cbd5e1',
                borderRadius: '8px',
                fontSize: '0.88rem',
                fontWeight: 600,
                color: '#475569',
                cursor: 'pointer',
              }}
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={submitting}
              style={{
                padding: '9px 20px',
                background: '#dc2626',
                border: 'none',
                borderRadius: '8px',
                color: '#fff',
                fontSize: '0.88rem',
                fontWeight: 600,
                cursor: submitting ? 'not-allowed' : 'pointer',
                opacity: submitting ? 0.7 : 1,
              }}
            >
              {submitting ? 'Đang gửi...' : 'Gửi yêu cầu khiếu nại'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
