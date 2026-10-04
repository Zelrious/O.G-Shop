import React, { useState } from 'react';
import { useManagementStore } from '../../features/management/useManagementStore';
import { ConsoleIcons } from '../../shared/layout/ConsoleIcons';
import { EmergencyAlertRecord } from '../../features/management/managementStore';
import {
  TableActionDropdown,
  BulkActionBar,
  TableCheckbox,
} from '../../shared/layout/ConsoleTableActions';

export const AdminEmergencyAlertsPage: React.FC = () => {
  const { emergencyAlerts, acknowledgeAlert, resolveAlert } = useManagementStore();
  const [selectedAlert, setSelectedAlert] = useState<EmergencyAlertRecord | null>(null);
  const [adminNote, setAdminNote] = useState('');
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Multi-select state
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  const isAllSelected =
    emergencyAlerts.length > 0 &&
    emergencyAlerts.every((a) => selectedIds.has(a.id));
  const isSomeSelected =
    emergencyAlerts.some((a) => selectedIds.has(a.id)) && !isAllSelected;

  const handleToggleAll = () => {
    if (isAllSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(emergencyAlerts.map((a) => a.id)));
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

  const handleBulkAcknowledge = () => {
    selectedIds.forEach((id) => {
      acknowledgeAlert(id, 'Admin đã tiếp nhận hàng loạt và đang phối hợp xử lý.');
    });
    setSuccessMessage(`Đã tiếp nhận ${selectedIds.size} cảnh báo được chọn.`);
    setSelectedIds(new Set());
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  const handleBulkResolve = () => {
    selectedIds.forEach((id) => {
      resolveAlert(id, 'Admin đã xử lý và đóng cảnh báo hàng loạt.');
    });
    setSuccessMessage(`Đã đánh dấu hoàn tất xử lý cho ${selectedIds.size} cảnh báo.`);
    setSelectedIds(new Set());
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  const handleAcknowledge = (alertId: string) => {
    acknowledgeAlert(alertId, 'Admin đã tiếp nhận và đang phối hợp với phòng kỹ thuật.');
    setSuccessMessage('Đã chuyển trạng thái cảnh báo sang Đang điều tra/xử lý.');
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  const handleOpenResolveModal = (alert: EmergencyAlertRecord) => {
    setSelectedAlert(alert);
    setAdminNote(alert.adminNote || '');
  };

  const handleConfirmResolve = () => {
    if (!selectedAlert || !adminNote.trim()) return;

    resolveAlert(selectedAlert.id, adminNote.trim());
    setSuccessMessage(`Đã đánh dấu xử lý hoàn tất sự cố "${selectedAlert.title}".`);
    setSelectedAlert(null);
    setAdminNote('');
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', margin: '0 0 6px', color: '#0f172a', fontWeight: 800 }}>
            Thông báo khẩn cấp từ Kỹ Thuật Viên
          </h1>
          <p style={{ margin: 0, color: '#64748b', fontSize: '0.92rem' }}>
            Tiếp nhận các cảnh báo lỗi hệ thống, sự cố cổng thanh toán hoặc nghi vấn gian lận nghiêm trọng được đội ngũ KTV đẩy lên.
          </p>
        </div>

        {emergencyAlerts.length > 0 && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <TableCheckbox
              checked={isAllSelected}
              indeterminate={isSomeSelected}
              onChange={handleToggleAll}
              ariaLabel="Chọn tất cả cảnh báo"
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
        totalCount={emergencyAlerts.length}
        onClearSelection={() => setSelectedIds(new Set())}
        actions={[
          {
            label: 'Tiếp nhận đã chọn',
            icon: <ConsoleIcons.CheckCircle size={15} />,
            variant: 'warning',
            onClick: handleBulkAcknowledge,
          },
          {
            label: 'Đóng đã xử lý xong',
            icon: <ConsoleIcons.CheckCircle size={15} />,
            variant: 'primary',
            onClick: handleBulkResolve,
          },
        ]}
      />

      {/* Emergency Alerts Feed (Light Theme) */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        {emergencyAlerts.length === 0 ? (
          <div
            style={{
              background: '#ffffff',
              border: '1px solid #e2e8f0',
              borderRadius: '12px',
              padding: '60px 20px',
              textAlign: 'center',
              boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
            }}
          >
            <div style={{ color: '#059669', marginBottom: '12px', display: 'flex', justifyContent: 'center' }}>
              <ConsoleIcons.CheckCircle size={40} />
            </div>
            <h3 style={{ color: '#0f172a', margin: '0 0 6px', fontWeight: 700 }}>Hệ thống vận hành an toàn</h3>
            <p style={{ margin: 0, color: '#64748b', fontSize: '0.9rem' }}>
              Hiện không có thông báo khẩn cấp nào từ đội ngũ KTV.
            </p>
          </div>
        ) : (
          emergencyAlerts.map((item) => {
            const isSelected = selectedIds.has(item.id);
            return (
              <div
                key={item.id}
                style={{
                  background: isSelected ? '#f0fdf4' : '#ffffff',
                  border: item.status === 'UNREAD' ? '1px solid #fca5a5' : '1px solid #e2e8f0',
                  borderRadius: '12px',
                  padding: '20px',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '14px',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
                  transition: 'background-color 0.15s ease',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '12px' }}>
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: '12px' }}>
                    <div style={{ paddingTop: '2px' }}>
                      <TableCheckbox
                        checked={isSelected}
                        onChange={() => handleToggleItem(item.id)}
                        ariaLabel={`Chọn cảnh báo ${item.title}`}
                      />
                    </div>
                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
                        <span
                          style={{
                            padding: '3px 8px',
                            borderRadius: '4px',
                            fontSize: '0.72rem',
                            fontWeight: 700,
                            background: item.severity === 'CRITICAL' ? '#fee2e2' : '#fef3c7',
                            color: item.severity === 'CRITICAL' ? '#b91c1c' : '#b45309',
                            border: `1px solid ${item.severity === 'CRITICAL' ? '#fecaca' : '#fde68a'}`,
                          }}
                        >
                          {item.severity === 'CRITICAL' ? 'KHẨN CẤP (CRITICAL)' : 'NGHIÊM TRỌNG (HIGH)'}
                        </span>

                        <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                          KTV chuyển giao: <strong style={{ color: '#0f172a' }}>{item.ktvName}</strong> · Lúc: {item.createdAt}
                        </span>
                      </div>

                      <h3 style={{ margin: '0 0 6px 0', fontSize: '1.15rem', color: '#0f172a', fontWeight: 700 }}>
                        {item.title}
                      </h3>
                    </div>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    {item.status === 'UNREAD' && (
                      <span
                        style={{
                          padding: '3px 10px',
                          borderRadius: '12px',
                          fontSize: '0.75rem',
                          fontWeight: 700,
                          background: '#fee2e2',
                          color: '#dc2626',
                          border: '1px solid #fca5a5',
                        }}
                      >
                        CHƯA TIẾP NHẬN
                      </span>
                    )}
                    {item.status === 'INVESTIGATING' && (
                      <span
                        style={{
                          padding: '3px 10px',
                          borderRadius: '12px',
                          fontSize: '0.75rem',
                          fontWeight: 700,
                          background: '#fef3c7',
                          color: '#d97706',
                          border: '1px solid #fcd34d',
                        }}
                      >
                        ĐANG ĐIỀU TRA
                      </span>
                    )}
                    {item.status === 'RESOLVED' && (
                      <span
                        style={{
                          padding: '3px 10px',
                          borderRadius: '12px',
                          fontSize: '0.75rem',
                          fontWeight: 700,
                          background: '#dcfce7',
                          color: '#15803d',
                          border: '1px solid #bbf7d0',
                        }}
                      >
                        ĐÃ KHẮC PHỤC XONG
                      </span>
                    )}

                    {/* 3-dots Menu for Row Actions */}
                    <TableActionDropdown
                      ariaLabel={`Tùy chọn cho cảnh báo ${item.id}`}
                      items={[
                        ...(item.status === 'UNREAD'
                          ? [
                              {
                                label: 'Tiếp nhận điều tra',
                                icon: <ConsoleIcons.CheckCircle size={16} />,
                                onClick: () => handleAcknowledge(item.id),
                              },
                            ]
                          : []),
                        ...(item.status !== 'RESOLVED'
                          ? [
                              {
                                label: 'Đóng & Xử lý xong',
                                icon: <ConsoleIcons.CheckCircle size={16} />,
                                onClick: () => handleOpenResolveModal(item),
                              },
                            ]
                          : []),
                        {
                          label: 'Sao chép ID sự cố',
                          icon: <ConsoleIcons.FileText size={16} />,
                          onClick: () => {
                            navigator.clipboard?.writeText(item.id);
                            alert(`Đã sao chép mã sự cố: ${item.id}`);
                          },
                        },
                      ]}
                    />
                  </div>
                </div>

                <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', padding: '14px', borderRadius: '8px', color: '#334155', fontSize: '0.9rem', lineHeight: '1.5' }}>
                  {item.details}
                </div>

                {item.adminNote && (
                  <div style={{ background: '#ecfdf5', border: '1px solid #a7f3d0', padding: '10px 14px', borderRadius: '8px', fontSize: '0.85rem' }}>
                    <strong style={{ color: '#047857' }}>Chỉ đạo xử lý của Admin:</strong>{' '}
                    <span style={{ color: '#065f46' }}>{item.adminNote}</span>
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>

      {/* Modal Resolve Emergency Alert (Light Theme) */}
      {selectedAlert && (
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
              maxWidth: '540px',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
            }}
          >
            <h2 style={{ margin: '0 0 8px 0', fontSize: '1.25rem', color: '#0f172a', fontWeight: 700 }}>
              Ghi chú hoàn tất xử lý sự cố khẩn cấp
            </h2>
            <p style={{ margin: '0 0 16px 0', fontSize: '0.86rem', color: '#64748b' }}>
              Nhập chỉ đạo hoặc nguyên nhân sự cố đã khắc phục để thông báo lại cho KTV <strong>{selectedAlert.ktvName}</strong>.
            </p>

            <div style={{ marginBottom: '14px' }}>
              <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                Nội dung xử lý hoàn tất *
              </label>
              <textarea
                rows={4}
                required
                placeholder="Ví dụ: Đã liên hệ cổng ngân hàng reset webhook, tiền ký quỹ đã cập nhật thành công..."
                value={adminNote}
                onChange={(e) => setAdminNote(e.target.value)}
                style={{
                  width: '100%',
                  padding: '10px 12px',
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
                onClick={() => setSelectedAlert(null)}
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
                onClick={handleConfirmResolve}
                disabled={!adminNote.trim()}
                style={{
                  padding: '9px 18px',
                  background: '#059669',
                  border: 'none',
                  color: '#ffffff',
                  borderRadius: '8px',
                  fontSize: '0.88rem',
                  fontWeight: 600,
                  cursor: !adminNote.trim() ? 'not-allowed' : 'pointer',
                  opacity: !adminNote.trim() ? 0.6 : 1,
                  boxShadow: '0 1px 2px rgba(5, 150, 105, 0.2)',
                }}
              >
                Xác nhận hoàn tất
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
