import { useContext, useEffect, useRef, useState } from 'react';
import { ChatContext } from '../features/chat-offer/chatContext';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth';
import { notificationApi, notificationTarget, type NotificationItem, type NotificationPage } from '../features/notifications/notificationApi';
import { Alert } from '../shared/components';
import '../features/notifications/notifications.css';

export function NotificationsPage() {
  const chat = useContext(ChatContext);
  const navigate = useNavigate();
  const { user } = useAuth();
  const [page, setPage] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [data, setData] = useState<NotificationPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [unreadTarget, setUnreadTarget] = useState<string | null>(null);
  const reading = useRef(new Set<number>());
  useEffect(() => {
    let ignore = false;
    setLoading(true); setError(null);
    notificationApi.list(page).then(result => { if (!ignore) setData(result); })
      .catch((e: unknown) => { if (!ignore) setError(e instanceof Error ? e.message : 'Không tải được thông báo.'); })
      .finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, [page, refresh, user?.userId]);
  const read = async (item: NotificationItem) => {
    if (item.read) return true;
    if (reading.current.has(item.id)) return false;
    reading.current.add(item.id);
    try { await notificationApi.read(item.id); setData(old => old && (!old.items.find(i => i.id === item.id)?.read ? { ...old, unreadCount: Math.max(0, old.unreadCount - 1), items: old.items.map(i => i.id === item.id ? { ...i, read: true } : i) } : old)); window.dispatchEvent(new Event('og-notifications-read')); return true; }
    catch (e: unknown) { setError(e instanceof Error ? e.message : 'Chưa ghi được đã đọc.'); return false; }
    finally { reading.current.delete(item.id); }
  };
  return <section className="og-notifications-page"><header><div><span className="og-eyebrow">HOẠT ĐỘNG CỦA BẠN</span><h1>Thông báo</h1><p>{data?.unreadCount ?? 0} thông báo chưa đọc</p></div><button className="og-button og-button--outline" disabled={loading} onClick={() => setRefresh(n => n + 1)}>Làm mới</button></header>
    {error && <div role="alert"><Alert type="danger">{error}</Alert></div>}
    {unreadTarget && <Link to={unreadTarget} className="og-button og-button--outline">Mở nội dung dù chưa ghi được đã đọc</Link>}
    {loading ? <p aria-busy="true" role="status">Đang tải thông báo...</p> : !data ? null : !data.items.length ? <div className="og-notification-empty"><h2>Chưa có thông báo</h2><p>Kết quả kiểm duyệt và mở bán sẽ xuất hiện tại đây.</p></div> : <ul className="og-notification-list">{data.items.map(item => {
      const target = notificationTarget(item, user?.roles ?? []);
      return <li key={item.id} className={!item.read ? 'is-unread' : ''}><div className="og-notification-dot" aria-hidden="true" /><article><div className="og-notification-top"><h2>{item.title}</h2><time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleString('vi-VN')}</time></div><p>{item.content}</p><div className="og-notification-links">
        {target ? <Link to={target} onClick={e => { e.preventDefault(); void read(item).then(ok => { if (ok) { if (target.startsWith('/messages?') && chat?.enabled) chat.open({ conversationId: item.conversationId ?? undefined }); else navigate(target); } else setUnreadTarget(target); }); }}>Mở nội dung liên quan →</Link> : <span>Nội dung không có đích phù hợp với quyền hiện tại.</span>}
        {!item.read && <button className="og-button og-button--ghost og-button--sm" onClick={() => void read(item)}>Đánh dấu đã đọc</button>}</div></article></li>;
    })}</ul>}
    {data && data.totalElements > data.size && <nav className="og-notification-pages" aria-label="Trang thông báo"><button className="og-button og-button--outline" disabled={loading || page === 0} onClick={() => setPage(n => n - 1)}>Trang trước</button><span>{page + 1}/{Math.ceil(data.totalElements / data.size)}</span><button className="og-button og-button--outline" disabled={loading || (page + 1) * data.size >= data.totalElements} onClick={() => setPage(n => n + 1)}>Trang sau</button></nav>}
  </section>;
}
