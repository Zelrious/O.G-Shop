import { useContext, useEffect, useRef, useState } from 'react';
import { ChatContext } from '../features/chat-offer/chatContext';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth';
import { notificationApi, notificationTarget, type NotificationItem, type NotificationPage } from '../features/notifications/notificationApi';
import { Alert } from '../shared/components';
import { Icon } from '../shared/components/Icon/Icon';
import '../features/notifications/notifications.css';

export function NotificationsPage() {
  const chat = useContext(ChatContext);
  const navigate = useNavigate();
  const { user } = useAuth();
  const [page, setPage] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [data, setData] = useState<NotificationPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [markingAll, setMarkingAll] = useState(false);
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
    try {
      await notificationApi.read(item.id);
      setData(old => old && (!old.items.find(i => i.id === item.id)?.read ? {
        ...old,
        unreadCount: Math.max(0, old.unreadCount - 1),
        items: old.items.map(i => i.id === item.id ? { ...i, read: true } : i),
      } : old));
      window.dispatchEvent(new Event('og-notifications-read'));
      return true;
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Chưa ghi được đã đọc.');
      return false;
    } finally {
      reading.current.delete(item.id);
    }
  };

  const markAllAsRead = async () => {
    if (markingAll || !data) return;
    setMarkingAll(true);
    setError(null);
    try {
      await notificationApi.readAll();
      setData(old => old ? {
        ...old,
        unreadCount: 0,
        items: old.items.map(item => ({ ...item, read: true })),
      } : old);
      window.dispatchEvent(new Event('og-notifications-read'));
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Chưa cập nhật được trạng thái đã đọc toàn bộ.');
    } finally {
      setMarkingAll(false);
    }
  };

  const handleItemClick = (item: NotificationItem) => {
    const target = notificationTarget(item, user?.roles ?? []);
    void read(item).then(ok => {
      if (ok) {
        if (target) {
          if (target.startsWith('/messages?') && chat?.enabled) {
            chat.open({ conversationId: item.conversationId ?? undefined });
          } else {
            navigate(target);
          }
        }
      } else if (target) {
        setUnreadTarget(target);
      }
    });
  };

  return (
    <section className="og-notifications-page">
      <header className="og-notifications-header">
        <div>
          <span className="og-eyebrow">HOẠT ĐỘNG CỦA BẠN</span>
          <h1>Thông báo</h1>
          <p>
            {data?.unreadCount ?? 0} thông báo chưa đọc
            {data && ` • Tổng số ${data.totalElements} thông báo`}
          </p>
        </div>
        <div className="og-notifications-actions">
          {(data?.unreadCount ?? 0) > 0 && (
            <button
              type="button"
              className="og-button og-button--primary og-button--sm"
              disabled={markingAll || loading}
              onClick={() => void markAllAsRead()}
            >
              {markingAll ? 'Đang xử lý...' : 'Đánh dấu đã đọc hết'}
            </button>
          )}
          <button
            type="button"
            className="og-button og-button--outline og-button--sm"
            disabled={loading}
            onClick={() => setRefresh(n => n + 1)}
          >
            Làm mới
          </button>
        </div>
      </header>

      {error && <div role="alert"><Alert type="danger">{error}</Alert></div>}
      {unreadTarget && (
        <div style={{ marginBottom: 16 }}>
          <Link to={unreadTarget} className="og-button og-button--outline og-button--sm">
            Mở nội dung dù chưa ghi được đã đọc
          </Link>
        </div>
      )}

      {loading ? (
        <div className="og-notification-loading" aria-busy="true" role="status">
          <p>Đang tải thông báo...</p>
        </div>
      ) : !data ? null : !data.items.length ? (
        <div className="og-notification-empty">
          <div className="og-notification-empty-icon">
            <Icon name="bell" size={40} />
          </div>
          <h2>Chưa có thông báo</h2>
          <p>Cập nhật giao dịch, đơn hàng, trao đổi và kiểm duyệt sẽ xuất hiện tại đây.</p>
        </div>
      ) : (
        <ul className="og-notification-list">
          {data.items.map(item => {
            const isClickable = Boolean(notificationTarget(item, user?.roles ?? []));
            return (
              <li
                key={item.id}
                className={`og-notification-item ${!item.read ? 'is-unread' : ''} ${isClickable ? 'is-clickable' : ''}`}
                tabIndex={0}
                role="button"
                onClick={() => handleItemClick(item)}
                onKeyDown={e => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    handleItemClick(item);
                  }
                }}
                aria-label={`Mở thông báo: ${item.title}`}
              >
                <div className="og-notification-item-icon" aria-hidden="true">
                  <Icon
                    name={
                      item.destination === 'CONVERSATION'
                        ? 'message'
                        : item.destination === 'VOUCHER_WALLET' || item.destination === 'ADMIN_VOUCHERS' || item.destination === 'KTV_VOUCHERS'
                        ? 'tag'
                        : item.destination === 'ACCOUNT_SELF' || item.destination === 'KTV_ACCOUNT'
                        ? 'user'
                        : 'package'
                    }
                    size={20}
                  />
                </div>
                <article className="og-notification-body">
                  <div className="og-notification-top">
                    <div className="og-notification-heading-wrap">
                      {!item.read && <span className="og-notification-dot" aria-label="Chưa đọc" />}
                      <h2>{item.title}</h2>
                    </div>
                    <time dateTime={item.createdAt}>
                      {new Date(item.createdAt).toLocaleString('vi-VN')}
                    </time>
                  </div>
                  <p>{item.content}</p>
                </article>
                <div className="og-notification-action-arrow" aria-hidden="true">
                  <Icon name="chevron" size={18} />
                </div>
              </li>
            );
          })}
        </ul>
      )}

      {data && data.totalElements > data.size && (
        <nav className="og-notification-pages" aria-label="Trang thông báo">
          <button
            type="button"
            className="og-button og-button--outline og-button--sm"
            disabled={loading || page === 0}
            onClick={() => setPage(n => n - 1)}
          >
            Trang trước
          </button>
          <span>
            {page + 1} / {Math.ceil(data.totalElements / data.size)}
          </span>
          <button
            type="button"
            className="og-button og-button--outline og-button--sm"
            disabled={loading || (page + 1) * data.size >= data.totalElements}
            onClick={() => setPage(n => n + 1)}
          >
            Trang sau
          </button>
        </nav>
      )}
    </section>
  );
}
