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
  const [verdictAction, setVerdictAction] = useState<'FULL_REFUND' | 'RELEASE_SELLER' | 'PARTIAL_REFUND'>('FULL_REFUND');
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

    resolveDispute(selectedDispute.id, verdictAction, verdictNotes.trim());
    setSuccessMessage(`Đã ban hành phán quyết trọng tài cho vụ việc ${selectedDispute.orderCode} thành công.`);
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
            ĐÃ HOÀN TIỀN CHO BUYER
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
            ĐÃ GIẢI NGÂN CHO SELLER
          </span>
        );
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Page Header */}
      <div>
        <h1 style={{ fontSize: '1.75rem', margin: '0 0 6px', color: '#0f172a', fontWeight: 800 }}>
          Phân xử tranh chấp & Khiếu nại giao dịch
        </h1>
        <p style={{ margin: 0, color: '#64748b', fontSize: '0.92rem' }}>
          Đóng vai trò trọng tài trung gian: kiểm tra video unbox của người mua, đối chiếu ảnh chụp đóng gói của người bán và đưa ra quyết định xử lý dòng tiền.
        </p>
      </div>

      {successMessage && (
        <div
          style={{
            background: '#ecfdf5',
            border: '1px solid #a7f3d0',
            borderRadius: '8px',
            padding: '12px 16px',
            color: '#065f46',
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
          background: '#ffffff',
          borderRadius: '12px',
          border: '1px solid #e2e8f0',
          overflow: 'hidden',
          boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
        }}
      >
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.88rem' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid #e2e8f0', color: '#64748b', background: '#f8fafc' }}>
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
                      borderBottom: '1px solid #f1f5f9',
                      background: isSelected ? '#f0fdf4' : 'transparent',
                      transition: 'background-color 0.15s ease',
                    }}
                    onMouseEnter={(e) => {
                      if (!isSelected) e.currentTarget.style.background = '#f8fafc';
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
                      <div style={{ fontWeight: 700, color: '#0f172a', fontFamily: 'monospace' }}>
                        {item.orderCode}
                      </div>
                      <div style={{ fontSize: '0.74rem', color: '#64748b' }}>Mở lúc: {item.createdAt}</div>
                    </td>
                    <td style={{ padding: '14px 16px', maxWidth: '300px' }}>
                      <div style={{ fontWeight: 600, color: '#0f172a' }}>{item.reason}</div>
                      <div style={{ fontSize: '0.78rem', color: '#64748b', marginTop: '2px' }}>
                        Bằng chứng: {item.buyerEvidenceUrls?.join(', ') || 'Chưa cung cấp'}
                      </div>
                    </td>
                    <td style={{ padding: '14px 16px' }}>
                      <div style={{ fontSize: '0.84rem', color: '#0f172a' }}>
                        Mua: <strong style={{ color: '#2563eb' }}>{item.buyerName}</strong>
                      </div>
                      <div style={{ fontSize: '0.84rem', color: '#64748b' }}>
                        Bán: <strong style={{ color: '#047857' }}>{item.sellerName}</strong>
                      </div>
                    </td>
                    <td style={{ padding: '14px 16px', fontWeight: 700, color: '#059669', fontSize: '0.95rem', whiteSpace: 'nowrap' }}>
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
                                  label: 'Ban hành phán quyết trọng tài',
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
              background: '#ffffff',
              borderRadius: '14px',
              border: '1px solid #e2e8f0',
              padding: '24px',
              width: '100%',
              maxWidth: '560px',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
            }}
          >
            <h2 style={{ margin: '0 0 8px', fontSize: '1.25rem', color: '#0f172a', fontWeight: 700 }}>
              Ban hành phán quyết: {selectedDispute.orderCode}
            </h2>
            <p style={{ margin: '0 0 16px', fontSize: '0.86rem', color: '#64748b' }}>
              Số tiền tranh chấp đang giữ tại Escrow:{' '}
              <strong style={{ color: '#059669' }}>{formatVnd(selectedDispute.disputeAmount)}</strong>
            </p>

            <div style={{ marginBottom: '14px' }}>
              <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                Lựa chọn phương án phán quyết dòng tiền *
              </label>
              <select
                value={verdictAction}
                onChange={(e) => setVerdictAction(e.target.value as 'FULL_REFUND' | 'RELEASE_SELLER' | 'PARTIAL_REFUND')}
                style={{
                  width: '100%',
                  padding: '9px 12px',
                  background: '#ffffff',
                  border: '1px solid #cbd5e1',
                  borderRadius: '8px',
                  color: '#0f172a',
                  fontSize: '0.88rem',
                  boxSizing: 'border-box',
                }}
              >
                <option value="FULL_REFUND">Hoàn tiền 100% cho Người mua (Hàng lỗi sai mô tả)</option>
                <option value="RELEASE_SELLER">Bác khiếu nại & Giải ngân tiền cho Người bán</option>
                <option value="PARTIAL_REFUND">Chia đôi hỗ trợ hoàn một phần (Thương lượng)</option>
              </select>
            </div>

            <div style={{ marginBottom: '16px' }}>
              <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                Căn cứ phán quyết & Lời dặn KTV *
              </label>
              <textarea
                rows={4}
                required
                placeholder="Nêu rõ kết quả đối soát video mở kiện hàng, lý do đưa ra phán quyết..."
                value={verdictNotes}
                onChange={(e) => setVerdictNotes(e.target.value)}
                style={{
                  width: '100%',
                  padding: '9px 12px',
                  background: '#ffffff',
                  border: '1px solid #cbd5e1',
                  borderRadius: '8px',
                  color: '#0f172a',
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
                  background: '#f1f5f9',
                  border: '1px solid #cbd5e1',
                  color: '#475569',
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
                  background: '#059669',
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
                Thực thi phán quyết
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
