import React, { useCallback, useEffect, useState } from 'react';
import { PageHeader } from '../../shared/components';
import {
  AdminEscalatedCaseSummary,
  returnCaseApi,
} from '../../features/disputes/returnCaseApi';
import { Uc83SettlementConfirmPanel } from '../../features/disputes/Uc83SettlementConfirmPanel';

export const AdminEscalatedCasesPage: React.FC = () => {
  const [items, setItems] = useState<AdminEscalatedCaseSummary[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [ktvIdFilter, setKtvIdFilter] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [bannerSuccess, setBannerSuccess] = useState<string | null>(null);

  const [selected, setSelected] = useState<AdminEscalatedCaseSummary | null>(null);
  const [outcome, setOutcome] = useState<'FULL_GOODS_REFUND' | 'EXEMPT_GOODS_REFUND'>('FULL_GOODS_REFUND');
  const [reason, setReason] = useState('');
  const [confirmStep, setConfirmStep] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [drawerError, setDrawerError] = useState<string | null>(null);

  const loadEscalated = useCallback(async (targetPage = page) => {
    setLoading(true);
    setErrorMsg(null);
    try {
      const parsedKtvId = ktvIdFilter.trim() ? Number(ktvIdFilter.trim()) : undefined;
      const res = await returnCaseApi.getAdminEscalatedCases({
        page: targetPage,
        size: 20,
        ktvId: Number.isFinite(parsedKtvId) ? parsedKtvId : undefined,
      });
      setItems(res.content || []);
      setPage(res.page ?? targetPage);
      setTotalPages(res.totalPages ?? 0);
      setTotalElements(res.totalElements ?? 0);
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : 'Không thể tải danh sách hồ sơ leo thang SLA.');
    } finally {
      setLoading(false);
    }
  }, [ktvIdFilter, page]);

  useEffect(() => {
    void loadEscalated(0);
  }, [loadEscalated]);

  const handleOpenDrawer = (item: AdminEscalatedCaseSummary) => {
    setSelected(item);
    setOutcome('FULL_GOODS_REFUND');
    setReason('');
    setConfirmStep(false);
    setDrawerError(null);
  };

  const handleSubmitDecision = async () => {
    if (!selected) return;
    if (reason.trim().length < 10) {
      setDrawerError('Lý do quyết định của Admin cần tối thiểu 10 ký tự.');
      return;
    }
    setSubmitting(true);
    setDrawerError(null);
    try {
      await returnCaseApi.adminDecideRound2(selected.caseId, {
        outcome,
        reason: reason.trim(),
      });
      setBannerSuccess(`Đã ban hành quyết định cuối cùng (${outcome}) cho hồ sơ #${selected.caseId}.`);
      setSelected(null);
      await loadEscalated(page);
    } catch (err) {
      setDrawerError(err instanceof Error ? err.message : 'Không thể gửi quyết định Vòng 2.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <PageHeader
        title="Hồ sơ Trả hàng Leo thang SLA (Admin)"
        subtitle="Danh sách các hồ sơ Vòng 2 quá hạn SLA 48 giờ của KTV cần Quản trị viên ra phán quyết cuối cùng."
      />

      {bannerSuccess && (
        <div
          role="status"
          style={{
            padding: '10px 14px',
            borderRadius: 8,
            background: '#dcfce7',
            border: '1px solid #86efac',
            color: '#166534',
            fontWeight: 600,
          }}
        >
          {bannerSuccess}
        </div>
      )}

      {errorMsg && (
        <div
          role="alert"
          style={{
            padding: '10px 14px',
            borderRadius: 8,
            background: '#fef2f2',
            border: '1px solid #fca5a5',
            color: '#991b1b',
          }}
        >
          {errorMsg}
        </div>
      )}

      <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
        <input
          type="text"
          placeholder="Lọc theo KTV ID..."
          value={ktvIdFilter}
          onChange={(e) => setKtvIdFilter(e.target.value)}
          style={{ padding: '8px 12px', borderRadius: 8, border: '1px solid #cbd5e1', minWidth: 180 }}
        />
        <button
          type="button"
          className="og-button og-button--secondary"
          onClick={() => void loadEscalated(0)}
          disabled={loading}
        >
          {loading ? 'Đang tải...' : 'Làm mới danh sách'}
        </button>
        <span style={{ fontSize: '0.85rem', color: '#64748b' }}>
          Tổng số hồ sơ leo thang: <strong>{totalElements}</strong>
        </span>
      </div>

      {items.length === 0 && !loading ? (
        <div
          style={{
            padding: 24,
            borderRadius: 10,
            border: '1px solid #e2e8f0',
            background: '#fff',
            color: '#64748b',
            textAlign: 'center',
          }}
        >
          Hiện chưa có hồ sơ trả hàng nào bị leo thang quá hạn SLA.
        </div>
      ) : (
        <div style={{ overflowX: 'auto', background: '#fff', borderRadius: 10, border: '1px solid #e2e8f0' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.88rem' }}>
            <thead>
              <tr style={{ background: '#f8fafc', borderBottom: '1px solid #e2e8f0', textAlign: 'left' }}>
                <th style={{ padding: '10px 12px' }}>Case #</th>
                <th style={{ padding: '10px 12px' }}>Đơn hàng #</th>
                <th style={{ padding: '10px 12px' }}>Trạng thái</th>
                <th style={{ padding: '10px 12px' }}>KTV phụ trách</th>
                <th style={{ padding: '10px 12px' }}>Hạn SLA</th>
                <th style={{ padding: '10px 12px' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.caseId} style={{ borderBottom: '1px solid #f1f5f9' }}>
                  <td style={{ padding: '10px 12px', fontWeight: 700 }}>#{item.caseId}</td>
                  <td style={{ padding: '10px 12px' }}>#{item.orderId}</td>
                  <td style={{ padding: '10px 12px' }}>{item.state}</td>
                  <td style={{ padding: '10px 12px' }}>{item.assignedKtvId ? `KTV #${item.assignedKtvId}` : 'Chưa gán'}</td>
                  <td style={{ padding: '10px 12px' }}>
                    {item.slaDeadline ? new Date(item.slaDeadline).toLocaleString('vi-VN') : '—'}
                  </td>
                  <td style={{ padding: '10px 12px' }}>
                    <button
                      type="button"
                      className="og-button og-button--primary"
                      onClick={() => handleOpenDrawer(item)}
                      style={{ padding: '6px 12px', fontSize: '0.82rem' }}
                    >
                      Xét duyệt Vòng 2
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {totalPages > 1 && (
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', alignItems: 'center' }}>
          <button
            type="button"
            disabled={page <= 0 || loading}
            onClick={() => void loadEscalated(page - 1)}
            className="og-button og-button--secondary"
          >
            Trang trước
          </button>
          <span>
            Trang {page + 1} / {totalPages}
          </span>
          <button
            type="button"
            disabled={page + 1 >= totalPages || loading}
            onClick={() => void loadEscalated(page + 1)}
            className="og-button og-button--secondary"
          >
            Trang sau
          </button>
        </div>
      )}

      {selected && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(15, 23, 42, 0.55)',
            zIndex: 1200,
            display: 'flex',
            justifyContent: 'flex-end',
          }}
          onClick={() => setSelected(null)}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              width: '100%',
              maxWidth: 520,
              background: '#fff',
              height: '100%',
              overflowY: 'auto',
              padding: 20,
              display: 'flex',
              flexDirection: 'column',
              gap: 14,
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3 style={{ margin: 0 }}>Phán quyết Admin Vòng 2 — Case #{selected.caseId}</h3>
              <button type="button" onClick={() => setSelected(null)} className="og-button og-button--secondary">
                Đóng
              </button>
            </div>

            <div style={{ fontSize: '0.86rem', color: '#475569' }}>
              Đơn hàng: <strong>#{selected.orderId}</strong> · Trạng thái: <strong>{selected.state}</strong>
            </div>

            {drawerError && (
              <div role="alert" style={{ padding: 10, borderRadius: 8, background: '#fef2f2', color: '#991b1b' }}>
                {drawerError}
              </div>
            )}

            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <label style={{ fontWeight: 600, fontSize: '0.85rem' }}>Kết quả phán quyết cuối cùng:</label>
              <select
                value={outcome}
                onChange={(e) => {
                  setOutcome(e.target.value as 'FULL_GOODS_REFUND' | 'EXEMPT_GOODS_REFUND');
                  setConfirmStep(false);
                }}
                style={{ padding: '8px 10px', borderRadius: 8, border: '1px solid #cbd5e1' }}
              >
                <option value="FULL_GOODS_REFUND">Hoàn tiền hàng 100% cho Người mua (FULL_GOODS_REFUND)</option>
                <option value="EXEMPT_GOODS_REFUND">Miễn hoàn tiền hàng, giải ngân cho Người bán (EXEMPT_GOODS_REFUND)</option>
              </select>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <label style={{ fontWeight: 600, fontSize: '0.85rem' }}>Lý do phán quyết (tối thiểu 10 ký tự):</label>
              <textarea
                rows={4}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Nhập căn cứ bằng chứng và quyết định của Quản trị viên..."
                style={{ padding: '8px 10px', borderRadius: 8, border: '1px solid #cbd5e1' }}
              />
            </div>

            {!confirmStep ? (
              <button
                type="button"
                className="og-button og-button--primary"
                onClick={() => setConfirmStep(true)}
                disabled={submitting || reason.trim().length < 10}
              >
                Tiếp tục xác nhận phán quyết
              </button>
            ) : (
              <div style={{ display: 'flex', gap: 8 }}>
                <button
                  type="button"
                  className="og-button og-button--secondary"
                  onClick={() => setConfirmStep(false)}
                  disabled={submitting}
                >
                  Quay lại
                </button>
                <button
                  type="button"
                  className="og-button og-button--primary"
                  onClick={() => void handleSubmitDecision()}
                  disabled={submitting}
                  style={{ background: '#dc2626', borderColor: '#dc2626' }}
                >
                  {submitting ? 'Đang xử lý...' : 'Xác nhận ban hành phán quyết cuối cùng'}
                </button>
              </div>
            )}

            <div style={{ borderTop: '1px solid #e2e8f0', paddingTop: 12 }}>
              <Uc83SettlementConfirmPanel orderId={selected.orderId} />
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
