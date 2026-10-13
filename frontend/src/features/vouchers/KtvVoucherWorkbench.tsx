import { Badge, EmptyState, PageHeader, Pagination, Select } from '../../shared/components';
import { useCallback, useRef, useState } from 'react';
import { ConfirmDialog } from '../../shared/components/Dialog/ConfirmDialog';
import { voucherApi, benefit, type Bulk, type Batch } from './voucherApi';
import { useVoucherData, useVoucherClock } from './useVoucherData';
import './vouchers.css';

export function KtvVoucherWorkbench() {
  const campaigns = useVoucherData(useCallback(() => voucherApi.campaigns('ktv'), []));
  const [selectedCampaignId, setId] = useState<number | null>(null);
  const id = selectedCampaignId ?? campaigns.data?.[0]?.id ?? 0;
  const [action, setAction] = useState<Bulk['action']>('GRANT');
  const [query, setQuery] = useState(''), [role, setRole] = useState('ALL'), [status, setStatus] = useState('ACTIVE'), [eligibleOnly, setEligibleOnly] = useState(true), [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Set<string>>(new Set()), [reason, setReason] = useState('');
  const [preview, setPreview] = useState<{ body: Bulk; batch: Batch } | null>(null), [result, setResult] = useState<Batch | null>(null);
  const [feedback, setFeedback] = useState(''), [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const targets = useVoucherData(useCallback(() => id ? voucherApi.targets(id, { action, query, role, status, eligibleOnly: String(eligibleOnly), page: String(page) }) : Promise.resolve(null), [id, action, query, role, status, eligibleOnly, page]));
  function reset() { setSelected(new Set()); setPage(0); setPreview(null); setResult(null); setFeedback(''); }
  async function prepare() {
    if (!targets.data || pending.current) return;
    pending.current = true; setBusy(true); setFeedback(''); setResult(null);
    const body: Bulk = { commandId: crypto.randomUUID(), version: targets.data.campaign.version, action, ids: [...selected], reason: reason.trim() };
    try { const batch = await voucherApi.preview(id, body); setPreview({ body, batch }); }
    catch (e) { setFeedback(e instanceof Error ? e.message : 'Chưa xem trước được danh sách.'); }
    finally { setBusy(false); pending.current = false; }
  }
  async function execute() {
    if (!preview || pending.current) return;
    pending.current = true; setBusy(true); setFeedback('');
    try { const batch = await voucherApi.execute(id, preview.body); setResult(batch); setPreview(null); setSelected(new Set()); targets.reload(); campaigns.reload(); setFeedback(`Đã xử lý: ${batch.successful} thành công, ${batch.skipped} bỏ qua.`); }
    catch (e) { setFeedback(`${e instanceof Error ? e.message : 'Chưa xử lý được.'} Có thể xác nhận lại để thử lại cùng thao tác, hoặc đóng để xem trước lại.`); targets.reload(); }
    finally { setBusy(false); pending.current = false; }
  }
  const now = useVoucherClock();
  const rows = targets.data?.items ?? [];
  return <section className="og-vouchers og-ktv-vouchers">
    <PageHeader title="Cấp và thu hồi voucher" description="Lọc khách hàng, chọn tối đa 100 người và xem trước trước khi xác nhận." />
    {campaigns.loading && <p role="status">Đang tải chương trình…</p>}{campaigns.error && <p role="alert">{campaigns.error} <button onClick={campaigns.reload}>Thử lại</button></p>}
    <Select label="Chương trình voucher" searchable disabled={busy || !campaigns.data?.length} value={String(id)} onChange={value => { setId(Number(value)); reset(); }} options={(campaigns.data ?? []).map(c => {
      const expired = new Date(c.endTime).getTime() <= now, future = new Date(c.startTime).getTime() > now;
      return { value: String(c.id), label: c.code, description: c.title, badge: <Badge variant={!c.active || expired ? 'neutral' : future ? 'warning' : 'success'}>{!c.active ? 'Dừng phát' : expired ? 'Hết hạn' : future ? 'Sắp diễn ra' : 'Hoạt động'}</Badge> };
    })} />
    {!campaigns.loading && campaigns.data?.length === 0 && <p className="og-vouchers__empty">Admin chưa tạo chương trình voucher.</p>}
    {id > 0 && <>
      <div className="og-vouchers__tabs" role="group" aria-label="Thao tác voucher"><button disabled={busy} aria-pressed={action === 'GRANT'} onClick={() => { setAction('GRANT'); reset(); }}>Cấp voucher</button><button disabled={busy} aria-pressed={action === 'REVOKE'} onClick={() => { setAction('REVOKE'); reset(); }}>Thu hồi voucher</button></div>
      {targets.data && <p>{benefit(targets.data.campaign)} · Đã cấp {targets.data.campaign.issued}/{targets.data.campaign.quantity}. {action === 'REVOKE' && 'Chỉ thu hồi voucher chưa dùng và chưa hết hạn.'}</p>}
      <fieldset disabled={busy} style={{ border: 0, padding: 0, margin: 0 }}><div className="og-vouchers__filters">
        <label>Tên hoặc email<input value={query} onChange={e => { setQuery(e.target.value); reset(); }} placeholder="Tìm khách hàng…" /></label>
        <Select label="Vai trò" size="sm" disabled={busy} value={role} onChange={value => { setRole(value); reset(); }} options={[{value:'ALL',label:'Mọi khách hàng'},{value:'BUYER',label:'Người mua'},{value:'SELLER',label:'Người bán'}]} />
        <Select label="Tình trạng" size="sm" disabled={busy || (action === 'GRANT' && eligibleOnly)} value={status} onChange={value => { setStatus(value); reset(); }} options={[{value:'ACTIVE',label:'Hoạt động'},{value:'ALL',label:'Tất cả'},{value:'LOCKED',label:'Bị khóa'},{value:'SUSPENDED',label:'Tạm ngừng'},{value:'DEACTIVATED',label:'Ngừng hoạt động'}]} />
        {action === 'GRANT' && <label className="og-vouchers__check"><input type="checkbox" checked={eligibleOnly} onChange={e => { setEligibleOnly(e.target.checked); if (e.target.checked) setStatus('ACTIVE'); reset(); }} />Chỉ người đủ điều kiện</label>}
      </div></fieldset>
      {targets.loading && <p role="status">Đang lọc danh sách…</p>}{targets.error && <p role="alert">{targets.error} <button onClick={targets.reload}>Thử lại</button></p>}
      {targets.data && <>
        <div className="og-vouchers__actions"><button className="og-button og-button--secondary" disabled={busy || !rows.some(r => r.eligible)} onClick={() => setSelected(prev => new Set([...prev, ...rows.filter(r => r.eligible).map(r => r.id)].slice(0, 100)))}>Chọn người đủ điều kiện trên trang</button><button className="og-button og-button--secondary" disabled={busy || !selected.size} onClick={() => setSelected(new Set())}>Bỏ chọn</button><span>{targets.data.total} khách hàng phù hợp</span></div>
        <ul className="og-vouchers__rows">{rows.map(r => <li key={r.id}><input id={`voucher-row-${r.id}`} aria-label={`Chọn ${r.userName}`} type="checkbox" checked={selected.has(r.id)} disabled={busy || !r.eligible || (!selected.has(r.id) && selected.size >= 100)} onChange={e => setSelected(prev => { const next = new Set(prev); if (e.target.checked) next.add(r.id); else next.delete(r.id); return next; })} /><div><label htmlFor={`voucher-row-${r.id}`}><strong>{r.userName.split(' · ')[0]}</strong></label>{r.userName.includes(' · ') && <small>{r.userName.split(' · ').slice(1).join(' · ')}</small>}<Badge variant={r.eligible ? 'success' : 'neutral'}>{r.eligible ? 'Đủ điều kiện' : 'Không đủ điều kiện'}</Badge><small>{r.reason || ''}{action === 'REVOKE' ? ` · Voucher ${r.id.slice(0, 8)}` : ''}</small></div></li>)}</ul>
        {!rows.length && <EmptyState title="Không có khách hàng phù hợp" description="Thử thay đổi từ khóa hoặc bộ lọc." />}
        <Pagination total={targets.data.total} page={page} totalPages={Math.ceil(targets.data.total / targets.data.size)} disabled={busy} onPage={setPage} />
      </>}

      <p role="status">{feedback}</p>
      <div className="og-vouchers__sticky">      <label>Lý do {action === 'GRANT' ? 'cấp' : 'thu hồi'}<textarea maxLength={1000} disabled={busy} value={reason} onChange={e => setReason(e.target.value)} placeholder="Lý do được lưu trong lịch sử và thông báo cho khách hàng" /></label><strong>Đã chọn {selected.size}/100</strong><button className="og-button og-button--primary" disabled={busy || !selected.size || !reason.trim() || !targets.data} onClick={() => void prepare()}>{busy ? 'Đang kiểm tra…' : `Xem trước ${action === 'GRANT' ? 'cấp' : 'thu hồi'}`}</button></div>
    </>}
    {result && <div className="og-vouchers__panel"><h2>Kết quả thao tác</h2><ul className="og-vouchers__rows">{result.items.map(r => <li key={r.id}><div><strong>{r.userName || `Khách hàng #${r.userId}`}</strong><small>{r.status === 'SUCCESS' ? 'Thành công' : `Bỏ qua: ${r.reason}`}</small></div></li>)}</ul></div>}
    <ConfirmDialog isOpen={preview !== null} title={`Xác nhận ${action === 'GRANT' ? 'cấp' : 'thu hồi'} voucher`} confirmText={busy ? 'Đang xử lý…' : 'Xác nhận thao tác'} variant={action === 'REVOKE' ? 'danger' : 'primary'} isLoading={busy} onCancel={() => { if (!busy) setPreview(null); }} onConfirm={() => void execute()} message={<div><p>{preview?.batch.successful} đủ điều kiện · {preview?.batch.skipped} sẽ bỏ qua. Server kiểm tra lại trước khi xử lý.</p><p>Lý do: {preview?.body.reason}</p>{feedback && <p role="alert">{feedback}</p>}<ul className="og-vouchers__rows og-vouchers__result">{preview?.batch.items.map(r => <li key={r.id}><div><strong>{r.userName || `Khách hàng #${r.userId}`}</strong><small>{r.eligible ? 'Đủ điều kiện' : r.reason}</small></div></li>)}</ul></div>} />
  </section>;
}
