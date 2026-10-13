import { useLocation } from 'react-router-dom';
import { ConversationWorkspace } from '../features/chat-offer/ConversationWorkspace';

export function MessagesPage() {
  const location = useLocation();
  const candidate = (location.state as { returnTo?: string } | null)?.returnTo;
  const returnTo = candidate?.startsWith('/') && !candidate.startsWith('//') && !candidate.startsWith('/messages') ? candidate : '/marketplace';
  return <ConversationWorkspace returnTo={returnTo} />;
}
