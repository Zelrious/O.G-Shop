import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert } from '../../shared/components';
import { chatApi, money, offerLabels, type ChatOffer, type OfferAction } from './chatApi';
interface Props { offer: ChatOffer; productId: number; sellerView: boolean; onCounter: (offer: ChatOffer) => void; onChanged: () => void }
const actions: Record<OfferAction, string> = { ACCEPT: 'Chấp nhận giá', REJECT: 'Từ chối', COUNTER: 'Trả giá khác', WITHDRAW: 'Rút đề nghị' };
export function OfferCard({ offer, productId, sellerView, onCounter, onChanged }: Props) {
  const [busy, setBusy] = useState(false), [error, setError] = useState<string | null>(null), [confirm, setConfirm] = useState<OfferAction | null>(null);
  const active = useRef(false), key = useRef<{ action: OfferAction; version: number; id: string } | null>(null);
  const decide = async (action: OfferAction) => {
    if (active.current) return; if (key.current?.action !== action || key.current.version !== offer.version) key.current = { action, version: offer.version, id: crypto.randomUUID() };
    active.current = true; setBusy(true); setError(null);
    try { await chatApi.decide(offer.id, { action, expectedVersion: offer.version, commandKey: key.current.id }); setConfirm(null); onChanged(); } catch (e: unknown) { setError(e instanceof Error ? e.message : 'Chưa ghi nhận được phản hồi.'); } finally { active.current = false; setBusy(false); }
  };
  return <section className={'og-chat-offer-card og-chat-offer-card--' + offer.status.toLowerCase()} aria-label={'Đề nghị trả giá #' + offer.id}>
    <div className="og-chat-offer-top"><span className="og-eyebrow">ĐỀ NGHỊ GIÁ</span><span className="og-status-pill">{offerLabels[offer.status]}</span></div>
    <strong className="og-chat-offer-price">{money(offer.price)}<small>/ 1 món</small></strong>
    {offer.parentId && <small>Tiếp nối đề nghị #{offer.parentId}</small>}
    {offer.expiresAt && <p className="og-chat-offer-time">Hạn phản hồi: {new Date(offer.expiresAt).toLocaleString('vi-VN')}</p>}
    {offer.status === 'ACCEPTED' && <p>{offer.orderId ? `Giá đã được dùng cho đơn #${offer.orderId}.` : 'Giá dành riêng cho người mua trong hội thoại. Chưa giữ hàng.'}</p>}
    {offer.orderId ? <Link className="og-button og-button--outline" to={(sellerView ? '/seller/orders/' : '/orders/') + offer.orderId}>Xem đơn #{offer.orderId}</Link> : offer.canCheckout && <Link className="og-button og-button--primary" to={'/checkout?productId=' + productId + '&offerId=' + offer.id}>Đặt mua theo giá này</Link>}
    {offer.allowedActions.length > 0 && !confirm && <div className="og-chat-offer-actions">{offer.allowedActions.map(action => <button key={action} className={'og-button ' + (action === 'ACCEPT' ? 'og-button--primary' : 'og-button--outline')} disabled={busy} onClick={() => { if (action === 'COUNTER') onCounter(offer); else setConfirm(action); }}>{actions[action]}</button>)}</div>}
    {confirm && <div className="og-chat-offer-confirm"><p>{confirm === 'ACCEPT' ? 'Xác nhận chấp nhận giá này? Hàng chỉ được giữ khi đặt đơn thành công.' : confirm === 'REJECT' ? 'Xác nhận từ chối đề nghị?' : 'Xác nhận rút đề nghị chưa phản hồi?'}</p><div className="og-chat-offer-actions"><button className="og-button og-button--outline" disabled={busy} onClick={() => { setConfirm(null); setError(null); }}>Quay lại</button><button className="og-button og-button--primary" disabled={busy} onClick={() => void decide(confirm)}>{busy ? 'Đang ghi nhận...' : 'Xác nhận'}</button></div></div>}
    {error && <div role="alert"><Alert type="danger">{error}</Alert><button className="og-button og-button--ghost" disabled={busy} onClick={onChanged}>Tải lại hội thoại</button></div>}
  </section>;
}
