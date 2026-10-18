import { useContext, useEffect, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ChatContext, chatQuery } from './chatContext';
import { ConversationWorkspace } from './ConversationWorkspace';
import { Icon } from '../../shared/components/Icon/Icon';
import './chat.css';

export function ChatDock() {
  const context = useContext(ChatContext);
  const location = useLocation();
  const navigate = useNavigate();
  const windowRef = useRef<HTMLElement>(null);
  const minimizedRef = useRef<HTMLButtonElement>(null);
  const quick = context?.quick;
  const visible = !!quick && quick.mode === 'open' && location.pathname !== '/messages' && !/^\/(login|register|forgot-password|showcase)/.test(location.pathname);
  useEffect(() => {
    if (visible) windowRef.current?.querySelector<HTMLTextAreaElement>('textarea')?.focus();
    else if (quick?.mode === 'minimized') minimizedRef.current?.focus({ preventScroll: true });
  }, [visible, quick?.key, quick?.mode]);
  if (!context?.enabled || !quick) return null;
  const hiddenOnRoute = location.pathname === '/messages' || /^\/(login|register|forgot-password|showcase)/.test(location.pathname);
  return <>
    <section ref={windowRef} className="og-chat-dock" aria-label={`Chat nhanh với ${quick.name}`} hidden={!visible}>
      <header className="og-chat-dock-toolbar"><strong>{quick.name}</strong><div>
        <button type="button" className="og-chat-icon-button" aria-label="Mở hộp thư đầy đủ" title="Mở hộp thư đầy đủ" onClick={() => navigate('/messages?' + chatQuery(quick.request), { state: { returnTo: location.pathname + location.search } })}><Icon name="expand" size={17} /></button>
        <button type="button" className="og-chat-icon-button" aria-label="Thu nhỏ chat" title="Thu nhỏ" onClick={context.minimize}><Icon name="minus" size={18} /></button>
        <button type="button" className="og-chat-icon-button" aria-label="Đóng chat" title="Đóng" onClick={context.close}><Icon name="close" size={18} /></button>
      </div></header>
      <ConversationWorkspace key={quick.key} compact initialQuery={quick.key} isActive={visible} onReady={detail => context.remember(quick.key, detail)} />
    </section>
    {quick.mode === 'minimized' && !hiddenOnRoute && <button ref={minimizedRef} type="button" className="og-chat-minimized" aria-label={`Mở lại chat với ${quick.name}`} title={quick.name} onClick={context.restore}><Icon name="message" size={23} /><span>{quick.name}</span></button>}
  </>;
}
