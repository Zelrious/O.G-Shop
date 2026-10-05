import React, { useState } from 'react';
import { useManagementStore } from '../../features/management/useManagementStore';
import { ConsoleIcons } from '../../shared/layout/ConsoleIcons';
import { NormalUserRecord } from '../../features/management/managementStore';
import {
  TableActionDropdown,
  BulkActionBar,
  TableCheckbox,
} from '../../shared/layout/ConsoleTableActions';

export const KtvUsersPage: React.FC = () => {
  const { users, lockUser, unlockUser } = useManagementStore();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [lockingUser, setLockingUser] = useState<NormalUserRecord | null>(null);
  const [lockReason, setLockReason] = useState('');
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  // Multi-select state
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());

  const filteredUsers = users.filter((u) => {
    const matchSearch =
      u.fullName.toLowerCase().includes(search.toLowerCase()) ||
      u.email.toLowerCase().includes(search.toLowerCase()) ||
      u.phoneNumber.includes(search);
    const matchStatus = statusFilter === 'ALL' || u.status === statusFilter;
    return matchSearch && matchStatus;
  });

  const isAllSelected =
    filteredUsers.length > 0 && filteredUsers.every((u) => selectedIds.has(u.id));
  const isSomeSelected =
    filteredUsers.some((u) => selectedIds.has(u.id)) && !isAllSelected;

  const handleToggleAll = () => {
    if (isAllSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(filteredUsers.map((u) => u.id)));
    }
  };

  const handleToggleRow = (id: number) => {
    const next = new Set(selectedIds);
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    setSelectedIds(next);
  };

  const handleBulkLock = () => {
    selectedIds.forEach((id) => {
      const u = users.find((user) => user.id === id);
      if (u && u.status === 'ACTIVE') {
        lockUser(id, 'Khóa hàng loạt do nghi vấn vi phạm.');
      }
    });
    setActionSuccess(`Đã khóa ${selectedIds.size} tài khoản được chọn.`);
    setSelectedIds(new Set());
    setTimeout(() => setActionSuccess(null), 4000);
  };

  const handleBulkUnlock = () => {
    selectedIds.forEach((id) => {
      const u = users.find((user) => user.id === id);
      if (u && u.status === 'LOCKED') {
        unlockUser(id);
      }
    });
    setActionSuccess(`Đã kích hoạt lại ${selectedIds.size} tài khoản.`);
    setSelectedIds(new Set());
    setTimeout(() => setActionSuccess(null), 4000);
  };

  const handleConfirmLock = () => {
    if (!lockingUser || !lockReason.trim()) return;
    lockUser(lockingUser.id, lockReason.trim());
    setActionSuccess(`Đã tạm khóa tài khoản của ${lockingUser.fullName} thành công.`);
    setLockingUser(null);
    setLockReason('');
    setTimeout(() => setActionSuccess(null), 4000);
  };

  const handleUnlock = (user: NormalUserRecord) => {
    unlockUser(user.id);
    setActionSuccess(`Đã kích hoạt lại tài khoản của ${user.fullName}.`);
    setTimeout(() => setActionSuccess(null), 4000);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Page Header */}
      <div>
        <h1 style={{ fontSize: '1.75rem', margin: '0 0 6px', color: '#0f172a', fontWeight: 800 }}>
          Quản lý tài khoản người dùng
        </h1>
        <p style={{ margin: 0, color: '#64748b', fontSize: '0.92rem' }}>
          Tra cứu, kích hoạt hoặc tạm khóa tài khoản người mua và người bán khi phát hiện vi phạm quy tắc sàn.
        </p>
      </div>

      {actionSuccess && (
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
          <span>{actionSuccess}</span>
        </div>
      )}

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
            placeholder="Tìm theo họ tên, email, số điện thoại..."
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

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span style={{ fontSize: '0.84rem', color: '#64748b', fontWeight: 600 }}>Bộ lọc:</span>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            style={{
              padding: '8px 12px',
              background: '#ffffff',
              border: '1px solid #cbd5e1',
              borderRadius: '8px',
              color: '#0f172a',
              fontSize: '0.88rem',
            }}
          >
            <option value="ALL">Tất cả tài khoản</option>
            <option value="ACTIVE">Đang hoạt động</option>
            <option value="LOCKED">Đang bị khóa</option>
          </select>
        </div>
      </div>

      {/* Bulk Action Bar */}
      <BulkActionBar
        selectedCount={selectedIds.size}
        totalCount={filteredUsers.length}
        onClearSelection={() => setSelectedIds(new Set())}
        actions={[
          {
            label: 'Khóa tài khoản đã chọn',
            icon: <ConsoleIcons.Lock size={15} />,
            variant: 'danger',
            onClick: handleBulkLock,
          },
          {
            label: 'Mở khóa đã chọn',
            icon: <ConsoleIcons.Unlock size={15} />,
            variant: 'primary',
            onClick: handleBulkUnlock,
          },
        ]}
      />

      {/* Users Table */}
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
                    ariaLabel="Chọn tất cả người dùng"
                  />
                </th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Thành viên</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Số điện thoại</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Vai trò</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Đơn hoàn tất / Đánh giá</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Trạng thái</th>
                <th style={{ padding: '14px 16px', textAlign: 'right', minWidth: '95px', whiteSpace: 'nowrap' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {filteredUsers.length === 0 ? (
                <tr>
                  <td colSpan={7} style={{ padding: '40px', textAlign: 'center', color: '#64748b' }}>
                    Không tìm thấy tài khoản người dùng nào phù hợp.
                  </td>
                </tr>
              ) : (
                filteredUsers.map((item) => {
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
                          ariaLabel={`Chọn thành viên ${item.fullName}`}
                        />
                      </td>
                      <td style={{ padding: '14px 16px' }}>
                        <div style={{ fontWeight: 600, color: '#0f172a' }}>{item.fullName}</div>
                        <div style={{ fontSize: '0.78rem', color: '#64748b' }}>{item.email}</div>
                      </td>
                      <td style={{ padding: '14px 16px', color: '#334155' }}>{item.phoneNumber}</td>
                      <td style={{ padding: '14px 16px' }}>
                        <div style={{ display: 'flex', gap: '6px', alignItems: 'center', flexWrap: 'wrap' }}>
                          {item.roles.map((r) => (
                            <span
                              key={r}
                              style={{
                                padding: '2px 6px',
                                borderRadius: '4px',
                                fontSize: '0.72rem',
                                fontWeight: 700,
                                background: r === 'SELLER' ? '#eff6ff' : '#f8fafc',
                                color: r === 'SELLER' ? '#2563eb' : '#475569',
                                border: `1px solid ${r === 'SELLER' ? '#bfdbfe' : '#e2e8f0'}`,
                              }}
                            >
                              {r === 'SELLER' ? 'NGƯỜI BÁN' : 'NGƯỜI MUA'}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td style={{ padding: '14px 16px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <span style={{ fontWeight: 600, color: '#0f172a' }}>
                            {item.completedOrdersCount} đơn
                          </span>
                          <span style={{ color: '#94a3b8' }}>·</span>
                          <span style={{ fontWeight: 700, color: item.rating >= 4.5 ? '#059669' : '#d97706' }}>
                            ★ {item.rating.toFixed(1)}
                          </span>
                        </div>
                      </td>
                      <td style={{ padding: '14px 16px' }}>
                        {item.status === 'ACTIVE' ? (
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
                            HOẠT ĐỘNG
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
                            ĐÃ KHÓA
                          </span>
                        )}
                      </td>
                      <td style={{ padding: '14px 16px', textAlign: 'right' }}>
                        <TableActionDropdown
                          ariaLabel={`Thao tác cho ${item.fullName}`}
                          items={[
                            ...(item.status === 'ACTIVE'
                              ? [
                                  {
                                    label: 'Tạm khóa tài khoản',
                                    icon: <ConsoleIcons.Lock size={16} />,
                                    variant: 'danger' as const,
                                    onClick: () => {
                                      setLockingUser(item);
                                      setLockReason('');
                                    },
                                  },
                                ]
                              : [
                                  {
                                    label: 'Kích hoạt mở khóa',
                                    icon: <ConsoleIcons.Unlock size={16} />,
                                    onClick: () => handleUnlock(item),
                                  },
                                ]),
                            {
                              label: 'Tặng voucher bồi thường',
                              icon: <ConsoleIcons.Gift size={16} />,
                              onClick: () => alert(`Điều hướng tặng voucher cho ${item.fullName}`),
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

      {/* Modal Lock User */}
      {lockingUser && (
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
            <h2 style={{ margin: '0 0 8px', fontSize: '1.25rem', color: '#0f172a', fontWeight: 700 }}>
              Xác nhận tạm khóa tài khoản: {lockingUser.fullName}
            </h2>
            <p style={{ margin: '0 0 16px', fontSize: '0.86rem', color: '#64748b' }}>
              Người dùng này sẽ không thể đăng tin mới, tạo đơn mua hàng hoặc rút tiền ký quỹ cho đến khi được mở lại.
            </p>

            <div style={{ marginBottom: '16px' }}>
              <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                Lý do tạm khóa tài khoản *
              </label>
              <textarea
                rows={3}
                required
                placeholder="Ví dụ: Đăng bán hàng giả nhái nhiều lần, không giải trình đúng hạn..."
                value={lockReason}
                onChange={(e) => setLockReason(e.target.value)}
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
                onClick={() => setLockingUser(null)}
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
                onClick={handleConfirmLock}
                disabled={!lockReason.trim()}
                style={{
                  padding: '9px 18px',
                  background: '#dc2626',
                  border: 'none',
                  color: '#ffffff',
                  borderRadius: '8px',
                  fontSize: '0.88rem',
                  fontWeight: 600,
                  cursor: !lockReason.trim() ? 'not-allowed' : 'pointer',
                  opacity: !lockReason.trim() ? 0.6 : 1,
                }}
              >
                Tạm khóa ngay
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
