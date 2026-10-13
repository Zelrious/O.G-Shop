import { useContext, type ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ChatContext } from './chatContext';

export function ChatLink({ to, children, className }: { to: string; children: ReactNode; className?: string }) {
  const context = useContext(ChatContext);
  const location = useLocation();
  return <Link to={to} className={className} state={{ returnTo: location.pathname + location.search }} onClick={event => {
    if (!context?.enabled || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    const params = new URLSearchParams(to.split('?')[1]);
    event.preventDefault();
    context.open({ conversationId: Number(params.get('conversationId')) || undefined, productId: Number(params.get('productId')) || undefined, orderId: Number(params.get('orderId')) || undefined, offer: params.get('offer') === '1' });
  }}>{children}</Link>;
}
