import React, { useState } from 'react';
import { useManagementStore } from '../../features/management/useManagementStore';
import { ConsoleIcons } from '../../shared/layout/ConsoleIcons';
import { KycSubmissionRecord } from '../../features/management/managementStore';
import {
  TableActionDropdown,
  BulkActionBar,
  TableCheckbox,
} from '../../shared/layout/ConsoleTableActions';

export const KtvKycPage: React.FC = () => {
  const { kycList, approveKyc, rejectKyc } = useManagementStore();
  const [selectedKyc, setSelectedKyc] = useState<KycSubmissionRecord | null>(null);
  const [isRejecting, setIsRejecting] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Multi-select state
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  const isAllSelected =
    kycList.length > 0 && kycList.every((k) => selectedIds.has(k.id));
  const isSomeSelected =
    kycList.some((k) => selectedIds.has(k.id)) && !isAllSelected;

  const handleToggleAll = () => {
    if (isAllSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(kycList.map((k) => k.id)));
    }
  };

  const handleToggleItem = (id: string) => {
    const next = new Set(selectedIds);
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    setSelectedIds(next);
  };

  const handleBulkApprove = () => {
    selectedIds.forEach((id) => {
      const item = kycList.find((k) => k.id === id);
      if (item && item.status === 'PENDING') {
        approveKyc(id);
      }
    });
    setSuccessMessage(`Đã phê duyệt định danh cho ${selectedIds.size} hồ sơ.`);
    setSelectedIds(new Set());
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  const handleApprove = (id: string, name: string) => {
    approveKyc(id);
    setSuccessMessage(`Đã phê duyệt eKYC cho người bán ${name} thành công.`);
    setSelectedKyc(null);
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  const handleConfirmReject = () => {
    if (!selectedKyc || !rejectReason.trim()) return;

    rejectKyc(selectedKyc.id, rejectReason.trim());
    setSuccessMessage(`Đã từ chối eKYC của ${selectedKyc.fullName}.`);
    setSelectedKyc(null);
    setIsRejecting(false);
    setRejectReason('');
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Page Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', margin: '0 0 6px', color: '#0f172a', fontWeight: 800 }}>
            Kiểm duyệt định danh người bán (eKYC)
          </h1>
          <p style={{ margin: 0, color: '#64748b', fontSize: '0.92rem' }}>
            Xác thực thông tin CCCD gắn chip và ảnh chân dung đối chiếu trước khi cấp quyền mở gian hàng bán đồ cũ.
          </p>
        </div>

        {kycList.length > 0 && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <TableCheckbox
              checked={isAllSelected}
              indeterminate={isSomeSelected}
              onChange={handleToggleAll}
              ariaLabel="Chọn tất cả hồ sơ"
            />
            <span style={{ fontSize: '0.84rem', color: '#64748b' }}>Chọn tất cả</span>
          </div>
        )}
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
        totalCount={kycList.length}
        onClearSelection={() => setSelectedIds(new Set())}
        actions={[
          {
            label: 'Duyệt eKYC đã chọn',
            icon: <ConsoleIcons.CheckCircle size={15} />,
            variant: 'primary',
            onClick: handleBulkApprove,
          },
        ]}
      />

      {/* KYC Queue List (Light Theme) */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '16px' }}>
        {kycList.map((item) => {
          const isSelected = selectedIds.has(item.id);
          return (
            <div
              key={item.id}
              style={{
                background: isSelected ? '#f0fdf4' : '#ffffff',
                border: isSelected ? '1px solid #86efac' : '1px solid #e2e8f0',
                borderRadius: '12px',
                padding: '20px',
                display: 'flex',
                flexDirection: 'column',
                gap: '14px',
                boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
                transition: 'background-color 0.15s ease',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div style={{ display: 'flex', alignItems: 'flex-start', gap: '10px' }}>
                  <div style={{ paddingTop: '2px' }}>
                    <TableCheckbox
                      checked={isSelected}
                      onChange={() => handleToggleItem(item.id)}
                      ariaLabel={`Chọn hồ sơ ${item.fullName}`}
                    />
                  </div>
                  <div>
                    <h3 style={{ margin: '0 0 4px', fontSize: '1.1rem', color: '#0f172a', fontWeight: 700 }}>
                      {item.fullName}
                    </h3>
                    <div style={{ fontSize: '0.8rem', color: '#64748b' }}>
                      Nộp lúc: {item.submittedAt}
                    </div>
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  {item.status === 'PENDING' && (
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
                      CHỜ DUYỆT
                    </span>
                  )}
                  {item.status === 'APPROVED' && (
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
                      ĐÃ PHÊ DUYỆT
                    </span>
                  )}
                  {item.status === 'REJECTED' && (
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
                      BỊ TỪ CHỐI
                    </span>
                  )}

                  <TableActionDropdown
                    ariaLabel={`Tùy chọn cho ${item.fullName}`}
                    items={[
                      {
                        label: 'Xem ảnh CCCD đối chiếu',
                        icon: <ConsoleIcons.IdCard size={16} />,
                        onClick: () => setSelectedKyc(item),
                      },
                      ...(item.status === 'PENDING'
                        ? [
                            {
                              label: 'Phê duyệt định danh',
                              icon: <ConsoleIcons.CheckCircle size={16} />,
                              onClick: () => handleApprove(item.id, item.fullName),
                            },
                            {
                              label: 'Từ chối định danh',
                              icon: <ConsoleIcons.XCircle size={16} />,
                              variant: 'danger' as const,
                              onClick: () => {
                                setSelectedKyc(item);
                                setIsRejecting(true);
                              },
                            },
                          ]
                        : []),
                    ]}
                  />
                </div>
              </div>

              <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', padding: '12px', borderRadius: '8px', fontSize: '0.86rem', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                <div>
                  <span style={{ color: '#64748b' }}>Số CCCD: </span>
                  <strong style={{ color: '#0f172a', fontFamily: 'monospace' }}>{item.cccdNumber}</strong>
                </div>
                <div>
                  <span style={{ color: '#64748b' }}>Ngày sinh: </span>
                  <span style={{ color: '#0f172a' }}>{item.dateOfBirth}</span>
                </div>
                <div>
                  <span style={{ color: '#64748b' }}>Địa chỉ: </span>
                  <span style={{ color: '#0f172a' }}>{item.address}</span>
                </div>
              </div>

              {item.status === 'PENDING' && (
                <div style={{ display: 'flex', gap: '10px', marginTop: 'auto' }}>
                  <button
                    type="button"
                    onClick={() => setSelectedKyc(item)}
                    style={{
                      flex: 1,
                      padding: '8px',
                      background: '#f1f5f9',
                      border: '1px solid #cbd5e1',
                      borderRadius: '6px',
                      color: '#475569',
                      fontSize: '0.84rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                    }}
                  >
                    Xem tài liệu
                  </button>
                  <button
                    type="button"
                    onClick={() => handleApprove(item.id, item.fullName)}
                    style={{
                      flex: 1,
                      padding: '8px',
                      background: '#059669',
                      border: 'none',
                      borderRadius: '6px',
                      color: '#ffffff',
                      fontSize: '0.84rem',
                      fontWeight: 700,
                      cursor: 'pointer',
                    }}
                  >
                    Duyệt ngay
                  </button>
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Modal View KYC Documents (Light Theme) */}
      {selectedKyc && (
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
              maxWidth: '640px',
              maxHeight: '90vh',
              overflowY: 'auto',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h2 style={{ margin: 0, fontSize: '1.25rem', color: '#0f172a', fontWeight: 700 }}>
                Hồ sơ định danh eKYC: {selectedKyc.fullName}
              </h2>
              <button
                type="button"
                onClick={() => {
                  setSelectedKyc(null);
                  setIsRejecting(false);
                }}
                style={{ background: 'transparent', border: 'none', color: '#94a3b8', fontSize: '1.2rem', cursor: 'pointer' }}
              >
                ✕
              </button>
            </div>

            {/* Document Previews */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '12px', marginBottom: '20px' }}>
              <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '8px', padding: '10px', textAlign: 'center' }}>
                <div style={{ fontSize: '0.8rem', color: '#64748b', marginBottom: '6px', fontWeight: 600 }}>CCCD Mặt trước</div>
                <div style={{ height: '110px', background: '#e2e8f0', borderRadius: '6px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <ConsoleIcons.IdCard size={32} />
                </div>
              </div>
              <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '8px', padding: '10px', textAlign: 'center' }}>
                <div style={{ fontSize: '0.8rem', color: '#64748b', marginBottom: '6px', fontWeight: 600 }}>CCCD Mặt sau (Chip)</div>
                <div style={{ height: '110px', background: '#e2e8f0', borderRadius: '6px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <ConsoleIcons.IdCard size={32} />
                </div>
              </div>
              <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '8px', padding: '10px', textAlign: 'center' }}>
                <div style={{ fontSize: '0.8rem', color: '#64748b', marginBottom: '6px', fontWeight: 600 }}>Chân dung Selfie đối chiếu</div>
                <div style={{ height: '110px', background: '#e2e8f0', borderRadius: '6px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <ConsoleIcons.Users size={32} />
                </div>
              </div>
            </div>

            {isRejecting ? (
              <div style={{ marginBottom: '16px' }}>
                <label style={{ display: 'block', fontSize: '0.84rem', color: '#dc2626', marginBottom: '6px', fontWeight: 600 }}>
                  Lý do từ chối hồ sơ *
                </label>
                <textarea
                  rows={3}
                  placeholder="Ví dụ: Ảnh CCCD bị chói lóa góc số, chân dung nghi vấn không khớp..."
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '9px 12px',
                    background: '#ffffff',
                    border: '1px solid #fca5a5',
                    borderRadius: '8px',
                    color: '#0f172a',
                    fontSize: '0.88rem',
                    boxSizing: 'border-box',
                  }}
                />
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '10px' }}>
                  <button
                    type="button"
                    onClick={() => setIsRejecting(false)}
                    style={{ padding: '8px 14px', background: '#f1f5f9', border: '1px solid #cbd5e1', borderRadius: '6px', color: '#475569', cursor: 'pointer' }}
                  >
                    Quay lại
                  </button>
                  <button
                    type="button"
                    onClick={handleConfirmReject}
                    disabled={!rejectReason.trim()}
                    style={{ padding: '8px 14px', background: '#dc2626', border: 'none', borderRadius: '6px', color: '#ffffff', fontWeight: 600, cursor: !rejectReason.trim() ? 'not-allowed' : 'pointer' }}
                  >
                    Xác nhận từ chối
                  </button>
                </div>
              </div>
            ) : (
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
                <button
                  type="button"
                  onClick={() => setIsRejecting(true)}
                  style={{
                    padding: '9px 16px',
                    background: '#fef2f2',
                    border: '1px solid #fecaca',
                    color: '#dc2626',
                    borderRadius: '8px',
                    fontSize: '0.88rem',
                    fontWeight: 600,
                    cursor: 'pointer',
                  }}
                >
                  Từ chối hồ sơ
                </button>
                <button
                  type="button"
                  onClick={() => handleApprove(selectedKyc.id, selectedKyc.fullName)}
                  style={{
                    padding: '9px 18px',
                    background: '#059669',
                    border: 'none',
                    color: '#ffffff',
                    borderRadius: '8px',
                    fontSize: '0.88rem',
                    fontWeight: 700,
                    cursor: 'pointer',
                    boxShadow: '0 1px 2px rgba(5, 150, 105, 0.2)',
                  }}
                >
                  Phê duyệt eKYC
                </button>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
