import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../features/auth';
import { Alert } from '../shared/components';
import { accountApi, type AccountDetail } from '../features/account-management/accountApi';
import { AccountDetailView } from '../features/account-management/AccountDetailView';
import { AccountHistoryView } from '../features/account-management/AccountHistoryView';
import { CaseResponsePanel } from '../features/account-management/CaseResponsePanel';
import '../features/account-management/accounts.css';
export function AccountStatusPage() {
  const { user } = useAuth();
  const [data, setData] = useState<AccountDetail | null>(null), [loading, setLoading] = useState(true), [error, setError] = useState<string | null>(null), [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let ignore = false; setLoading(true); setError(null); setData(null);
    accountApi.self().then(value => { if (!ignore) setData(value); }).catch((e: unknown) => { if (!ignore) setError(e instanceof Error ? e.message : 'Chưa tải được tình trạng tài khoản.'); }).finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, [user?.userId, refresh]);
  return <section className="og-account-self"><header className="og-accounts-heading"><div><span className="og-eyebrow">TÀI KHOẢN CỦA BẠN</span><h1>Tình trạng và điểm uy tín</h1><p>Xem lý do, thời hạn và các nghĩa vụ đang có. Liên hệ hỗ trợ nếu bạn không đồng ý với quyết định.</p></div><button className="og-button og-button--outline" disabled={loading} onClick={() => setRefresh(n => n + 1)}>Làm mới hồ sơ</button></header>
    {loading && <p role="status">Đang tải tình trạng tài khoản...</p>}{error && <div role="alert"><Alert type="danger">{error}</Alert></div>}
    {!loading && !error && data && <><article className="og-account-detail"><AccountDetailView detail={data} /><AccountHistoryView key={`${data.user.userId}:${data.user.version}`} userId={null} /></article>
      <div className="og-account-toolbar"><Link className="og-button og-button--outline" to="/orders">Xem toàn bộ đơn mua cũ</Link>{data.user.roles.includes('SELLER') && <Link className="og-button og-button--outline" to="/seller/orders">Xem đơn bán cũ</Link>}</div>
      {data.cases.map(c => <article className="og-account-detail" key={c.caseId}><CaseResponsePanel caseInfo={c} userId={data.user.userId} /></article>)}
    </>}
  </section>;
}
