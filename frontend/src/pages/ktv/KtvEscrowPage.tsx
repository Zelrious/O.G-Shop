import { Select } from '../../shared/components/Select/Select';
import React, { useState } from 'react';
import { useManagementStore } from '../../features/management/useManagementStore';
import { ConsoleIcons } from '../../shared/layout/ConsoleIcons';
import { EscrowRecord } from '../../features/management/managementStore';
import {
  TableActionDropdown,
  BulkActionBar,
  TableCheckbox,
} from '../../shared/layout/ConsoleTableActions';
import { formatVnd } from '../../shared/utils/currency';

export const KtvEscrowPage: React.FC = () => {
  const { escrows } = useManagementStore();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [selectedEscrow, setSelectedEscrow] = useState<EscrowRecord | null>(null);

  // Multi-select state
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  const filteredEscrows = escrows.filter((item) => {
    const matchSearch =
      item.orderCode.toLowerCase().includes(search.toLowerCase()) ||
      item.buyerName.toLowerCase().includes(search.toLowerCase()) ||
      item.sellerName.toLowerCase().includes(search.toLowerCase()) ||
      item.productTitle.toLowerCase().includes(search.toLowerCase());
    const matchStatus = statusFilter === 'ALL' || item.status === statusFilter;
    return matchSearch && matchStatus;
  });

  const isAllSelected =
    filteredEscrows.length > 0 && filteredEscrows.every((e) => selectedIds.has(e.id));
  const isSomeSelected =
    filteredEscrows.some((e) => selectedIds.has(e.id)) && !isAllSelected;

  const handleToggleAll = () => {
    if (isAllSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(filteredEscrows.map((e) => e.id)));
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

  const totalHeld = escrows
    .filter((e) => e.status === 'HELD')
    .reduce((sum, e) => sum + e.amount, 0);
  const totalFrozen = escrows
    .filter((e) => e.status === 'FROZEN')
    .reduce((sum, e) => sum + e.amount, 0);
  const totalReleased = escrows
    .filter((e) => e.status === 'RELEASED')
    .reduce((sum, e) => sum + e.amount, 0);

  const renderStatusBadge = (status: EscrowRecord['status']) => {
    switch (status) {
      case 'HELD':
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
            ĐANG TẠM GIỮ
          </span>
        );
      case 'FROZEN':
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
            ĐÓNG BĂNG KHIẾU NẠI
          </span>
        );
      case 'RELEASED':
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
            ĐÃ GIẢI NGÂN
          </span>
        );
      case 'REFUNDED':
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
            ĐÃ HOÀN TIỀN
          </span>
        );
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Header */}
      <div>
        <h1 style={{ fontSize: '1.75rem', margin: '0 0 6px', color: 'var(--og-color-text-primary)', fontWeight: 800 }}>
          Quản lý dòng tiền Ký Quỹ Escrow
        </h1>
        <p style={{ margin: 0, color: 'var(--og-color-text-secondary)', fontSize: '0.92rem' }}>
          Giám sát trạng thái dòng tiền trung gian giữ hộ cho các đơn mua bán đồ cũ trên nền tảng O.G Shop.
        </p>
      </div>

      {/* KPI Stats Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '16px' }}>
        <div style={{ background: 'var(--og-color-surface)', padding: '20px', borderRadius: '12px', border: '1px solid var(--og-color-border)', boxShadow: '0 1px 3px rgba(0,0,0,0.04)' }}>
          <div style={{ fontSize: '0.82rem', color: 'var(--og-color-text-secondary)', fontWeight: 600 }}>Tiền đang bảo vệ tại Escrow (Held)</div>
          <div style={{ fontSize: '1.65rem', fontWeight: 800, color: 'var(--og-color-primary)', margin: '6px 0' }}>
            {formatVnd(totalHeld)}
          </div>
          <div style={{ fontSize: '0.78rem', color: '#16a34a', fontWeight: 600 }}>Chờ người mua đồng kiểm tra hàng</div>
        </div>

        <div style={{ background: 'var(--og-color-surface)', padding: '20px', borderRadius: '12px', border: '1px solid var(--og-color-border)', boxShadow: '0 1px 3px rgba(0,0,0,0.04)' }}>
          <div style={{ fontSize: '0.82rem', color: 'var(--og-color-text-secondary)', fontWeight: 600 }}>Dòng tiền đang đóng băng tranh chấp</div>
          <div style={{ fontSize: '1.65rem', fontWeight: 800, color: '#dc2626', margin: '6px 0' }}>
            {formatVnd(totalFrozen)}
          </div>
          <div style={{ fontSize: '0.78rem', color: '#dc2626', fontWeight: 600 }}>Cần KTV can thiệp phân xử</div>
        </div>

        <div style={{ background: 'var(--og-color-surface)', padding: '20px', borderRadius: '12px', border: '1px solid var(--og-color-border)', boxShadow: '0 1px 3px rgba(0,0,0,0.04)' }}>
          <div style={{ fontSize: '0.82rem', color: 'var(--og-color-text-secondary)', fontWeight: 600 }}>Đã giải ngân thành công (Tháng này)</div>
          <div style={{ fontSize: '1.65rem', fontWeight: 800, color: '#2563eb', margin: '6px 0' }}>
            {formatVnd(totalReleased)}
          </div>
          <div style={{ fontSize: '0.78rem', color: 'var(--og-color-text-secondary)' }}>Đã chuyển vào ví người bán</div>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '12px',
          background: 'var(--og-color-surface)',
          padding: '16px',
          borderRadius: '12px',
          border: '1px solid var(--og-color-border)',
          boxShadow: '0 1px 2px rgba(0,0,0,0.03)',
        }}
      >
        <div style={{ position: 'relative', width: '100%', maxWidth: '380px' }}>
          <span
            style={{
              position: 'absolute',
              left: '12px',
              top: '50%',
              transform: 'translateY(-50%)',
              color: 'var(--og-color-text-muted)',
              display: 'flex',
            }}
          >
            <ConsoleIcons.Search size={16} />
          </span>
          <input
            type="text"
            placeholder="Tìm theo mã đơn, người mua, người bán..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{
              width: '100%',
              padding: '9px 12px 9px 36px',
              background: 'var(--og-color-surface)',
              border: '1px solid var(--og-color-border-strong)',
              borderRadius: '8px',
              color: 'var(--og-color-text-primary)',
              fontSize: '0.88rem',
              boxSizing: 'border-box',
            }}
          />
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span style={{ fontSize: '0.84rem', color: 'var(--og-color-text-secondary)', fontWeight: 600 }}>Trạng thái:</span>
          <Select hideLabel label="Trạng thái" value={String(statusFilter)} onChange={(value) => setStatusFilter(value)} options={[({ value: "ALL", label: "Tất cả giao dịch Escrow" }), ({ value: "HELD", label: "Đang tạm giữ (HELD)" }), ({ value: "FROZEN", label: "Đóng băng tranh chấp (FROZEN)" }), ({ value: "RELEASED", label: "Đã giải ngân (RELEASED)" }), ({ value: "REFUNDED", label: "Đã hoàn tiền (REFUNDED)" })]} />
        </div>
      </div>

      {/* Bulk Action Bar */}
      <BulkActionBar
        selectedCount={selectedIds.size}
        totalCount={filteredEscrows.length}
        onClearSelection={() => setSelectedIds(new Set())}
        actions={[
          {
            label: 'Giải ngân hàng loạt',
            icon: <ConsoleIcons.CheckCircle size={15} />,
            variant: 'primary',
            onClick: () => {
              alert(`Đã yêu cầu giải ngân hàng loạt cho ${selectedIds.size} đơn ký quỹ.`);
              setSelectedIds(new Set());
            },
          },
          {
            label: 'Đóng băng bảo vệ',
            icon: <ConsoleIcons.Lock size={15} />,
            variant: 'danger',
            onClick: () => {
              alert(`Đã đóng băng ${selectedIds.size} đơn hàng đang xử lý.`);
              setSelectedIds(new Set());
            },
          },
        ]}
      />

      {/* Table */}
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
                    ariaLabel="Chọn tất cả giao dịch"
                  />
                </th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Mã đơn hàng</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Sản phẩm</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Số tiền ký quỹ</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Bên mua / Bên bán</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Trạng thái</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Tự động giải ngân sau</th>
                <th style={{ padding: '14px 16px', textAlign: 'right', minWidth: '95px', whiteSpace: 'nowrap' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {filteredEscrows.length === 0 ? (
                <tr>
                  <td colSpan={8} style={{ padding: '40px', textAlign: 'center', color: 'var(--og-color-text-secondary)' }}>
                    Không tìm thấy giao dịch Escrow nào phù hợp.
                  </td>
                </tr>
              ) : (
                filteredEscrows.map((item) => {
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
                          ariaLabel={`Chọn đơn ${item.orderCode}`}
                        />
                      </td>
                      <td style={{ padding: '14px 16px' }}>
                        <span style={{ fontWeight: 700, color: 'var(--og-color-text-primary)', fontFamily: 'monospace' }}>
                          {item.orderCode}
                        </span>
                        <div style={{ fontSize: '0.74rem', color: 'var(--og-color-text-secondary)' }}>{item.updatedAt}</div>
                      </td>
                      <td style={{ padding: '14px 16px', maxWidth: '240px' }}>
                        <div style={{ fontWeight: 600, color: 'var(--og-color-text-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {item.productTitle}
                        </div>
                      </td>
                      <td style={{ padding: '14px 16px', fontWeight: 700, color: 'var(--og-color-primary)', fontSize: '0.95rem', whiteSpace: 'nowrap' }}>
                        {formatVnd(item.amount)}
                      </td>
                      <td style={{ padding: '14px 16px' }}>
                        <div style={{ fontSize: '0.84rem', color: 'var(--og-color-text-primary)' }}>
                          Mua: <strong style={{ color: '#2563eb' }}>{item.buyerName}</strong>
                        </div>
                        <div style={{ fontSize: '0.84rem', color: 'var(--og-color-text-secondary)' }}>
                          Bán: <strong style={{ color: 'var(--og-color-primary)' }}>{item.sellerName}</strong>
                        </div>
                      </td>
                      <td style={{ padding: '14px 16px' }}>
                        {renderStatusBadge(item.status)}
                      </td>
                      <td style={{ padding: '14px 16px', color: 'var(--og-color-text-secondary)', fontSize: '0.82rem' }}>
                        {item.releaseDeadline || '3 ngày sau giao hàng; tạm dừng nếu có vụ việc mở'}
                      </td>
                      <td style={{ padding: '14px 16px', textAlign: 'right' }}>
                        <TableActionDropdown
                          ariaLabel={`Thao tác cho đơn ${item.orderCode}`}
                          items={[
                            {
                              label: 'Xem chi tiết đối soát',
                              icon: <ConsoleIcons.FileText size={16} />,
                              onClick: () => setSelectedEscrow(item),
                            },
                            {
                              label: 'Giải ngân cho người bán',
                              icon: <ConsoleIcons.CheckCircle size={16} />,
                              disabled: item.status === 'RELEASED',
                              onClick: () => alert(`Đã kích hoạt giải ngân tiền cho đơn ${item.orderCode}`),
                            },
                            {
                              label: 'Đóng băng tranh chấp',
                              icon: <ConsoleIcons.Lock size={16} />,
                              variant: 'danger',
                              disabled: item.status === 'FROZEN',
                              onClick: () => alert(`Đã đóng băng tạm giữ đơn ${item.orderCode}`),
                            },
                          ]}
                        />
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Modal Detail Escrow */}
      {selectedEscrow && (
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
              maxWidth: '520px',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h2 style={{ margin: 0, fontSize: '1.25rem', color: 'var(--og-color-text-primary)', fontWeight: 700 }}>
                Chi tiết Ký Quỹ Escrow: {selectedEscrow.orderCode}
              </h2>
              <button
                type="button"
                onClick={() => setSelectedEscrow(null)}
                style={{ background: 'transparent', border: 'none', color: 'var(--og-color-text-muted)', fontSize: '1.2rem', cursor: 'pointer' }}
              >
                ✕
              </button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '0.9rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--og-color-border)' }}>
                <span style={{ color: 'var(--og-color-text-secondary)' }}>Mặt hàng:</span>
                <strong style={{ color: 'var(--og-color-text-primary)' }}>{selectedEscrow.productTitle}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--og-color-border)' }}>
                <span style={{ color: 'var(--og-color-text-secondary)' }}>Số tiền ký quỹ:</span>
                <strong style={{ color: 'var(--og-color-primary)', fontSize: '1.1rem' }}>{formatVnd(selectedEscrow.amount)}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--og-color-border)' }}>
                <span style={{ color: 'var(--og-color-text-secondary)' }}>Người mua:</span>
                <span style={{ color: 'var(--og-color-text-primary)' }}>{selectedEscrow.buyerName}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--og-color-border)' }}>
                <span style={{ color: 'var(--og-color-text-secondary)' }}>Người bán:</span>
                <span style={{ color: 'var(--og-color-text-primary)' }}>{selectedEscrow.sellerName}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--og-color-border)' }}>
                <span style={{ color: 'var(--og-color-text-secondary)' }}>Trạng thái Escrow:</span>
                <span>{renderStatusBadge(selectedEscrow.status)}</span>
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '20px' }}>
              <button
                type="button"
                onClick={() => setSelectedEscrow(null)}
                style={{
                  padding: '9px 18px',
                  background: 'var(--og-color-primary-fill)',
                  border: 'none',
                  color: '#ffffff',
                  borderRadius: '8px',
                  fontSize: '0.88rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
              >
                Đóng lại
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
