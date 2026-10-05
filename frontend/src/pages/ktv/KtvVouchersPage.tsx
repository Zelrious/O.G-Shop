import React, { useState } from 'react';
import { useManagementStore } from '../../features/management/useManagementStore';
import { ConsoleIcons } from '../../shared/layout/ConsoleIcons';
import {
  TableActionDropdown,
  BulkActionBar,
  TableCheckbox,
} from '../../shared/layout/ConsoleTableActions';
import { formatVnd } from '../../shared/utils/currency';

export const KtvVouchersPage: React.FC = () => {
  const { users, vouchers, grantedVouchers, grantVoucher, revokeVoucher } = useManagementStore();
  const [search, setSearch] = useState('');
  const [isGrantModalOpen, setIsGrantModalOpen] = useState(false);
  const [selectedUserId, setSelectedUserId] = useState<number>(users[0]?.id || 101);
  const [selectedVoucherId, setSelectedVoucherId] = useState<string>(vouchers[0]?.id || 'VOU-01');
  const [grantReason, setGrantReason] = useState('');
  const [revokingGrantId, setRevokingGrantId] = useState<string | null>(null);
  const [revokeReason, setRevokeReason] = useState('');
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  // Multi-select state
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  const activeVouchers = vouchers.filter((v) => v.status === 'ACTIVE');

  const filteredGrants = grantedVouchers.filter((g) => {
    return (
      g.userEmail.toLowerCase().includes(search.toLowerCase()) ||
      g.voucherCode.toLowerCase().includes(search.toLowerCase()) ||
      g.voucherName.toLowerCase().includes(search.toLowerCase())
    );
  });

  const isAllSelected =
    filteredGrants.length > 0 && filteredGrants.every((g) => selectedIds.has(g.id));
  const isSomeSelected =
    filteredGrants.some((g) => selectedIds.has(g.id)) && !isAllSelected;

  const handleToggleAll = () => {
    if (isAllSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(filteredGrants.map((g) => g.id)));
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

  const handleBulkRevoke = () => {
    setActionError(null);
    let revoked = 0;
    selectedIds.forEach((id) => {
      const g = grantedVouchers.find((item) => item.id === id);
      if (g && g.status === 'ACTIVE') {
        try {
          revokeVoucher(id, 'KTV thu hồi hàng loạt.');
          revoked++;
        } catch (error) {
          setActionError(error instanceof Error ? error.message : 'Không thể thu hồi voucher.');
        }
      }
    });
    setSuccessMessage(`Đã thu hồi ${revoked}/${selectedIds.size} voucher được chọn.`);
    setSelectedIds(new Set());
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  const handleConfirmGrant = () => {
    if (!grantReason.trim()) return;

    setActionError(null);
    try {
      grantVoucher(selectedUserId, selectedVoucherId, grantReason.trim());
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Không thể tặng voucher.');
      return;
    }
    setSuccessMessage('Đã tặng Voucher vào ví tài khoản người dùng thành công.');
    setIsGrantModalOpen(false);
    setGrantReason('');
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  const handleConfirmRevoke = () => {
    if (!revokingGrantId || !revokeReason.trim()) return;

    setActionError(null);
    try {
      revokeVoucher(revokingGrantId, revokeReason.trim());
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Không thể thu hồi voucher.');
      return;
    }
    setSuccessMessage('Đã thu hồi Voucher của người dùng.');
    setRevokingGrantId(null);
    setRevokeReason('');
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Page Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', margin: '0 0 6px', color: '#0f172a', fontWeight: 800 }}>
            Tặng & Thu hồi Voucher cho người dùng
          </h1>
          <p style={{ margin: 0, color: '#64748b', fontSize: '0.92rem' }}>
            Cấp phát voucher giảm giá để đền bù tranh chấp, hỗ trợ khách hàng hoặc thu hồi mã trong trường hợp cấp nhầm/vi phạm.
          </p>
        </div>

        <button
          type="button"
          onClick={() => setIsGrantModalOpen(true)}
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
          <span>Tặng Voucher cho User</span>
        </button>
      </div>

      {actionError && <div role="alert">{actionError}</div>}
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

      {/* Available Vouchers Catalog Preview (Light Theme) */}
      <div>
        <h2 style={{ fontSize: '1.15rem', color: '#0f172a', margin: '0 0 12px 0', fontWeight: 700 }}>
          Kho mã Voucher khả dụng của sàn
        </h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '14px' }}>
          {activeVouchers.map((v) => (
            <div
              key={v.id}
              style={{
                background: '#ffffff',
                border: '1px solid #e2e8f0',
                borderRadius: '10px',
                padding: '16px',
                boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                <span
                  style={{
                    fontFamily: 'monospace',
                    fontWeight: 800,
                    color: '#1d4ed8',
                    background: '#eff6ff',
                    padding: '3px 8px',
                    borderRadius: '6px',
                    fontSize: '0.84rem',
                    border: '1px solid #bfdbfe',
                  }}
                >
                  {v.code}
                </span>
                <span style={{ fontSize: '0.78rem', color: '#64748b' }}>
                  Còn lại: <strong style={{ color: '#0f172a' }}>{v.totalQuantity - v.usedQuantity}</strong>
                </span>
              </div>
              <div style={{ fontWeight: 600, color: '#0f172a', fontSize: '0.92rem', marginBottom: '4px' }}>
                {v.name}
              </div>
              <div style={{ color: '#059669', fontWeight: 700, fontSize: '0.9rem' }}>
                {v.discountType === 'PERCENT' ? `Giảm ${v.discountValue}%` : `-${formatVnd(v.discountValue)}`}
              </div>
              <div style={{ fontSize: '0.76rem', color: '#64748b', marginTop: '4px' }}>
                Đơn tối thiểu: {formatVnd(v.minOrderValue)}
              </div>
            </div>
          ))}
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
          background: '#ffffff',
          padding: '16px',
          borderRadius: '12px',
          border: '1px solid #e2e8f0',
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
              color: '#94a3b8',
              display: 'flex',
            }}
          >
            <ConsoleIcons.Search size={16} />
          </span>
          <input
            type="text"
            placeholder="Tìm theo email user, mã voucher..."
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
          Đã phát: <strong style={{ color: '#0f172a' }}>{grantedVouchers.length}</strong> lượt tặng
        </div>
      </div>

      {/* Bulk Action Bar */}
      <BulkActionBar
        selectedCount={selectedIds.size}
        totalCount={filteredGrants.length}
        onClearSelection={() => setSelectedIds(new Set())}
        actions={[
          {
            label: 'Thu hồi các voucher đã chọn',
            icon: <ConsoleIcons.Lock size={15} />,
            variant: 'danger',
            onClick: handleBulkRevoke,
          },
        ]}
      />

      {/* Granted Vouchers Table */}
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
                    ariaLabel="Chọn tất cả lượt tặng"
                  />
                </th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Mã & Tên Voucher</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Người nhận (User)</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Lý do tặng</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>KTV thực hiện</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Trạng thái</th>
                <th style={{ padding: '14px 16px', textAlign: 'right', minWidth: '95px', whiteSpace: 'nowrap' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {filteredGrants.length === 0 ? (
                <tr>
                  <td colSpan={7} style={{ padding: '40px', textAlign: 'center', color: '#64748b' }}>
                    Chưa có lượt tặng voucher nào phù hợp.
                  </td>
                </tr>
              ) : (
                filteredGrants.map((item) => {
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
                          ariaLabel={`Chọn lượt tặng #${item.id}`}
                        />
                      </td>
                      <td style={{ padding: '14px 16px' }}>
                        <div style={{ fontFamily: 'monospace', fontWeight: 700, color: '#1d4ed8' }}>
                          {item.voucherCode}
                        </div>
                        <div style={{ fontSize: '0.78rem', color: '#64748b' }}>{item.voucherName}</div>
                      </td>
                      <td style={{ padding: '14px 16px', color: '#0f172a', fontWeight: 600 }}>
                        {item.userEmail}
                      </td>
                      <td style={{ padding: '14px 16px', color: '#334155', maxWidth: '280px' }}>
                        {item.reason}
                      </td>
                      <td style={{ padding: '14px 16px', color: '#64748b', fontSize: '0.82rem' }}>
                        <div>{item.grantedBy}</div>
                        <div style={{ fontSize: '0.74rem' }}>{item.grantedAt}</div>
                      </td>
                      <td style={{ padding: '14px 16px' }}>
                        {item.status === 'ACTIVE' && (
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
                            KHẢ DỤNG
                          </span>
                        )}
                        {item.status === 'USED' && (
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
                            ĐÃ SỬ DỤNG
                          </span>
                        )}
                        {item.status === 'REVOKED' && (
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
                            ĐÃ THU HỒI
                          </span>
                        )}
                      </td>
                      <td style={{ padding: '14px 16px', textAlign: 'right' }}>
                        <TableActionDropdown
                          ariaLabel={`Thao tác cho lượt tặng #${item.id}`}
                          items={[
                            ...(item.status === 'ACTIVE'
                              ? [
                                  {
                                    label: 'Thu hồi voucher này',
                                    icon: <ConsoleIcons.Lock size={16} />,
                                    variant: 'danger' as const,
                                    onClick: () => {
                                      setRevokingGrantId(item.id);
                                      setRevokeReason('');
                                    },
                                  },
                                ]
                              : []),
                            {
                              label: 'Sao chép mã voucher',
                              icon: <ConsoleIcons.Ticket size={16} />,
                              onClick: () => {
                                navigator.clipboard?.writeText(item.voucherCode);
                                alert(`Đã sao chép mã ${item.voucherCode}`);
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

      {/* Modal Grant Voucher */}
      {isGrantModalOpen && (
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
              maxWidth: '520px',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
            }}
          >
            <h2 style={{ margin: '0 0 16px 0', fontSize: '1.25rem', color: '#0f172a', fontWeight: 700 }}>
              Tặng Voucher ưu đãi cho người dùng
            </h2>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                  Người dùng nhận voucher *
                </label>
                <select
                  value={selectedUserId}
                  onChange={(e) => setSelectedUserId(Number(e.target.value))}
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
                  {users.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.fullName} ({u.email})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                  Mã Voucher muốn tặng *
                </label>
                <select
                  value={selectedVoucherId}
                  onChange={(e) => setSelectedVoucherId(e.target.value)}
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
                  {activeVouchers.map((v) => (
                    <option key={v.id} value={v.id}>
                      {v.code} - {v.name} (
                      {v.discountType === 'PERCENT' ? `-${v.discountValue}%` : `-${formatVnd(v.discountValue)}`}
                      )
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                  Lý do cấp tặng *
                </label>
                <textarea
                  rows={3}
                  required
                  placeholder="Ví dụ: Bồi thường chậm trễ giao hàng, tri ân seller tích cực..."
                  value={grantReason}
                  onChange={(e) => setGrantReason(e.target.value)}
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

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '6px' }}>
                <button
                  type="button"
                  onClick={() => setIsGrantModalOpen(false)}
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
                  onClick={handleConfirmGrant}
                  disabled={!grantReason.trim()}
                  style={{
                    padding: '9px 18px',
                    background: '#059669',
                    border: 'none',
                    color: '#ffffff',
                    borderRadius: '8px',
                    fontSize: '0.88rem',
                    fontWeight: 600,
                    cursor: !grantReason.trim() ? 'not-allowed' : 'pointer',
                    opacity: !grantReason.trim() ? 0.6 : 1,
                    boxShadow: '0 1px 2px rgba(5, 150, 105, 0.2)',
                  }}
                >
                  Xác nhận tặng
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Modal Revoke Voucher */}
      {revokingGrantId && (
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
              maxWidth: '500px',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
            }}
          >
            <h2 style={{ margin: '0 0 8px 0', fontSize: '1.25rem', color: '#0f172a', fontWeight: 700 }}>
              Xác nhận thu hồi Voucher
            </h2>
            <p style={{ margin: '0 0 16px', fontSize: '0.86rem', color: '#64748b' }}>
              Mã voucher sẽ bị vô hiệu hóa khỏi ví của người dùng và ghi nhận vào lịch sử thao tác hệ thống.
            </p>

            <div style={{ marginBottom: '16px' }}>
              <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                Lý do thu hồi *
              </label>
              <textarea
                rows={3}
                required
                placeholder="Ví dụ: Cấp nhầm người dùng, tài khoản vi phạm chính sách..."
                value={revokeReason}
                onChange={(e) => setRevokeReason(e.target.value)}
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
                onClick={() => setRevokingGrantId(null)}
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
                onClick={handleConfirmRevoke}
                disabled={!revokeReason.trim()}
                style={{
                  padding: '9px 18px',
                  background: '#dc2626',
                  border: 'none',
                  color: '#ffffff',
                  borderRadius: '8px',
                  fontSize: '0.88rem',
                  fontWeight: 600,
                  cursor: !revokeReason.trim() ? 'not-allowed' : 'pointer',
                  opacity: !revokeReason.trim() ? 0.6 : 1,
                  boxShadow: '0 1px 2px rgba(220, 38, 38, 0.2)',
                }}
              >
                Thu hồi ngay
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
