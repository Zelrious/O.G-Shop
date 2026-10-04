import React, { useState } from 'react';
import { useManagementStore } from '../../features/management/useManagementStore';
import { ConsoleIcons } from '../../shared/layout/ConsoleIcons';
import {
  TableActionDropdown,
  BulkActionBar,
  TableCheckbox,
} from '../../shared/layout/ConsoleTableActions';
import { formatVnd } from '../../shared/utils/currency';

export const AdminVouchersPage: React.FC = () => {
  const { vouchers, createVoucher, toggleVoucherStatus } = useManagementStore();
  const [search, setSearch] = useState('');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Multi-select state
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  // Form states
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [discountType, setDiscountType] = useState<'AMOUNT' | 'PERCENT'>('AMOUNT');
  const [discountValue, setDiscountValue] = useState<number>(50000);
  const [minOrderValue, setMinOrderValue] = useState<number>(500000);
  const [totalQuantity, setTotalQuantity] = useState<number>(200);
  const [startDate, setStartDate] = useState(new Date().toISOString().slice(0, 10));
  const [endDate, setEndDate] = useState('2026-12-31');
  const [formError, setFormError] = useState<string | null>(null);

  const filteredVouchers = vouchers.filter((v) => {
    return (
      v.code.toLowerCase().includes(search.toLowerCase()) ||
      v.name.toLowerCase().includes(search.toLowerCase())
    );
  });

  const isAllSelected =
    filteredVouchers.length > 0 && filteredVouchers.every((v) => selectedIds.has(v.id));
  const isSomeSelected =
    filteredVouchers.some((v) => selectedIds.has(v.id)) && !isAllSelected;

  const handleToggleAll = () => {
    if (isAllSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(filteredVouchers.map((v) => v.id)));
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

  const handleBulkDeactivate = () => {
    selectedIds.forEach((id) => {
      const v = vouchers.find((item) => item.id === id);
      if (v && v.status === 'ACTIVE') {
        toggleVoucherStatus(id);
      }
    });
    setSuccessMessage(`Đã tạm dừng ${selectedIds.size} mã voucher.`);
    setSelectedIds(new Set());
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  const handleBulkActivate = () => {
    selectedIds.forEach((id) => {
      const v = vouchers.find((item) => item.id === id);
      if (v && v.status === 'INACTIVE') {
        toggleVoucherStatus(id);
      }
    });
    setSuccessMessage(`Đã kích hoạt ${selectedIds.size} mã voucher.`);
    setSelectedIds(new Set());
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  const handleOpenCreateModal = () => {
    setCode('');
    setName('');
    setDiscountType('AMOUNT');
    setDiscountValue(50000);
    setMinOrderValue(500000);
    setTotalQuantity(200);
    setStartDate(new Date().toISOString().slice(0, 10));
    setEndDate('2026-12-31');
    setFormError(null);
    setIsModalOpen(true);
  };

  const handleCreateVoucher = (e: React.FormEvent) => {
    e.preventDefault();
    if (!code.trim() || !name.trim()) {
      setFormError('Vui lòng nhập mã và tên chương trình Voucher.');
      return;
    }

    createVoucher({
      code: code.trim().toUpperCase(),
      name: name.trim(),
      discountType,
      discountValue,
      minOrderValue,
      totalQuantity,
      startDate,
      endDate,
      status: 'ACTIVE',
    });

    setSuccessMessage(`Đã phát hành Voucher ${code.trim().toUpperCase()} thành công.`);
    setIsModalOpen(false);
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', margin: '0 0 6px', color: '#0f172a', fontWeight: 800 }}>
            Tạo & Quản lý chương trình Voucher
          </h1>
          <p style={{ margin: 0, color: '#64748b', fontSize: '0.92rem' }}>
            Thiết lập mã khuyến mại, mức giảm, giá trị đơn tối thiểu, số lượng phát hành và thời hạn hiệu lực.
          </p>
        </div>

        <button
          type="button"
          onClick={handleOpenCreateModal}
          style={{
            padding: '10px 18px',
            background: '#059669',
            color: '#ffffff',
            border: 'none',
            borderRadius: '8px',
            fontSize: '0.88rem',
            fontWeight: 600,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            boxShadow: '0 1px 3px rgba(5, 150, 105, 0.2)',
          }}
          onMouseEnter={(e) => (e.currentTarget.style.background = '#047857')}
          onMouseLeave={(e) => (e.currentTarget.style.background = '#059669')}
        >
          <ConsoleIcons.Plus size={18} />
          <span>Tạo Voucher mới</span>
        </button>
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

      {/* Search Bar & Counter */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '12px',
          background: '#ffffff',
          padding: '16px',
          borderRadius: '12px',
          border: '1px solid #e2e8f0',
          boxShadow: '0 1px 2px rgba(0,0,0,0.03)',
        }}
      >
        <div style={{ position: 'relative', width: '100%', maxWidth: '420px' }}>
          <span
            style={{
              position: 'absolute',
              left: '12px',
              top: '50%',
              transform: 'translateY(-50%)',
              color: '#94a3b8',
              display: 'flex',
            }}
          >
            <ConsoleIcons.Search size={16} />
          </span>
          <input
            type="text"
            placeholder="Tìm theo mã code, tên chương trình..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{
              width: '100%',
              padding: '9px 12px 9px 36px',
              background: '#ffffff',
              border: '1px solid #cbd5e1',
              borderRadius: '8px',
              color: '#0f172a',
              fontSize: '0.88rem',
              boxSizing: 'border-box',
            }}
          />
        </div>

        <div style={{ fontSize: '0.86rem', color: '#64748b' }}>
          Tổng số Voucher: <strong style={{ color: '#0f172a' }}>{vouchers.length}</strong> mã
        </div>
      </div>

      {/* Bulk Action Bar */}
      <BulkActionBar
        selectedCount={selectedIds.size}
        totalCount={filteredVouchers.length}
        onClearSelection={() => setSelectedIds(new Set())}
        actions={[
          {
            label: 'Tạm dừng đã chọn',
            icon: <ConsoleIcons.Lock size={15} />,
            variant: 'danger',
            onClick: handleBulkDeactivate,
          },
          {
            label: 'Kích hoạt đã chọn',
            icon: <ConsoleIcons.Unlock size={15} />,
            variant: 'primary',
            onClick: handleBulkActivate,
          },
        ]}
      />

      {/* Vouchers Table (Light Theme) */}
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
                    ariaLabel="Chọn tất cả voucher"
                  />
                </th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Mã Voucher</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Tên chương trình</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Mức giảm</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Đơn tối thiểu</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Đã dùng / Tổng số</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Thời hạn áp dụng</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Trạng thái</th>
                <th style={{ padding: '14px 16px', textAlign: 'right', minWidth: '95px', whiteSpace: 'nowrap' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {filteredVouchers.length === 0 ? (
                <tr>
                  <td colSpan={9} style={{ padding: '40px', textAlign: 'center', color: '#64748b' }}>
                    Không tìm thấy chương trình Voucher nào.
                  </td>
                </tr>
              ) : (
                filteredVouchers.map((item) => {
                  const isSelected = selectedIds.has(item.id);
                  const isAvailable = item.status === 'ACTIVE' && item.usedQuantity < item.totalQuantity;
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
                          ariaLabel={`Chọn voucher ${item.code}`}
                        />
                      </td>
                      <td style={{ padding: '14px 16px' }}>
                        <span
                          style={{
                            fontFamily: 'monospace',
                            fontWeight: 700,
                            padding: '3px 8px',
                            borderRadius: '6px',
                            background: '#eff6ff',
                            color: '#1d4ed8',
                            border: '1px solid #bfdbfe',
                            fontSize: '0.85rem',
                          }}
                        >
                          {item.code}
                        </span>
                      </td>
                      <td style={{ padding: '14px 16px', fontWeight: 600, color: '#0f172a' }}>
                        {item.name}
                      </td>
                      <td style={{ padding: '14px 16px', color: '#059669', fontWeight: 700, whiteSpace: 'nowrap' }}>
                        {item.discountType === 'PERCENT'
                          ? `Giảm ${item.discountValue}%`
                          : `-${formatVnd(item.discountValue)}`}
                      </td>
                      <td style={{ padding: '14px 16px', color: '#334155', whiteSpace: 'nowrap' }}>
                        {formatVnd(item.minOrderValue)}
                      </td>
                      <td style={{ padding: '14px 16px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <span style={{ fontWeight: 700, color: '#0f172a' }}>{item.usedQuantity}</span>
                          <span style={{ color: '#64748b' }}>/ {item.totalQuantity}</span>
                        </div>
                        <div style={{ width: '80px', height: '4px', background: '#e2e8f0', borderRadius: '2px', marginTop: '4px', overflow: 'hidden' }}>
                          <div
                            style={{
                              width: `${Math.min(100, Math.round((item.usedQuantity / item.totalQuantity) * 100))}%`,
                              height: '100%',
                              background: '#059669',
                            }}
                          />
                        </div>
                      </td>
                      <td style={{ padding: '14px 16px', color: '#64748b', fontSize: '0.8rem' }}>
                        {item.startDate} đến {item.endDate}
                      </td>
                      <td style={{ padding: '14px 16px' }}>
                        {isAvailable ? (
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
                            ĐANG ÁP DỤNG
                          </span>
                        ) : (
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
                            TẠM DỪNG / HẾT
                          </span>
                        )}
                      </td>
                      <td style={{ padding: '14px 16px', textAlign: 'right' }}>
                        <TableActionDropdown
                          ariaLabel={`Thao tác cho voucher ${item.code}`}
                          items={[
                            {
                              label: 'Sao chép mã voucher',
                              icon: <ConsoleIcons.Ticket size={16} />,
                              onClick: () => {
                                navigator.clipboard?.writeText(item.code);
                                alert(`Đã sao chép mã ${item.code}`);
                              },
                            },
                            {
                              label: item.status === 'ACTIVE' ? 'Tạm dừng mã voucher' : 'Kích hoạt lại voucher',
                              icon: item.status === 'ACTIVE' ? <ConsoleIcons.Lock size={16} /> : <ConsoleIcons.Unlock size={16} />,
                              variant: item.status === 'ACTIVE' ? 'danger' : 'normal',
                              onClick: () => {
                                toggleVoucherStatus(item.id);
                                setSuccessMessage(`Đã đổi trạng thái mã ${item.code}.`);
                                setTimeout(() => setSuccessMessage(null), 3000);
                              },
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

      {/* Modal Create Voucher (Light Theme) */}
      {isModalOpen && (
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
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h2 style={{ margin: 0, fontSize: '1.25rem', color: '#0f172a', fontWeight: 700 }}>
                Tạo mã Voucher khuyến mại mới
              </h2>
              <button
                type="button"
                onClick={() => setIsModalOpen(false)}
                style={{ background: 'transparent', border: 'none', color: '#94a3b8', fontSize: '1.2rem', cursor: 'pointer' }}
              >
                ✕
              </button>
            </div>

            {formError && (
              <div style={{ padding: '10px 14px', background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '6px', color: '#dc2626', fontSize: '0.85rem', marginBottom: '16px' }}>
                {formError}
              </div>
            )}

            <form onSubmit={handleCreateVoucher} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                    Mã code (viết hoa) *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="VD: OGGIAM50K"
                    value={code}
                    onChange={(e) => setCode(e.target.value.toUpperCase())}
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

                <div>
                  <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                    Loại khuyến mại
                  </label>
                  <select
                    value={discountType}
                    onChange={(e) => setDiscountType(e.target.value as 'AMOUNT' | 'PERCENT')}
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
                    <option value="AMOUNT">Giảm tiền trực tiếp (VNĐ)</option>
                    <option value="PERCENT">Giảm theo tỷ lệ (%)</option>
                  </select>
                </div>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                  Tên chương trình voucher *
                </label>
                <input
                  type="text"
                  required
                  placeholder="VD: Tri ân khách hàng tháng 10"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
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

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                    Mức giảm ({discountType === 'PERCENT' ? '%' : 'VNĐ'}) *
                  </label>
                  <input
                    type="number"
                    required
                    min="1"
                    value={discountValue}
                    onChange={(e) => setDiscountValue(Number(e.target.value))}
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

                <div>
                  <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                    Đơn hàng tối thiểu (VNĐ) *
                  </label>
                  <input
                    type="number"
                    required
                    min="0"
                    step="50000"
                    value={minOrderValue}
                    onChange={(e) => setMinOrderValue(Number(e.target.value))}
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
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                    Số lượng phát hành *
                  </label>
                  <input
                    type="number"
                    required
                    min="1"
                    value={totalQuantity}
                    onChange={(e) => setTotalQuantity(Number(e.target.value))}
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

                <div>
                  <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                    Ngày hết hạn áp dụng *
                  </label>
                  <input
                    type="date"
                    required
                    value={endDate}
                    onChange={(e) => setEndDate(e.target.value)}
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
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '14px' }}>
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
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
                  type="submit"
                  style={{
                    padding: '9px 18px',
                    background: '#059669',
                    border: 'none',
                    color: '#ffffff',
                    borderRadius: '8px',
                    fontSize: '0.88rem',
                    fontWeight: 600,
                    cursor: 'pointer',
                    boxShadow: '0 1px 2px rgba(5, 150, 105, 0.2)',
                  }}
                >
                  Phát hành Voucher
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
