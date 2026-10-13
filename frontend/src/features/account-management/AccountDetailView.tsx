import { accountStatusLabels, scopeLabels, type AccountDetail } from './accountApi';
import { Alert } from '../../shared/components';
import './accounts.css';
export function AccountDetailView({ detail }: { detail: AccountDetail }) {
  const u = detail.user;
  const openOrders = detail.orders.filter(o => o.open);
  return <>
    <header className="og-account-identity"><span className="og-account-avatar" aria-hidden="true">{u.fullName.slice(0, 1).toUpperCase()}</span><div><span className="og-status-pill">{accountStatusLabels[u.status] || u.status}</span><h2>{u.fullName}</h2><p>#{u.userId} · {u.roles.includes('SELLER') ? 'Người bán' : 'Người mua'} · {u.email}</p>{u.phoneNumber && <p>{u.phoneNumber}</p>}</div><div className="og-account-score"><strong>{u.reputation}<small>/100</small></strong><span>Điểm uy tín</span></div></header>
    {u.reputation < 90 && <div className="og-account-pad"><Alert type="warning">{u.reputation < 80 ? 'Uy tín dưới 80: KTV xem xét biện pháp phù hợp.' : 'Uy tín dưới 90: cần xem lịch sử và kiểm tra.'} Điểm cảnh báo không tự khóa hoặc xóa tài khoản.</Alert></div>}
    {detail.reason && u.status !== 'ACTIVE' && <div className="og-account-pad"><Alert type="warning">{detail.reason}{detail.expiresAt && <p>Hạn biện pháp: {new Date(detail.expiresAt).toLocaleString('vi-VN')}</p>}</Alert></div>}
    <section className="og-account-section"><div className="og-account-section-title"><h3>Hạn chế riêng và thời hạn</h3><span>{u.activeRestrictions} hạn chế riêng đang áp dụng</span></div>{!detail.restrictions.length ? <p>Chưa có hạn chế riêng theo chức năng.</p> : <ul className="og-account-restrictions">{detail.restrictions.map(r => {
      const active = !r.closedAt && (!r.expiresAt || Date.parse(r.expiresAt) > Date.parse(detail.serverTime));
      return <li key={r.id}><div><strong>{scopeLabels[r.scope] || r.scope}</strong><span className="og-status-pill">{r.closedAt ? 'Đã gỡ' : active ? 'Đang áp dụng' : 'Đã hết hạn'}</span></div><p>{r.reason}</p><small>KTV #{r.imposedBy} · Từ {new Date(r.openedAt).toLocaleString('vi-VN')}{r.expiresAt ? ` đến ${new Date(r.expiresAt).toLocaleString('vi-VN')}` : ' · Không thời hạn'}</small></li>;
    })}</ul>}</section>
    <section className="og-account-section"><div className="og-account-section-title"><h3>Nghĩa vụ và hồ sơ đơn cũ</h3><span>{openOrders.length} đơn mở trong dữ liệu hiển thị</span></div><p>Hiển thị tối đa 50 đơn, ưu tiên đơn đang mở. Biện pháp tài khoản giữ lịch sử, tiền và các nghĩa vụ đã có.</p>{openOrders.length ? <ul className="og-account-obligations">{openOrders.map(o => <li key={o.orderId}><strong>Đơn #{o.orderId}</strong><span>{o.role === 'SELLER' ? 'Bên bán' : 'Bên mua'} · {o.status}</span><span>{new Intl.NumberFormat('vi-VN').format(o.total)} ₫</span></li>)}</ul> : <p>Không có đơn đang mở trong dữ liệu hiển thị.</p>}
      {detail.cases.length ? <ul className="og-account-obligations">{detail.cases.map(c => <li key={c.caseId}><strong>Hồ sơ #{c.caseId} · {c.state}</strong><p>{c.summary}</p>{c.responseDeadline && <small>Hạn phản hồi: {new Date(c.responseDeadline).toLocaleString('vi-VN')}</small>}</li>)}</ul> : <p>Không có hồ sơ chính thức trong 50 hồ sơ gần nhất.</p>}</section>
    <section className="og-account-section"><h3>Thay đổi điểm uy tín</h3>{detail.reputation.events.length ? <ol className="og-account-timeline">{detail.reputation.events.map(e => <li key={e.id}><strong>{e.pointsDelta > 0 ? '+' : ''}{e.pointsDelta} điểm · {e.source === 'REVIEW' ? 'Đánh giá hợp lệ' : 'Quyết định xử phạt'}</strong><span>{new Date(e.createdAt).toLocaleString('vi-VN')}{e.decidedBy && ` · KTV #${e.decidedBy}`}</span><p>{e.reason}</p></li>)}</ol> : <p>Chưa có thay đổi; điểm bắt đầu 100. Điểm uy tín được quản lý riêng với xu mua hàng.</p>}</section>
  </>;
}
