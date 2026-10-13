import { useEffect, useState } from 'react';
import { Alert } from '../../shared/components';
import { accountApi, actionLabels, type AccountHistoryPage } from './accountApi';
export function AccountHistoryView({ userId }: { userId: number | null }) {
  const [page, setPage] = useState(0), [refresh, setRefresh] = useState(0);
  const [data, setData] = useState<AccountHistoryPage | null>(null), [loading, setLoading] = useState(true), [error, setError] = useState<string | null>(null);
  useEffect(() => {
    let ignore = false; setLoading(true); setError(null);
    accountApi.history(userId, page).then(value => { if (!ignore) setData(value); }).catch((e: unknown) => { if (!ignore) setError(e instanceof Error ? e.message : 'Chưa tải được lịch sử.'); }).finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, [userId, page, refresh]);
  return <section className="og-account-section"><h3>Lịch sử quyết định tài khoản</h3>{loading ? <p role="status">Đang tải lịch sử...</p> : error ? <Alert type="danger">{error}<button className="og-button og-button--ghost" onClick={() => setRefresh(n => n + 1)}>Thử lại lịch sử</button></Alert> : !data?.items.length ? <p>Chưa có quyết định được ghi nhận.</p> : <ol className="og-account-timeline">{data.items.map(h => <li key={h.id}><strong>{actionLabels[h.action] || h.action}</strong><span>KTV #{h.actorId} · {new Date(h.createdAt).toLocaleString('vi-VN')}</span><p>{h.request.reason}</p></li>)}</ol>}
    {data && data.totalElements > data.size && <nav aria-label="Trang lịch sử tài khoản" className="og-account-pagination"><button className="og-button og-button--outline" disabled={loading || page === 0} onClick={() => setPage(n => n - 1)}>Trang trước</button><span>{page + 1}/{Math.ceil(data.totalElements / data.size)}</span><button className="og-button og-button--outline" disabled={loading || (page + 1) * data.size >= data.totalElements} onClick={() => setPage(n => n + 1)}>Trang sau</button></nav>}</section>;
}
