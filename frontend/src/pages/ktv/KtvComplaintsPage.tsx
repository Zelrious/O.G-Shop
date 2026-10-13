import { Button, Badge, EmptyState, PageHeader, Select } from '../../shared/components';
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
    <div className="og-ktv-workspace">
      {/* Page Header */}
      <PageHeader title="Tiếp nhận & Xử lý khiếu nại" description="Giải quyết các phản ánh người dùng về giao dịch và đối tác. Khi phát hiện sự cố lỗi hệ thống kỹ thuật, KTV có thể đẩy trực tiếp lên cho Quản Trị Viên (Admin) xử lý khẩn cấp." actions={<>{complaints.length > 0 && (
          <label style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <TableCheckbox
              checked={isAllSelected}
              indeterminate={isSomeSelected}
              onChange={handleToggleAll}
              ariaLabel="Chọn tất cả khiếu nại"
            />
            <span style={{ fontSize: '0.84rem', color: 'var(--og-color-text-secondary)' }}>Chọn tất cả</span>
          </label>
        )}</>} />

      {successMessage && (
        <div
          style={{
            background: 'var(--og-color-primary-light)',
            border: '1px solid var(--og-color-border)',
            borderRadius: '8px',
            padding: '14px 16px',
            color: 'var(--og-color-success)',
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

      {complaints.length === 0 && <EmptyState title="Không có khiếu nại" description="Các phản ánh mới sẽ xuất hiện tại đây." />}
      {/* Complaints List (Light Theme) */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        {complaints.map((item) => {
          const isSelected = selectedIds.has(item.id);
          return (
            <div
              key={item.id}
              style={{
                background: isSelected ? 'var(--og-color-primary-light)' : 'var(--og-color-surface-elevated)',
                border: item.status === 'ESCALATED_TO_ADMIN' ? '1px solid var(--og-color-danger-border)' : '1px solid var(--og-color-border)',
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
                    <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: '8px', marginBottom: '6px' }}>
                      <Badge variant={item.urgency === 'CRITICAL' ? 'danger' : 'neutral'}>{item.urgency === 'CRITICAL' ? 'KHẨN CẤP' : 'TIÊU CHUẨN'}</Badge>

                      <Badge variant="info">{item.type === 'SYSTEM_ERROR'
                          ? 'SỰ CỐ HỆ THỐNG'
                          : item.type === 'TRANSACTION'
                          ? 'GIAO DỊCH / ESCROW'
                          : 'VI PHẠM NỘI QUY'}</Badge>


                    </div>

                    <h3 style={{ margin: '0 0 6px 0', fontSize: '1.15rem', color: 'var(--og-color-text-primary)', fontWeight: 700 }}>
                      {item.title}
                    </h3>
                      <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)' }}>
                        Người phản ánh: <strong style={{ color: 'var(--og-color-text-primary)' }}>{item.complainantName}</strong> ({item.complainantEmail})
                      </span>
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  {item.status === 'PENDING' && (
                    <Badge variant="warning">CHỜ KTV XỬ LÝ</Badge>
                  )}
                  {item.status === 'RESOLVED' && (
                    <Badge variant="success">ĐÃ GIẢI QUYẾT XONG</Badge>
                  )}
                  {item.status === 'ESCALATED_TO_ADMIN' && (
                    <Badge variant="danger">ĐÃ ĐẨY LÊN CHO ADMIN</Badge>
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

              <div style={{ background: 'var(--og-color-surface-subtle)', border: '1px solid var(--og-color-border)', padding: '14px', borderRadius: '8px', color: 'var(--og-color-text-secondary)', fontSize: '0.9rem', lineHeight: '1.5' }}>
                {item.description}
              </div>

              {item.escalationNote && (
                <div style={{ background: 'var(--og-color-danger-bg)', border: '1px solid var(--og-color-danger-border)', padding: '10px 14px', borderRadius: '8px', fontSize: '0.86rem', color: 'var(--og-color-danger)' }}>
                  <strong>Cảnh báo khẩn đã gửi tới Admin:</strong> {item.escalationNote}
                </div>
              )}

              {item.resolution && (
                <div style={{ background: 'var(--og-color-primary-light)', border: '1px solid var(--og-color-border)', padding: '10px 14px', borderRadius: '8px', fontSize: '0.86rem', color: 'var(--og-color-success)' }}>
                  <strong>Kết quả giải quyết của KTV:</strong> {item.resolution}
                </div>
              )}

              {/* Action Buttons */}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', paddingTop: '10px', borderTop: '1px solid var(--og-color-surface-subtle)' }}>
                {item.status !== 'ESCALATED_TO_ADMIN' && item.status !== 'RESOLVED' && (
                  <Button
                    type="button"
                    onClick={() => handleOpenEscalate(item)} variant="danger-outline">
                    <ConsoleIcons.AlertTriangle size={15} />
                    <span>Đẩy lên Admin (Báo lỗi hệ thống)</span>
                  </Button>
                )}

                {item.status !== 'RESOLVED' && (
                  <Button
                    type="button"
                    onClick={() => handleOpenResolve(item)} variant="primary">
                    <ConsoleIcons.CheckCircle size={15} />
                    <span>Xử lý khiếu nại</span>
                  </Button>
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
              background: 'var(--og-color-surface)',
              borderRadius: '14px',
              border: '1px solid var(--og-color-border)',
              padding: '24px',
              width: '100%',
              maxWidth: '560px',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
            }}
          >
            <h2 style={{ margin: '0 0 8px', fontSize: '1.25rem', color: 'var(--og-color-text-primary)', fontWeight: 700 }}>
              Đẩy sự vụ lên Quản Trị Viên (Admin)
            </h2>
            <p style={{ margin: '0 0 16px', fontSize: '0.86rem', color: 'var(--og-color-text-secondary)' }}>
              Khiếu nại này liên quan đến lỗi hệ thống hoặc lỗ hổng kỹ thuật nghiêm trọng vượt quá thẩm quyền của KTV.
            </p>

            <div style={{ marginBottom: '14px' }}>
              <Select label="Mức độ khẩn cấp" value={escalateSeverity} onChange={value => setEscalateSeverity(value as 'HIGH' | 'CRITICAL')} options={[{ value: 'CRITICAL', label: 'KHẨN CẤP (CRITICAL - Cần xử lý ngay)' }, { value: 'HIGH', label: 'NGHIÊM TRỌNG (HIGH - Trong 2 giờ)' }]} />
            </div>

            <div style={{ marginBottom: '16px' }}>
              <label htmlFor="complaint-escalate" style={{ display: 'block', fontSize: '0.84rem', color: 'var(--og-color-text-secondary)', marginBottom: '6px', fontWeight: 600 }}>
                Báo cáo tóm tắt lỗi hệ thống gửi Admin *
              </label>
              <textarea id="complaint-escalate"
                rows={4}
                required
                value={escalateNote}
                onChange={(e) => setEscalateNote(e.target.value)}
                style={{
                  width: '100%',
                  padding: '9px 12px',
                  background: 'var(--og-color-surface)',
                  border: '1px solid var(--og-color-border-strong)',
                  borderRadius: '8px',
                  color: 'var(--og-color-text-primary)',
                  fontSize: '0.88rem',
                  boxSizing: 'border-box',
                }}
              />
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
              <Button
                type="button"
                onClick={() => setIsEscalating(false)} variant="secondary">
                Hủy bỏ
              </Button>
              <Button
                type="button"
                onClick={handleConfirmEscalate}
                disabled={!escalateNote.trim()} variant="danger">
                Gửi báo cáo khẩn cho Admin
              </Button>
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
              background: 'var(--og-color-surface)',
              borderRadius: '14px',
              border: '1px solid var(--og-color-border)',
              padding: '24px',
              width: '100%',
              maxWidth: '540px',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
            }}
          >
            <h2 style={{ margin: '0 0 8px', fontSize: '1.25rem', color: 'var(--og-color-text-primary)', fontWeight: 700 }}>
              Ghi nhận kết quả xử lý khiếu nại
            </h2>
            <p style={{ margin: '0 0 16px', fontSize: '0.86rem', color: 'var(--og-color-text-secondary)' }}>
              Khiếu nại: <strong>{selectedComplaint.title}</strong>
            </p>

            <div style={{ marginBottom: '16px' }}>
              <label htmlFor="complaint-resolve" style={{ display: 'block', fontSize: '0.84rem', color: 'var(--og-color-text-secondary)', marginBottom: '6px', fontWeight: 600 }}>
                Nội dung giải quyết và phản hồi cho người dùng *
              </label>
              <textarea id="complaint-resolve"
                rows={4}
                required
                placeholder="Ví dụ: Đã liên hệ bên bán hỗ trợ kiểm tra phụ kiện thất lạc, hoàn tất thỏa thuận..."
                value={resolutionText}
                onChange={(e) => setResolutionText(e.target.value)}
                style={{
                  width: '100%',
                  padding: '9px 12px',
                  background: 'var(--og-color-surface)',
                  border: '1px solid var(--og-color-border-strong)',
                  borderRadius: '8px',
                  color: 'var(--og-color-text-primary)',
                  fontSize: '0.88rem',
                  boxSizing: 'border-box',
                }}
              />
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
              <Button
                type="button"
                onClick={() => setIsResolving(false)} variant="secondary">
                Hủy bỏ
              </Button>
              <Button
                type="button"
                onClick={handleConfirmResolve}
                disabled={!resolutionText.trim()} variant="primary">
                Hoàn tất giải quyết
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
