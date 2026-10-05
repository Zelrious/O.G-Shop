import React, { useState } from 'react';
import { useManagementStore } from '../../features/management/useManagementStore';
import { ConsoleIcons } from '../../shared/layout/ConsoleIcons';
import { KtvAccountRecord } from '../../features/management/managementStore';
import {
  TableActionDropdown,
  BulkActionBar,
  TableCheckbox,
} from '../../shared/layout/ConsoleTableActions';

export const AdminKtvAccountsPage: React.FC = () => {
  const { ktvAccounts, createKtvAccount, updateKtvAccount, toggleKtvStatus } = useManagementStore();
  const [search, setSearch] = useState('');
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [editingKtv, setEditingKtv] = useState<KtvAccountRecord | null>(null);

  // Multi-select state
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());

  // Form states
  const [formData, setFormData] = useState({
    fullName: '',
    email: '',
    phoneNumber: '',
    assignedZone: 'Miền Bắc & Kiểm duyệt điện tử',
  });
  const [formError, setFormError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const filteredKtvList = ktvAccounts.filter((k) => {
    return (
      k.fullName.toLowerCase().includes(search.toLowerCase()) ||
      k.email.toLowerCase().includes(search.toLowerCase()) ||
      k.assignedZone.toLowerCase().includes(search.toLowerCase())
    );
  });

  // Select all logic
  const isAllSelected =
    filteredKtvList.length > 0 &&
    filteredKtvList.every((k) => selectedIds.has(k.id));
  const isSomeSelected =
    filteredKtvList.some((k) => selectedIds.has(k.id)) && !isAllSelected;

  const handleToggleAll = () => {
    if (isAllSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(filteredKtvList.map((k) => k.id)));
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

  const handleBulkDeactivate = () => {
    selectedIds.forEach((id) => {
      const ktv = ktvAccounts.find((k) => k.id === id);
      if (ktv && ktv.status === 'ACTIVE') {
        toggleKtvStatus(id);
      }
    });
    setSuccessMessage(`Đã vô hiệu hóa ${selectedIds.size} tài khoản KTV được chọn.`);
    setSelectedIds(new Set());
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  const handleBulkActivate = () => {
    selectedIds.forEach((id) => {
      const ktv = ktvAccounts.find((k) => k.id === id);
      if (ktv && ktv.status === 'INACTIVE') {
        toggleKtvStatus(id);
      }
    });
    setSuccessMessage(`Đã kích hoạt ${selectedIds.size} tài khoản KTV được chọn.`);
    setSelectedIds(new Set());
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  const handleOpenCreate = () => {
    setFormData({
      fullName: '',
      email: '',
      phoneNumber: '',
      assignedZone: 'Miền Bắc & Kiểm duyệt điện tử',
    });
    setFormError(null);
    setIsCreateModalOpen(true);
  };

  const handleOpenEdit = (ktv: KtvAccountRecord) => {
    setEditingKtv(ktv);
    setFormData({
      fullName: ktv.fullName,
      email: ktv.email,
      phoneNumber: ktv.phoneNumber,
      assignedZone: ktv.assignedZone,
    });
    setFormError(null);
  };

  const handleSaveKtv = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.fullName.trim() || !formData.email.trim() || !formData.phoneNumber.trim()) {
      setFormError('Vui lòng điền đầy đủ các thông tin bắt buộc.');
      return;
    }

    if (editingKtv) {
      updateKtvAccount(editingKtv.id, formData);
      setSuccessMessage(`Đã cập nhật thông tin KTV ${formData.fullName} thành công.`);
      setEditingKtv(null);
    } else {
      createKtvAccount(formData);
      setSuccessMessage(`Đã tạo tài khoản KTV ${formData.fullName} thành công.`);
      setIsCreateModalOpen(false);
    }

    setTimeout(() => setSuccessMessage(null), 4000);
  };

  const handleToggleStatus = (id: number, name: string) => {
    toggleKtvStatus(id);
    setSuccessMessage(`Đã thay đổi trạng thái hoạt động của KTV ${name}.`);
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', margin: '0 0 6px', color: '#0f172a', fontWeight: 800 }}>
            Quản lý tài khoản Kỹ Thuật Viên (KTV)
          </h1>
          <p style={{ margin: 0, color: '#64748b', fontSize: '0.92rem' }}>
            Cấp quyền, phân công khu vực trực ban, chỉnh sửa thông tin hoặc tạm ngừng quyền kiểm duyệt của nhân sự KTV.
          </p>
        </div>

        <button
          type="button"
          onClick={handleOpenCreate}
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
            transition: 'background-color 0.15s ease',
          }}
          onMouseEnter={(e) => (e.currentTarget.style.background = '#047857')}
          onMouseLeave={(e) => (e.currentTarget.style.background = '#059669')}
        >
          <ConsoleIcons.Plus size={18} />
          <span>Tạo tài khoản KTV mới</span>
        </button>
      </div>

      {/* Success Notification Alert */}
      {successMessage && (
        <div
          style={{
            padding: '12px 16px',
            background: '#ecfdf5',
            border: '1px solid #a7f3d0',
            borderRadius: '8px',
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

      {/* Search Bar & Stats */}
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
        <div style={{ position: 'relative', minWidth: '320px', flex: 1, maxWidth: '480px' }}>
          <span
            style={{
              position: 'absolute',
              left: '12px',
              top: '50%',
              transform: 'translateY(-50%)',
              color: '#94a3b8',
              display: 'flex',
              alignItems: 'center',
            }}
          >
            <ConsoleIcons.Search size={16} />
          </span>
          <input
            type="text"
            placeholder="Tìm theo tên KTV, email, khu vực..."
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
              outline: 'none',
              boxSizing: 'border-box',
            }}
          />
        </div>
        <div style={{ fontSize: '0.86rem', color: '#64748b' }}>
          Đội ngũ KTV: <strong style={{ color: '#0f172a' }}>{ktvAccounts.length}</strong> nhân sự
        </div>
      </div>

      {/* Bulk Action Bar (when rows are checked) */}
      <BulkActionBar
        selectedCount={selectedIds.size}
        totalCount={filteredKtvList.length}
        onClearSelection={() => setSelectedIds(new Set())}
        actions={[
          {
            label: 'Vô hiệu hóa đã chọn',
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

      {/* KTV Table (Light Theme) */}
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
                    ariaLabel="Chọn tất cả KTV"
                  />
                </th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Họ và tên</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Email</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Điện thoại</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Phân vùng / Nghiệp vụ trực</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Số vụ đã xử lý</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Trạng thái</th>
                <th style={{ padding: '14px 16px', textAlign: 'right', minWidth: '95px', whiteSpace: 'nowrap' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {filteredKtvList.map((item) => {
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
                        ariaLabel={`Chọn KTV ${item.fullName}`}
                      />
                    </td>
                    <td style={{ padding: '14px 16px' }}>
                      <div style={{ fontWeight: 600, color: '#0f172a' }}>{item.fullName}</div>
                      <div style={{ fontSize: '0.75rem', color: '#64748b' }}>Hoạt động: {item.lastActive}</div>
                    </td>
                    <td style={{ padding: '14px 16px', color: '#334155' }}>{item.email}</td>
                    <td style={{ padding: '14px 16px', color: '#334155' }}>{item.phoneNumber}</td>
                    <td style={{ padding: '14px 16px', color: '#475569' }}>{item.assignedZone}</td>
                    <td style={{ padding: '14px 16px', fontWeight: 700, color: '#059669' }}>
                      {item.casesResolvedCount} vụ
                    </td>
                    <td style={{ padding: '14px 16px' }}>
                      {item.status === 'ACTIVE' ? (
                        <span
                          style={{
                            padding: '3px 9px',
                            borderRadius: '12px',
                            fontSize: '0.74rem',
                            fontWeight: 700,
                            background: '#dcfce7',
                            color: '#15803d',
                            border: '1px solid #bbf7d0',
                          }}
                        >
                          ĐANG TRỰC BAN
                        </span>
                      ) : (
                        <span
                          style={{
                            padding: '3px 9px',
                            borderRadius: '12px',
                            fontSize: '0.74rem',
                            fontWeight: 700,
                            background: '#fee2e2',
                            color: '#b91c1c',
                            border: '1px solid #fecaca',
                          }}
                        >
                          VÔ HIỆU HÓA
                        </span>
                      )}
                    </td>
                    <td style={{ padding: '14px 16px', textAlign: 'right' }}>
                      <TableActionDropdown
                        ariaLabel={`Thao tác cho ${item.fullName}`}
                        items={[
                          {
                            label: 'Chỉnh sửa thông tin',
                            icon: <ConsoleIcons.Edit size={16} />,
                            onClick: () => handleOpenEdit(item),
                          },
                          {
                            label: item.status === 'ACTIVE' ? 'Vô hiệu hóa tài khoản' : 'Kích hoạt tài khoản',
                            icon: item.status === 'ACTIVE' ? <ConsoleIcons.Lock size={16} /> : <ConsoleIcons.Unlock size={16} />,
                            variant: item.status === 'ACTIVE' ? 'danger' : 'normal',
                            onClick: () => handleToggleStatus(item.id, item.fullName),
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

      {/* Modal Create or Edit KTV (Light Theme) */}
      {(isCreateModalOpen || editingKtv) && (
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
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h2 style={{ margin: 0, fontSize: '1.25rem', color: '#0f172a', fontWeight: 700 }}>
                {editingKtv ? 'Chỉnh sửa thông tin KTV' : 'Tạo tài khoản KTV mới'}
              </h2>
              <button
                type="button"
                onClick={() => {
                  setIsCreateModalOpen(false);
                  setEditingKtv(null);
                }}
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: '#94a3b8',
                  fontSize: '1.2rem',
                  cursor: 'pointer',
                  padding: '4px',
                }}
              >
                ✕
              </button>
            </div>

            {formError && (
              <div
                style={{
                  padding: '10px 14px',
                  background: '#fef2f2',
                  border: '1px solid #fecaca',
                  borderRadius: '6px',
                  color: '#dc2626',
                  fontSize: '0.85rem',
                  marginBottom: '16px',
                }}
              >
                {formError}
              </div>
            )}

            <form onSubmit={handleSaveKtv} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                  Họ và tên KTV *
                </label>
                <input
                  type="text"
                  required
                  placeholder="Ví dụ: Nguyễn Văn Tuấn"
                  value={formData.fullName}
                  onChange={(e) => setFormData({ ...formData, fullName: e.target.value })}
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
                  Địa chỉ Email làm việc *
                </label>
                <input
                  type="email"
                  required
                  placeholder="ktv.name@ogshop.vn"
                  value={formData.email}
                  onChange={(e) => setFormData({ ...formData, email: e.target.value })}
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
                  Số điện thoại nội bộ *
                </label>
                <input
                  type="text"
                  required
                  placeholder="091xxxxxxx"
                  value={formData.phoneNumber}
                  onChange={(e) => setFormData({ ...formData, phoneNumber: e.target.value })}
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
                  Phân vùng / Nhiệm vụ trực ban
                </label>
                <select
                  value={formData.assignedZone}
                  onChange={(e) => setFormData({ ...formData, assignedZone: e.target.value })}
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
                  <option value="Miền Bắc & Kiểm duyệt điện tử">Miền Bắc & Kiểm duyệt điện tử</option>
                  <option value="Miền Nam & Trọng tài tranh chấp">Miền Nam & Trọng tài tranh chấp</option>
                  <option value="Toàn quốc & Kiểm duyệt eKYC">Toàn quốc & Kiểm duyệt eKYC</option>
                  <option value="Ca đêm & Xử lý sự cố Escrow">Ca đêm & Xử lý sự cố Escrow</option>
                </select>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '14px' }}>
                <button
                  type="button"
                  onClick={() => {
                    setIsCreateModalOpen(false);
                    setEditingKtv(null);
                  }}
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
                  {editingKtv ? 'Lưu thay đổi' : 'Tạo tài khoản'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
