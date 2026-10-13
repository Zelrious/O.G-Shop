import { Select } from '../../shared/components/Select/Select';
import { useCallback, useRef, useState } from 'react';
import { voucherApi, benefit, dateTime, type Campaign, type Save } from './voucherApi';
import { useVoucherData, useVoucherClock } from './useVoucherData';
import './vouchers.css';
const localDate = (iso: string) => { const d = new Date(iso); return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16); };
const initial = (): Save => ({ commandId: crypto.randomUUID(), version: 0, code: '', title: '', description: '', voucherType: 'ORDER_DISCOUNT', discountType: 'FIXED_AMOUNT', discountValue: 50000, maxDiscountAmount: null, minOrderAmount: 0, scope: 'CHECKOUT', productIds: [], quantity: 100, startTime: new Date(Date.now() - 60000).toISOString(), endTime: new Date(Date.now() + 30 * 86400000).toISOString(), active: true, selfClaim: true });

export function AdminVoucherWorkbench() {
  const now = useVoucherClock();
  const data = useVoucherData(useCallback(() => voucherApi.campaigns('admin'), []));
  const [query, setQuery] = useState(''), [status, setStatus] = useState('ALL');
  const [editor, setEditor] = useState<{ id: number | null; body: Save } | null>(null);
  const [busy, setBusy] = useState(false), [feedback, setFeedback] = useState('');
  const [products, setProducts] = useState('');
  const pending = useRef(false);
  function edit(c: Campaign) { setFeedback(''); setProducts(c.productIds.join(', ')); setEditor({ id: c.id, body: { ...c, commandId: crypto.randomUUID() } }); }
  function field<K extends keyof Save>(key: K, value: Save[K]) { setEditor(v => v ? { ...v, body: { ...v.body, [key]: value, commandId: crypto.randomUUID() } } : v); }
  async function save() {
    if (!editor || pending.current) return;
    pending.current = true; setBusy(true); setFeedback('');
    try { const c = await voucherApi.save(editor.id, editor.body); setFeedback(`Đã lưu ${c.code}. Chương trình ${c.active ? 'đang được bật' : 'đang dừng phát'}.`); setEditor(null); data.reload(); }
    catch (e) { setFeedback(e instanceof Error ? e.message : 'Chưa lưu được voucher.'); }
    finally { pending.current = false; setBusy(false); }
  }
  const body = editor?.body;
  return <section className="og-vouchers">
    <div className="og-vouchers__heading"><div><h1>Chương trình voucher</h1><p>Tạo chính sách ưu đãi. KTV cấp và thu hồi voucher trong ví khách hàng.</p></div><button className="og-button og-button--primary" disabled={busy} onClick={() => { setEditor({ id: null, body: initial() }); setProducts(''); setFeedback(''); }}>Tạo chương trình</button></div>
    {feedback && <p role="status">{feedback}</p>}
    {editor && body && <form className="og-vouchers__panel" onSubmit={e => { e.preventDefault(); void save(); }}>
      <h2>{editor.id === null ? 'Tạo voucher' : `Chỉnh sửa ${body.code}`}</h2><p>Thay đổi chỉ áp dụng cho lần nhận mới. Dừng phát vẫn giữ quyền dùng voucher đã cấp.</p>
      <fieldset disabled={busy} style={{ border: 0, padding: 0, margin: 0 }}><div className="og-vouchers__form">
        <label>Mã voucher<input required pattern="[A-Za-z0-9_-]{3,50}" maxLength={50} readOnly={editor.id !== null} value={body.code} onChange={e => field('code', e.target.value.toUpperCase())} /></label>
        <label>Tên chương trình<input required maxLength={200} value={body.title} onChange={e => field('title', e.target.value)} /></label>
        <label className="wide">Mô tả<textarea maxLength={2000} value={body.description || ''} onChange={e => field('description', e.target.value)} /></label>
        <label>Loại ưu đãi<Select hideLabel label="Loại ưu đãi" value={String(body.voucherType)} onChange={value => { field('voucherType', value as Save['voucherType']); if (value === 'SHIPPING_DISCOUNT') { field('scope', 'CHECKOUT'); field('productIds', []); } }} options={[({ value: "ORDER_DISCOUNT", label: "Giảm tiền hàng" }), ({ value: "SHIPPING_DISCOUNT", label: "Giảm phí vận chuyển" })]} /></label>
        <label>Cách giảm<Select hideLabel label="Cách giảm" value={String(body.discountType)} onChange={value => field('discountType', value as Save['discountType'])} options={[({ value: "FIXED_AMOUNT", label: "Số tiền cố định" }), ({ value: "PERCENTAGE", label: "Phần trăm" })]} /></label>
        <label>{body.discountType === 'PERCENTAGE' ? 'Giá trị giảm (%)' : 'Giá trị giảm (đ)'}<input type="number" required min={1} max={body.discountType === 'PERCENTAGE' ? 100 : undefined} step="1" value={body.discountValue} onChange={e => field('discountValue', Number(e.target.value))} /></label>
        <label>Giảm tối đa (đ)<input type="number" required={body.discountType === 'PERCENTAGE'} min={1} step="1" value={body.maxDiscountAmount ?? ''} onChange={e => field('maxDiscountAmount', e.target.value ? Number(e.target.value) : null)} /></label>
        <label>Đơn tối thiểu (đ)<input type="number" required min={0} step="1" value={body.minOrderAmount} onChange={e => field('minOrderAmount', Number(e.target.value))} /></label>
        <label>Số lượt phát<input type="number" required min={1} max={1000000} step="1" value={body.quantity} onChange={e => field('quantity', Number(e.target.value))} /></label>
        <label>Phạm vi<Select hideLabel label="Phạm vi" value={String(body.scope)} onChange={value => { field('scope', value as Save['scope']); if (value === 'CHECKOUT') field('productIds', []); }} options={[({ value: "CHECKOUT", label: "Toàn đơn" }), ({ value: "PRODUCTS", label: "Sản phẩm cụ thể" })]} disabled={body.voucherType === 'SHIPPING_DISCOUNT'} /></label>
        {body.scope === 'PRODUCTS' && <label>ID sản phẩm, cách nhau bằng dấu phẩy<input required pattern="\d+(\s*,\s*\d+)*" value={products} onChange={e => { setProducts(e.target.value); field('productIds', e.target.value.split(',').map(v => Number(v.trim())).filter(v => v > 0)); }} /><small>Server kiểm tra sản phẩm đang mở bán.</small></label>}
        <label>Bắt đầu<input type="datetime-local" required value={localDate(body.startTime)} onChange={e => { if (e.target.value) field('startTime', new Date(e.target.value).toISOString()); }} /></label>
        <label>Kết thúc<input type="datetime-local" required value={localDate(body.endTime)} onChange={e => { if (e.target.value) field('endTime', new Date(e.target.value).toISOString()); }} /></label>
        <label className="og-vouchers__check"><input type="checkbox" checked={body.active} onChange={e => field('active', e.target.checked)} />Bật nhận và cấp mới</label>
        <label className="og-vouchers__check"><input type="checkbox" checked={body.selfClaim} onChange={e => field('selfClaim', e.target.checked)} />Cho phép khách tự nhận</label>
      </div></fieldset><div className="og-vouchers__actions"><button className="og-button og-button--primary" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu chương trình'}</button><button type="button" className="og-button og-button--secondary" disabled={busy} onClick={() => setEditor(null)}>Đóng</button></div>
    </form>}
    <div className="og-vouchers__filters"><label>Tìm chương trình<input placeholder="Mã hoặc tên voucher" value={query} onChange={e => setQuery(e.target.value)} /></label><label>Trạng thái<Select hideLabel label="Trạng thái" value={String(status)} onChange={value => setStatus(value)} options={[({ value: "ALL", label: "Tất cả" }), ({ value: "ACTIVE", label: "Đang bật" }), ({ value: "PAUSED", label: "Dừng phát" }), ({ value: "EXPIRED", label: "Hết hạn" })]} /></label></div>
    {data.loading && <p role="status">Đang tải chương trình…</p>}{data.error && <p role="alert">{data.error} <button onClick={data.reload}>Thử lại</button></p>}
    <div className="og-vouchers__grid">{data.data?.filter(c => `${c.code} ${c.title}`.toLowerCase().includes(query.toLowerCase()) && (status === 'ALL' || (status === 'EXPIRED' ? new Date(c.endTime).getTime() <= now : status === 'ACTIVE' ? c.active : !c.active))).map(c => <article key={c.id} className="og-voucher-card"><span className="og-voucher-card__code">{c.code}</span><h2>{c.title}</h2><strong>{benefit(c)}</strong><p>{c.active ? 'Đang bật' : 'Dừng phát'} · {c.selfClaim ? 'Khách tự nhận' : 'KTV cấp trực tiếp'}</p><p>Đã cấp {c.issued}/{c.quantity} · Đã dùng {c.used}</p><p>{dateTime(c.startTime)} → {dateTime(c.endTime)}</p><button className="og-button og-button--secondary" disabled={busy} onClick={() => edit(c)}>Chỉnh sửa</button></article>)}</div>
    {data.data && !data.data.filter(c => `${c.code} ${c.title}`.toLowerCase().includes(query.toLowerCase()) && (status === 'ALL' || (status === 'EXPIRED' ? new Date(c.endTime).getTime() <= now : status === 'ACTIVE' ? c.active : !c.active))).length && <p className="og-vouchers__empty">Không có chương trình phù hợp.</p>}
  </section>;
}
