import React, { useState } from 'react';
import { returnCaseApi, SettlementOperationSummary } from './returnCaseApi';

interface Uc83SettlementConfirmPanelProps {
  orderId: number;
}

export const Uc83SettlementConfirmPanel: React.FC<Uc83SettlementConfirmPanelProps> = ({ orderId }) => {
  const [operations, setOperations] = useState<SettlementOperationSummary[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [confirmingId, setConfirmingId] = useState<string | null>(null);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const loadSettlements = async (preserveMessage = false) => {
    setLoading(true);
    if (!preserveMessage) {
      setMessage(null);
    }
    try {
      const list = await returnCaseApi.listOrderSettlements(orderId);
      setOperations(list);
    } catch (err) {
      setMessage({ type: 'error', text: err instanceof Error ? err.message : 'Không thể tải danh sách lệnh hoàn tiền UC83.' });
    } finally {
      setLoading(false);
    }
  };

  const handleConfirm = async (operationId: string) => {
    setConfirmingId(operationId);
    setMessage(null);
    try {
      const res = await returnCaseApi.confirmSettlementOperation(operationId);
      setMessage({
        type: 'success',
        text: `Đã xác nhận lệnh ${res.operationId.slice(0, 8)} (${res.state}). Hoàn ${res.coinsCredited.toLocaleString('vi-VN')} xu.`,
      });
      await loadSettlements(true);
    } catch (err) {
      setMessage({ type: 'error', text: err instanceof Error ? err.message : 'Xác nhận lệnh hoàn tiền thất bại.' });
    } finally {
      setConfirmingId(null);
    }
  };

  return (
    <div
      data-testid="uc83-settlement-confirm-panel"
      style={{
        marginTop: 12,
        padding: 12,
        borderRadius: 10,
        border: '1px solid var(--og-border, #e2e8f0)',
        background: 'var(--og-surface-subtle, #f8fafc)',
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
        fontSize: '0.85rem',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
        <strong>Đối soát &amp; Xác nhận Hoàn tiền UC83 (Đơn #{orderId})</strong>
        <button
          type="button"
          disabled={loading}
          onClick={() => loadSettlements(false)}
          className="og-button og-button--secondary"
          style={{ padding: '4px 10px', fontSize: '0.8rem', fontWeight: 600 }}
        >
          {loading ? 'Đang tải...' : 'Kiểm tra lệnh hoàn tiền UC83'}
        </button>
      </div>

      {message && (
        <div
          role="status"
          style={{
            padding: '6px 10px',
            borderRadius: 6,
            background: message.type === 'success' ? '#dcfce7' : '#fee2e2',
            color: message.type === 'success' ? '#166534' : '#991b1b',
          }}
        >
          {message.text}
        </div>
      )}

      {operations && operations.length === 0 && (
        <div style={{ color: '#64748b' }}>Chưa có lệnh hoàn tiền / giải ngân UC83 nào cho đơn hàng này.</div>
      )}

      {operations && operations.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {operations.map((op) => (
            <div
              key={op.operationId}
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                gap: 8,
                padding: '8px 10px',
                borderRadius: 6,
                background: '#fff',
                border: '1px solid #cbd5e1',
              }}
            >
              <div>
                <div style={{ fontWeight: 600 }}>
                  {op.operationType} — {op.amount.toLocaleString('vi-VN')} ₫
                </div>
                <div style={{ fontSize: '0.78rem', color: '#475569' }}>
                  Trạng thái: <strong>{op.state}</strong>
                </div>
              </div>
              {op.state === 'REQUESTED' && (
                <button
                  type="button"
                  disabled={confirmingId === op.operationId}
                  onClick={() => handleConfirm(op.operationId)}
                  className="og-button og-button--primary"
                  style={{ padding: '6px 12px', fontSize: '0.8rem', fontWeight: 600 }}
                >
                  {confirmingId === op.operationId ? 'Đang xác nhận...' : 'Xác nhận hoàn tiền'}
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
