import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import { chatApi, type ChatDetail, type ConversationPage } from './chatApi';
import { ChatContext, chatQuery, createChatMemory, type ChatRequest, type QuickChatState } from './chatContext';
import { ChatDock } from './ChatDock';

export function ChatProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const enabled = !!user && user.roles.some(role => role === 'BUYER' || role === 'SELLER') && !user.roles.some(role => role === 'ADMIN' || role === 'KTV');
  // An account change discards every draft, selected file, cached inbox and pending UI.
  return <ChatRuntime key={`${user?.userId ?? 0}:${enabled}`} owner={user?.userId ?? 0} enabled={enabled}>{children}</ChatRuntime>;
}

function ChatRuntime({ children, owner, enabled }: { children: ReactNode; owner: number; enabled: boolean }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [memory] = useState(() => createChatMemory(owner));
  const [quick, setQuick] = useState<QuickChatState | null>(null);
  const [inbox, setInbox] = useState<ConversationPage | null>(null);
  const [inboxLoading, setInboxLoading] = useState(true);
  const [inboxError, setInboxError] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);
  const opener = useRef<HTMLElement | null>(null);
  const refreshInbox = useCallback(() => setRevision(value => value + 1), []);
  const returnTo = location.pathname === '/messages' ? (location.state as { returnTo?: string } | null)?.returnTo : location.pathname + location.search;

  const open = useCallback((request: ChatRequest) => {
    if (!enabled) return;
    opener.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    if (location.pathname === '/messages' || (window.matchMedia && window.matchMedia('(max-width: 800px)').matches)) {
      navigate('/messages?' + chatQuery(request), { state: { returnTo } });
      return;
    }
    const key = chatQuery(request);
    setQuick(previous => previous?.key === key ? { ...previous, mode: 'open' } : { request, key, mode: 'open', name: 'Cuộc trò chuyện' });
  }, [enabled, location.pathname, navigate, returnTo]);
  const close = useCallback(() => { setQuick(null); const target = opener.current?.isConnected ? opener.current : document.querySelector<HTMLElement>('button[title="Tin nhắn"]'); target?.focus({ preventScroll: true }); }, []);
  const minimize = useCallback(() => setQuick(previous => previous && { ...previous, mode: 'minimized' }), []);
  const restore = useCallback(() => setQuick(previous => previous && { ...previous, mode: 'open' }), []);
  const remember = useCallback((key: string, detail: ChatDetail) => {
    setQuick(previous => !previous || previous.key !== key || (previous.request.conversationId === detail.conversation.id && previous.name === detail.conversation.partnerName) ? previous : {
      ...previous, name: detail.conversation.partnerName,
      request: { conversationId: detail.conversation.id },
    });
  }, []);

  useEffect(() => {
    if (!enabled) return;
    let ignore = false, fetching = false;
    const controller = new AbortController();
    const load = async () => {
      if (fetching || document.hidden) return;
      if (!navigator.onLine) { setInboxLoading(false); setInboxError('Bạn đang ngoại tuyến. Kết nối lại để cập nhật tin nhắn.'); return; }
      fetching = true;
      try {
        const data = await chatApi.list('', 0, controller.signal);
        if (!ignore) { setInbox(data); setInboxError(null); }
      } catch (error) {
        if (!ignore) setInboxError(error instanceof Error ? error.message : 'Chưa tải được hộp thư.');
      } finally { fetching = false; if (!ignore) setInboxLoading(false); }
    };
    void load();
    const timer = window.setInterval(() => void load(), 5000);
    document.addEventListener('visibilitychange', load);
    window.addEventListener('online', load);
    window.addEventListener('offline', load);
    window.addEventListener('og-chat-updated', load);
    return () => { ignore = true; controller.abort(); window.clearInterval(timer); document.removeEventListener('visibilitychange', load); window.removeEventListener('online', load); window.removeEventListener('offline', load); window.removeEventListener('og-chat-updated', load); };
  }, [enabled, revision]);

  useEffect(() => {
    if (!quick || !window.matchMedia) return;
    const media = window.matchMedia('(max-width: 800px)');
    const change = (event: MediaQueryListEvent) => {
      if (event.matches && quick.mode === 'open' && location.pathname !== '/messages') navigate('/messages?' + chatQuery(quick.request), { state: { returnTo } });
    };
    media.addEventListener('change', change);
    return () => media.removeEventListener('change', change);
  }, [quick, location.pathname, navigate, returnTo]);

  const value = useMemo(() => ({ enabled, memory, quick, inbox, inboxLoading, inboxError, open, close, minimize, restore, remember, refreshInbox }), [enabled, memory, quick, inbox, inboxLoading, inboxError, open, close, minimize, restore, remember, refreshInbox]);
  return <ChatContext.Provider value={value}>{children}<ChatDock /></ChatContext.Provider>;
}
