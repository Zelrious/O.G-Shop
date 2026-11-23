import React, { useEffect, useState } from 'react';
import { returnCaseApi, ReturnCaseDetail } from './returnCaseApi';

interface Uc83SellerReturnPanelProps {
  orderId: number;
  orderStatus: string;
  onUpdated: () => void;
}

export const Uc83SellerReturnPanel: React.FC<Uc83SellerReturnPanelProps> = ({
  orderId,
  orderStatus,
  onUpdated,
}) => {
  const [returnCase, setReturnCase] = useState<ReturnCaseDetail | null>(null);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const [showExemptForm, setShowExemptForm] = useState(false);
  const [exemptReason, setExemptReason] = useState('');
  const [exemptEvidenceUrl, setExemptEvidenceUrl] = useState('');

  useEffect(() => {
    let active = true;
    if (!['DELIVERED', 'DISPUTED'].includes(orderStatus)) {
      setReturnCase(null);
      return;
    }
    returnCaseApi
      .getActiveReturnCase(orderId)
      .then((data) => {
        if (active) setReturnCase(data);
      })
      .catch(() => {
        // Silent fallback when order has no UC83 return case
      });
    return () => {
      active = false;
    };
  }, [orderId, orderStatus]);

  if (!returnCase) {
    return null;
  }

  const handleRound1 = async (outcome: 'APPROVE_RETURN' | 'SELLER_DECLINED') => {
    setBusy(true);
    setFeedback(null);
    try {
      const updated = await returnCaseApi.sellerRespondRound1(orderId, outcome);
      setReturnCase(updated);
      setFeedback({
        type: 'success',
        text: outcome === 'APPROVE_RETURN'
          ? 'Đã chấp thuận cho Người mua trả hàng.'
          : 'Đã ghi nhận không đồng ý và chuyển hồ sơ lên KTV phân xử.',
      });
      onUpdated();
    } catch (err) {
      setFeedback({ type: 'error', text: err instanceof Error ? err.message : 'Không thể gửi phản hồi Vòng 1.' });
    } finally {
      setBusy(false);
    }
  };

  const handleAgreeRefund = async () => {
    setBusy(true);
    setFeedback(null);
    try {
      const updated = await returnCaseApi.sellerAgreeRefundRound2(orderId);
      setReturnCase(updated);
      setFeedback({ type: 'success', text: 'Đã xác nhận đồng ý hoàn tiền cho Người mua.' });
      onUpdated();
    } catch (err) {
      setFeedback({ type: 'error', text: err instanceof Error ? err.message : 'Không thể xác nhận hoàn tiền.' });
    } finally {
      setBusy(false);
    }
  };

  const handleSubmitExemption = async () => {
    if (exemptReason.trim().length < 10 || !exemptEvidenceUrl.trim()) {
      setFeedback({ type: 'error', text: 'Cần nhập lý do tối thiểu 10 ký tự và ít nhất 1 ảnh/video bằng chứng.' });
      return;
    }
    setBusy(true);
    setFeedback(null);
    try {
      const updated = await returnCaseApi.sellerSubmitDamageExemption(orderId, exemptReason.trim(), [
        {
          mediaType: 'IMAGE',
          objectKey: exemptEvidenceUrl.trim(),
          contentDigest: 'b'.repeat(64),
          fileSizeBytes: 2048,
        },
      ]);
      setReturnCase(updated);
      setShowExemptForm(false);
      setExemptReason('');
      setExemptEvidenceUrl('');
      setFeedback({ type: 'success', text: 'Đã gửi đề nghị KTV miễn hoàn tiền kèm bằng chứng.' });
      onUpdated();
    } catch (err) {
      setFeedback({ type: 'error', text: err instanceof Error ? err.message : 'Không thể gửi đề nghị miễn hoàn.' });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      data-testid="uc83-seller-return-panel"
      style={{
        marginTop: 12,
        padding: 12,
        borderRadius: 10,
        border: '1px solid #f59e0b',
        background: '#fffbeb',
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        fontSize: '0.85rem',
      }}
    >
      <div style={{ fontWeight: 700, color: '#92400e' }}>
        Hồ sơ Trả hàng UC83 #{returnCase.caseId} — Trạng thái: {returnCase.state}
      </div>

      {feedback && (
        <div
          role="status"
          style={{
            padding: '8px 10px',
            borderRadius: 6,
            background: feedback.type === 'success' ? '#dcfce7' : '#fee2e2',
            color: feedback.type === 'success' ? '#166534' : '#991b1b',
            fontWeight: 500,
          }}
        >
          {feedback.text}
        </div>
      )}

      {returnCase.state === 'OPEN' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <button
            type="button"
            disabled={busy}
            onClick={() => handleRound1('APPROVE_RETURN')}
            className="og-button og-button--primary"
            style={{ width: '100%', padding: '8px 12px', fontWeight: 600 }}
          >
            Chấp thuận trả hàng (Vòng 1)
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={() => handleRound1('SELLER_DECLINED')}
            className="og-button og-button--secondary"
            style={{ width: '100%', padding: '8px 12px', fontWeight: 600 }}
          >
            Không đồng ý, chuyển KTV phân xử
          </button>
        </div>
      )}

      {returnCase.state === 'POST_RETURN' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <button
            type="button"
            disabled={busy}
            onClick={handleAgreeRefund}
            className="og-button og-button--primary"
            style={{ width: '100%', padding: '8px 12px', fontWeight: 600, background: '#16a34a', borderColor: '#16a34a' }}
          >
            Hàng đúng, đồng ý hoàn tiền (Vòng 2)
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={() => setShowExemptForm((v) => !v)}
            className="og-button og-button--secondary"
            style={{ width: '100%', padding: '8px 12px', fontWeight: 600 }}
          >
            Hàng hỏng / tráo hàng — Đề nghị KTV miễn hoàn
          </button>

          {showExemptForm && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6, padding: 8, background: '#fff', borderRadius: 6, border: '1px solid #cbd5e1' }}>
              <textarea
                rows={2}
                placeholder="Mô tả thiệt hại / sai hàng / tráo hàng (tối thiểu 10 ký tự)..."
                value={exemptReason}
                onChange={(e) => setExemptReason(e.target.value)}
                style={{ padding: '6px 8px', borderRadius: 6, border: '1px solid #cbd5e1' }}
              />
              <input
                type="text"
                placeholder="Đường dẫn ảnh/video bằng chứng..."
                value={exemptEvidenceUrl}
                onChange={(e) => setExemptEvidenceUrl(e.target.value)}
                style={{ padding: '6px 8px', borderRadius: 6, border: '1px solid #cbd5e1' }}
              />
              <button
                type="button"
                disabled={busy}
                onClick={handleSubmitExemption}
                className="og-button og-button--primary"
                style={{ padding: '6px 12px', fontWeight: 600 }}
              >
                Gửi đề nghị xét lại lên KTV
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
