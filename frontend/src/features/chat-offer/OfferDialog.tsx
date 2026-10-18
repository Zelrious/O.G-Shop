import { useEffect, useRef, useState } from 'react';
import { Alert } from '../../shared/components';
import { chatApi, money, type ChatDetail, type ChatOffer } from './chatApi';
import '../listing/review.css';
interface Props { detail: ChatDetail; counter?: ChatOffer; onClose: () => void; onDone: (offer: ChatOffer) => void }
export function OfferDialog({ detail, counter, onClose, onDone }: Props) {
  const [price, setPrice] = useState(String(counter?.price ?? detail.product.price)), [end, setEnd] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState<string | null>(null);
  const dialog = useRef<HTMLDivElement>(null), inFlight = useRef(false), command = useRef<{ signature: string; key: string } | null>(null);
  useEffect(() => { const prior = document.activeElement as HTMLElement | null, overflow = document.body.style.overflow; document.body.style.overflow = 'hidden'; dialog.current?.focus(); return () => { document.body.style.overflow = overflow; prior?.focus(); }; }, []);
  const submit = async () => {
    if (inFlight.current) return; const value = Number(price);
    if (!/^[0-9]+$/.test(price) || !Number.isSafeInteger(value) || value < 1 || value > 999999999999999) { setError('Giá phải là số đồng nguyên dương, không dùng dấu phân cách.'); return; }
    let expiresAt: string | undefined;
    if (end) { const date = new Date(end); if (!Number.isFinite(date.getTime()) || date.getTime() <= Date.parse(detail.serverTime)) { setError('Hạn phản hồi phải ở tương lai.'); return; } expiresAt = date.toISOString(); }
    const payload = { price: value, expiresAt, version: counter?.version, offer: counter?.id, conversation: detail.conversation.id };
    const signature = JSON.stringify(payload); if (command.current?.signature !== signature) command.current = { signature, key: crypto.randomUUID() };
    inFlight.current = true; setBusy(true); setError(null);
    try { const result = counter ? await chatApi.decide(counter.id, { commandKey: command.current.key, action: 'COUNTER', expectedVersion: counter.version, price: value, expiresAt }) : await chatApi.propose(detail.conversation.id, { clientId: command.current.key, price: value, expiresAt }); onDone(result); }
    catch (e: unknown) { setError(e instanceof Error ? e.message : 'Chưa gửi được đề nghị.'); } finally { inFlight.current = false; setBusy(false); }
  };
  return <div className="og-modal-backdrop" onClick={() => { if (!busy) onClose(); }}><div ref={dialog} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby="offer-dialog-title" className="og-fee-dialog og-chat-offer-dialog" onClick={e => e.stopPropagation()} onKeyDown={e => {
    if (e.key === 'Escape' && !busy) onClose();
    if (e.key === 'Tab') { const controls = dialog.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), a[href]'); if (!controls?.length) return; const first = controls[0], last = controls[controls.length - 1]; if (e.shiftKey && (document.activeElement === first || document.activeElement === dialog.current)) { e.preventDefault(); last.focus(); } else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); } }
  }}><header><div><span className="og-eyebrow">THƯƠNG LƯỢNG</span><h2 id="offer-dialog-title">{counter ? 'Đề nghị giá khác' : 'Gửi đề nghị trả giá'}</h2></div><button className="og-button og-button--ghost" onClick={onClose} disabled={busy} aria-label="Đóng đề nghị">×</button></header>
    <p><strong>{detail.conversation.title}</strong></p><p>Giá niêm yết: {money(detail.product.price)} · Cho 1 món</p><form onSubmit={e => { e.preventDefault(); void submit(); }}>
      <label>Giá bạn đề nghị (đồng)<input type="number" inputMode="numeric" min="1" max="999999999999999" step="1" value={price} onChange={e => setPrice(e.target.value)} required disabled={busy} /></label>
      <label>Hạn phản hồi (tùy chọn)<input type="datetime-local" value={end} onChange={e => setEnd(e.target.value)} disabled={busy} /></label><small>Để trống nếu không đặt hạn phản hồi.</small>
      <Alert type="warning">Chấp nhận giá chưa giữ hàng hoặc tạo đơn. Khi đặt mua vẫn kiểm tra hàng và điều kiện giao dịch.</Alert>
      {error && <div role="alert"><Alert type="danger">{error}</Alert><p>Bản soạn được giữ để thử lại. Tải lại hội thoại nếu đối tác đã phản hồi.</p></div>}
      <footer><button type="button" className="og-button og-button--outline" onClick={onClose} disabled={busy}>Hủy</button><button className="og-button og-button--primary" disabled={busy || !price}>{busy ? 'Đang gửi...' : counter ? 'Gửi giá khác' : 'Gửi đề nghị'}</button></footer>
    </form></div></div>;
}
