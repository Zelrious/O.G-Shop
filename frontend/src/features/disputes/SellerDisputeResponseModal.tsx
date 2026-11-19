import React, { useState } from 'react';
import { disputeApi } from './disputeApi';

interface SellerDisputeResponseModalProps {
  isOpen: boolean;
  orderId: number;
  productTitle: string;
  onClose: () => void;
  onSuccess: () => void;
}

export const SellerDisputeResponseModal: React.FC<SellerDisputeResponseModalProps> = ({
  isOpen,
  orderId,
  productTitle,
  onClose,
  onSuccess,
}) => {
  const [sellerResponse, setSellerResponse] = useState('');
  const [evidenceInput, setEvidenceInput] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!sellerResponse.trim()) {
      setError('Vui lòng nhập nội dung giải trình.');
      return;
    }

    const sellerEvidenceUrls = evidenceInput
      .split('\n')
      .map((s) => s.trim())
      .filter((s) => s.length > 0);

    setError(null);
    setSubmitting(true);
    try {
      await disputeApi.submitSellerResponse(orderId, {
        sellerResponse: sellerResponse.trim(),
        sellerEvidenceUrls: sellerEvidenceUrls.length > 0 ? sellerEvidenceUrls : undefined,
      });
      onSuccess();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể gửi giải trình.');
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
              Phản hồi khiếu nại của Người mua (UC63)
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
              Giải trình của Người bán *
            </label>
            <textarea
              rows={4}
              required
              placeholder="Giải thích chi tiết về quy trình đóng gói, tình trạng sản phẩm trước khi gửi, hoặc trao đổi với người mua..."
              value={sellerResponse}
              onChange={(e) => setSellerResponse(e.target.value)}
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
            <label style={{ display: 'block', fontSize: '0.84rem', fontWeight: 600, marginBottom: '4px', color: '#334155' }}>
              Link hình ảnh kiện hàng trước khi gửi (Mỗi link một dòng)
            </label>
            <textarea
              rows={3}
              placeholder="https://.../pack1.jpg&#10;https://.../pack2.jpg"
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
            <span style={{ fontSize: '0.76rem', color: '#64748b' }}>
              Ảnh gói hàng dán nhãn vận đơn là bằng chứng quan trọng để Kỹ thuật viên bác khiếu nại sai.
            </span>
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
                background: 'var(--og-color-primary-fill, #059669)',
                border: 'none',
                borderRadius: '8px',
                color: '#fff',
                fontSize: '0.88rem',
                fontWeight: 600,
                cursor: submitting ? 'not-allowed' : 'pointer',
                opacity: submitting ? 0.7 : 1,
              }}
            >
              {submitting ? 'Đang gửi...' : 'Gửi giải trình'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
