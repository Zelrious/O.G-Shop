import React, { useEffect, useState, useMemo } from 'react';
import { ConsoleIcons } from '../../shared/layout/ConsoleIcons';
import {
  fulfillmentAdminApi,
  ShipmentIncident,
  IncidentResolutionAction,
} from '../../features/fulfillment/fulfillmentAdminApi';

export const AdminFulfillmentIncidentsPage: React.FC = () => {
  const [incidents, setIncidents] = useState<ShipmentIncident[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Search & Filter
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [filterActionable, setFilterActionable] = useState<string>('ALL');

  // Modal State
  const [selectedIncident, setSelectedIncident] = useState<ShipmentIncident | null>(null);
  const [resolveAction, setResolveAction] = useState<IncidentResolutionAction>('DISMISS_AND_RESUME');
  const [adminNote, setAdminNote] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  const fetchIncidents = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await fulfillmentAdminApi.getIncidents();
      setIncidents(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Không thể tải danh sách sự cố vận đơn';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchIncidents();
  }, []);

  const handleOpenResolveModal = (incident: ShipmentIncident) => {
    setSelectedIncident(incident);
    setResolveAction('DISMISS_AND_RESUME');
    setAdminNote('');
  };

  const handleCloseModal = () => {
    if (isSubmitting) return;
    setSelectedIncident(null);
    setAdminNote('');
  };

  const handleConfirmResolve = async () => {
    if (!selectedIncident) return;
    try {
      setIsSubmitting(true);
      setError(null);
      const response = await fulfillmentAdminApi.resolveIncident(selectedIncident.orderId, {
        action: resolveAction,
        notes: adminNote.trim(),
      });
      setSuccessMessage(response.message || `Đã giải quyết sự cố cho đơn hàng #${selectedIncident.orderId}`);
      setSelectedIncident(null);
      setAdminNote('');
      await fetchIncidents();
      setTimeout(() => setSuccessMessage(null), 5000);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Lỗi khi xử lý sự cố';
      setError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  // Filtered incidents
  const filteredIncidents = useMemo(() => {
    return incidents.filter((item) => {
      const q = searchQuery.trim().toLowerCase();
      const matchQuery =
        !q ||
        item.orderId.toString().includes(q) ||
        (item.trackingNumber && item.trackingNumber.toLowerCase().includes(q)) ||
        (item.clientOrderCode && item.clientOrderCode.toLowerCase().includes(q)) ||
        (item.rawGhnStatus && item.rawGhnStatus.toLowerCase().includes(q)) ||
        (item.incidentNotes && item.incidentNotes.toLowerCase().includes(q));

      const matchFilter =
        filterActionable === 'ALL' ||
        (filterActionable === 'DAMAGE_LOST' &&
          ['damage', 'lost', 'scrap'].includes(item.rawGhnStatus?.toLowerCase() || '')) ||
        (filterActionable === 'EXCEPTION' &&
          ['exception', 'delivery_fail', 'return'].includes(item.rawGhnStatus?.toLowerCase() || '')) ||
        (filterActionable === 'TIMEOUT' && item.reconciliationAttempts > 0);

      return matchQuery && matchFilter;
    });
  }, [incidents, searchQuery, filterActionable]);

  // Metric counts
  const totalIncidents = incidents.length;
  const damageLostCount = incidents.filter((i) =>
    ['damage', 'lost', 'scrap'].includes(i.rawGhnStatus?.toLowerCase() || '')
  ).length;
  const exceptionCount = incidents.filter((i) =>
    ['exception', 'delivery_fail', 'return'].includes(i.rawGhnStatus?.toLowerCase() || '')
  ).length;
  const timeoutCount = incidents.filter((i) => i.reconciliationAttempts > 0).length;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', margin: '0 0 6px', color: '#0f172a', fontWeight: 800 }}>
            Sự Cố Vận Đơn (GHN Incidents)
          </h1>
          <p style={{ margin: 0, color: '#64748b', fontSize: '0.92rem' }}>
            Hàng đợi kiểm tra và giải quyết các vận đơn gặp lỗi nghiêm trọng (hư hỏng, mất hàng, timeout kéo dài hoặc sai lệch trạng thái giao hàng).
          </p>
        </div>

        <button
          type="button"
          onClick={fetchIncidents}
          disabled={loading}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '8px',
            padding: '9px 16px',
            backgroundColor: '#f1f5f9',
            color: '#334155',
            border: '1px solid #cbd5e1',
            borderRadius: '8px',
            fontSize: '0.88rem',
            fontWeight: 600,
            cursor: loading ? 'not-allowed' : 'pointer',
            transition: 'background 0.15s ease',
          }}
        >
          <ConsoleIcons.Refresh size={16} />
          {loading ? 'Đang tải...' : 'Làm mới'}
        </button>
      </div>

      {/* Alerts */}
      {successMessage && (
        <div
          style={{
            padding: '12px 16px',
            backgroundColor: '#ecfdf5',
            color: '#065f46',
            border: '1px solid #a7f3d0',
            borderRadius: '8px',
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            fontSize: '0.9rem',
            fontWeight: 600,
          }}
        >
          <ConsoleIcons.CheckCircle size={18} />
          <span>{successMessage}</span>
        </div>
      )}

      {error && (
        <div
          style={{
            padding: '12px 16px',
            backgroundColor: '#fef2f2',
            color: '#991b1b',
            border: '1px solid #fecaca',
            borderRadius: '8px',
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            fontSize: '0.9rem',
            fontWeight: 600,
          }}
        >
          <ConsoleIcons.AlertTriangle size={18} />
          <span>{error}</span>
        </div>
      )}

      {/* Summary Stat Cards */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: '16px',
        }}
      >
        <div
          style={{
            backgroundColor: '#ffffff',
            border: '1px solid #e2e8f0',
            borderRadius: '12px',
            padding: '16px 20px',
            boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
          }}
        >
          <div style={{ color: '#64748b', fontSize: '0.82rem', fontWeight: 600, textTransform: 'uppercase', marginBottom: '6px' }}>
            Tổng sự cố cần duyệt
          </div>
          <div style={{ fontSize: '1.8rem', fontWeight: 800, color: '#dc2626' }}>
            {totalIncidents}
          </div>
        </div>

        <div
          style={{
            backgroundColor: '#ffffff',
            border: '1px solid #e2e8f0',
            borderRadius: '12px',
            padding: '16px 20px',
            boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
          }}
        >
          <div style={{ color: '#64748b', fontSize: '0.82rem', fontWeight: 600, textTransform: 'uppercase', marginBottom: '6px' }}>
            Hư hỏng / Mất hàng
          </div>
          <div style={{ fontSize: '1.8rem', fontWeight: 800, color: '#b91c1c' }}>
            {damageLostCount}
          </div>
        </div>

        <div
          style={{
            backgroundColor: '#ffffff',
            border: '1px solid #e2e8f0',
            borderRadius: '12px',
            padding: '16px 20px',
            boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
          }}
        >
          <div style={{ color: '#64748b', fontSize: '0.82rem', fontWeight: 600, textTransform: 'uppercase', marginBottom: '6px' }}>
            Giao thất bại / Bất thường
          </div>
          <div style={{ fontSize: '1.8rem', fontWeight: 800, color: '#d97706' }}>
            {exceptionCount}
          </div>
        </div>

        <div
          style={{
            backgroundColor: '#ffffff',
            border: '1px solid #e2e8f0',
            borderRadius: '12px',
            padding: '16px 20px',
            boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
          }}
        >
          <div style={{ color: '#64748b', fontSize: '0.82rem', fontWeight: 600, textTransform: 'uppercase', marginBottom: '6px' }}>
            Đối soát lặp lại &gt; 0 lần
          </div>
          <div style={{ fontSize: '1.8rem', fontWeight: 800, color: '#4f46e5' }}>
            {timeoutCount}
          </div>
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
          backgroundColor: '#ffffff',
          padding: '12px 16px',
          borderRadius: '10px',
          border: '1px solid #e2e8f0',
        }}
      >
        <div style={{ position: 'relative', minWidth: '280px', flex: 1 }}>
          <span style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: '#94a3b8' }}>
            <ConsoleIcons.Search size={16} />
          </span>
          <input
            type="text"
            placeholder="Tìm theo Mã đơn, Mã GHN, Mã Client, ghi chú..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{
              width: '100%',
              padding: '8px 12px 8px 36px',
              borderRadius: '6px',
              border: '1px solid #cbd5e1',
              fontSize: '0.88rem',
              outline: 'none',
              boxSizing: 'border-box',
            }}
          />
        </div>

        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <span style={{ fontSize: '0.85rem', color: '#64748b', fontWeight: 600 }}>Lọc phân loại:</span>
          <select
            value={filterActionable}
            onChange={(e) => setFilterActionable(e.target.value)}
            style={{
              padding: '8px 12px',
              borderRadius: '6px',
              border: '1px solid #cbd5e1',
              fontSize: '0.88rem',
              backgroundColor: '#fff',
              outline: 'none',
              cursor: 'pointer',
            }}
          >
            <option value="ALL">Tất cả sự cố ({totalIncidents})</option>
            <option value="DAMAGE_LOST">Hư hỏng / Mất hàng ({damageLostCount})</option>
            <option value="EXCEPTION">Bất thường / Trả hàng ({exceptionCount})</option>
            <option value="TIMEOUT">Đối soát quá hạn ({timeoutCount})</option>
          </select>
        </div>
      </div>

      {/* Incidents Table */}
      <div
        style={{
          backgroundColor: '#ffffff',
          borderRadius: '12px',
          border: '1px solid #e2e8f0',
          overflow: 'hidden',
          boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
        }}
      >
        {loading ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>
            <div style={{ marginBottom: '8px', fontWeight: 600 }}>Đang tải danh sách sự cố vận đơn...</div>
          </div>
        ) : filteredIncidents.length === 0 ? (
          <div style={{ padding: '64px 24px', textAlign: 'center', color: '#64748b' }}>
            <div style={{ color: '#10b981', display: 'flex', justifyContent: 'center', marginBottom: '12px' }}>
              <ConsoleIcons.CheckCircle size={48} />
            </div>
            <h3 style={{ margin: '0 0 6px', fontSize: '1.15rem', color: '#0f172a', fontWeight: 700 }}>
              Không có sự cố vận đơn nào cần xử lý
            </h3>
            <p style={{ margin: 0, fontSize: '0.88rem' }}>
              Toàn bộ các đơn hàng vận chuyển qua GHN đang vận hành trơn tru và đúng tiến độ.
            </p>
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.88rem' }}>
              <thead>
                <tr style={{ backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0', color: '#475569' }}>
                  <th style={{ padding: '12px 16px', fontWeight: 700 }}>Mã Đơn</th>
                  <th style={{ padding: '12px 16px', fontWeight: 700 }}>Mã Vận Đơn GHN</th>
                  <th style={{ padding: '12px 16px', fontWeight: 700 }}>Trạng Thái Nội Bộ</th>
                  <th style={{ padding: '12px 16px', fontWeight: 700 }}>Mã GHN Thô</th>
                  <th style={{ padding: '12px 16px', fontWeight: 700 }}>Số Lần Thử</th>
                  <th style={{ padding: '12px 16px', fontWeight: 700 }}>Ghi Chú Sự Cố</th>
                  <th style={{ padding: '12px 16px', fontWeight: 700 }}>Cập Nhật</th>
                  <th style={{ padding: '12px 16px', fontWeight: 700, textAlign: 'right' }}>Thao Tác</th>
                </tr>
              </thead>
              <tbody>
                {filteredIncidents.map((incident) => {
                  const isDamageOrLost = ['damage', 'lost', 'scrap'].includes(incident.rawGhnStatus?.toLowerCase() || '');
                  return (
                    <tr
                      key={incident.id}
                      style={{
                        borderBottom: '1px solid #f1f5f9',
                        transition: 'background 0.1s ease',
                      }}
                    >
                      <td style={{ padding: '14px 16px', fontWeight: 700, color: '#0f172a' }}>
                        #{incident.orderId}
                      </td>

                      <td style={{ padding: '14px 16px' }}>
                        <div style={{ fontWeight: 600, color: '#1e293b' }}>
                          {incident.trackingNumber || <span style={{ color: '#94a3b8' }}>Chưa có tracking</span>}
                        </div>
                        {incident.clientOrderCode && (
                          <div style={{ fontSize: '0.78rem', color: '#64748b' }}>
                            Client: {incident.clientOrderCode}
                          </div>
                        )}
                      </td>

                      <td style={{ padding: '14px 16px' }}>
                        <span
                          style={{
                            display: 'inline-block',
                            padding: '3px 8px',
                            borderRadius: '6px',
                            fontSize: '0.75rem',
                            fontWeight: 700,
                            backgroundColor:
                              incident.status === 'DELIVERED'
                                ? '#dcfce7'
                                : incident.status === 'RETURNED'
                                ? '#fef3c7'
                                : incident.status === 'FAILED'
                                ? '#fee2e2'
                                : '#e0f2fe',
                            color:
                              incident.status === 'DELIVERED'
                                ? '#166534'
                                : incident.status === 'RETURNED'
                                ? '#92400e'
                                : incident.status === 'FAILED'
                                ? '#991b1b'
                                : '#075985',
                          }}
                        >
                          {incident.status}
                        </span>
                      </td>

                      <td style={{ padding: '14px 16px' }}>
                        <span
                          style={{
                            display: 'inline-block',
                            padding: '3px 8px',
                            borderRadius: '6px',
                            fontSize: '0.75rem',
                            fontWeight: 700,
                            backgroundColor: isDamageOrLost ? '#fee2e2' : '#fef9c3',
                            color: isDamageOrLost ? '#991b1b' : '#854d0e',
                          }}
                        >
                          {incident.rawGhnStatus || 'N/A'}
                        </span>
                      </td>

                      <td style={{ padding: '14px 16px' }}>
                        <span
                          style={{
                            display: 'inline-block',
                            padding: '2px 8px',
                            borderRadius: '12px',
                            fontSize: '0.75rem',
                            fontWeight: 700,
                            backgroundColor: incident.reconciliationAttempts > 3 ? '#fee2e2' : '#f1f5f9',
                            color: incident.reconciliationAttempts > 3 ? '#dc2626' : '#475569',
                          }}
                        >
                          {incident.reconciliationAttempts} lần
                        </span>
                      </td>

                      <td style={{ padding: '14px 16px', maxWidth: '280px' }}>
                        <div
                          style={{
                            color: '#334155',
                            fontSize: '0.82rem',
                            lineHeight: 1.4,
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                          title={incident.incidentNotes || ''}
                        >
                          {incident.incidentNotes || <span style={{ color: '#94a3b8' }}>Không có ghi chú</span>}
                        </div>
                      </td>

                      <td style={{ padding: '14px 16px', color: '#64748b', fontSize: '0.8rem', whiteSpace: 'nowrap' }}>
                        {incident.updatedAt ? new Date(incident.updatedAt).toLocaleString('vi-VN') : '—'}
                      </td>

                      <td style={{ padding: '14px 16px', textAlign: 'right' }}>
                        <button
                          type="button"
                          onClick={() => handleOpenResolveModal(incident)}
                          style={{
                            padding: '6px 14px',
                            backgroundColor: '#2563eb',
                            color: '#ffffff',
                            border: 'none',
                            borderRadius: '6px',
                            fontSize: '0.82rem',
                            fontWeight: 600,
                            cursor: 'pointer',
                            transition: 'background 0.15s ease',
                          }}
                        >
                          Xử lý
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Modal Xử Lý Sự Cố */}
      {selectedIncident && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.6)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1050,
            padding: '16px',
          }}
        >
          <div
            style={{
              backgroundColor: '#ffffff',
              borderRadius: '14px',
              maxWidth: '600px',
              width: '100%',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
              overflow: 'hidden',
            }}
          >
            {/* Modal Header */}
            <div
              style={{
                padding: '18px 24px',
                borderBottom: '1px solid #e2e8f0',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
              }}
            >
              <div>
                <h3 style={{ margin: 0, fontSize: '1.2rem', fontWeight: 800, color: '#0f172a' }}>
                  Xử Lý Sự Cố Vận Đơn #{selectedIncident.orderId}
                </h3>
                <div style={{ fontSize: '0.82rem', color: '#64748b', marginTop: '2px' }}>
                  Vận đơn GHN: <strong>{selectedIncident.trackingNumber || selectedIncident.clientOrderCode}</strong>
                </div>
              </div>
              <button
                type="button"
                onClick={handleCloseModal}
                disabled={isSubmitting}
                style={{
                  background: 'none',
                  border: 'none',
                  fontSize: '1.25rem',
                  cursor: isSubmitting ? 'not-allowed' : 'pointer',
                  color: '#94a3b8',
                }}
              >
                ✕
              </button>
            </div>

            {/* Modal Body */}
            <div style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
              {/* Incident Context Info */}
              <div
                style={{
                  backgroundColor: '#f8fafc',
                  padding: '12px 16px',
                  borderRadius: '8px',
                  border: '1px solid #e2e8f0',
                  fontSize: '0.85rem',
                }}
              >
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', marginBottom: '8px' }}>
                  <div>
                    <span style={{ color: '#64748b' }}>Trạng thái GHN thô: </span>
                    <strong style={{ color: '#dc2626' }}>{selectedIncident.rawGhnStatus || 'N/A'}</strong>
                  </div>
                  <div>
                    <span style={{ color: '#64748b' }}>Lần thử đối soát: </span>
                    <strong>{selectedIncident.reconciliationAttempts} lần</strong>
                  </div>
                </div>
                {selectedIncident.incidentNotes && (
                  <div>
                    <span style={{ color: '#64748b' }}>Ghi chú sự cố gần nhất: </span>
                    <span style={{ color: '#334155', fontStyle: 'italic' }}>{selectedIncident.incidentNotes}</span>
                  </div>
                )}
              </div>

              {/* Action Selection */}
              <div>
                <label style={{ display: 'block', fontSize: '0.88rem', fontWeight: 700, color: '#0f172a', marginBottom: '8px' }}>
                  Chọn quyết định xử lý an toàn:
                </label>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  <label
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: '10px',
                      padding: '10px 14px',
                      borderRadius: '8px',
                      border: `1.5px solid ${resolveAction === 'DISMISS_AND_RESUME' ? '#2563eb' : '#e2e8f0'}`,
                      backgroundColor: resolveAction === 'DISMISS_AND_RESUME' ? '#eff6ff' : '#ffffff',
                      cursor: 'pointer',
                    }}
                  >
                    <input
                      type="radio"
                      name="resolveAction"
                      value="DISMISS_AND_RESUME"
                      checked={resolveAction === 'DISMISS_AND_RESUME'}
                      onChange={() => setResolveAction('DISMISS_AND_RESUME')}
                      style={{ marginTop: '3px' }}
                    />
                    <div>
                      <div style={{ fontWeight: 700, color: '#1e40af', fontSize: '0.88rem' }}>
                        Bỏ qua sự cố &amp; Khôi phục đối soát tự động (DISMISS_AND_RESUME)
                      </div>
                      <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '2px' }}>
                        Xóa cờ sự cố, đặt lại số lần thử đối soát về 0 để hệ thống tiếp tục tự động theo dõi hoặc nhận webhook.
                      </div>
                    </div>
                  </label>

                  <label
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: '10px',
                      padding: '10px 14px',
                      borderRadius: '8px',
                      border: `1.5px solid ${resolveAction === 'MARK_DELIVERED' ? '#16a34a' : '#e2e8f0'}`,
                      backgroundColor: resolveAction === 'MARK_DELIVERED' ? '#f0fdf4' : '#ffffff',
                      cursor: 'pointer',
                    }}
                  >
                    <input
                      type="radio"
                      name="resolveAction"
                      value="MARK_DELIVERED"
                      checked={resolveAction === 'MARK_DELIVERED'}
                      onChange={() => setResolveAction('MARK_DELIVERED')}
                      style={{ marginTop: '3px' }}
                    />
                    <div>
                      <div style={{ fontWeight: 700, color: '#166534', fontSize: '0.88rem' }}>
                        Xác nhận Đã giao hàng thành công (MARK_DELIVERED)
                      </div>
                      <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '2px' }}>
                        Đồng bộ vận đơn và đơn hàng sang DELIVERED sau khi Admin đã xác minh với GHN kiện hàng đã đến tay Buyer.
                      </div>
                    </div>
                  </label>

                  <label
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: '10px',
                      padding: '10px 14px',
                      borderRadius: '8px',
                      border: `1.5px solid ${resolveAction === 'MARK_RETURNED' ? '#d97706' : '#e2e8f0'}`,
                      backgroundColor: resolveAction === 'MARK_RETURNED' ? '#fffbeb' : '#ffffff',
                      cursor: 'pointer',
                    }}
                  >
                    <input
                      type="radio"
                      name="resolveAction"
                      value="MARK_RETURNED"
                      checked={resolveAction === 'MARK_RETURNED'}
                      onChange={() => setResolveAction('MARK_RETURNED')}
                      style={{ marginTop: '3px' }}
                    />
                    <div>
                      <div style={{ fontWeight: 700, color: '#92400e', fontSize: '0.88rem' }}>
                        Xác nhận Hoàn trả hàng về người bán (MARK_RETURNED)
                      </div>
                      <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '2px' }}>
                        Đánh dấu vận đơn RETURNED sau khi GHN hoàn trả bưu kiện về cho Seller.
                      </div>
                    </div>
                  </label>

                  <label
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: '10px',
                      padding: '10px 14px',
                      borderRadius: '8px',
                      border: `1.5px solid ${resolveAction === 'MARK_FAILED' ? '#dc2626' : '#e2e8f0'}`,
                      backgroundColor: resolveAction === 'MARK_FAILED' ? '#fef2f2' : '#ffffff',
                      cursor: 'pointer',
                    }}
                  >
                    <input
                      type="radio"
                      name="resolveAction"
                      value="MARK_FAILED"
                      checked={resolveAction === 'MARK_FAILED'}
                      onChange={() => setResolveAction('MARK_FAILED')}
                      style={{ marginTop: '3px' }}
                    />
                    <div>
                      <div style={{ fontWeight: 700, color: '#991b1b', fontSize: '0.88rem' }}>
                        Đánh dấu Thất bại / Hủy vận đơn (MARK_FAILED)
                      </div>
                      <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '2px' }}>
                        Vận đơn bị mất, hư hỏng không thể phục hồi hoặc bị hủy dứt điểm.
                      </div>
                    </div>
                  </label>
                </div>
              </div>

              {/* Admin Notes Textarea */}
              <div>
                <label style={{ display: 'block', fontSize: '0.88rem', fontWeight: 700, color: '#0f172a', marginBottom: '6px' }}>
                  Ghi chú xử lý của Admin:
                </label>
                <textarea
                  rows={3}
                  value={adminNote}
                  onChange={(e) => setAdminNote(e.target.value)}
                  placeholder="Nhập căn cứ xử lý, mã biên bản đối soát GHN hoặc nội dung làm việc với người dùng..."
                  style={{
                    width: '100%',
                    padding: '10px 12px',
                    borderRadius: '8px',
                    border: '1px solid #cbd5e1',
                    fontSize: '0.88rem',
                    outline: 'none',
                    boxSizing: 'border-box',
                    fontFamily: 'inherit',
                  }}
                />
              </div>
            </div>

            {/* Modal Footer */}
            <div
              style={{
                padding: '16px 24px',
                backgroundColor: '#f8fafc',
                borderTop: '1px solid #e2e8f0',
                display: 'flex',
                justifyContent: 'flex-end',
                gap: '12px',
              }}
            >
              <button
                type="button"
                onClick={handleCloseModal}
                disabled={isSubmitting}
                style={{
                  padding: '9px 18px',
                  backgroundColor: '#ffffff',
                  color: '#475569',
                  border: '1px solid #cbd5e1',
                  borderRadius: '8px',
                  fontSize: '0.88rem',
                  fontWeight: 600,
                  cursor: isSubmitting ? 'not-allowed' : 'pointer',
                }}
              >
                Hủy bỏ
              </button>

              <button
                type="button"
                onClick={handleConfirmResolve}
                disabled={isSubmitting}
                style={{
                  padding: '9px 20px',
                  backgroundColor: '#2563eb',
                  color: '#ffffff',
                  border: 'none',
                  borderRadius: '8px',
                  fontSize: '0.88rem',
                  fontWeight: 700,
                  cursor: isSubmitting ? 'not-allowed' : 'pointer',
                  opacity: isSubmitting ? 0.7 : 1,
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '8px',
                }}
              >
                {isSubmitting ? 'Đang thực hiện...' : 'Xác nhận xử lý'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
