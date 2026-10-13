import { useContext } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ChatContext } from './chatContext';
import { UtilityPopover } from '../../shared/components/Popover/UtilityPopover';

export function MessageBell() {
  const chat = useContext(ChatContext);
  const location = useLocation();
  if (!chat?.enabled) return null;
  return <UtilityPopover title="Tin nhắn" icon="message" unread={chat.inbox?.unread ?? 0} onOpen={chat.refreshInbox}>{close => <>
    {chat.inboxError && chat.inbox && <p className="og-utility-error" role="alert">{chat.inboxError}</p>}
    {chat.inboxLoading && !chat.inbox ? <p className="og-utility-empty" role="status">Đang tải hội thoại...</p> : chat.inboxError && !chat.inbox ? <div className="og-utility-empty"><p role="alert">{chat.inboxError}</p><button type="button" className="og-button og-button--outline og-button--sm" onClick={chat.refreshInbox}>Thử lại</button></div> : !chat.inbox?.items.length ? <div className="og-utility-empty"><strong>Chưa có cuộc trò chuyện</strong><p>Chọn “Nhắn người bán” trên món đồ bạn quan tâm.</p></div> : <ul className="og-utility-list">{chat.inbox.items.slice(0, 6).map(thread => <li key={thread.id}><button type="button" className="og-utility-row" onClick={() => { close(); chat.open({ conversationId: thread.id }); }}><span className="og-chat-avatar">{thread.partnerName.charAt(0).toUpperCase()}</span><span className="og-utility-row-body"><strong>{thread.partnerName}</strong><span>{thread.title}</span><small>{thread.preview || 'Chưa có tin nhắn'}</small></span>{thread.unread > 0 && <span className="og-utility-unread" aria-label={`${thread.unread} tin chưa đọc`} />}</button></li>)}</ul>}
    <Link to="/messages" className="og-utility-footer" state={{ returnTo: location.pathname === '/messages' ? undefined : location.pathname + location.search }} onClick={close}>Mở hộp thư đầy đủ</Link>
  </>}</UtilityPopover>;
}
