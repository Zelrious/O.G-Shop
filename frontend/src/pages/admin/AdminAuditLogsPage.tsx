import React, { useState } from 'react';
import { useManagementStore } from '../../features/management/useManagementStore';
import { ConsoleIcons } from '../../shared/layout/ConsoleIcons';
import { AuditLogRecord } from '../../features/management/managementStore';
import {
  BulkActionBar,
  TableCheckbox,
} from '../../shared/layout/ConsoleTableActions';

export const AdminAuditLogsPage: React.FC = () => {
  const { auditLogs } = useManagementStore();
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState<string>('ALL');

  // Multi-select state
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  // Detail Modal state
  const [selectedDetailLog, setSelectedDetailLog] = useState<AuditLogRecord | null>(null);
  const [copiedMessage, setCopiedMessage] = useState(false);

  const filteredLogs = auditLogs.filter((log) => {
    const matchSearch =
      log.actorName.toLowerCase().includes(search.toLowerCase()) ||
      log.action.toLowerCase().includes(search.toLowerCase()) ||
      log.target.toLowerCase().includes(search.toLowerCase()) ||
      (log.details && log.details.toLowerCase().includes(search.toLowerCase()));
    const matchRole = roleFilter === 'ALL' || log.actorRole === roleFilter;
    return matchSearch && matchRole;
  });

  const isAllSelected =
    filteredLogs.length > 0 && filteredLogs.every((l) => selectedIds.has(l.id));
  const isSomeSelected =
    filteredLogs.some((l) => selectedIds.has(l.id)) && !isAllSelected;

  const handleToggleAll = () => {
    if (isAllSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(filteredLogs.map((l) => l.id)));
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

  const handleExportSelectedLogs = () => {
    const selectedData = filteredLogs.filter((l) => selectedIds.has(l.id));
    const csvContent =
      'data:text/csv;charset=utf-8,' +
      'ID,Thời gian,Người tác động,Chức vụ,Hành động,Đối tượng tác động,Chi tiết,Kết quả\n' +
      selectedData
        .map(
          (l) =>
            `"${l.id}","${l.timestamp}","${l.actorName}","${l.actorRole}","${l.action}","${l.target}","${l.details || ''}","${l.status}"`
        )
        .join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `audit_logs_${Date.now()}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Header */}
      <div>
        <h1 style={{ fontSize: '1.75rem', margin: '0 0 6px', color: '#0f172a', fontWeight: 800 }}>
          Lịch sử thao tác hệ thống (Audit Logs)
        </h1>
        <p style={{ margin: 0, color: '#64748b', fontSize: '0.92rem' }}>
          Theo dõi toàn bộ nhật ký nghiệp vụ: phê duyệt tin, xử lý Escrow, can thiệp tài khoản và điều chỉnh biểu phí. Nhấp vào hàng để xem chi tiết đối tượng.
        </p>
      </div>

      {/* Filter and Search Bar (Light Theme) */}
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
            placeholder="Tìm theo người thực hiện, hành động, đối tượng..."
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

        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <span style={{ fontSize: '0.84rem', color: '#64748b', fontWeight: 600 }}>Chủ thể thao tác:</span>
          <select
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value)}
            style={{
              padding: '8px 12px',
              background: '#ffffff',
              border: '1px solid #cbd5e1',
              borderRadius: '8px',
              color: '#0f172a',
              fontSize: '0.88rem',
            }}
          >
            <option value="ALL">Tất cả (Admin, KTV, Hệ thống)</option>
            <option value="ADMIN">Chỉ Quản trị viên (ADMIN)</option>
            <option value="KTV">Chỉ Kỹ thuật viên (KTV)</option>
            <option value="SYSTEM">Hệ thống tự động (SYSTEM)</option>
          </select>
        </div>
      </div>

      {/* Bulk Action Bar */}
      <BulkActionBar
        selectedCount={selectedIds.size}
        totalCount={filteredLogs.length}
        onClearSelection={() => setSelectedIds(new Set())}
        actions={[
          {
            label: 'Kết xuất log đã chọn (CSV)',
            icon: <ConsoleIcons.Download size={15} />,
            variant: 'primary',
            onClick: handleExportSelectedLogs,
          },
        ]}
      />

      {/* Logs Table (Light Theme) */}
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
                    ariaLabel="Chọn tất cả nhật ký"
                  />
                </th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Thời gian</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Người tác động</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Chức vụ</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Hành động</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Kết quả</th>
                <th style={{ padding: '14px 16px', textAlign: 'right', minWidth: '95px', whiteSpace: 'nowrap' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {filteredLogs.length === 0 ? (
                <tr>
                  <td colSpan={7} style={{ padding: '40px', textAlign: 'center', color: '#64748b' }}>
                    Không tìm thấy bản ghi nhật ký hệ thống nào.
                  </td>
                </tr>
              ) : (
                filteredLogs.map((log) => {
                  const isSelected = selectedIds.has(log.id);
                  return (
                    <tr
                      key={log.id}
                      onClick={() => setSelectedDetailLog(log)}
                      style={{
                        borderBottom: '1px solid #f1f5f9',
                        background: isSelected ? '#f0fdf4' : 'transparent',
                        transition: 'background-color 0.15s ease',
                        cursor: 'pointer',
                      }}
                      onMouseEnter={(e) => {
                        if (!isSelected) e.currentTarget.style.background = '#f8fafc';
                      }}
                      onMouseLeave={(e) => {
                        if (!isSelected) e.currentTarget.style.background = 'transparent';
                      }}
                      title="Nhấp vào hàng để xem chi tiết nhật ký thao tác"
                    >
                      <td style={{ padding: '14px 16px', textAlign: 'center' }} onClick={(e) => e.stopPropagation()}>
                        <TableCheckbox
                          checked={isSelected}
                          onChange={() => handleToggleRow(log.id)}
                          ariaLabel={`Chọn log #${log.id}`}
                        />
                      </td>
                      <td style={{ padding: '14px 16px', color: '#64748b', whiteSpace: 'nowrap' }}>
                        {log.timestamp}
                      </td>
                      <td style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>
                        <div style={{ fontWeight: 600, color: '#0f172a' }}>{log.actorName}</div>
                      </td>
                      <td style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>
                        <span
                          style={{
                            display: 'inline-block',
                            padding: '2px 8px',
                            borderRadius: '6px',
                            fontSize: '0.74rem',
                            fontWeight: 700,
                            background:
                              log.actorRole === 'ADMIN'
                                ? '#fef3c7'
                                : log.actorRole === 'KTV'
                                ? '#dcfce7'
                                : '#f1f5f9',
                            color:
                              log.actorRole === 'ADMIN'
                                ? '#b45309'
                                : log.actorRole === 'KTV'
                                ? '#15803d'
                                : '#475569',
                            border: `1px solid ${
                              log.actorRole === 'ADMIN'
                                ? '#fde68a'
                                : log.actorRole === 'KTV'
                                ? '#bbf7d0'
                                : '#e2e8f0'
                            }`,
                          }}
                        >
                          {log.actorRole === 'ADMIN' ? 'Quản trị viên' : log.actorRole === 'KTV' ? 'Kỹ thuật viên' : 'Hệ thống'}
                        </span>
                      </td>
                      <td style={{ padding: '14px 16px', fontWeight: 600, color: '#0f172a' }}>
                        {log.action}
                      </td>
                      <td style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>
                        <span
                          style={{
                            padding: '3px 8px',
                            borderRadius: '12px',
                            fontSize: '0.74rem',
                            fontWeight: 700,
                            background: log.status === 'SUCCESS' ? '#dcfce7' : '#fee2e2',
                            color: log.status === 'SUCCESS' ? '#15803d' : '#b91c1c',
                            border: `1px solid ${log.status === 'SUCCESS' ? '#bbf7d0' : '#fecaca'}`,
                          }}
                        >
                          {log.status === 'SUCCESS' ? 'THÀNH CÔNG' : 'THẤT BẠI'}
                        </span>
                      </td>
                      <td style={{ padding: '14px 16px', textAlign: 'right', whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                        <button
                          type="button"
                          onClick={() => setSelectedDetailLog(log)}
                          style={{
                            padding: '5px 12px',
                            background: '#f1f5f9',
                            border: '1px solid #cbd5e1',
                            borderRadius: '6px',
                            fontSize: '0.8rem',
                            fontWeight: 600,
                            color: '#334155',
                            cursor: 'pointer',
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '4px',
                            transition: 'all 0.15s ease',
                          }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.background = '#e2e8f0';
                            e.currentTarget.style.color = '#0f172a';
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.background = '#f1f5f9';
                            e.currentTarget.style.color = '#334155';
                          }}
                        >
                          <ConsoleIcons.FileText size={14} />
                          <span>Chi tiết</span>
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Audit Log Detail Modal */}
      {selectedDetailLog && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'rgba(15, 23, 42, 0.45)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '16px',
          }}
          onClick={() => setSelectedDetailLog(null)}
        >
          <div
            style={{
              background: '#ffffff',
              borderRadius: '14px',
              width: '100%',
              maxWidth: '560px',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
              border: '1px solid #e2e8f0',
              overflow: 'hidden',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            {/* Modal Header */}
            <div
              style={{
                padding: '18px 24px',
                borderBottom: '1px solid #e2e8f0',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                background: '#f8fafc',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <div
                  style={{
                    width: '36px',
                    height: '36px',
                    borderRadius: '8px',
                    background: '#ecfdf5',
                    color: '#059669',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <ConsoleIcons.FileText size={20} />
                </div>
                <div>
                  <h3 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 700, color: '#0f172a' }}>
                    Chi tiết lịch sử thao tác
                  </h3>
                  <span style={{ fontSize: '0.78rem', color: '#64748b' }}>
                    Mã bản ghi: #{selectedDetailLog.id}
                  </span>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setSelectedDetailLog(null)}
                style={{
                  background: 'transparent',
                  border: 'none',
                  fontSize: '1.4rem',
                  color: '#64748b',
                  cursor: 'pointer',
                  padding: '4px',
                  borderRadius: '6px',
                  lineHeight: 1,
                }}
                aria-label="Đóng chi tiết"
              >
                &times;
              </button>
            </div>

            {/* Modal Content */}
            <div style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                <div style={{ background: '#f8fafc', padding: '12px 14px', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                  <div style={{ fontSize: '0.76rem', color: '#64748b', fontWeight: 600, marginBottom: '4px' }}>Thời gian thực hiện</div>
                  <div style={{ fontSize: '0.88rem', color: '#0f172a', fontWeight: 600 }}>{selectedDetailLog.timestamp}</div>
                </div>

                <div style={{ background: '#f8fafc', padding: '12px 14px', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                  <div style={{ fontSize: '0.76rem', color: '#64748b', fontWeight: 600, marginBottom: '4px' }}>Kết quả thao tác</div>
                  <span
                    style={{
                      display: 'inline-block',
                      padding: '2px 8px',
                      borderRadius: '10px',
                      fontSize: '0.76rem',
                      fontWeight: 700,
                      background: selectedDetailLog.status === 'SUCCESS' ? '#dcfce7' : '#fee2e2',
                      color: selectedDetailLog.status === 'SUCCESS' ? '#15803d' : '#b91c1c',
                      border: `1px solid ${selectedDetailLog.status === 'SUCCESS' ? '#bbf7d0' : '#fecaca'}`,
                    }}
                  >
                    {selectedDetailLog.status === 'SUCCESS' ? 'THÀNH CÔNG' : 'THẤT BẠI'}
                  </span>
                </div>

                <div style={{ background: '#f8fafc', padding: '12px 14px', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                  <div style={{ fontSize: '0.76rem', color: '#64748b', fontWeight: 600, marginBottom: '4px' }}>Người tác động</div>
                  <div style={{ fontSize: '0.88rem', color: '#0f172a', fontWeight: 600 }}>{selectedDetailLog.actorName}</div>
                </div>

                <div style={{ background: '#f8fafc', padding: '12px 14px', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                  <div style={{ fontSize: '0.76rem', color: '#64748b', fontWeight: 600, marginBottom: '4px' }}>Chức vụ / Vai trò</div>
                  <div style={{ fontSize: '0.88rem', color: '#0f172a', fontWeight: 600 }}>
                    {selectedDetailLog.actorRole === 'ADMIN' ? 'Quản trị viên (ADMIN)' : selectedDetailLog.actorRole === 'KTV' ? 'Kỹ thuật viên (KTV)' : 'Hệ thống tự động'}
                  </div>
                </div>
              </div>

              <div style={{ background: '#f8fafc', padding: '12px 14px', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                <div style={{ fontSize: '0.76rem', color: '#64748b', fontWeight: 600, marginBottom: '4px' }}>Hành động nghiệp vụ</div>
                <div style={{ fontSize: '0.92rem', color: '#0f172a', fontWeight: 700 }}>{selectedDetailLog.action}</div>
              </div>

              <div style={{ background: '#f8fafc', padding: '12px 14px', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                <div style={{ fontSize: '0.76rem', color: '#64748b', fontWeight: 600, marginBottom: '4px' }}>Đối tượng tác động</div>
                <div style={{ fontSize: '0.92rem', color: '#059669', fontWeight: 700 }}>{selectedDetailLog.target}</div>
              </div>

              {selectedDetailLog.details && (
                <div style={{ background: '#f8fafc', padding: '12px 14px', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                  <div style={{ fontSize: '0.76rem', color: '#64748b', fontWeight: 600, marginBottom: '4px' }}>Nội dung / Ghi chú chi tiết</div>
                  <div style={{ fontSize: '0.88rem', color: '#334155', whiteSpace: 'pre-wrap', lineHeight: 1.5 }}>
                    {selectedDetailLog.details}
                  </div>
                </div>
              )}
            </div>

            {/* Modal Footer */}
            <div
              style={{
                padding: '14px 24px',
                borderTop: '1px solid #e2e8f0',
                display: 'flex',
                justifyContent: 'flex-end',
                gap: '10px',
                background: '#f8fafc',
              }}
            >
              <button
                type="button"
                onClick={() => {
                  const summary = `[Audit Log #${selectedDetailLog.id}]\nThời gian: ${selectedDetailLog.timestamp}\nNgười thực hiện: ${selectedDetailLog.actorName} (${selectedDetailLog.actorRole})\nHành động: ${selectedDetailLog.action}\nĐối tượng: ${selectedDetailLog.target}\nChi tiết: ${selectedDetailLog.details || 'N/A'}\nKết quả: ${selectedDetailLog.status}`;
                  navigator.clipboard?.writeText(summary);
                  setCopiedMessage(true);
                  setTimeout(() => setCopiedMessage(false), 2000);
                }}
                style={{
                  padding: '8px 16px',
                  background: '#ffffff',
                  color: '#334155',
                  border: '1px solid #cbd5e1',
                  borderRadius: '6px',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
              >
                {copiedMessage ? 'Đã sao chép!' : 'Sao chép thông tin'}
              </button>
              <button
                type="button"
                onClick={() => setSelectedDetailLog(null)}
                style={{
                  padding: '8px 20px',
                  background: '#059669',
                  color: '#ffffff',
                  border: 'none',
                  borderRadius: '6px',
                  fontSize: '0.85rem',
                  fontWeight: 700,
                  cursor: 'pointer',
                }}
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
