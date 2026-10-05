import React, { useState } from 'react';
import { useManagementStore } from '../../features/management/useManagementStore';
import { ConsoleIcons } from '../../shared/layout/ConsoleIcons';
import {
  TableActionDropdown,
  BulkActionBar,
  TableCheckbox,
} from '../../shared/layout/ConsoleTableActions';

export const AdminBroadcastsPage: React.FC = () => {
  const { broadcasts, sendBroadcast } = useManagementStore();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [targetAudience, setTargetAudience] = useState<'ALL' | 'KTV_ONLY' | 'SELLER_ONLY'>('ALL');
  const [priority, setPriority] = useState<'NORMAL' | 'HIGH' | 'URGENT'>('HIGH');
  const [formError, setFormError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Multi-select state
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  const isAllSelected =
    broadcasts.length > 0 && broadcasts.every((b) => selectedIds.has(b.id));
  const isSomeSelected =
    broadcasts.some((b) => selectedIds.has(b.id)) && !isAllSelected;

  const handleToggleAll = () => {
    if (isAllSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(broadcasts.map((b) => b.id)));
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

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !content.trim()) {
      setFormError('Vui lòng nhập đầy đủ tiêu đề và nội dung thông báo.');
      return;
    }

    sendBroadcast({
      title: title.trim(),
      content: content.trim(),
      targetAudience,
      priority,
    });

    setSuccessMessage(`Đã phát thông báo "${title}" tới đối tượng được chọn thành công.`);
    setIsModalOpen(false);
    setTitle('');
    setContent('');
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', margin: '0 0 6px', color: '#0f172a', fontWeight: 800 }}>
            Điều hướng & Phát thông báo hệ thống
          </h1>
          <p style={{ margin: 0, color: '#64748b', fontSize: '0.92rem' }}>
            Soạn thảo và điều hướng thông báo đẩy tới Toàn bộ thành viên, Riêng đội ngũ KTV hoặc Riêng Kênh Người Bán.
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          {broadcasts.length > 0 && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <TableCheckbox
                checked={isAllSelected}
                indeterminate={isSomeSelected}
                onChange={handleToggleAll}
                ariaLabel="Chọn tất cả thông báo"
              />
              <span style={{ fontSize: '0.84rem', color: '#64748b' }}>Chọn tất cả</span>
            </div>
          )}

          <button
            type="button"
            onClick={() => {
              setTitle('');
              setContent('');
              setFormError(null);
              setIsModalOpen(true);
            }}
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
            <span>Soạn thông báo điều hướng</span>
          </button>
        </div>
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
        totalCount={broadcasts.length}
        onClearSelection={() => setSelectedIds(new Set())}
        actions={[
          {
            label: 'Thu hồi các bản tin đã chọn',
            icon: <ConsoleIcons.Lock size={15} />,
            variant: 'danger',
            onClick: () => {
              alert(`Đã thu hồi ${selectedIds.size} bản tin thông báo khỏi bảng tin người dùng.`);
              setSelectedIds(new Set());
            },
          },
        ]}
      />

      {/* Broadcasts List (Light Theme) */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        {broadcasts.map((item) => {
          const isSelected = selectedIds.has(item.id);
          return (
            <div
              key={item.id}
              style={{
                background: isSelected ? '#f0fdf4' : '#ffffff',
                border: '1px solid #e2e8f0',
                borderRadius: '12px',
                padding: '20px',
                display: 'flex',
                flexDirection: 'column',
                gap: '12px',
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
                      ariaLabel={`Chọn thông báo ${item.title}`}
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
                          background:
                            item.priority === 'URGENT'
                              ? '#fee2e2'
                              : item.priority === 'HIGH'
                              ? '#fef3c7'
                              : '#eff6ff',
                          color:
                            item.priority === 'URGENT'
                              ? '#b91c1c'
                              : item.priority === 'HIGH'
                              ? '#b45309'
                              : '#1d4ed8',
                          border: `1px solid ${
                            item.priority === 'URGENT'
                              ? '#fecaca'
                              : item.priority === 'HIGH'
                              ? '#fde68a'
                              : '#bfdbfe'
                          }`,
                        }}
                      >
                        {item.priority === 'URGENT'
                          ? 'KHẨN CẤP'
                          : item.priority === 'HIGH'
                          ? 'ƯU TIÊN CAO'
                          : 'TIÊU CHUẨN'}
                      </span>

                      <span
                        style={{
                          padding: '3px 8px',
                          borderRadius: '4px',
                          fontSize: '0.72rem',
                          fontWeight: 700,
                          background: '#f1f5f9',
                          color: '#475569',
                          border: '1px solid #cbd5e1',
                        }}
                      >
                        ĐỐI TƯỢNG:{' '}
                        {item.targetAudience === 'ALL'
                          ? 'TOÀN SÀN C2C'
                          : item.targetAudience === 'KTV_ONLY'
                          ? 'ĐỘI NGŨ KTV'
                          : 'NGƯỜI BÁN'}
                      </span>

                      <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                        Phát lúc: {item.sentAt} · Người gửi: {item.author}
                      </span>
                    </div>

                    <h3 style={{ margin: '0 0 6px 0', fontSize: '1.15rem', color: '#0f172a', fontWeight: 700 }}>
                      {item.title}
                    </h3>
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <TableActionDropdown
                    ariaLabel={`Tùy chọn thông báo ${item.title}`}
                    items={[
                      {
                        label: 'Gửi lại bản tin này',
                        icon: <ConsoleIcons.Send size={16} />,
                        onClick: () => {
                          alert(`Đã gửi lại thông báo: ${item.title}`);
                        },
                      },
                      {
                        label: 'Sao chép nội dung',
                        icon: <ConsoleIcons.FileText size={16} />,
                        onClick: () => {
                          navigator.clipboard?.writeText(item.content);
                          alert('Đã sao chép nội dung bản tin vào bộ nhớ tạm.');
                        },
                      },
                    ]}
                  />
                </div>
              </div>

              <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', padding: '14px', borderRadius: '8px', color: '#334155', fontSize: '0.9rem', lineHeight: '1.5' }}>
                {item.content}
              </div>
            </div>
          );
        })}
      </div>

      {/* Modal Broadcast (Light Theme) */}
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
                Soạn thảo thông báo điều hướng hệ thống
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

            <form onSubmit={handleSend} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                  Tiêu đề thông báo *
                </label>
                <input
                  type="text"
                  required
                  placeholder="Ví dụ: Cảnh báo bảo trì cổng thanh toán 02:00 sáng mai"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
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
                    Đối tượng điều hướng
                  </label>
                  <select
                    value={targetAudience}
                    onChange={(e) => setTargetAudience(e.target.value as 'ALL' | 'KTV_ONLY' | 'SELLER_ONLY')}
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
                    <option value="ALL">Toàn bộ sàn (Tất cả người dùng)</option>
                    <option value="KTV_ONLY">Chỉ đội ngũ Kỹ Thuật Viên (KTV)</option>
                    <option value="SELLER_ONLY">Chỉ kênh Người Bán (Sellers)</option>
                  </select>
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                    Mức độ ưu tiên
                  </label>
                  <select
                    value={priority}
                    onChange={(e) => setPriority(e.target.value as 'NORMAL' | 'HIGH' | 'URGENT')}
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
                    <option value="NORMAL">Tiêu chuẩn (Normal)</option>
                    <option value="HIGH">Ưu tiên cao (High)</option>
                    <option value="URGENT">Khẩn cấp (Urgent Pop-up)</option>
                  </select>
                </div>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.84rem', color: '#475569', marginBottom: '6px', fontWeight: 600 }}>
                  Nội dung chi tiết thông báo *
                </label>
                <textarea
                  rows={4}
                  required
                  placeholder="Nhập nội dung thông điệp gửi tới người dùng hoặc nhân sự..."
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
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
                  Phát thông báo ngay
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
