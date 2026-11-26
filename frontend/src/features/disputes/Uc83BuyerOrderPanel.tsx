import React, { useEffect, useState } from 'react';
import { ordersApi } from '../orders/ordersApi';
import { resolveReturnMediaUrl } from './mediaUrl';
import { returnCaseApi, type ReturnEvidenceItemInput, ReturnCaseDetail, ReturnReasonCode } from './returnCaseApi';

interface Uc83BuyerOrderPanelProps {
  orderId: number;
  orderStatus: string;
  onUpdated: () => void;
}

const CASE_STATE_LABELS: Record<string, string> = {
  OPEN: 'Vòng 1 — Đang chờ Người bán / KTV xem xét',
  REVIEWING: 'Vòng 1 — KTV đang phân xử',
  RETURN_APPROVED: 'Đã duyệt trả hàng — Chờ gửi hàng trả (hạn 3 ngày)',
  RETURNING: 'Đang vận chuyển hàng trả về Người bán',
  POST_RETURN: 'Vòng 2 — Người bán kiểm tra hàng trả (hạn 2 ngày)',
  CLOSED: 'Đã đóng hồ sơ',
  REJECTED: 'Đã bác yêu cầu trả hàng',
};

export const Uc83BuyerOrderPanel: React.FC<Uc83BuyerOrderPanelProps> = ({
  orderId,
  orderStatus,
  onUpdated,
}) => {
  const [videoOption, setVideoOption] = useState<'VIDEO_RECORDED' | 'SKIPPED_AFTER_WARNING'>('VIDEO_RECORDED');
  const [earlyConsent, setEarlyConsent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const [returnCase, setReturnCase] = useState<ReturnCaseDetail | null>(null);
  const [method, setMethod] = useState('CARRIER');
  const [carrier, setCarrier] = useState('GHN');
  const [tracking, setTracking] = useState('');
  const [shipmentKey, setShipmentKey] = useState(() => crypto.randomUUID());
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [createReasonCode, setCreateReasonCode] = useState<ReturnReasonCode>('NOT_AS_DESCRIBED');
  const [createDetail, setCreateDetail] = useState('');
  const [createEvidence, setCreateEvidence] = useState<ReturnEvidenceItemInput | null>(null);
  const [showSupplementForm, setShowSupplementForm] = useState(false);
  const [supplementNote, setSupplementNote] = useState('');
  const [supplement, setSupplement] = useState<ReturnEvidenceItemInput | null>(null);

  useEffect(() => {
    let active = true;
    if (orderStatus === 'DELIVERED') {
      returnCaseApi
        .getActiveReturnCase(orderId)
        .then((data) => {
          if (active) setReturnCase(data);
        })
        .catch(() => {
          // Silent fallback for LEGACY_V14 orders or mocked unit tests
        });
    } else if (['DISPUTED', 'REFUNDED', 'COMPLETED'].includes(orderStatus) && typeof returnCaseApi.getLatestReturnCase === 'function') {
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

  if (orderStatus !== 'DELIVERED' && !returnCase) {
    return null;
  }

  const handleConfirmReceived = async () => {
    setBusy(true);
    setFeedback(null);
    try {
      const res = await ordersApi.confirmReceived(orderId, videoOption);
      setFeedback({ type: 'success', text: res.message || 'Đã ghi nhận xác nhận nhận hàng (vẫn giữ quyền kiểm tra 3 ngày).' });
      onUpdated();
    } catch (err) {
      setFeedback({ type: 'error', text: err instanceof Error ? err.message : 'Không thể xác nhận nhận hàng.' });
    } finally {
      setBusy(false);
    }
  };

  const handleEarlyComplete = async () => {
    if (!earlyConsent) {
      setFeedback({ type: 'error', text: 'Vui lòng tích chọn xác nhận từ bỏ quyền trả hàng trước khi hoàn tất sớm.' });
      return;
    }
    setBusy(true);
    setFeedback(null);
    try {
      const res = await ordersApi.earlyCompleteOrder(orderId, true);
      setFeedback({ type: 'success', text: res.message || 'Đã ghi nhận yêu cầu hoàn tất sớm đơn hàng.' });
      onUpdated();
    } catch (err) {
      setFeedback({ type: 'error', text: err instanceof Error ? err.message : 'Không thể hoàn tất sớm đơn hàng.' });
    } finally {
      setBusy(false);
    }
  };

  const handleUploadEvidenceFile = async (
    file: File | undefined,
    setter: (item: ReturnEvidenceItemInput) => void,
  ) => {
    if (!file) return;
    setBusy(true);
    setFeedback(null);
    try {
      const isVideo = file.type.startsWith('video/') || /\.(mp4|webm)$/i.test(file.name);
      const dto = await returnCaseApi.uploadEvidence(file, isVideo ? 'VIDEO' : 'IMAGE');
      setter(dto);
    } catch (err) {
      setFeedback({ type: 'error', text: err instanceof Error ? err.message : 'Không thể tải tệp bằng chứng lên.' });
    } finally {
      setBusy(false);
    }
  };

  const handleCreateReturnRequest = async () => {
    if (!createEvidence) {
      setFeedback({ type: 'error', text: 'Vui lòng tải lên ảnh/video bằng chứng mở hộp (UC62).' });
      return;
    }
    setBusy(true);
    setFeedback(null);
    try {
      const created = await returnCaseApi.createReturnRequest(orderId, {
        reasonCode: createReasonCode,
        detail: createDetail.trim() || undefined,
        evidenceList: [createEvidence],
      });
      setReturnCase(created);
      setShowCreateForm(false);
      setCreateDetail('');
      setCreateEvidence(null);
      setFeedback({ type: 'success', text: `Đã gửi yêu cầu trả hàng UC62 (#${created.caseId}) thành công.` });
      onUpdated();
    } catch (err) {
      setFeedback({ type: 'error', text: err instanceof Error ? err.message : 'Không thể gửi yêu cầu trả hàng.' });
    } finally {
      setBusy(false);
    }
  };

  const handleWithdraw = async () => {
    setBusy(true);
    setFeedback(null);
    try {
      const updated = await returnCaseApi.withdrawReturnRequest(orderId);
      setReturnCase(updated);
      setFeedback({ type: 'success', text: 'Đã rút yêu cầu trả hàng thành công.' });
      onUpdated();
    } catch (err) {
      setFeedback({ type: 'error', text: err instanceof Error ? err.message : 'Không thể rút yêu cầu trả hàng.' });
    } finally {
      setBusy(false);
    }
  };

  const handleSupplementSubmit = async () => {
    if (!supplement) {
      setFeedback({ type: 'error', text: 'Vui lòng tải lên ảnh/video bằng chứng bổ sung.' });
      return;
    }
    setBusy(true);
    setFeedback(null);
    try {
      const updated = await returnCaseApi.supplementBuyerEvidenceRound1(orderId, {
        note: supplementNote.trim() || undefined,
        evidenceList: [supplement],
      });
      setReturnCase(updated);
      setShowSupplementForm(false);
      setSupplementNote('');
      setSupplement(null);
      setFeedback({ type: 'success', text: 'Đã bổ sung bằng chứng Vòng 1 thành công.' });
      onUpdated();
    } catch (err) {
      setFeedback({ type: 'error', text: err instanceof Error ? err.message : 'Không thể gửi bằng chứng bổ sung.' });
    } finally {
      setBusy(false);
    }
  };

  const handleDirectHandover = async () => {
    setBusy(true);
    setFeedback(null);
    try {
      const updated = await returnCaseApi.confirmDirectHandover(orderId);
      setReturnCase(updated);
      setFeedback({ type: 'success', text: 'Đã xác nhận bàn giao hàng trả trực tiếp.' });
      onUpdated();
    } catch (err) {
      setFeedback({ type: 'error', text: err instanceof Error ? err.message : 'Không thể xác nhận bàn giao.' });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      data-testid="uc83-buyer-order-panel"
      style={{
        marginTop: 12,
        padding: 12,
        borderRadius: 10,
        border: '1px solid var(--og-border, #e2e8f0)',
        background: 'var(--og-surface-subtle, #f8fafc)',
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        fontSize: '0.85rem',
      }}
    >
      <div style={{ fontWeight: 700, color: 'var(--og-text-primary, #0f172a)' }}>
        Thao tác nhận hàng &amp; Hoàn trả (UC27 / UC83)
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

      {/* UC27 Step 3: Confirm Received with Video Unboxing Option */}
      {orderStatus === 'DELIVERED' && (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        <label style={{ fontWeight: 600, fontSize: '0.8rem' }}>
          Lựa chọn video khui hàng khi xác nhận nhận hàng:
        </label>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer' }}>
            <input
              type="radio"
              name={`uc27-video-${orderId}`}
              value="VIDEO_RECORDED"
              checked={videoOption === 'VIDEO_RECORDED'}
              onChange={() => setVideoOption('VIDEO_RECORDED')}
            />
            <span>Đã quay video khui hàng (khuyên dùng)</span>
          </label>
          <label style={{ display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer' }}>
            <input
              type="radio"
              name={`uc27-video-${orderId}`}
              value="SKIPPED_AFTER_WARNING"
              checked={videoOption === 'SKIPPED_AFTER_WARNING'}
              onChange={() => setVideoOption('SKIPPED_AFTER_WARNING')}
            />
            <span>Bỏ qua cảnh báo (không quay video)</span>
          </label>
        </div>
        <button
          type="button"
          disabled={busy}
          onClick={handleConfirmReceived}
          className="og-button og-button--secondary"
          style={{ width: '100%', padding: '8px 12px', fontWeight: 600 }}
        >
          Xác nhận đã nhận bưu kiện (giữ quyền kiểm tra 3 ngày)
        </button>
      </div>
      )}

      {/* UC27 Early Complete */}
      {orderStatus === 'DELIVERED' && (
      <div style={{ borderTop: '1px solid var(--og-border, #e2e8f0)', paddingTop: 8, display: 'flex', flexDirection: 'column', gap: 6 }}>
        <label style={{ display: 'flex', alignItems: 'flex-start', gap: 6, cursor: 'pointer', fontSize: '0.8rem' }}>
          <input
            type="checkbox"
            checked={earlyConsent}
            onChange={(e) => setEarlyConsent(e.target.checked)}
            style={{ marginTop: 2 }}
          />
          <span>Tôi đồng ý từ bỏ quyền yêu cầu trả hàng/khiếu nại để hoàn tất sớm đơn hàng.</span>
        </label>
        <button
          type="button"
          disabled={busy || !earlyConsent}
          onClick={handleEarlyComplete}
          className="og-button og-button--secondary"
          style={{ width: '100%', padding: '8px 12px', fontWeight: 600 }}
        >
          Hoàn tất sớm (UC27)
        </button>
      </div>
      )}

      {/* UC62 Return Request Form when no active return case */}
      {orderStatus === 'DELIVERED' && !returnCase && (
        <div style={{ borderTop: '1px solid var(--og-border, #e2e8f0)', paddingTop: 8, display: 'flex', flexDirection: 'column', gap: 6 }}>
          <button
            type="button"
            disabled={busy}
            onClick={() => setShowCreateForm((v) => !v)}
            className="og-button og-button--secondary"
            style={{ width: '100%', padding: '8px 12px', fontWeight: 600 }}
          >
            Yêu cầu trả hàng / hoàn tiền (UC62)
          </button>

          {showCreateForm && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6, padding: 8, background: '#fff', borderRadius: 6, border: '1px solid #cbd5e1' }}>
              <select
                value={createReasonCode}
                onChange={(e) => setCreateReasonCode(e.target.value as ReturnReasonCode)}
                style={{ padding: '6px 8px', borderRadius: 6, border: '1px solid #cbd5e1' }}
              >
                <option value="NOT_AS_DESCRIBED">Hàng không đúng mô tả</option>
                <option value="DAMAGED_OR_DEFECTIVE">Hàng bị lỗi / hư hỏng</option>
                <option value="CHANGE_MIND">Đổi ý (còn nguyên vẹn)</option>
              </select>
              <textarea
                rows={2}
                placeholder="Mô tả chi tiết tình trạng hàng nhận được..."
                value={createDetail}
                onChange={(e) => setCreateDetail(e.target.value)}
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
                      void handleUploadEvidenceFile(e.target.files?.[0], setCreateEvidence);
                      e.target.value = '';
                    }}
                    style={{ display: 'none' }}
                  />
                </label>
              </div>
              {createEvidence?.objectKey && (
                <a href={resolveReturnMediaUrl(createEvidence.objectKey)} target="_blank" rel="noreferrer">Xem trước bằng chứng trả hàng</a>
              )}
              <button
                type="button"
                disabled={busy}
                onClick={handleCreateReturnRequest}
                className="og-button og-button--primary"
                style={{ padding: '6px 12px', fontWeight: 600 }}
              >
                Gửi yêu cầu trả hàng
              </button>
            </div>
          )}
        </div>
      )}

      {/* Active UC83 Return Case Actions */}
      {returnCase && (
        <div
          data-testid="uc83-buyer-active-case"
          style={{
            borderTop: '1px solid var(--og-border, #e2e8f0)',
            paddingTop: 10,
            display: 'flex',
            flexDirection: 'column',
            gap: 8,
          }}
        >
          <div style={{ fontWeight: 700, color: '#b45309' }}>
            Hồ sơ trả hàng #{returnCase.caseId}: {CASE_STATE_LABELS[returnCase.state] || returnCase.state}
          </div>

          {returnCase.pendingEvidenceRequest && <p>Yêu cầu bổ sung từ KTV · {returnCase.pendingEvidenceRequest.reason}</p>}
          {(returnCase.state === 'OPEN' || returnCase.state === 'REVIEWING') && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <button
                type="button"
                disabled={busy}
                onClick={() => setShowSupplementForm((v) => !v)}
                className="og-button og-button--secondary"
                style={{ width: '100%', padding: '8px 12px', fontWeight: 600 }}
              >
                Bổ sung bằng chứng Vòng 1 (UC62 7a)
              </button>

              {showSupplementForm && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6, padding: 8, background: '#fff', borderRadius: 6, border: '1px solid #cbd5e1' }}>
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
                          void handleUploadEvidenceFile(e.target.files?.[0], setSupplement);
                          e.target.value = '';
                        }}
                        style={{ display: 'none' }}
                      />
                    </label>
                  </div>
                  {supplement?.objectKey && (
                    <a href={resolveReturnMediaUrl(supplement.objectKey)} target="_blank" rel="noreferrer">Xem trước bằng chứng bổ sung</a>
                  )}
                  <input
                    type="text"
                    placeholder="Ghi chú mô tả thêm (tùy chọn)..."
                    value={supplementNote}
                    onChange={(e) => setSupplementNote(e.target.value)}
                    style={{ padding: '6px 8px', borderRadius: 6, border: '1px solid #cbd5e1' }}
                  />
                  <button
                    type="button"
                    disabled={busy}
                    onClick={handleSupplementSubmit}
                    className="og-button og-button--primary"
                    style={{ padding: '6px 12px', fontWeight: 600 }}
                  >
                    Gửi bằng chứng bổ sung
                  </button>
                </div>
              )}

              <button
                type="button"
                disabled={busy}
                onClick={handleWithdraw}
                style={{
                  width: '100%',
                  padding: '8px 12px',
                  borderRadius: 6,
                  border: '1px solid #94a3b8',
                  background: 'transparent',
                  cursor: 'pointer',
                  fontWeight: 600,
                }}
              >
                Rút yêu cầu trả hàng (UC62 7c)
              </button>
            </div>
          )}

          {returnCase.state === 'RETURN_APPROVED' && <form onChange={() => setShipmentKey(crypto.randomUUID())} onSubmit={async e => {
            e.preventDefault();
            setBusy(true); setFeedback(null);
            try {
              setReturnCase(await returnCaseApi.submitShipment(returnCase.caseId, method,
                method === 'CARRIER' ? carrier.trim() : null, method === 'CARRIER' ? tracking.trim() : null, shipmentKey));
              setFeedback({type:'success', text:'Đã đăng ký gửi hàng trả.'}); onUpdated();
            } catch (err) { setFeedback({type:'error', text:err instanceof Error ? err.message : 'Chưa đăng ký được vận đơn.'}); }
            finally { setBusy(false); }
          }}>
            <label>Phương thức trả hàng<select value={method} onChange={e => setMethod(e.target.value)}>
              <option value="CARRIER">Qua đơn vị vận chuyển</option><option value="SELF_DELIVERY">Tự giao</option><option value="SELF_PICKUP">Đối tác đến nhận</option>
            </select></label>
            {method === 'CARRIER' && <><label>Đơn vị vận chuyển<input required value={carrier} onChange={e => setCarrier(e.target.value)} /></label>
              <label>Mã vận đơn<input required value={tracking} onChange={e => setTracking(e.target.value)} /></label></>}
            <button className="og-button og-button--primary" disabled={busy}>Đăng ký gửi hàng trả</button>
          </form>}
          {returnCase.state === 'RETURNING' && returnCase.returnShipment?.deliveryMethod !== 'CARRIER' && (
            <button
              type="button"
              disabled={busy}
              onClick={handleDirectHandover}
              className="og-button og-button--primary"
              style={{ width: '100%', padding: '8px 12px', fontWeight: 600 }}
            >
              Xác nhận bàn giao hàng trả trực tiếp (UC33)
            </button>
          )}
        </div>
      )}
    </div>
  );
};
