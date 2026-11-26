import React, { useEffect, useState } from 'react';
import { PrivateEvidenceLink } from './PrivateEvidenceMedia';
import { returnCaseApi, type ReturnEvidenceItemInput, ReturnCaseDetail } from './returnCaseApi';

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
  const [exemptEvidence, setExemptEvidence] = useState<ReturnEvidenceItemInput | null>(null);

  useEffect(() => {
    let active = true;
    if (['DELIVERED', 'DISPUTED'].includes(orderStatus)) {
      returnCaseApi
        .getActiveReturnCase(orderId)
        .then((data) => {
          if (active) setReturnCase(data);
        })
        .catch(() => {
          // Silent fallback when order has no UC83 return case
        });
    } else if (['REFUNDED', 'COMPLETED'].includes(orderStatus) && typeof returnCaseApi.getLatestReturnCase === 'function') {
      returnCaseApi
        .getLatestReturnCase(orderId)
        .then((data) => {
          if (active) setReturnCase(data);
        })
        .catch(() => {
          if (active) setReturnCase(null);
        });
    } else {
      setReturnCase(null);
    }
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

  const handleUploadExemptionFile = async (file: File | undefined) => {
    if (!file) return;
    setBusy(true);
    setFeedback(null);
    try {
      const isVideo = file.type.startsWith('video/') || /\.(mp4|webm)$/i.test(file.name);
      const dto = await returnCaseApi.uploadEvidence(file, isVideo ? 'VIDEO' : 'IMAGE');
      setExemptEvidence(dto);
    } catch (err) {
      setFeedback({ type: 'error', text: err instanceof Error ? err.message : 'Không thể tải tệp bằng chứng lên.' });
    } finally {
      setBusy(false);
    }
  };

  const handleSubmitExemption = async () => {
    if (exemptReason.trim().length < 10 || !exemptEvidence) {
      setFeedback({ type: 'error', text: 'Cần nhập lý do tối thiểu 10 ký tự và ít nhất 1 ảnh/video bằng chứng.' });
      return;
    }
    setBusy(true);
    setFeedback(null);
    try {
      const updated = await returnCaseApi.sellerSubmitDamageExemption(orderId, exemptReason.trim(), [exemptEvidence], returnCase.pendingEvidenceRequest?.requestId);
      setReturnCase(updated);
      setShowExemptForm(false);
      setExemptReason('');
      setExemptEvidence(null);
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

      {returnCase.pendingEvidenceRequest && <p>Yêu cầu bổ sung từ KTV · {returnCase.pendingEvidenceRequest.reason}</p>}
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
            disabled={busy || !!returnCase.currentRound?.validDamageRequestAt}
            onClick={handleAgreeRefund}
            className="og-button og-button--primary"
            style={{ width: '100%', padding: '8px 12px', fontWeight: 600, background: '#16a34a', borderColor: '#16a34a' }}
          >
            Hàng đúng, đồng ý hoàn tiền (Vòng 2)
          </button>
          <button
            type="button"
            disabled={busy || (!!returnCase.currentRound?.validDamageRequestAt && !returnCase.pendingEvidenceRequest)}
            onClick={() => setShowExemptForm((v) => !v)}
            className="og-button og-button--secondary"
            style={{ width: '100%', padding: '8px 12px', fontWeight: 600 }}
          >
            {returnCase.pendingEvidenceRequest ? 'Bổ sung bằng chứng theo yêu cầu KTV' : 'Hàng hỏng / tráo hàng — Đề nghị KTV miễn hoàn'}
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
              <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                <label
                  style={{
                    padding: '6px 10px',
                    borderRadius: 6,
                    background: '#e0f2fe',
                    color: '#0369a1',
                    fontWeight: 600,
                    fontSize: '0.78rem',
                    cursor: busy ? 'not-allowed' : 'pointer',
                    whiteSpace: 'nowrap',
                  }}
                >
                  + Chọn tệp
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp,video/mp4,video/webm"
                    disabled={busy}
                    onChange={(e) => {
                      void handleUploadExemptionFile(e.target.files?.[0]);
                      e.target.value = '';
                    }}
                    style={{ display: 'none' }}
                  />
                </label>
              </div>
              {exemptEvidence?.objectKey && (
                <PrivateEvidenceLink objectKey={exemptEvidence.objectKey} label="Xem trước bằng chứng miễn hoàn" />
              )}
              <button
                type="button"
                disabled={busy}
                onClick={handleSubmitExemption}
                className="og-button og-button--primary"
                style={{ padding: '6px 12px', fontWeight: 600 }}
              >
                {returnCase.pendingEvidenceRequest ? 'Gửi bằng chứng bổ sung' : 'Gửi đề nghị xét lại lên KTV'}
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
