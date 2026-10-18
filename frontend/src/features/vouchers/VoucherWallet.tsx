import { useCallback, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Icon } from '../../shared/components/Icon/Icon';
import { benefit, dateTime, money, voucherApi, type Campaign } from './voucherApi';
import { useVoucherData, useVoucherClock } from './useVoucherData';
import './vouchers.css';

export function VoucherWallet() {
  const now = useVoucherClock();
  const data = useVoucherData(useCallback(() => voucherApi.wallet(), []));
  const [tab, setTab] = useState('AVAILABLE');
  const [busy, setBusy] = useState<number | null>(null);
  const [feedback, setFeedback] = useState('');
  const commands = useRef(new Map<number, string>());
  const [params] = useSearchParams();
  const product = params.get('productId'), offer = params.get('offerId');
  const back = product && /^\d+$/.test(product) ? `/checkout?productId=${product}${offer && /^\d+$/.test(offer) ? `&offerId=${offer}` : ''}` : '/marketplace';
  async function claim(c: Campaign) {
    const key = commands.current.get(c.id) ?? crypto.randomUUID(); commands.current.set(c.id, key);
    setBusy(c.id); setFeedback('');
    try { await voucherApi.claim(c, key); setFeedback(`Đã nhận ${c.code}. Voucher đã có trong ví.`); data.reload(); }
    catch (e) { setFeedback(e instanceof Error ? e.message : 'Chưa nhận được voucher.'); }
    finally { setBusy(null); }
  }
  return <section className="og-vouchers">
    <div className="og-vouchers__heading"><div><h1>Voucher và xu</h1><p>Nhận ưu đãi, chọn voucher trong ví khi thanh toán.</p></div><Link className="og-button og-button--secondary" to={back}>{product ? 'Trở lại thanh toán' : 'Khám phá món đồ'}</Link></div>
    {data.loading && <p role="status">Đang tải ví voucher…</p>}
    {data.error && <div role="alert">{data.error} <button className="og-button" onClick={data.reload}>Thử lại</button></div>}
    <p role="status">{feedback}</p>
    {data.data && <>
      <div className="og-vouchers__balance"><Icon name="credit" /><div><strong>{data.data.points.toLocaleString('vi-VN')} xu</strong><span>Số dư hiện tại</span></div></div>
      <div className="og-vouchers__tabs" role="group" aria-label="Lọc voucher trong ví">{[['AVAILABLE', 'Có thể dùng'], ['REDEEMED', 'Đã dùng'], ['EXPIRED', 'Hết hạn'], ['REVOKED', 'Đã thu hồi']].map(([id, label]) => <button key={id} aria-pressed={tab === id} onClick={() => setTab(id)}>{label}</button>)}</div>
      <div className="og-vouchers__grid">{data.data.grants.filter(g => (g.state === 'AVAILABLE' && new Date(g.expiresAt).getTime() <= now ? 'EXPIRED' : g.state) === tab).map(g => <article className="og-voucher-card" key={g.id}>
        <span className="og-voucher-card__code"><Icon name="tag" />{g.campaign.code}</span><h2>{benefit(g.campaign)}</h2><strong>{g.campaign.title}</strong><p>Đơn từ {money(g.campaign.minOrderAmount)} · {g.campaign.scope === 'CHECKOUT' ? 'Toàn đơn' : `Sản phẩm: ${g.campaign.productIds.join(', ')}`}</p><p>Hạn dùng: {dateTime(g.expiresAt)}</p>{tab === 'AVAILABLE' && <Link className="og-button og-button--secondary" to={back}>Chọn khi thanh toán</Link>}
      </article>)}</div>
      {!data.data.grants.some(g => (g.state === 'AVAILABLE' && new Date(g.expiresAt).getTime() <= now ? 'EXPIRED' : g.state) === tab) && <p className="og-vouchers__empty">Chưa có voucher trong mục này.</p>}
      <h2>Ưu đãi có thể nhận</h2><div className="og-vouchers__grid">{data.data.receivable.map(c => <article className="og-voucher-card" key={c.id}><span className="og-voucher-card__code"><Icon name="tag" />{c.code}</span><h3>{benefit(c)}</h3><strong>{c.title}</strong><p>{c.description}</p><p>Đơn từ {money(c.minOrderAmount)} · {c.scope === 'CHECKOUT' ? 'Toàn đơn' : `Sản phẩm: ${c.productIds.join(', ')}`}</p><p>Đến {dateTime(c.endTime)} · Còn {c.quantity - c.issued} lượt</p><button className="og-button og-button--primary" disabled={busy !== null} onClick={() => void claim(c)}>{busy === c.id ? 'Đang nhận…' : 'Nhận voucher'}</button></article>)}</div>
      {!data.data.receivable.length && <p className="og-vouchers__empty">Hiện chưa có chương trình mới để nhận.</p>}
      <details className="og-vouchers__history"><summary>Lịch sử xu gần đây</summary>{data.data.pointsHistory.length ? <ul>{data.data.pointsHistory.map((p, i) => <li key={i}><strong>{p.delta > 0 ? '+' : ''}{p.delta.toLocaleString('vi-VN')} xu</strong> · {p.reason || p.type} <small>{dateTime(p.createdAt)}</small></li>)}</ul> : <p>Chưa có giao dịch xu.</p>}</details>
    </>}
  </section>;
}
