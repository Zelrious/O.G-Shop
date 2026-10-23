import { useContext, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import { ChatContext } from '../chat-offer/chatContext';
import { UtilityPopover } from '../../shared/components/Popover/UtilityPopover';
import { Icon } from '../../shared/components/Icon/Icon';
import { notificationApi, notificationTarget, type NotificationItem, type NotificationPage } from './notificationApi';
import './notifications.css';

export function NotificationBell() {
  const { user } = useAuth();
  const chat = useContext(ChatContext);
  const navigate = useNavigate();
  const userId = user?.userId;
  const actor = useRef(userId);
  useLayoutEffect(() => { actor.current = userId; }, [userId]);
  const [snapshot, setSnapshot] = useState<{ owner: number; data: NotificationPage } | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [failedTarget, setFailedTarget] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);
  const [markingAll, setMarkingAll] = useState(false);
  const reading = useRef(new Set<number>());
  const data = snapshot && snapshot.owner === userId ? snapshot.data : null;
  useEffect(() => {
    if (!userId) return;
    let ignore = false, fetching = false;
    setLoading(true); setError(null); setFailedTarget(null);
    const load = async () => {
      if (fetching || document.hidden) return;
      fetching = true;
      try { const result = await notificationApi.list(0, 6); if (!ignore) { setSnapshot({ owner: userId, data: result }); setError(null); } }
      catch (reason) { if (!ignore) setError(reason instanceof Error ? reason.message : 'Chưa tải được thông báo.'); }
      finally { fetching = false; if (!ignore) setLoading(false); }
    };
    void load(); const timer = window.setInterval(() => void load(), 15000);
    window.addEventListener('og-notifications-read', load); document.addEventListener('visibilitychange', load);
    return () => { ignore = true; window.clearInterval(timer); window.removeEventListener('og-notifications-read', load); document.removeEventListener('visibilitychange', load); };
  }, [userId, revision]);
  if (!user) return null;
  const destination = user.roles.includes('KTV') ? '/ktv/notifications' : user.roles.includes('ADMIN') ? '/admin/notifications' : '/notifications';
  const go = (target: string, close: () => void) => {
    close();
    if (target.startsWith('/messages?') && chat?.enabled) chat.open({ conversationId: Number(new URLSearchParams(target.split('?')[1]).get('conversationId')) });
    else navigate(target);
  };
  const markAllAsRead = async () => {
    if (markingAll || !userId) return;
    setMarkingAll(true);
    try {
      await notificationApi.readAll();
      setSnapshot(prev => {
        if (!prev || prev.owner !== userId) return prev;
        return {
          ...prev,
          data: {
            ...prev.data,
            unreadCount: 0,
            items: prev.data.items.map(item => ({ ...item, read: true })),
          },
        };
      });
      window.dispatchEvent(new Event('og-notifications-read'));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Chưa cập nhật được trạng thái đã đọc.');
    } finally {
      setMarkingAll(false);
    }
  };
  const open = async (item: NotificationItem, close: () => void) => {
    const target = notificationTarget(item, user.roles), owner = user.userId;
    if (reading.current.has(item.id)) return;
    reading.current.add(item.id);
    try {
      if (!item.read) await notificationApi.read(item.id);
      if (actor.current !== owner) return;
      setSnapshot(previous => {
        if (!previous || previous.owner !== owner) return previous;
        const alreadyRead = previous.data.items.find(notice => notice.id === item.id)?.read ?? item.read;
        return { ...previous, data: { ...previous.data, unreadCount: Math.max(0, previous.data.unreadCount - (alreadyRead ? 0 : 1)), items: previous.data.items.map(notice => notice.id === item.id ? { ...notice, read: true } : notice) } };
      });
      window.dispatchEvent(new Event('og-notifications-read'));
      if (target) go(target, close);
    } catch (reason) { if (actor.current === owner) { setError(reason instanceof Error ? reason.message : 'Chưa ghi nhận đã đọc.'); setFailedTarget(target); } }
    finally { reading.current.delete(item.id); }
  };
  return <UtilityPopover title="Thông báo" icon="bell" unread={data?.unreadCount ?? 0} onOpen={() => setRevision(value => value + 1)}>{close => <>
    <div className="og-utility-topbar">
      <Link className="og-utility-top-link" to={destination} onClick={close}>
        Xem toàn bộ →
      </Link>
      {(data?.unreadCount ?? 0) > 0 && (
        <button
          type="button"
          className="og-utility-top-btn"
          disabled={markingAll}
          onClick={() => void markAllAsRead()}
        >
          {markingAll ? 'Đang xử lý...' : 'Đánh dấu đã xem'}
        </button>
      )}
    </div>
    {error && <p className="og-utility-error" role="alert">{error}</p>}
    {failedTarget && <button type="button" className="og-utility-footer" onClick={() => go(failedTarget, close)}>Mở nội dung dù chưa ghi được đã đọc</button>}
    {loading && !data ? <p className="og-utility-empty" role="status">Đang tải thông báo...</p> : !data ? <div className="og-utility-empty"><button type="button" className="og-button og-button--outline og-button--sm" onClick={() => setRevision(value => value + 1)}>Thử lại</button></div> : !data.items.length ? <div className="og-utility-empty"><strong>Chưa có thông báo</strong><p>Cập nhật giao dịch và tài khoản sẽ xuất hiện tại đây.</p></div> : <ul className="og-utility-list">{data.items.map(item => <li key={item.id}><button type="button" className="og-utility-row" aria-label={`Mở thông báo: ${item.title}`} onClick={() => void open(item, close)}><span className="og-utility-notice-icon"><Icon name={item.destination === 'CONVERSATION' ? 'message' : item.destination === 'ACCOUNT_SELF' || item.destination === 'KTV_ACCOUNT' ? 'user' : 'file'} size={19} /></span><span className="og-utility-row-body"><strong>{item.title}</strong><span>{item.content}</span><small>{new Date(item.createdAt).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' })}</small></span>{!item.read && <span className="og-utility-unread" aria-label="Chưa đọc" />}</button></li>)}</ul>}
  </>}</UtilityPopover>;
}
