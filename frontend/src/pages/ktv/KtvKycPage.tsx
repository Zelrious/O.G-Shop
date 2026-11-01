import '../../features/listing/review.css';
import { Alert, Button, Badge, EmptyState, PageHeader } from '../../shared/components';
import React, { useState, useEffect, useCallback } from 'react';
import { useManagementStore } from '../../features/management/useManagementStore';
import { ConsoleIcons } from '../../shared/layout/ConsoleIcons';
import {
  BulkActionBar,
  TableCheckbox,
} from '../../shared/layout/ConsoleTableActions';
import { ekycKtvApi, KtvEkycDetail, SecureAssetImage } from '../../features/verification';

interface NormalizedKycItem {
  id: string;
  profileId?: number;
  fullName: string;
  status: string;
  submittedAt: string;
  cccdNumber: string;
  dateOfBirth: string;
  address: string;
  rejectReason?: string;
  frontAssetId?: number;
  backAssetId?: number;
  liveAssetId?: number;
  portraitAssetId?: number;
  distance?: number;
  confidence?: number;
}

export const KtvKycPage: React.FC = () => {
  const { kycList: mockKycList, approveKyc: mockApprove, rejectKyc: mockReject } = useManagementStore();
  const [items, setItems] = useState<NormalizedKycItem[]>(() => {
    return mockKycList.map((m) => ({
      id: m.id,
      fullName: m.fullName,
      status: m.status,
      submittedAt: m.submittedAt,
      cccdNumber: m.cccdNumber,
      dateOfBirth: m.dateOfBirth,
      address: m.address,
      rejectReason: m.rejectReason,
    }));
  });
  const [selectedKycId, setSelectedKycId] = useState<string | null>(null);
  const [isRejecting, setIsRejecting] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [serverDetail, setServerDetail] = useState<KtvEkycDetail | null>(null);

  // Multi-select state
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  // Load from server, fallback to mock
  const fetchServerQueue = useCallback(async () => {
    try {
      const page = await ekycKtvApi.getQueue(0, 50);
      if (page && Array.isArray(page.items)) {
        const mapped: NormalizedKycItem[] = page.items.map((item) => ({
          id: `server-${item.profileId}`,
          profileId: item.profileId,
          fullName: item.fullName,
          status: item.state === 'PENDING' ? 'PENDING' : item.state === 'VERIFIED' ? 'APPROVED' : 'REJECTED',
          submittedAt: item.submittedAt ? new Date(item.submittedAt).toLocaleString('vi-VN') : '',
          cccdNumber: item.maskedCccdNumber || '',
          dateOfBirth: '',
          address: '',
          distance: item.matchDistance,
        }));
        setItems(mapped);
        return;
      }
    } catch {
      // Fallback to mock in test environment or offline
      const mappedMock: NormalizedKycItem[] = mockKycList.map((m) => ({
        id: m.id,
        fullName: m.fullName,
        status: m.status,
        submittedAt: m.submittedAt,
        cccdNumber: m.cccdNumber,
        dateOfBirth: m.dateOfBirth,
        address: m.address,
        rejectReason: m.rejectReason,
      }));
      setItems(mappedMock);
    }
  }, [mockKycList]);

  useEffect(() => {
    fetchServerQueue();
  }, [fetchServerQueue]);

  // When selectedKyc changes, if it has profileId, fetch real detail from server
  const selectedKyc = items.find((item) => item.id === selectedKycId) ?? items[0] ?? null;

  useEffect(() => {
    if (selectedKyc?.profileId) {
      ekycKtvApi.getDetail(selectedKyc.profileId)
        .then((detail) => {
          setServerDetail(detail);
        })
        .catch(() => {
          setServerDetail(null);
        });
    } else {
      setServerDetail(null);
    }
  }, [selectedKyc?.profileId]);

  const isAllSelected = items.length > 0 && items.every((k) => selectedIds.has(k.id));
  const isSomeSelected = items.some((k) => selectedIds.has(k.id)) && !isAllSelected;

  const handleToggleAll = () => {
    if (isAllSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(items.map((k) => k.id)));
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

  const handleBulkApprove = async () => {
    let approvedCount = 0;
    for (const id of selectedIds) {
      const item = items.find((k) => k.id === id);
      if (item && item.status === 'PENDING') {
        if (item.profileId) {
          try {
            await ekycKtvApi.approve(item.profileId, 'Đã đối chiếu thông tin hợp lệ');
          } catch {
            // continue
          }
        }
        mockApprove(id);
        approvedCount++;
      }
    }
    setSuccessMessage(`Đã phê duyệt định danh cho ${approvedCount} hồ sơ.`);
    setSelectedIds(new Set());
    await fetchServerQueue();
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  const handleApprove = async (id: string, name: string) => {
    setErrorMessage(null);
    const item = items.find((k) => k.id === id);
    if (item?.profileId) {
      try {
        await ekycKtvApi.approve(item.profileId, 'Đã đối chiếu thông tin hợp lệ');
      } catch (err: unknown) {
        setErrorMessage(err instanceof Error ? err.message : 'Phê duyệt thất bại.');
        await fetchServerQueue();
        return;
      }
    }
    mockApprove(id);
    setSuccessMessage(`Đã phê duyệt eKYC cho người bán ${name} thành công.`);
    setSelectedKycId(null);
    await fetchServerQueue();
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  const handleConfirmReject = async () => {
    if (!selectedKyc || !rejectReason.trim()) return;

    setErrorMessage(null);
    if (selectedKyc.profileId) {
      try {
        await ekycKtvApi.reject(selectedKyc.profileId, rejectReason.trim());
      } catch (err: unknown) {
        setErrorMessage(err instanceof Error ? err.message : 'Từ chối thất bại.');
        await fetchServerQueue();
        return;
      }
    }
    mockReject(selectedKyc.id, rejectReason.trim());
    setSuccessMessage(`Đã từ chối eKYC của ${selectedKyc.fullName}.`);
    setSelectedKycId(null);
    setIsRejecting(false);
    setRejectReason('');
    await fetchServerQueue();
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  const badge = (status: string) => (
    <Badge variant={status === 'PENDING' ? 'warning' : status === 'APPROVED' ? 'success' : 'danger'}>
      {status === 'PENDING' ? 'Chờ duyệt' : status === 'APPROVED' ? 'Đã phê duyệt' : 'Bị từ chối'}
    </Badge>
  );

  const displayCccd = serverDetail?.submittedData?.cccdNumber || selectedKyc?.cccdNumber || '';
  const displayDob = serverDetail?.submittedData?.dob || selectedKyc?.dateOfBirth || '';
  const displayAddress = serverDetail?.submittedData?.address || selectedKyc?.address || '';

  return (
    <section className="og-ktv-workspace og-kyc-workspace">
      <PageHeader
        title="Kiểm duyệt định danh người bán (eKYC)"
        description="Chọn hồ sơ để đối chiếu thông tin định danh và tài liệu trước khi cấp quyền bán hàng."
      />
      {successMessage && <div role="status"><Alert type="success">{successMessage}</Alert></div>}
      {errorMessage && <div role="status"><Alert type="danger">{errorMessage}</Alert></div>}

      <div className="og-review-columns">
        <aside className="og-review-queue" aria-label="Danh sách hồ sơ eKYC">
          <div className="og-review-queue__heading">
            <strong>{items.length} hồ sơ định danh</strong>
            <label className="og-kyc-select-all">
              <TableCheckbox checked={isAllSelected} indeterminate={isSomeSelected} onChange={handleToggleAll} ariaLabel="Chọn tất cả hồ sơ" />
              <span>Chọn tất cả</span>
            </label>
          </div>
          <BulkActionBar
            selectedCount={selectedIds.size}
            totalCount={items.length}
            onClearSelection={() => setSelectedIds(new Set())}
            actions={[{ label: 'Duyệt eKYC đã chọn', variant: 'primary', onClick: handleBulkApprove }]}
          />
          {!items.length ? (
            <EmptyState title="Hiện không có hồ sơ eKYC nào đang chờ duyệt." description="Hồ sơ gửi mới sẽ xuất hiện tại đây." />
          ) : (
            <ul className="og-review-queue__list">
              {items.map((item) => (
                <li key={item.id} className={item.id === selectedKyc?.id ? 'is-selected' : ''}>
                  <TableCheckbox
                    checked={selectedIds.has(item.id)}
                    onChange={() => handleToggleItem(item.id)}
                    ariaLabel={`Chọn hồ sơ ${item.fullName}`}
                  />
                  <button
                    type="button"
                    className="og-review-queue__item og-kyc-row"
                    aria-label={`Xem hồ sơ ${item.fullName}`}
                    aria-current={item.id === selectedKyc?.id ? 'true' : undefined}
                    onClick={() => { setSelectedKycId(item.id); setIsRejecting(false); setRejectReason(''); }}
                  >
                    <span>
                      <strong>{item.fullName}</strong>
                      {badge(item.status)}
                      <small>Nộp lúc: {item.submittedAt}</small>
                      <small>
                        {item.cccdNumber.includes('••••')
                          ? `CCCD: ${item.cccdNumber}`
                          : `CCCD: ${item.cccdNumber.length > 8 ? `${item.cccdNumber.slice(0, 4)}••••${item.cccdNumber.slice(-4)}` : '••••'}`}
                      </small>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </aside>

        <article className="og-review-detail og-kyc-detail" aria-label="Hồ sơ eKYC được chọn">
          {selectedKyc ? (
            <>
              <header className="og-review-detail__heading">
                <div>
                  {badge(selectedKyc.status)}
                  <h2>Hồ sơ định danh eKYC: {selectedKyc.fullName}</h2>
                  <p>Nộp lúc: {selectedKyc.submittedAt}</p>
                </div>
              </header>

              <div className="og-kyc-detail__body">
                <h3>Thông tin định danh</h3>
                <dl className="og-kyc-fields">
                  <div>
                    <dt>Số CCCD</dt>
                    <dd>{displayCccd}</dd>
                  </div>
                  <div>
                    <dt>Ngày sinh</dt>
                    <dd>{displayDob}</dd>
                  </div>
                  <div>
                    <dt>Địa chỉ</dt>
                    <dd>{displayAddress}</dd>
                  </div>
                  {serverDetail?.matchDistance !== undefined && (
                    <div>
                      <dt>Độ lệch Cosine AI</dt>
                      <dd>{serverDetail.matchDistance} (&le; {serverDetail.thresholdUsed ? serverDetail.thresholdUsed.toFixed(2) : '0.68'} đạt)</dd>
                    </div>
                  )}
                  {serverDetail?.confidenceScore !== undefined && (
                    <div>
                      <dt>Độ tin cậy sinh trắc học</dt>
                      <dd>{(serverDetail.confidenceScore * 100).toFixed(1)}%</dd>
                    </div>
                  )}
                </dl>

                <h3>Tài liệu đối chiếu</h3>
                <div className="og-kyc-documents">
                  {/* CCCD mặt trước */}
                  <div>
                    <span>CCCD mặt trước</span>
                    <SecureAssetImage
                      assetId={serverDetail?.frontAssetId}
                      alt="CCCD mặt trước"
                      style={{ width: '100%', height: '140px', objectFit: 'contain', borderRadius: '4px' }}
                      fallbackIcon={<ConsoleIcons.IdCard size={32} />}
                      fallbackText="Chưa có ảnh mặt trước"
                    />
                  </div>

                  {/* CCCD mặt sau */}
                  <div>
                    <span>CCCD mặt sau (chip)</span>
                    <SecureAssetImage
                      assetId={serverDetail?.backAssetId}
                      alt="CCCD mặt sau"
                      style={{ width: '100%', height: '140px', objectFit: 'contain', borderRadius: '4px' }}
                      fallbackIcon={<ConsoleIcons.IdCard size={32} />}
                      fallbackText="Chưa có ảnh mặt sau"
                    />
                  </div>

                  {/* Ảnh thẻ chân dung 3x4 */}
                  <div>
                    <span style={{ fontWeight: 600, color: 'var(--og-color-primary)' }}>Ảnh thẻ chân dung 3x4</span>
                    <SecureAssetImage
                      assetId={serverDetail?.portraitAssetId}
                      alt="Ảnh thẻ chân dung 3x4"
                      style={{ width: '100%', height: '140px', objectFit: 'contain', borderRadius: '4px' }}
                      fallbackIcon={<ConsoleIcons.Users size={32} />}
                      fallbackText="Chưa có ảnh thẻ"
                    />
                  </div>

                  {/* Chân dung đối chiếu (Webcam) */}
                  <div>
                    <span>Chân dung đối chiếu (Webcam)</span>
                    <SecureAssetImage
                      assetId={serverDetail?.liveAssetId}
                      alt="Chân dung đối chiếu"
                      style={{ width: '100%', height: '140px', objectFit: 'contain', borderRadius: '4px' }}
                      fallbackIcon={<ConsoleIcons.Users size={32} />}
                      fallbackText="Chưa có ảnh chân dung"
                    />
                  </div>

                  {/* Hồ sơ eKYC cũ (mẫu sinh trắc học tham chiếu) - chỉ hiển thị khi có revision > 1 */}
                  {serverDetail?.referenceAssetId && serverDetail?.referenceAssetUrl && (
                    <div style={{ borderTop: '2px dashed var(--og-color-border)', paddingTop: '16px', marginTop: '16px' }}>
                      <span style={{ fontWeight: 600, color: 'var(--og-color-primary)' }}>
                        📋 Hồ sơ eKYC cũ (mẫu sinh trắc học tham chiếu)
                      </span>
                      <SecureAssetImage
                        assetId={serverDetail.referenceAssetId}
                        alt="Hồ sơ eKYC cũ - Mẫu sinh trắc học"
                        style={{ width: '100%', height: '140px', objectFit: 'contain', borderRadius: '4px', border: '2px solid var(--og-color-primary)' }}
                        fallbackIcon={<ConsoleIcons.Users size={32} />}
                        fallbackText="Không tải được hồ sơ cũ"
                      />
                      <p style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', marginTop: '8px' }}>
                        Ảnh chân dung từ hồ sơ eKYC đã duyệt trước đó (revision {serverDetail.revisionNo - 1}). Dùng để đối chiếu khuôn mặt khi người dùng làm lại eKYC.
                      </p>
                    </div>
                  )}
                </div>

                {selectedKyc.rejectReason && (
                  <Alert type="danger">Lý do từ chối: {selectedKyc.rejectReason}</Alert>
                )}

                {isRejecting && selectedKyc.status === 'PENDING' && (
                  <div className="og-kyc-rejection">
                    <label htmlFor="kyc-reject-reason">Lý do từ chối hồ sơ *</label>
                    <textarea
                      id="kyc-reject-reason"
                      rows={3}
                      value={rejectReason}
                      onChange={(e) => setRejectReason(e.target.value)}
                      placeholder="Nêu rõ thông tin hoặc tài liệu cần bổ sung…"
                    />
                  </div>
                )}
              </div>

              {selectedKyc.status === 'PENDING' && (
                <footer className="og-kyc-actions">
                  {isRejecting ? (
                    <>
                      <Button type="button" variant="secondary" onClick={() => setIsRejecting(false)}>
                        Quay lại
                      </Button>
                      <Button
                        type="button"
                        variant="danger"
                        disabled={!rejectReason.trim()}
                        onClick={handleConfirmReject}
                      >
                        Xác nhận từ chối
                      </Button>
                    </>
                  ) : (
                    <>
                      <Button type="button" variant="danger-outline" onClick={() => setIsRejecting(true)}>
                        Từ chối hồ sơ
                      </Button>
                      <Button
                        type="button"
                        variant="primary"
                        onClick={() => handleApprove(selectedKyc.id, selectedKyc.fullName)}
                      >
                        Phê duyệt eKYC
                      </Button>
                    </>
                  )}
                </footer>
              )}
            </>
          ) : (
            <EmptyState
              title="Chọn hồ sơ để kiểm duyệt"
              description="Thông tin và tài liệu đối chiếu sẽ xuất hiện tại đây."
            />
          )}
        </article>
      </div>
    </section>
  );
};
