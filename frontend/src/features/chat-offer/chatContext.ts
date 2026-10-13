import { createContext } from 'react';
import type { ChatDetail, ConversationPage } from './chatApi';

export interface ChatRequest { conversationId?: number; productId?: number; orderId?: number; offer?: boolean }
export function chatQuery(request: ChatRequest) {
  const params = new URLSearchParams();
  if (request.conversationId) params.set('conversationId', String(request.conversationId));
  else if (request.productId) params.set('productId', String(request.productId));
  if (request.orderId) params.set('orderId', String(request.orderId));
  if (request.offer) params.set('offer', '1');
  return params.toString();
}
export function createChatMemory(owner: number) {
  return {
    owner,
    drafts: { current: new Map<number, string>() },
    files: { current: new Map<number, File>() },
    client: { current: new Map<number, { signature: string; id: string; file?: File }>() },
    sending: new Set<number>(),
  };
}
export interface QuickChatState { request: ChatRequest; key: string; mode: 'open' | 'minimized'; name: string }
export interface ChatContextValue {
  enabled: boolean;
  memory: ReturnType<typeof createChatMemory>;
  quick: QuickChatState | null;
  inbox: ConversationPage | null;
  inboxLoading: boolean;
  inboxError: string | null;
  open: (request: ChatRequest) => void;
  close: () => void;
  minimize: () => void;
  restore: () => void;
  remember: (key: string, detail: ChatDetail) => void;
  refreshInbox: () => void;
}
export const ChatContext = createContext<ChatContextValue | null>(null);
