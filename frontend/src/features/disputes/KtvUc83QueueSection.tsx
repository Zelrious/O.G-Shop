import { PrivateEvidenceLink } from './PrivateEvidenceMedia';
import { isPrivateEvidenceKey } from './privateEvidenceUrl';
import React, { useCallback, useEffect, useState } from 'react';
import { AdminAssigneeOption, ReturnCaseDetail, returnCaseApi } from './returnCaseApi';

export const KtvUc83QueueSection: React.FC = () => {
  const [round, setRound] = useState<1 | 2>(1);
  const [scope, setScope] = useState<'AVAILABLE' | 'MY_CASES'>('AVAILABLE');
  const [cases, setCases] = useState<ReturnCaseDetail[]>([]);
  const [admins, setAdmins] = useState<AdminAssigneeOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [busyCaseId, setBusyCaseId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [selectedCase, setSelectedCase] = useState<ReturnCaseDetail | null>(null);
  const [reason, setReason] = useState('');
  const [targetAdminId, setTargetAdminId] = useState<string>('');

  const sequence = React.useRef(0);
  const loadQueue = useCallback(async () => {
    const version = ++sequence.current;
    setLoading(true);
    setError(null);
    try {
      const list = await returnCaseApi.getKtvWorkQueue(scope, round);
      if (version === sequence.current) setCases(list || []);
    } catch (err) {
      if (version !== sequence.current) return;
      setError(err instanceof Error ? err.message : 'Không thể tải hàng đợi UC83.');
      setCases([]);
    } finally {
      if (version === sequence.current) setLoading(false);
    }
  }, [scope, round]);

  useEffect(() => {
    setSelectedCase(null);
    void loadQueue();
  }, [loadQueue]);

  useEffect(() => {
    if (typeof returnCaseApi.getKtvAdmins === 'function') {
      returnCaseApi.getKtvAdmins().then((rows) => setAdmins(rows || [])).catch(() => setAdmins([]));
    }
  }, []);

  const refreshSelected = async (caseId: number) => {
    try {
      const detail = await returnCaseApi.getReturnCaseById(caseId);
      setSelectedCase(detail);
    } catch {
      setSelectedCase(null);
    }
  };

  const handleClaim = async (caseId: number) => {
    setBusyCaseId(caseId);
    setError(null);
    try {
      await returnCaseApi.assignCaseToKtv(caseId);
      setSuccess(`Đã nhận thụ lý hồ sơ #${caseId}.`);
      setScope('MY_CASES');
    } catch (err) {
      await loadQueue();
      setError(err instanceof Error ? err.message : 'Hồ sơ đã có KTV khác xử lý.');
    } finally {
      setBusyCaseId(null);
    }
  };

  const handleDecisionOrRequest = async (action: string) => {
    if (!selectedCase) return;
    if (reason.trim().length < 5) {
      setError('Vui lòng nhập lý do tối thiểu 5 ký tự (10 ký tự khi yêu cầu bổ sung bằng chứng).');
      return;
    }
    setBusyCaseId(selectedCase.caseId);
    setError(null);
    try {
      if (action === 'R1_REQ_EVIDENCE') {
        await returnCaseApi.ktvRequestRound1Evidence(selectedCase.caseId, reason.trim());
        setSuccess(`Đã yêu cầu Người mua bổ sung bằng chứng Vòng 1 cho hồ sơ #${selectedCase.caseId}.`);
      } else if (action === 'R1_APPROVE') {
        await returnCaseApi.ktvDecideRound1(selectedCase.caseId, { outcome: 'APPROVE_RETURN', reason: reason.trim() });
        setSuccess(`Đã chấp thuận trả hàng Vòng 1 cho hồ sơ #${selectedCase.caseId}.`);
      } else if (action === 'R1_REJECT') {
        await returnCaseApi.ktvDecideRound1(selectedCase.caseId, { outcome: 'REJECT_RETURN', reason: reason.trim() });
        setSuccess(`Đã bác yêu cầu trả hàng Vòng 1 cho hồ sơ #${selectedCase.caseId}.`);
      } else if (action === 'R2_REQ_EVIDENCE') {
        await returnCaseApi.ktvRequestRound2Evidence(selectedCase.caseId, reason.trim());
        setSuccess(`Đã yêu cầu Người bán bổ sung bằng chứng thiệt hại Vòng 2 cho hồ sơ #${selectedCase.caseId}.`);
      } else if (action === 'R2_REFUND') {
        await returnCaseApi.ktvDecideRound2(selectedCase.caseId, { outcome: 'FULL_GOODS_REFUND', reason: reason.trim() });
        setSuccess(`Đã quyết định hoàn tiền 100% Vòng 2 cho hồ sơ #${selectedCase.caseId}.`);
      } else if (action === 'R2_EXEMPT') {
        await returnCaseApi.ktvDecideRound2(selectedCase.caseId, { outcome: 'EXEMPT_GOODS_REFUND', reason: reason.trim() });
        setSuccess(`Đã quyết định miễn hoàn tiền Vòng 2 cho hồ sơ #${selectedCase.caseId}.`);
      } else if (action === 'ESCALATE') {
        const adminIdNum = Number(targetAdminId);
        if (!adminIdNum) {
          setError('Vui lòng chọn một Quản trị viên cụ thể để chuyển cấp.');
          setBusyCaseId(null);
          return;
        }
        await returnCaseApi.ktvEscalateCase(selectedCase.caseId, adminIdNum, reason.trim());
        setSuccess(`Đã chuyển cấp hồ sơ #${selectedCase.caseId} tới Admin #${adminIdNum}.`);
      }
      setReason('');
      setSelectedCase(null);
      await loadQueue();
    } catch (err) {
      await refreshSelected(selectedCase.caseId);
      await loadQueue();
      setError(err instanceof Error ? err.message : 'Thao tác không thành công (hồ sơ có thể đã thay đổi trạng thái).');
    } finally {
      setBusyCaseId(null);
    }
  };

  return (
    <div style={{ background: 'var(--og-color-surface)', border: '1px solid var(--og-color-border)', borderRadius: 12, padding: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10, marginBottom: 12 }}>
        <div>
          <h3 style={{ margin: 0, fontSize: '1.05rem', fontWeight: 700 }}>Hàng đợi Trả hàng 2 Vòng (UC62 / UC75 / UC83)</h3>
          <div style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)' }}>
            Phân tách Vòng 1 (duyệt trả hàng) và Vòng 2 (xét thiệt hại sau khi hàng hoàn về Người bán).
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <button type="button" onClick={() => setRound(1)} className={round === 1 ? 'og-button og-button--primary' : 'og-button og-button--secondary'}>
            Vòng 1: Duyệt trả
          </button>
          <button type="button" onClick={() => setRound(2)} className={round === 2 ? 'og-button og-button--primary' : 'og-button og-button--secondary'}>
            Vòng 2: Hậu trả hàng
          </button>
          <button type="button" onClick={() => setScope('AVAILABLE')} className={scope === 'AVAILABLE' ? 'og-button og-button--primary' : 'og-button og-button--secondary'}>
            Chưa nhận
          </button>
          <button type="button" onClick={() => setScope('MY_CASES')} className={scope === 'MY_CASES' ? 'og-button og-button--primary' : 'og-button og-button--secondary'}>
            Hồ sơ của tôi
          </button>
        </div>
      </div>

      {error && <div role="alert" style={{ padding: 10, marginBottom: 10, borderRadius: 8, background: '#fef2f2', color: '#991b1b', fontSize: '0.84rem' }}>{error}</div>}
      {success && <div role="status" style={{ padding: 10, marginBottom: 10, borderRadius: 8, background: '#f0fdf4', color: '#166534', fontSize: '0.84rem' }}>{success}</div>}

      {loading ? (
        <div style={{ fontSize: '0.86rem', color: 'var(--og-color-text-secondary)' }}>Đang tải hàng đợi UC83...</div>
      ) : cases.length === 0 ? (
        <div style={{ fontSize: '0.86rem', color: 'var(--og-color-text-secondary)' }}>Không có hồ sơ UC83 nào trong hàng đợi này.</div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {cases.map((c) => (
            <div key={c.caseId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, padding: 10, borderRadius: 8, border: '1px solid var(--og-color-border)', flexWrap: 'wrap' }}>
              <div>
                <div style={{ fontWeight: 700, fontSize: '0.9rem' }}>Case #{c.caseId} · Đơn #{c.orderId} · {c.state}</div>
                <div style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)' }}>{c.cleanDescription || c.description || 'Yêu cầu trả hàng UC83'}</div>
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                {scope === 'AVAILABLE' ? (
                  <button type="button" disabled={busyCaseId === c.caseId} onClick={() => void handleClaim(c.caseId)} className="og-button og-button--primary">
                    {busyCaseId === c.caseId ? 'Đang nhận...' : 'Nhận xử lý'}
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedCase(c);
                      setReason('');
                    }}
                    className="og-button og-button--primary"
                  >
                    Mở xử lý Vòng {round}
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {selectedCase && (
        <div style={{ marginTop: 14, padding: 14, borderRadius: 10, border: '1px solid #cbd5e1', background: '#f8fafc', display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <strong>Xử lý Case #{selectedCase.caseId} (Đơn #{selectedCase.orderId} · Trạng thái {selectedCase.state})</strong>
            <button type="button" onClick={() => setSelectedCase(null)} className="og-button og-button--secondary">Đóng</button>
          </div>
          <p>{selectedCase.fullDescription || selectedCase.cleanDescription}</p>
          {selectedCase.returnShipment && <p>Vận đơn trả · {selectedCase.returnShipment.deliveryMethod} · {selectedCase.returnShipment.trackingNumber || 'Giao nhận trực tiếp'}</p>}
          {selectedCase.evidence?.map(item => <p key={item.evidenceId}>{(isPrivateEvidenceKey(item.objectKey) || item.objectKey?.startsWith('/api/v1/media/')) ? <PrivateEvidenceLink objectKey={item.objectKey!} label={`Xem ${item.mediaType === 'VIDEO' ? 'video' : 'ảnh'} bằng chứng`} /> : item.textContent || item.mediaType}</p>)}
          <textarea
            rows={2}
            placeholder="Nhập lý do / nội dung yêu cầu (tối thiểu 10 ký tự khi yêu cầu bổ sung bằng chứng)..."
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            style={{ width: '100%', padding: 8, borderRadius: 6, border: '1px solid #cbd5e1', boxSizing: 'border-box' }}
          />
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
            {round === 1 ? (
              <>
                <button type="button" disabled={busyCaseId === selectedCase.caseId} onClick={() => void handleDecisionOrRequest('R1_REQ_EVIDENCE')} className="og-button og-button--secondary">
                  Yêu cầu Buyer bổ sung bằng chứng
                </button>
                <button type="button" disabled={busyCaseId === selectedCase.caseId} onClick={() => void handleDecisionOrRequest('R1_APPROVE')} className="og-button og-button--primary">
                  Chấp thuận trả hàng (APPROVE_RETURN)
                </button>
                <button type="button" disabled={busyCaseId === selectedCase.caseId} onClick={() => void handleDecisionOrRequest('R1_REJECT')} className="og-button og-button--danger">
                  Bác trả hàng (REJECT_RETURN)
                </button>
              </>
            ) : (
              <>
                <button type="button" disabled={busyCaseId === selectedCase.caseId} onClick={() => void handleDecisionOrRequest('R2_REQ_EVIDENCE')} className="og-button og-button--secondary">
                  Yêu cầu Seller bổ sung bằng chứng thiệt hại
                </button>
                <button type="button" disabled={busyCaseId === selectedCase.caseId} onClick={() => void handleDecisionOrRequest('R2_REFUND')} className="og-button og-button--primary">
                  Hoàn tiền 100% (FULL_GOODS_REFUND)
                </button>
                <button type="button" disabled={busyCaseId === selectedCase.caseId} onClick={() => void handleDecisionOrRequest('R2_EXEMPT')} className="og-button og-button--secondary">
                  Miễn hoàn tiền (EXEMPT_GOODS_REFUND)
                </button>
              </>
            )}
            <select value={targetAdminId} onChange={(e) => setTargetAdminId(e.target.value)} style={{ padding: '6px 10px', borderRadius: 6, border: '1px solid #cbd5e1' }}>
              <option value="">-- Chọn Admin chuyển cấp --</option>
              {admins.map((a) => (
                <option key={a.userId} value={a.userId}>{a.fullName} (#{a.userId})</option>
              ))}
            </select>
            <button type="button" disabled={busyCaseId === selectedCase.caseId} onClick={() => void handleDecisionOrRequest('ESCALATE')} className="og-button og-button--danger">
              Chuyển Admin cụ thể
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

