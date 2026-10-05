import React, { useState } from 'react';
import { useManagementStore } from '../../features/management/useManagementStore';
import { ConsoleIcons } from '../../shared/layout/ConsoleIcons';
import { ComplaintRecord } from '../../features/management/managementStore';
import {
  TableActionDropdown,
  BulkActionBar,
  TableCheckbox,
} from '../../shared/layout/ConsoleTableActions';

export const KtvComplaintsPage: React.FC = () => {
  const { complaints, resolveComplaint, escalateComplaintToAdmin } = useManagementStore();
  const [selectedComplaint, setSelectedComplaint] = useState<ComplaintRecord | null>(null);
  const [isEscalating, setIsEscalating] = useState(false);
  const [escalateNote, setEscalateNote] = useState('');
  const [escalateSeverity, setEscalateSeverity] = useState<'HIGH' | 'CRITICAL'>('CRITICAL');
  const [isResolving, setIsResolving] = useState(false);
  const [resolutionText, setResolutionText] = useState('');
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Multi-select state
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  const isAllSelected =
    complaints.length > 0 && complaints.every((c) => selectedIds.has(c.id));
  const isSomeSelected =
    complaints.some((c) => selectedIds.has(c.id)) && !isAllSelected;

  const handleToggleAll = () => {
    if (isAllSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(complaints.map((c) => c.id)));
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

  const handleBulkEscalate = () => {
    selectedIds.forEach((id) => {
      const c = complaints.find((item) => item.id === id);
      if (c && c.status !== 'ESCALATED_TO_ADMIN') {
        escalateComplaintToAdmin(id, `KTV đẩy hàng loạt sự cố lỗi hệ thống: ${c.title}`, 'HIGH');
      }
    });
    setSuccessMessage(`Đã đẩy ${selectedIds.size} khiếu nại lên Quản Trị Viên (Admin) thành công.`);
    setSelectedIds(new Set());
    setTimeout(() => setSuccessMessage(null), 5000);
  };

  const handleOpenEscalate = (c: ComplaintRecord) => {
    setSelectedComplaint(c);
    setIsEscalating(true);
    setIsResolving(false);
    setEscalateNote(
      `Phát hiện lỗi kỹ thuật hệ thống từ khiếu nại của ${c.complainantName}: ${c.description}`
    );
    setEscalateSeverity(c.urgency === 'CRITICAL' ? 'CRITICAL' : 'HIGH');
  };

  const handleConfirmEscalate = () => {
    if (!selectedComplaint || !escalateNote.trim()) return;

    escalateComplaintToAdmin(selectedComplaint.id, escalateNote.trim(), escalateSeverity);
    setSuccessMessage(
      `Đã chuyển sự vụ "${selectedComplaint.title}" lên trung tâm Thông Báo Khẩn của Quản Trị Viên (Admin) thành công.`
    );
    setIsEscalating(false);
    setSelectedComplaint(null);
    setEscalateNote('');
    setTimeout(() => setSuccessMessage(null), 5000);
  };

  const handleOpenResolve = (c: ComplaintRecord) => {
    setSelectedComplaint(c);
    setIsResolving(true);
    setIsEscalating(false);
    setResolutionText('');
  };

  const handleConfirmResolve = () => {
    if (!selectedComplaint || !resolutionText.trim()) return;

    resolveComplaint(selectedComplaint.id, resolutionText.trim());
    setSuccessMessage(`Đã xử lý khiếu nại "${selectedComplaint.title}" thành công.`);
    setIsResolving(false);
    setSelectedComplaint(null);
    setResolutionText('');
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Page Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', margin: '0 0 6px', color: '#0f172a', fontWeight: 800 }}>
            Tiếp nhận & Xử lý khiếu nại
          </h1>
          <p style={{ margin: 0, color: '#64748b', fontSize: '0.92rem' }}>
            Giải quyết các phản ánh người dùng về giao dịch và đối tác. Khi phát hiện sự cố lỗi hệ thống kỹ thuật, KTV có thể đẩy trực tiếp lên cho Quản Trị Viên (Admin) xử lý khẩn cấp.
          </p>
        </div>

        {complaints.length > 0 && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <TableCheckbox
              checked={isAllSelected}
              indeterminate={isSomeSelected}
              onChange={handleToggleAll}
              ariaLabel="Chọn tất cả khiếu nại"
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
            padding: '14px 16px',
            color: '#065f46',
            fontSize: '0.9rem',
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
        totalCount={complaints.length}
        onClearSelection={() => setSelectedIds(new Set())}
        actions={[
          {
            label: 'Đẩy lên Admin (Báo lỗi hệ thống) hàng loạt',
            icon: <ConsoleIcons.AlertTriangle size={15} />,
            variant: 'danger',
            onClick: handleBulkEscalate,
          },
        ]}
      />

      {/* Complaints List (Light Theme) */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        {complaints.map((item) => {
          const isSelected = selectedIds.has(item.id);
          return (
            <div
              key={item.id}
              style={{
                background: isSelected ? '#f0fdf4' : '#ffffff',
                border: item.status === 'ESCALATED_TO_ADMIN' ? '1px solid #fca5a5' : '1px solid #e2e8f0',
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
                      onChange={() => handleToggleRow(item.id)}
                      ariaLabel={`Chọn khiếu nại ${item.title}`}
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
                          background: item.urgency === 'CRITICAL' ? '#fee2e2' : '#fef3c7',
                          color: item.urgency === 'CRITICAL' ? '#b91c1c' : '#b45309',
                          border: `1px solid ${item.urgency === 'CRITICAL' ? '#fecaca' : '#fde68a'}`,
                        }}
                      >
                        {item.urgency === 'CRITICAL' ? 'KHẨN CẤP' : 'TIÊU CHUẨN'}
                      </span>

                      <span
                        style={{
                          padding: '3px 8px',
                          borderRadius: '4px',
                          fontSize: '0.72rem',
                          fontWeight: 700,
                          background: '#eff6ff',
                          color: '#1d4ed8',
                          border: '1px solid #bfdbfe',
                        }}
                      >
                        {item.type === 'SYSTEM_ERROR'
                          ? 'SỰ CỐ HỆ THỐNG'
                          : item.type === 'TRANSACTION'
                          ? 'GIAO DỊCH / ESCROW'
                          : 'VI PHẠM NỘI QUY'}
                      </span>

                      <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                        Người phản ánh: <strong style={{ color: '#0f172a' }}>{item.complainantName}</strong> ({item.complainantEmail})
                      </span>
                    </div>

                    <h3 style={{ margin: '0 0 6px 0', fontSize: '1.15rem', color: '#0f172a', fontWeight: 700 }}>
                      {item.title}
                    </h3>
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  {item.status === 'PENDING' && (
                    <span
                      style={{
                        padding: '3px 10px',
                        borderRadius: '12px',
                        fontSize: '0.74rem',
                        fontWeight: 700,
                        background: '#fef3c7',
                        color: '#b45309',
                        border: '1px solid #fde68a',
                      }}
                    >
                      CHỜ KTV XỬ LÝ
                    </span>
                  )}
                  {item.status === 'RESOLVED' && (
                    <span
                      style={{
                        padding: '3px 10px',
                        borderRadius: '12px',
                        fontSize: '0.74rem',
                        fontWeight: 700,
                        background: '#dcfce7',
                        color: '#15803d',
                        border: '1px solid #bbf7d0',
                      }}
                    >
                      ĐÃ GIẢI QUYẾT XONG
                    </span>
                  )}
                  {item.status === 'ESCALATED_TO_ADMIN' && (
                    <span
                      style={{
                        padding: '3px 10px',
                        borderRadius: '12px',
                        fontSize: '0.74rem',
                        fontWeight: 700,
                        background: '#fee2e2',
                        color: '#b91c1c',
                        border: '1px solid #fecaca',
                      }}
                    >
                      ĐÃ ĐẨY LÊN CHO ADMIN
                    </span>
                  )}

                  <TableActionDropdown
                    ariaLabel={`Tùy chọn khiếu nại ${item.id}`}
                    items={[
                      ...(item.status !== 'RESOLVED'
                        ? [
                            {
                              label: 'Xử lý khiếu nại',
                              icon: <ConsoleIcons.CheckCircle size={16} />,
                              onClick: () => handleOpenResolve(item),
                            },
                          ]
                        : []),
                      ...(item.status !== 'ESCALATED_TO_ADMIN'
                        ? [
                            {
                              label: 'Đẩy lên Admin (Báo lỗi hệ thống)',
                              icon: <ConsoleIcons.AlertTriangle size={16} />,
                              variant: 'danger' as const,
                              onClick: () => handleOpenEscalate(item),
                            },
                          ]
                        : []),
                      {
                        label: 'Tặng voucher bồi thường',
                        icon: <ConsoleIcons.Gift size={16} />,
                        onClick: () => alert(`Điều hướng tặng voucher cho ${item.complainantName}`),
                      },
                    ]}
                  />
                </div>
              </div>

              <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', padding: '14px', borderRadius: '8px', color: '#334155', fontSize: '0.9rem', lineHeight: '1.5' }}>
                {item.description}
              </div>

              {item.escalationNote && (
                <div style={{ background: '#fef2f2', border: '1px solid #fecaca', padding: '10px 14px', borderRadius: '8px', fontSize: '0.86rem', color: '#991b1b' }}>
                  <strong>Cảnh báo khẩn đã gửi tới Admin:</strong> {item.escalationNote}
                </div>
              )}

              {item.resolution && (
                <div style={{ background: '#ecfdf5', border: '1px solid #a7f3d0', padding: '10px 14px', borderRadius: '8px', fontSize: '0.86rem', color: '#065f46' }}>
                  <strong>Kết quả giải quyết của KTV:</strong> {item.resolution}
                </div>
              )}

              {/* Action Buttons */}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', paddingTop: '10px', borderTop: '1px solid #f1f5f9' }}>
                {item.status !== 'ESCALATED_TO_ADMIN' && item.status !== 'RESOLVED' && (
                  <button
                    type="button"
                    onClick={() => handleOpenEscalate(item)}
                    style={{
                      padding: '8px 14px',
                      background: '#fef2f2',
                      color: '#dc2626',
                      border: '1px solid #fecaca',
                      borderRadius: '6px',
                      fontSize: '0.84rem',
                      fontWeight: 700,
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '6px',
                    }}
                  >
                    <ConsoleIcons.AlertTriangle size={15} />
                    <span>Đẩy lên Admin (Báo lỗi hệ thống)</span>
                  </button>
                )}

                {item.status !== 'RESOLVED' && (
                  <button
                    type="button"
                    onClick={() => handleOpenResolve(item)}
                    style={{
                      padding: '8px 16px',
                      background: '#059669',
                      color: '#ffffff',
                      border: 'none',
                      borderRadius: '6px',
                      fontSize: '0.84rem',
                      fontWeight: 700,
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '6px',
                    }}
                  >
                    <ConsoleIcons.CheckCircle size={15} />
                    <span>Xử lý khiếu nại</span>
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Modal Escalate to Admin */}
      {isEscalating && selectedComplaint && (
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
              Đẩy sự vụ lên Quản Trị Viên (Admin)
            </h2>
            <p style={{ margin: '0 0 16px', fontSize: '0.86rem', color: '#64748b' }}>
              Khiếu nại này liên quan đến lỗi hệ thống hoặc lỗ hổng kỹ thuật nghiêm trọng vượt quá thẩm quyền của KTV.
            </p>

            <div style={{ marginBottom: '14px' }}>
              <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                Mức độ khẩn cấp
              </label>
              <select
                value={escalateSeverity}
                onChange={(e) => setEscalateSeverity(e.target.value as 'HIGH' | 'CRITICAL')}
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
                <option value="CRITICAL">KHẨN CẤP (CRITICAL - Cần xử lý ngay)</option>
                <option value="HIGH">NGHIÊM TRỌNG (HIGH - Trong 2 giờ)</option>
              </select>
            </div>

            <div style={{ marginBottom: '16px' }}>
              <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                Báo cáo tóm tắt lỗi hệ thống gửi Admin *
              </label>
              <textarea
                rows={4}
                required
                value={escalateNote}
                onChange={(e) => setEscalateNote(e.target.value)}
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
                onClick={() => setIsEscalating(false)}
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
                onClick={handleConfirmEscalate}
                disabled={!escalateNote.trim()}
                style={{
                  padding: '9px 18px',
                  background: '#dc2626',
                  border: 'none',
                  color: '#ffffff',
                  borderRadius: '8px',
                  fontSize: '0.88rem',
                  fontWeight: 700,
                  cursor: !escalateNote.trim() ? 'not-allowed' : 'pointer',
                  opacity: !escalateNote.trim() ? 0.6 : 1,
                  boxShadow: '0 1px 2px rgba(220, 38, 38, 0.2)',
                }}
              >
                Gửi báo cáo khẩn cho Admin
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal Resolve Complaint */}
      {isResolving && selectedComplaint && (
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
            <h2 style={{ margin: '0 0 8px', fontSize: '1.25rem', color: '#0f172a', fontWeight: 700 }}>
              Ghi nhận kết quả xử lý khiếu nại
            </h2>
            <p style={{ margin: '0 0 16px', fontSize: '0.86rem', color: '#64748b' }}>
              Khiếu nại: <strong>{selectedComplaint.title}</strong>
            </p>

            <div style={{ marginBottom: '16px' }}>
              <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                Nội dung giải quyết và phản hồi cho người dùng *
              </label>
              <textarea
                rows={4}
                required
                placeholder="Ví dụ: Đã liên hệ bên bán hỗ trợ kiểm tra phụ kiện thất lạc, hoàn tất thỏa thuận..."
                value={resolutionText}
                onChange={(e) => setResolutionText(e.target.value)}
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
                onClick={() => setIsResolving(false)}
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
                disabled={!resolutionText.trim()}
                style={{
                  padding: '9px 18px',
                  background: '#059669',
                  border: 'none',
                  color: '#ffffff',
                  borderRadius: '8px',
                  fontSize: '0.88rem',
                  fontWeight: 700,
                  cursor: !resolutionText.trim() ? 'not-allowed' : 'pointer',
                  opacity: !resolutionText.trim() ? 0.6 : 1,
                  boxShadow: '0 1px 2px rgba(5, 150, 105, 0.2)',
                }}
              >
                Hoàn tất giải quyết
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
