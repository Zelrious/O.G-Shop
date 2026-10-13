import { Select } from '../../shared/components/Select/Select';
import { PageHeader } from '../../shared/components';
import React, { useState } from 'react';
import { useManagementStore } from '../../features/management/useManagementStore';
import { ConsoleIcons } from '../../shared/layout/ConsoleIcons';
import { DisputeRecord } from '../../features/management/managementStore';
import {
  TableActionDropdown,
  BulkActionBar,
  TableCheckbox,
} from '../../shared/layout/ConsoleTableActions';
import { formatVnd } from '../../shared/utils/currency';

export const KtvDisputesPage: React.FC = () => {
  const { disputes, resolveDispute } = useManagementStore();
  const [selectedDispute, setSelectedDispute] = useState<DisputeRecord | null>(null);
  const [verdictAction, setVerdictAction] = useState<'FULL_REFUND' | 'RELEASE_SELLER'>('FULL_REFUND');
  const [actionError, setActionError] = useState<string | null>(null);
  const [verdictNotes, setVerdictNotes] = useState('');
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Multi-select state
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  const isAllSelected =
    disputes.length > 0 && disputes.every((d) => selectedIds.has(d.id));
  const isSomeSelected =
    disputes.some((d) => selectedIds.has(d.id)) && !isAllSelected;

  const handleToggleAll = () => {
    if (isAllSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(disputes.map((d) => d.id)));
    }
  };

  const handleToggleRow = (id: string) => {
    const next = new Set(selectedIds);
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    setSelectedIds(next);
  };

  const handleOpenArbitration = (dispute: DisputeRecord) => {
    setSelectedDispute(dispute);
    setVerdictAction('FULL_REFUND');
    setVerdictNotes('');
  };

  const handleApplyVerdict = () => {
    if (!selectedDispute || !verdictNotes.trim()) return;

    setActionError(null);
    try { resolveDispute(selectedDispute.id, verdictAction, verdictNotes.trim()); }
    catch (error) { setActionError(error instanceof Error ? error.message : 'Không thể ghi nhận quyết định.'); return; }
    setSuccessMessage(`Đã ghi nhận kết quả xử lý tranh chấp cho đơn ${selectedDispute.orderCode} thành công.`);
    setSelectedDispute(null);
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  const renderStatus = (status: DisputeRecord['status']) => {
    switch (status) {
      case 'OPEN':
        return (
          <span
            style={{
              padding: '3px 8px',
              borderRadius: '12px',
              fontSize: '0.74rem',
              fontWeight: 700,
              background: '#fee2e2',
              color: '#b91c1c',
              border: '1px solid #fecaca',
            }}
          >
            ĐANG CHỜ XỬ LÝ
          </span>
        );
      case 'INVESTIGATING':
        return (
          <span
            style={{
              padding: '3px 8px',
              borderRadius: '12px',
              fontSize: '0.74rem',
              fontWeight: 700,
              background: '#fef3c7',
              color: '#b45309',
              border: '1px solid #fde68a',
            }}
          >
            ĐANG ĐỐI SOÁT
          </span>
        );
      case 'RESOLVED_REFUND':
      case 'RETURN_APPROVED':
        return (
          <span
            style={{
              padding: '3px 8px',
              borderRadius: '12px',
              fontSize: '0.74rem',
              fontWeight: 700,
              background: '#dcfce7',
              color: '#15803d',
              border: '1px solid #bbf7d0',
            }}
          >
            CHẤP THUẬN TRẢ HÀNG
          </span>
        );
      case 'RESOLVED_RELEASE':
        return (
          <span
            style={{
              padding: '3px 8px',
              borderRadius: '12px',
              fontSize: '0.74rem',
              fontWeight: 700,
              background: '#eff6ff',
              color: '#1d4ed8',
              border: '1px solid #bfdbfe',
            }}
          >
            QUYẾT ĐỊNH GIẢI NGÂN SELLER
          </span>
        );
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>

      {/* Page Header */}
      <PageHeader title="Phân xử tranh chấp & Khiếu nại giao dịch" description="Kiểm tra video mở kiện hàng của người mua, đối chiếu ảnh đóng gói của người bán và chọn cách xử lý khoản thanh toán." />
      {actionError && <p role="alert">{actionError}</p>}
      <p>Chấp thuận trả hàng chưa phải hoàn tiền. Hoàn tiền hàng sau khi giao trả thành công và Người bán xác nhận hoặc hết 2 ngày không phản hồi; phí ship chiều mua không được hoàn.</p>

      {successMessage && (
        <div
          style={{
            background: 'var(--og-color-primary-light)',
            border: '1px solid var(--og-color-border)',
            borderRadius: '8px',
            padding: '12px 16px',
            color: 'var(--og-color-success)',
            fontSize: '0.88rem',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
          }}
        >
          <ConsoleIcons.CheckCircle size={18} />
          <span>{successMessage}</span>
        </div>
      )}

      {/* Bulk Action Bar */}
      <BulkActionBar
        selectedCount={selectedIds.size}
        totalCount={disputes.length}
        onClearSelection={() => setSelectedIds(new Set())}
        actions={[
          {
            label: 'Đẩy lên Admin xử lý hàng loạt',
            icon: <ConsoleIcons.AlertTriangle size={15} />,
            variant: 'warning',
            onClick: () => {
              alert(`Đã đẩy ${selectedIds.size} vụ việc lên Admin cấp cao.`);
              setSelectedIds(new Set());
            },
          },
        ]}
      />

      {/* Disputes Table */}
      <div
        style={{
          background: 'var(--og-color-surface)',
          borderRadius: '12px',
          border: '1px solid var(--og-color-border)',
          overflow: 'hidden',
          boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
        }}
      >
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.88rem' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--og-color-border)', color: 'var(--og-color-text-secondary)', background: 'var(--og-color-bg)' }}>
                <th style={{ padding: '14px 16px', width: '40px', textAlign: 'center', whiteSpace: 'nowrap' }}>
                  <TableCheckbox
                    checked={isAllSelected}
                    indeterminate={isSomeSelected}
                    onChange={handleToggleAll}
                    ariaLabel="Chọn tất cả vụ việc"
                  />
                </th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Mã vụ việc & Đơn</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Lý do tranh chấp</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Người mua / Người bán</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Số tiền giữ Escrow</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Trạng thái</th>
                <th style={{ padding: '14px 16px', textAlign: 'right', minWidth: '95px', whiteSpace: 'nowrap' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {disputes.map((item) => {
                const isSelected = selectedIds.has(item.id);
                return (
                  <tr
                    key={item.id}
                    style={{
                      borderBottom: '1px solid var(--og-color-surface-subtle)',
                      background: isSelected ? 'var(--og-color-primary-light)' : 'transparent',
                      transition: 'background-color 0.15s ease',
                    }}
                    onMouseEnter={(e) => {
                      if (!isSelected) e.currentTarget.style.background = 'var(--og-color-bg)';
                    }}
                    onMouseLeave={(e) => {
                      if (!isSelected) e.currentTarget.style.background = 'transparent';
                    }}
                  >
                    <td style={{ padding: '14px 16px', textAlign: 'center' }}>
                      <TableCheckbox
                        checked={isSelected}
                        onChange={() => handleToggleRow(item.id)}
                        ariaLabel={`Chọn vụ việc ${item.orderCode}`}
                      />
                    </td>
                    <td style={{ padding: '14px 16px' }}>
                      <div style={{ fontWeight: 700, color: 'var(--og-color-text-primary)', fontFamily: 'monospace' }}>
                        {item.orderCode}
                      </div>
                      <div style={{ fontSize: '0.74rem', color: 'var(--og-color-text-secondary)' }}>Mở lúc: {item.createdAt}</div>
                    </td>
                    <td style={{ padding: '14px 16px', maxWidth: '300px' }}>
                      <div style={{ fontWeight: 600, color: 'var(--og-color-text-primary)' }}>{item.reason}</div>
                      <div style={{ fontSize: '0.78rem', color: 'var(--og-color-text-secondary)', marginTop: '2px' }}>
                        Bằng chứng: {item.buyerEvidenceUrls?.join(', ') || 'Chưa cung cấp'}
                      </div>
                    </td>
                    <td style={{ padding: '14px 16px' }}>
                      <div style={{ fontSize: '0.84rem', color: 'var(--og-color-text-primary)' }}>
                        Mua: <strong style={{ color: '#2563eb' }}>{item.buyerName}</strong>
                      </div>
                      <div style={{ fontSize: '0.84rem', color: 'var(--og-color-text-secondary)' }}>
                        Bán: <strong style={{ color: 'var(--og-color-primary)' }}>{item.sellerName}</strong>
                      </div>
                    </td>
                    <td style={{ padding: '14px 16px', fontWeight: 700, color: 'var(--og-color-primary)', fontSize: '0.95rem', whiteSpace: 'nowrap' }}>
                      {formatVnd(item.disputeAmount)}
                    </td>
                    <td style={{ padding: '14px 16px' }}>{renderStatus(item.status)}</td>
                    <td style={{ padding: '14px 16px', textAlign: 'right' }}>
                      <TableActionDropdown
                        ariaLabel={`Thao tác phân xử đơn ${item.orderCode}`}
                        items={[
                          ...(item.status === 'OPEN' || item.status === 'INVESTIGATING'
                            ? [
                                {
                                  label: 'Xử lý tranh chấp',
                                  icon: <ConsoleIcons.Scale size={16} />,
                                  onClick: () => handleOpenArbitration(item),
                                },
                              ]
                            : []),
                          {
                            label: 'Đẩy lên Admin xử lý',
                            icon: <ConsoleIcons.AlertTriangle size={16} />,
                            variant: 'danger',
                            onClick: () => alert(`Đã đẩy vụ việc ${item.orderCode} lên Admin cấp cao do phát hiện lỗi hệ thống.`),
                          },
                          {
                            label: 'Sao chép mã đơn',
                            icon: <ConsoleIcons.FileText size={16} />,
                            onClick: () => {
                              navigator.clipboard?.writeText(item.orderCode);
                              alert(`Đã sao chép mã ${item.orderCode}`);
                            },
                          },
                        ]}
                      />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* Modal Arbitration */}
      {selectedDispute && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(15, 23, 42, 0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '16px',
            backdropFilter: 'blur(2px)',
          }}
        >
          <div
            style={{
              background: 'var(--og-color-surface)',
              borderRadius: '14px',
              border: '1px solid var(--og-color-border)',
              padding: '24px',
              width: '100%',
              maxWidth: '560px',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
            }}
          >
            <h2 style={{ margin: '0 0 8px', fontSize: '1.25rem', color: 'var(--og-color-text-primary)', fontWeight: 700 }}>
              Xử lý tranh chấp: {selectedDispute.orderCode}
            </h2>
            <p style={{ margin: '0 0 16px', fontSize: '0.86rem', color: 'var(--og-color-text-secondary)' }}>
              Số tiền tranh chấp đang giữ tại Escrow:{' '}
              <strong style={{ color: 'var(--og-color-primary)' }}>{formatVnd(selectedDispute.disputeAmount)}</strong>
            </p>

            <div style={{ marginBottom: '14px' }}>
              <label style={{ display: 'block', fontSize: '0.84rem', color: 'var(--og-color-text-secondary)', marginBottom: '6px', fontWeight: 600 }}>
                Chọn cách xử lý khoản thanh toán *
              </label>
              <Select hideLabel label="Chọn cách xử lý khoản thanh toán" value={String(verdictAction)} onChange={(value) => setVerdictAction(value as 'FULL_REFUND' | 'RELEASE_SELLER')} options={[({ value: "FULL_REFUND", label: "Chấp thuận trả hàng; hoàn tiền hàng sau khi giao trả thành công" }), ({ value: "RELEASE_SELLER", label: "Bác khiếu nại & Giải ngân tiền cho Người bán" })]} />
            </div>

            <div style={{ marginBottom: '16px' }}>
              <label style={{ display: 'block', fontSize: '0.84rem', color: 'var(--og-color-text-secondary)', marginBottom: '6px', fontWeight: 600 }}>
                Lý do xử lý và hướng dẫn cho hai bên *
              </label>
              <textarea
                rows={4}
                required
                placeholder="Nêu rõ kết quả đối soát video mở kiện hàng, lý do chọn cách xử lý..."
                value={verdictNotes}
                onChange={(e) => setVerdictNotes(e.target.value)}
                style={{
                  width: '100%',
                  padding: '9px 12px',
                  background: 'var(--og-color-surface)',
                  border: '1px solid var(--og-color-border-strong)',
                  borderRadius: '8px',
                  color: 'var(--og-color-text-primary)',
                  fontSize: '0.88rem',
                  boxSizing: 'border-box',
                }}
              />
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
              <button
                type="button"
                onClick={() => setSelectedDispute(null)}
                style={{
                  padding: '9px 16px',
                  background: 'var(--og-color-surface-subtle)',
                  border: '1px solid var(--og-color-border-strong)',
                  color: 'var(--og-color-text-secondary)',
                  borderRadius: '8px',
                  fontSize: '0.88rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                onClick={handleApplyVerdict}
                disabled={!verdictNotes.trim()}
                style={{
                  padding: '9px 18px',
                  background: 'var(--og-color-primary-fill)',
                  border: 'none',
                  color: '#ffffff',
                  borderRadius: '8px',
                  fontSize: '0.88rem',
                  fontWeight: 600,
                  cursor: !verdictNotes.trim() ? 'not-allowed' : 'pointer',
                  opacity: !verdictNotes.trim() ? 0.6 : 1,
                  boxShadow: '0 1px 2px rgba(5, 150, 105, 0.2)',
                }}
              >
                Xác nhận xử lý tranh chấp
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
