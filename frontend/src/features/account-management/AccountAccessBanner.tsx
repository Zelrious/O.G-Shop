import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth';
import { accountApi, type AccountDetail } from './accountApi';
import './accounts.css';
export function AccountAccessBanner() {
  const { user } = useAuth();
  const id = user?.userId;
  const customer = user?.roles.some(r => r === 'BUYER' || r === 'SELLER') && !user?.roles.some(r => r === 'ADMIN' || r === 'KTV');
  const [data, setData] = useState<AccountDetail | null>(null);
  useEffect(() => {
    if (!id || !customer) return;
    let ignore = false;
    const load = () => accountApi.self().then(value => { if (!ignore) setData(value); }).catch(() => {});
    void load();const timer = window.setInterval(() => { if (!document.hidden) void load(); }, 30000);
    return () => { ignore = true; window.clearInterval(timer); };
  }, [id, customer]);
  if (!customer || !data || data.user.userId !== id || data.user.status === 'ACTIVE' && data.user.activeRestrictions === 0) return null;
  return <aside className="og-account-access-banner" aria-label="Tình trạng tài khoản">Tài khoản có biện pháp hạn chế. Lịch sử và quyền xử lý đơn cũ được giữ.<Link to="/account/status">Xem lý do và thời hạn →</Link></aside>;
}
