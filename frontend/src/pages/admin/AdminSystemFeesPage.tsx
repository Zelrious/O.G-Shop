import React, { useState } from 'react';
import { useManagementStore } from '../../features/management/useManagementStore';
import { ConsoleIcons } from '../../shared/layout/ConsoleIcons';
import {
  TableActionDropdown,
  BulkActionBar,
  TableCheckbox,
} from '../../shared/layout/ConsoleTableActions';
import { formatVnd } from '../../shared/utils/currency';

export const AdminSystemFeesPage: React.FC = () => {
  const { systemFees, updateSystemFees } = useManagementStore();
  const [fees, setFees] = useState({
    escrowFeePercent: systemFees.escrowFeePercent,
    minTransactionFee: systemFees.minTransactionFee,
    withdrawalFeeVnd: systemFees.withdrawalFeeVnd,
    buyerProtectionFixedFee: systemFees.buyerProtectionFixedFee,
  });
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Multi-select for tier simulation rows
  const [selectedTiers, setSelectedTiers] = useState<Set<number>>(new Set());

  // Quick Fee Calculator State (Interactive Right Column)
  const [calcOrderValue, setCalcOrderValue] = useState<number>(5000000);

  const feeSimulationTiers = [
    { id: 1, name: 'Giao dịch nhỏ (Đồ phụ kiện / Sách cũ)', orderValue: 200000, desc: 'Phí tối thiểu bảo đảm' },
    { id: 2, name: 'Giao dịch phổ thông (Quần áo / Giày dép)', orderValue: 1200000, desc: 'Áp dụng biểu phí tiêu chuẩn' },
    { id: 3, name: 'Giao dịch giá trị cao (Điện thoại / Laptop)', orderValue: 15000000, desc: 'Escrow giữ dòng tiền kiểm định' },
    { id: 4, name: 'Giao dịch sưu tầm xa xỉ (Đồng hồ cơ / Máy ảnh Leica)', orderValue: 45000000, desc: 'Hỗ trợ kiểm tra chuyên sâu' },
  ];

  const isAllTiersSelected =
    feeSimulationTiers.length > 0 && selectedTiers.size === feeSimulationTiers.length;
  const isSomeTiersSelected =
    selectedTiers.size > 0 && !isAllTiersSelected;

  const handleToggleAllTiers = () => {
    if (isAllTiersSelected) {
      setSelectedTiers(new Set());
    } else {
      setSelectedTiers(new Set(feeSimulationTiers.map((t) => t.id)));
    }
  };

  const handleToggleTier = (id: number) => {
    const next = new Set(selectedTiers);
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    setSelectedTiers(next);
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    updateSystemFees(fees);
    setSuccessMessage('Đã cập nhật biểu phí hệ thống và ghi nhận vào lịch sử thao tác (Audit Log).');
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  // Quick calculator computation
  const calcEscrowPart = Math.round((calcOrderValue * fees.escrowFeePercent) / 100);
  const calcRawTotal = calcEscrowPart + fees.buyerProtectionFixedFee;
  const isMinFeeTriggered = calcRawTotal < fees.minTransactionFee;
  const calcTotalFee = Math.max(fees.minTransactionFee, calcRawTotal);
  const calcSellerPayout = Math.max(0, calcOrderValue - calcTotalFee);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Header */}
      <div>
        <h1 style={{ fontSize: '1.75rem', margin: '0 0 6px', color: '#0f172a', fontWeight: 800 }}>
          Quản lý biểu phí hệ thống & Escrow
        </h1>
        <p style={{ margin: 0, color: '#64748b', fontSize: '0.92rem' }}>
          Cấu hình tỷ lệ phí sàn, phí bảo vệ người mua và phí dịch vụ rút tiền áp dụng cho mọi giao dịch.
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

      {/* Main Responsive Grid Layout (Workspace Optimized: Left Main Forms & Simulation, Right Calculator & Policy) */}
      <div className="og-admin-fees-grid">
        {/* Left Column: Settings Form + Simulation Table */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
          {/* Fee Settings Form */}
          <form onSubmit={handleSave}>
            <div
              style={{
                background: '#ffffff',
                border: '1px solid #e2e8f0',
                borderRadius: '12px',
                padding: '24px',
                display: 'flex',
                flexDirection: 'column',
                gap: '20px',
                boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <h2 style={{ fontSize: '1.15rem', color: '#0f172a', margin: 0, display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 700 }}>
                  <ConsoleIcons.Sliders size={18} />
                  <span>Biểu phí giao dịch ký quỹ & Escrow</span>
                </h2>
                <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                  Áp dụng toàn hệ thống O.G Shop
                </span>
              </div>

              {/* 2x2 Grid with uniform heights so inputs align perfectly */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '20px' }}>
                {/* Field 1: Phí Escrow % */}
                <div style={{ display: 'flex', flexDirection: 'column' }}>
                  <label
                    style={{
                      minHeight: '44px',
                      display: 'flex',
                      alignItems: 'flex-end',
                      color: '#334155',
                      fontSize: '0.88rem',
                      fontWeight: 600,
                      marginBottom: '8px',
                    }}
                  >
                    Phí bảo vệ Escrow trung gian (%):
                  </label>
                  <div style={{ position: 'relative' }}>
                    <input
                      type="number"
                      step="0.1"
                      min="0"
                      max="15"
                      value={fees.escrowFeePercent}
                      onChange={(e) => setFees({ ...fees, escrowFeePercent: parseFloat(e.target.value) || 0 })}
                      style={{
                        width: '100%',
                        padding: '10px 36px 10px 12px',
                        background: '#ffffff',
                        border: '1px solid #cbd5e1',
                        borderRadius: '8px',
                        color: '#0f172a',
                        fontSize: '1rem',
                        fontWeight: 700,
                        boxSizing: 'border-box',
                      }}
                    />
                    <span style={{ position: 'absolute', right: '12px', top: '50%', transform: 'translateY(-50%)', color: '#64748b', fontWeight: 700 }}>
                      %
                    </span>
                  </div>
                  <p style={{ minHeight: '34px', margin: '6px 0 0', fontSize: '0.78rem', color: '#64748b' }}>
                    Mặc định sàn: 2.5% cho mỗi đơn thanh toán thành công qua escrow.
                  </p>
                </div>

                {/* Field 2: Mức phí sàn tối thiểu */}
                <div style={{ display: 'flex', flexDirection: 'column' }}>
                  <label
                    style={{
                      minHeight: '44px',
                      display: 'flex',
                      alignItems: 'flex-end',
                      color: '#334155',
                      fontSize: '0.88rem',
                      fontWeight: 600,
                      marginBottom: '8px',
                    }}
                  >
                    Mức phí sàn tối thiểu (VNĐ / đơn hàng):
                  </label>
                  <div style={{ position: 'relative' }}>
                    <input
                      type="number"
                      step="1000"
                      min="0"
                      value={fees.minTransactionFee}
                      onChange={(e) => setFees({ ...fees, minTransactionFee: parseInt(e.target.value, 10) || 0 })}
                      style={{
                        width: '100%',
                        padding: '10px 12px',
                        background: '#ffffff',
                        border: '1px solid #cbd5e1',
                        borderRadius: '8px',
                        color: '#0f172a',
                        fontSize: '1rem',
                        fontWeight: 700,
                        boxSizing: 'border-box',
                      }}
                    />
                    <span
                      style={{
                        position: 'absolute',
                        right: '10px',
                        top: '50%',
                        transform: 'translateY(-50%)',
                        fontSize: '0.75rem',
                        fontWeight: 600,
                        color: '#059669',
                        background: '#ecfdf5',
                        padding: '2px 6px',
                        borderRadius: '4px',
                      }}
                    >
                      {formatVnd(fees.minTransactionFee)}
                    </span>
                  </div>
                  <p style={{ minHeight: '34px', margin: '6px 0 0', fontSize: '0.78rem', color: '#64748b' }}>
                    Mức phí thấp nhất để chi trả vận hành máy chủ đối với đơn hàng giá trị nhỏ.
                  </p>
                </div>

                {/* Field 3: Phí rút tiền về ngân hàng */}
                <div style={{ display: 'flex', flexDirection: 'column' }}>
                  <label
                    style={{
                      minHeight: '44px',
                      display: 'flex',
                      alignItems: 'flex-end',
                      color: '#334155',
                      fontSize: '0.88rem',
                      fontWeight: 600,
                      marginBottom: '8px',
                    }}
                  >
                    Phí rút tiền về ngân hàng của Người bán (VNĐ / lượt):
                  </label>
                  <div style={{ position: 'relative' }}>
                    <input
                      type="number"
                      step="1000"
                      min="0"
                      value={fees.withdrawalFeeVnd}
                      onChange={(e) => setFees({ ...fees, withdrawalFeeVnd: parseInt(e.target.value, 10) || 0 })}
                      style={{
                        width: '100%',
                        padding: '10px 12px',
                        background: '#ffffff',
                        border: '1px solid #cbd5e1',
                        borderRadius: '8px',
                        color: '#0f172a',
                        fontSize: '1rem',
                        fontWeight: 700,
                        boxSizing: 'border-box',
                      }}
                    />
                    <span
                      style={{
                        position: 'absolute',
                        right: '10px',
                        top: '50%',
                        transform: 'translateY(-50%)',
                        fontSize: '0.75rem',
                        fontWeight: 600,
                        color: '#059669',
                        background: '#ecfdf5',
                        padding: '2px 6px',
                        borderRadius: '4px',
                      }}
                    >
                      {formatVnd(fees.withdrawalFeeVnd)}
                    </span>
                  </div>
                  <p style={{ minHeight: '34px', margin: '6px 0 0', fontSize: '0.78rem', color: '#64748b' }}>
                    Chi trả phí giao dịch liên ngân hàng NAPAS khi người bán rút ví Escrow.
                  </p>
                </div>

                {/* Field 4: Phí bảo hiểm kiện tụng cố định */}
                <div style={{ display: 'flex', flexDirection: 'column' }}>
                  <label
                    style={{
                      minHeight: '44px',
                      display: 'flex',
                      alignItems: 'flex-end',
                      color: '#334155',
                      fontSize: '0.88rem',
                      fontWeight: 600,
                      marginBottom: '8px',
                    }}
                  >
                    Phí bảo hiểm kiện tụng cố định (VNĐ / đơn hàng):
                  </label>
                  <div style={{ position: 'relative' }}>
                    <input
                      type="number"
                      step="500"
                      min="0"
                      value={fees.buyerProtectionFixedFee}
                      onChange={(e) => setFees({ ...fees, buyerProtectionFixedFee: parseInt(e.target.value, 10) || 0 })}
                      style={{
                        width: '100%',
                        padding: '10px 12px',
                        background: '#ffffff',
                        border: '1px solid #cbd5e1',
                        borderRadius: '8px',
                        color: '#0f172a',
                        fontSize: '1rem',
                        fontWeight: 700,
                        boxSizing: 'border-box',
                      }}
                    />
                    <span
                      style={{
                        position: 'absolute',
                        right: '10px',
                        top: '50%',
                        transform: 'translateY(-50%)',
                        fontSize: '0.75rem',
                        fontWeight: 600,
                        color: '#059669',
                        background: '#ecfdf5',
                        padding: '2px 6px',
                        borderRadius: '4px',
                      }}
                    >
                      {formatVnd(fees.buyerProtectionFixedFee)}
                    </span>
                  </div>
                  <p style={{ minHeight: '34px', margin: '6px 0 0', fontSize: '0.78rem', color: '#64748b' }}>
                    Quỹ dự phòng bồi thường khi phát sinh kiện tụng tranh chấp cần giám định KTV.
                  </p>
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', paddingTop: '10px', borderTop: '1px solid #e2e8f0' }}>
                <button
                  type="submit"
                  style={{
                    padding: '10px 24px',
                    background: '#059669',
                    color: '#ffffff',
                    border: 'none',
                    borderRadius: '8px',
                    fontSize: '0.88rem',
                    fontWeight: 700,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                    boxShadow: '0 1px 2px rgba(5, 150, 105, 0.2)',
                    transition: 'background-color 0.15s ease',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.background = '#047857')}
                  onMouseLeave={(e) => (e.currentTarget.style.background = '#059669')}
                >
                  <ConsoleIcons.Sliders size={16} />
                  <span>Lưu cấu hình biểu phí</span>
                </button>
              </div>
            </div>
          </form>

          {/* Simulated Fee Impact Table */}
          <div
            style={{
              background: '#ffffff',
              border: '1px solid #e2e8f0',
              borderRadius: '12px',
              padding: '24px',
              boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
            }}
          >
            <div style={{ marginBottom: '16px' }}>
              <h2 style={{ fontSize: '1.15rem', color: '#0f172a', margin: '0 0 4px', fontWeight: 700 }}>
                Mô phỏng doanh thu phí theo hạn mức giao dịch
              </h2>
              <p style={{ margin: 0, fontSize: '0.84rem', color: '#64748b' }}>
                Bảng tính thử nghiệm doanh thu phí sàn thu được dựa trên cấu hình đang thiết lập.
              </p>
            </div>

            {/* Bulk Action Bar */}
            <BulkActionBar
              selectedCount={selectedTiers.size}
              totalCount={feeSimulationTiers.length}
              onClearSelection={() => setSelectedTiers(new Set())}
              actions={[
                {
                  label: 'Xuất kịch bản tính phí',
                  icon: <ConsoleIcons.Download size={15} />,
                  variant: 'primary',
                  onClick: () => alert(`Đã xuất kịch bản tính phí cho ${selectedTiers.size} phân khúc.`),
                },
              ]}
            />

            <div style={{ overflowX: 'auto', border: '1px solid #e2e8f0', borderRadius: '8px' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.88rem' }}>
                <thead>
                  <tr style={{ borderBottom: '1px solid #e2e8f0', color: '#64748b', background: '#f8fafc' }}>
                    <th style={{ padding: '12px 14px', width: '40px', textAlign: 'center', whiteSpace: 'nowrap' }}>
                      <TableCheckbox
                        checked={isAllTiersSelected}
                        indeterminate={isSomeTiersSelected}
                        onChange={handleToggleAllTiers}
                        ariaLabel="Chọn tất cả kịch bản"
                      />
                    </th>
                    <th style={{ padding: '12px 14px', whiteSpace: 'nowrap' }}>Phân khúc giao dịch</th>
                    <th style={{ padding: '12px 14px', whiteSpace: 'nowrap' }}>Giá trị đơn mẫu (VNĐ)</th>
                    <th style={{ padding: '12px 14px', whiteSpace: 'nowrap' }}>Phí sàn thu (VNĐ)</th>
                    <th style={{ padding: '12px 14px', whiteSpace: 'nowrap' }}>Người bán thực nhận (VNĐ)</th>
                    <th style={{ padding: '12px 14px', textAlign: 'right', minWidth: '95px', whiteSpace: 'nowrap' }}>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {feeSimulationTiers.map((tier) => {
                    const calculatedFee = Math.max(
                      fees.minTransactionFee,
                      Math.round((tier.orderValue * fees.escrowFeePercent) / 100) + fees.buyerProtectionFixedFee
                    );
                    const sellerReceives = tier.orderValue - calculatedFee;
                    const isSelected = selectedTiers.has(tier.id);

                    return (
                      <tr
                        key={tier.id}
                        style={{
                          borderBottom: '1px solid #f1f5f9',
                          background: isSelected ? '#f0fdf4' : 'transparent',
                        }}
                      >
                        <td style={{ padding: '12px 14px', textAlign: 'center' }}>
                          <TableCheckbox
                            checked={isSelected}
                            onChange={() => handleToggleTier(tier.id)}
                            ariaLabel={`Chọn kịch bản ${tier.name}`}
                          />
                        </td>
                        <td style={{ padding: '12px 14px', whiteSpace: 'nowrap' }}>
                          <div style={{ fontWeight: 600, color: '#0f172a' }}>{tier.name}</div>
                          <div style={{ fontSize: '0.74rem', color: '#64748b' }}>{tier.desc}</div>
                        </td>
                        {/* Values formatted without VNĐ because VNĐ is already in the column header */}
                        <td style={{ padding: '12px 14px', color: '#334155', fontWeight: 600, whiteSpace: 'nowrap' }}>
                          {formatVnd(tier.orderValue, false)}
                        </td>
                        <td style={{ padding: '12px 14px', color: '#059669', fontWeight: 700, whiteSpace: 'nowrap' }}>
                          {formatVnd(calculatedFee, false)}
                        </td>
                        <td style={{ padding: '12px 14px', color: '#2563eb', fontWeight: 700, whiteSpace: 'nowrap' }}>
                          {formatVnd(sellerReceives, false)}
                        </td>
                        <td style={{ padding: '12px 14px', textAlign: 'right', whiteSpace: 'nowrap' }}>
                          <TableActionDropdown
                            ariaLabel={`Thao tác cho ${tier.name}`}
                            items={[
                              {
                                label: 'Xem chi tiết công thức',
                                icon: <ConsoleIcons.Sliders size={16} />,
                                onClick: () => alert(`Công thức tính: max(${formatVnd(fees.minTransactionFee)}, ${formatVnd(tier.orderValue)} * ${fees.escrowFeePercent}% + ${formatVnd(fees.buyerProtectionFixedFee)})`),
                              },
                              {
                                label: 'Kiểm tra trên công cụ tính nhanh',
                                icon: <ConsoleIcons.TrendingUp size={16} />,
                                onClick: () => setCalcOrderValue(tier.orderValue),
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
        </div>

        {/* Right Column: Quick Calculator & Policies (Utilizing screen real-estate) */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Quick Fee Calculator Widget */}
          <div
            style={{
              background: '#ffffff',
              border: '1px solid #e2e8f0',
              borderRadius: '12px',
              padding: '20px',
              boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '14px' }}>
              <div
                style={{
                  width: '32px',
                  height: '32px',
                  borderRadius: '6px',
                  background: '#ecfdf5',
                  color: '#059669',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <ConsoleIcons.CreditCard size={18} />
              </div>
              <div>
                <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: 700, color: '#0f172a' }}>
                  Công cụ tính nhanh phí đơn hàng
                </h3>
                <span style={{ fontSize: '0.74rem', color: '#64748b' }}>
                  Thử nghiệm số tiền bất kỳ theo biểu phí hiện tại
                </span>
              </div>
            </div>

            <div style={{ marginBottom: '14px' }}>
              <label style={{ display: 'block', fontSize: '0.82rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>
                Nhập giá trị đơn hàng mẫu (VNĐ):
              </label>
              <input
                type="number"
                step="50000"
                min="10000"
                value={calcOrderValue}
                onChange={(e) => setCalcOrderValue(Math.max(0, parseInt(e.target.value, 10) || 0))}
                style={{
                  width: '100%',
                  padding: '9px 12px',
                  background: '#f8fafc',
                  border: '1px solid #cbd5e1',
                  borderRadius: '8px',
                  fontSize: '1rem',
                  fontWeight: 700,
                  color: '#0f172a',
                  boxSizing: 'border-box',
                }}
              />
              <div style={{ display: 'flex', gap: '6px', marginTop: '8px', flexWrap: 'wrap' }}>
                {[500000, 2000000, 10000000, 30000000].map((val) => (
                  <button
                    key={val}
                    type="button"
                    onClick={() => setCalcOrderValue(val)}
                    style={{
                      padding: '3px 8px',
                      borderRadius: '4px',
                      background: calcOrderValue === val ? '#ecfdf5' : '#f1f5f9',
                      color: calcOrderValue === val ? '#059669' : '#475569',
                      border: `1px solid ${calcOrderValue === val ? '#a7f3d0' : '#e2e8f0'}`,
                      fontSize: '0.74rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                    }}
                  >
                    {formatVnd(val)}
                  </button>
                ))}
              </div>
            </div>

            {/* Breakdown summary */}
            <div
              style={{
                background: '#f8fafc',
                border: '1px solid #e2e8f0',
                borderRadius: '8px',
                padding: '14px',
                display: 'flex',
                flexDirection: 'column',
                gap: '8px',
                fontSize: '0.84rem',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', color: '#64748b' }}>
                <span>Giá trị đơn hàng:</span>
                <strong style={{ color: '#0f172a' }}>{formatVnd(calcOrderValue)}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', color: '#64748b' }}>
                <span>Phí Escrow ({fees.escrowFeePercent}%):</span>
                <span>{formatVnd(calcEscrowPart)}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', color: '#64748b' }}>
                <span>Phí bảo hiểm kiện tụng:</span>
                <span>{formatVnd(fees.buyerProtectionFixedFee)}</span>
              </div>
              {isMinFeeTriggered && (
                <div style={{ fontSize: '0.75rem', color: '#b45309', background: '#fef3c7', padding: '4px 8px', borderRadius: '4px' }}>
                  ⚠️ Áp dụng mức phí sàn tối thiểu {formatVnd(fees.minTransactionFee)} do tổng phí nhỏ hơn ngưỡng.
                </div>
              )}
              <div
                style={{
                  borderTop: '1px solid #e2e8f0',
                  paddingTop: '8px',
                  display: 'flex',
                  justifyContent: 'space-between',
                  fontWeight: 700,
                }}
              >
                <span style={{ color: '#0f172a' }}>Tổng phí sàn thu:</span>
                <span style={{ color: '#059669' }}>{formatVnd(calcTotalFee)}</span>
              </div>
              <div
                style={{
                  background: '#ecfdf5',
                  padding: '8px 10px',
                  borderRadius: '6px',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  marginTop: '4px',
                }}
              >
                <span style={{ fontWeight: 700, color: '#065f46', fontSize: '0.86rem' }}>Người bán nhận:</span>
                <span style={{ fontWeight: 800, color: '#047857', fontSize: '1rem' }}>{formatVnd(calcSellerPayout)}</span>
              </div>
            </div>
          </div>

          {/* Fee Policy & Operating Rules Card */}
          <div
            style={{
              background: '#ffffff',
              border: '1px solid #e2e8f0',
              borderRadius: '12px',
              padding: '20px',
              boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
            }}
          >
            <h3 style={{ margin: '0 0 10px 0', fontSize: '0.95rem', fontWeight: 700, color: '#0f172a', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <ConsoleIcons.ShieldCheck size={17} />
              <span>Chính sách áp dụng biểu phí</span>
            </h3>
            <ul style={{ margin: 0, paddingLeft: '18px', fontSize: '0.8rem', color: '#64748b', display: 'flex', flexDirection: 'column', gap: '6px', lineHeight: 1.45 }}>
              <li>
                <strong style={{ color: '#334155' }}>Cơ chế Escrow 48h:</strong> Tiền hàng được giữ an toàn tại tài khoản bảo chứng cho đến khi người mua kiểm tra hàng thành công.
              </li>
              <li>
                <strong style={{ color: '#334155' }}>Bảo hiểm kiện tụng:</strong> Phí cố định {formatVnd(fees.buyerProtectionFixedFee)} tài trợ chi phí kiểm định chuyên sâu của đội ngũ KTV khi có tranh chấp.
              </li>
              <li>
                <strong style={{ color: '#334155' }}>Rút ví Escrow:</strong> Phí rút tiền NAPAS {formatVnd(fees.withdrawalFeeVnd)} khấu trừ trực tiếp khi lệnh rút về ngân hàng thực hiện thành công.
              </li>
            </ul>

            <div style={{ marginTop: '14px', paddingTop: '12px', borderTop: '1px solid #f1f5f9', fontSize: '0.76rem', color: '#94a3b8' }}>
              Lần cập nhật gần nhất: <strong style={{ color: '#475569' }}>{systemFees.updatedAt}</strong> bởi <strong style={{ color: '#475569' }}>{systemFees.updatedBy}</strong>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
