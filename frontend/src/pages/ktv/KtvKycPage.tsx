import '../../features/listing/review.css';
import { Alert, Button, Badge, EmptyState, PageHeader } from '../../shared/components';
import React, { useState } from 'react';
import { useManagementStore } from '../../features/management/useManagementStore';
import { ConsoleIcons } from '../../shared/layout/ConsoleIcons';
import {
  BulkActionBar,
  TableCheckbox,
} from '../../shared/layout/ConsoleTableActions';

export const KtvKycPage: React.FC = () => {
  const { kycList, approveKyc, rejectKyc } = useManagementStore();
  const [selectedKycId, setSelectedKycId] = useState<string | null>(null);
  const selectedKyc = kycList.find(item => item.id === selectedKycId) ?? kycList[0] ?? null;
  const [isRejecting, setIsRejecting] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Multi-select state
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  const isAllSelected =
    kycList.length > 0 && kycList.every((k) => selectedIds.has(k.id));
  const isSomeSelected =
    kycList.some((k) => selectedIds.has(k.id)) && !isAllSelected;

  const handleToggleAll = () => {
    if (isAllSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(kycList.map((k) => k.id)));
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

  const handleBulkApprove = () => {
    selectedIds.forEach((id) => {
      const item = kycList.find((k) => k.id === id);
      if (item && item.status === 'PENDING') {
        approveKyc(id);
      }
    });
    setSuccessMessage(`Đã phê duyệt định danh cho ${selectedIds.size} hồ sơ.`);
    setSelectedIds(new Set());
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  const handleApprove = (id: string, name: string) => {
    approveKyc(id);
    setSuccessMessage(`Đã phê duyệt eKYC cho người bán ${name} thành công.`);
    setSelectedKycId(null);
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  const handleConfirmReject = () => {
    if (!selectedKyc || !rejectReason.trim()) return;

    rejectKyc(selectedKyc.id, rejectReason.trim());
    setSuccessMessage(`Đã từ chối eKYC của ${selectedKyc.fullName}.`);
    setSelectedKycId(null);
    setIsRejecting(false);
    setRejectReason('');
    setTimeout(() => setSuccessMessage(null), 4000);
  };

  const badge = (status: string) => <Badge variant={status === 'PENDING' ? 'warning' : status === 'APPROVED' ? 'success' : 'danger'}>{status === 'PENDING' ? 'Chờ duyệt' : status === 'APPROVED' ? 'Đã phê duyệt' : 'Bị từ chối'}</Badge>;
  return <section className="og-ktv-workspace og-kyc-workspace">
    <PageHeader title="Kiểm duyệt định danh người bán (eKYC)" description="Chọn hồ sơ để đối chiếu thông tin định danh và tài liệu trước khi cấp quyền bán hàng." />
    {successMessage && <div role="status"><Alert type="success">{successMessage}</Alert></div>}
    <div className="og-review-columns">
      <aside className="og-review-queue" aria-label="Danh sách hồ sơ eKYC">
        <div className="og-review-queue__heading"><strong>{kycList.length} hồ sơ định danh</strong><label className="og-kyc-select-all"><TableCheckbox checked={isAllSelected} indeterminate={isSomeSelected} onChange={handleToggleAll} ariaLabel="Chọn tất cả hồ sơ" /><span>Chọn tất cả</span></label></div>
        <BulkActionBar selectedCount={selectedIds.size} totalCount={kycList.length} onClearSelection={() => setSelectedIds(new Set())} actions={[{ label: 'Duyệt eKYC đã chọn', variant: 'primary', onClick: handleBulkApprove }]} />
        {!kycList.length ? <EmptyState title="Không có hồ sơ định danh" description="Hồ sơ gửi mới sẽ xuất hiện tại đây." /> : <ul className="og-review-queue__list">{kycList.map(item => <li key={item.id} className={item.id === selectedKyc?.id ? 'is-selected' : ''}>
          <TableCheckbox checked={selectedIds.has(item.id)} onChange={() => handleToggleItem(item.id)} ariaLabel={`Chọn hồ sơ ${item.fullName}`} />
          <button type="button" className="og-review-queue__item og-kyc-row" aria-label={`Xem hồ sơ ${item.fullName}`} aria-current={item.id === selectedKyc?.id ? 'true' : undefined} onClick={() => { setSelectedKycId(item.id); setIsRejecting(false); setRejectReason(''); }}><span><strong>{item.fullName}</strong>{badge(item.status)}<small>Nộp lúc: {item.submittedAt}</small><small>CCCD: {item.cccdNumber.length > 8 ? `${item.cccdNumber.slice(0, 4)}••••${item.cccdNumber.slice(-4)}` : '••••'}</small></span></button>
        </li>)}</ul>}
      </aside>
      <article className="og-review-detail og-kyc-detail" aria-label="Hồ sơ eKYC được chọn">
        {selectedKyc ? <>
          <header className="og-review-detail__heading"><div>{badge(selectedKyc.status)}<h2>Hồ sơ định danh eKYC: {selectedKyc.fullName}</h2><p>Nộp lúc: {selectedKyc.submittedAt}</p></div></header>
          <div className="og-kyc-detail__body"><h3>Thông tin định danh</h3><dl className="og-kyc-fields"><div><dt>Số CCCD</dt><dd>{selectedKyc.cccdNumber}</dd></div><div><dt>Ngày sinh</dt><dd>{selectedKyc.dateOfBirth}</dd></div><div><dt>Địa chỉ</dt><dd>{selectedKyc.address}</dd></div></dl>
            <h3>Tài liệu đối chiếu</h3><div className="og-kyc-documents">{['CCCD mặt trước', 'CCCD mặt sau (chip)', 'Chân dung đối chiếu'].map((label, index) => <div key={label}><span>{label}</span><div aria-hidden="true">{index === 2 ? <ConsoleIcons.Users size={32} /> : <ConsoleIcons.IdCard size={32} />}</div><small>Chưa có bản xem ảnh</small></div>)}</div>
            {selectedKyc.rejectReason && <Alert type="danger">Lý do từ chối: {selectedKyc.rejectReason}</Alert>}
            {isRejecting && selectedKyc.status === 'PENDING' && <div className="og-kyc-rejection"><label htmlFor="kyc-reject-reason">Lý do từ chối hồ sơ *</label><textarea id="kyc-reject-reason" rows={3} value={rejectReason} onChange={e => setRejectReason(e.target.value)} placeholder="Nêu rõ thông tin hoặc tài liệu cần bổ sung…" /></div>}
          </div>
          {selectedKyc.status === 'PENDING' && <footer className="og-kyc-actions">{isRejecting ? <><Button type="button" variant="secondary" onClick={() => setIsRejecting(false)}>Quay lại</Button><Button type="button" variant="danger" disabled={!rejectReason.trim()} onClick={handleConfirmReject}>Xác nhận từ chối</Button></> : <><Button type="button" variant="danger-outline" onClick={() => setIsRejecting(true)}>Từ chối hồ sơ</Button><Button type="button" variant="primary" onClick={() => handleApprove(selectedKyc.id, selectedKyc.fullName)}>Phê duyệt eKYC</Button></>}</footer>}
        </> : <EmptyState title="Chọn hồ sơ để kiểm duyệt" description="Thông tin và tài liệu đối chiếu sẽ xuất hiện tại đây." />}
      </article>
    </div>
  </section>;
};
