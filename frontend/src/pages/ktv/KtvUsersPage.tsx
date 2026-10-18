import { Select } from '../../shared/components/Select/Select';
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Alert } from '../../shared/components';
import { accountApi, accountStatusLabels, type AccountAction, type AccountDetail, type AccountPage } from '../../features/account-management/accountApi';
import { AccountDetailView } from '../../features/account-management/AccountDetailView';
import { AccountHistoryView } from '../../features/account-management/AccountHistoryView';
import { AccountCommandDialog } from '../../features/account-management/AccountCommandDialog';
import '../../features/account-management/accounts.css';
export function KtvUsersPage() {
  const [params, setParams] = useSearchParams();
  const requestedId = Number(params.get('userId')) || null;
  const [query, setQuery] = useState(''), [status, setStatus] = useState('ALL'), [role, setRole] = useState('ALL');
  const [filter, setFilter] = useState({ query: '', status: 'ALL', role: 'ALL' });
  const [page, setPage] = useState(0), [refresh, setRefresh] = useState(0);
  const [data, setData] = useState<AccountPage | null>(null), [loading, setLoading] = useState(true), [error, setError] = useState<string | null>(null);
  const targetId = requestedId ?? data?.items[0]?.userId;
  const [detail, setDetail] = useState<AccountDetail | null>(null), [detailLoading, setDetailLoading] = useState(false), [detailError, setDetailError] = useState<string | null>(null);
  const [action, setAction] = useState<AccountAction | null>(null), [success, setSuccess] = useState<string | null>(null);
  const shownDetail = detail?.user.userId === targetId ? detail : null;
  useEffect(() => {
    let ignore = false; setLoading(true); setError(null);
    accountApi.list(filter.query, filter.status, filter.role, page).then(value => { if (!ignore) setData(value); }).catch((e: unknown) => { if (!ignore) setError(e instanceof Error ? e.message : 'Chưa tải được danh sách.'); }).finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, [filter, page, refresh]);
  useEffect(() => {
    setAction(null); setDetail(null); setDetailError(null);
    if (!targetId) return;
    let ignore = false; setDetailLoading(true);
    accountApi.detail(targetId).then(value => { if (!ignore) setDetail(value); }).catch((e: unknown) => { if (!ignore) setDetailError(e instanceof Error ? e.message : 'Chưa tải được hồ sơ.'); }).finally(() => { if (!ignore) setDetailLoading(false); });
    return () => { ignore = true; };
  }, [targetId, refresh]);
  return <section className="og-accounts"><header className="og-accounts-heading"><div><span className="og-eyebrow">KÊNH KỸ THUẬT VIÊN</span><h1>Quản lý tài khoản người dùng</h1><p>Đối chiếu lịch sử và nghĩa vụ trước khi quyết định biện pháp.</p></div><button className="og-button og-button--outline" onClick={() => setRefresh(n => n + 1)} disabled={loading}>Làm mới dữ liệu</button></header>
    {success && <div role="status"><Alert type="success">{success}</Alert></div>}
    <form className="og-accounts-filters" onSubmit={e => { e.preventDefault(); setFilter({ query, status, role }); setPage(0); setParams({}); setSuccess(null); }}>
      <label>Tìm tài khoản<input type="search" maxLength={100} placeholder="Tên, email hoặc số điện thoại" value={query} onChange={e => setQuery(e.target.value)} /></label>
      <label>Trạng thái<Select hideLabel label="Trạng thái" value={String(status)} onChange={value => setStatus(value)} options={[({ value: "ALL", label: "Tất cả trạng thái" }), ...Object.entries(accountStatusLabels).map(([value, label]) => ({ value: String(value), label: label }))]} /></label>
      <label>Vai trò<Select hideLabel label="Vai trò" value={String(role)} onChange={value => setRole(value)} options={[({ value: "ALL", label: "Người mua và Người bán" }), ({ value: "BUYER", label: "Người mua" }), ({ value: "SELLER", label: "Người bán" })]} /></label>
      <button className="og-button og-button--primary" disabled={loading}>Áp dụng bộ lọc</button>
    </form>
    {error && <Alert type="danger">{error}<button className="og-button og-button--ghost" onClick={() => setRefresh(n => n + 1)}>Thử lại danh sách</button></Alert>}
    <div className="og-accounts-grid"><aside className="og-account-list" aria-label="Danh sách tài khoản"><div className="og-account-list__top"><strong>{data?.totalElements ?? 0} tài khoản</strong><small>Người mua / Người bán</small></div>
      {loading ? <p className="og-account-section" role="status">Đang tải tài khoản...</p> : error ? null : !data?.items.length ? <div className="og-account-section"><h3>Không có tài khoản phù hợp</h3><p>Thử thay bộ lọc hoặc từ khóa.</p></div> : <ul>{data.items.map(u => <li key={u.userId}><button className="og-account-row" aria-label={`Xem tài khoản ${u.fullName} #${u.userId}`} aria-current={u.userId === targetId} onClick={() => { setParams({ userId: String(u.userId) }); setSuccess(null); }}>
        <span className="og-account-avatar" aria-hidden="true">{u.fullName.slice(0, 1)}</span><span><strong>{u.fullName}</strong><small>{u.email}</small><small>{accountStatusLabels[u.status] || u.status} · {u.activeRestrictions} hạn chế</small></span><span className="og-account-row__score" aria-label={`Uy tín ${u.reputation} trên 100`}>{u.reputation}</span></button></li>)}</ul>}
      {data && data.totalElements > data.size && <nav className="og-account-pagination" aria-label="Trang danh sách user"><button className="og-button og-button--ghost" disabled={loading || page === 0} onClick={() => setPage(n => n - 1)}>Trang trước</button><span>{page + 1}/{Math.ceil(data.totalElements / data.size)}</span><button className="og-button og-button--ghost" disabled={loading || (page + 1) * data.size >= data.totalElements} onClick={() => setPage(n => n + 1)}>Trang sau</button></nav>}
    </aside><article className="og-account-detail" aria-label="Hồ sơ tài khoản được chọn" aria-busy={detailLoading}>
      {detailLoading ? <p className="og-account-section" role="status">Đang tải hồ sơ...</p> : detailError ? <div className="og-account-section"><Alert type="danger">{detailError}<button className="og-button og-button--outline" onClick={() => setRefresh(n => n + 1)}>Thử lại hồ sơ</button></Alert></div> : shownDetail ? <>
        <div className="og-account-toolbar"><span>Hồ sơ #{shownDetail.user.userId}</span><button className="og-button og-button--primary" onClick={() => setAction(shownDetail.allowedActions[0])} disabled={loading || !shownDetail.allowedActions.length}>Quyết định biện pháp</button></div>
        <AccountDetailView detail={shownDetail} /><AccountHistoryView key={`${shownDetail.user.userId}:${shownDetail.user.version}`} userId={shownDetail.user.userId} />
      </> : <div className="og-account-section"><h3>Chọn tài khoản để xem hồ sơ</h3><p>Hạn chế, uy tín và nghĩa vụ sẽ hiển thị ở đây.</p></div>}
    </article></div>
    {shownDetail && action && <AccountCommandDialog detail={shownDetail} initialAction={action} onClose={() => setAction(null)} onDone={message => { setAction(null); setSuccess(message); setRefresh(n => n + 1); }} />}
  </section>;
}
