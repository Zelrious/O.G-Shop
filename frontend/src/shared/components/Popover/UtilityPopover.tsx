import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { Icon, type IconName } from '../Icon/Icon';

export function UtilityPopover({ title, icon, unread = 0, children, onOpen }: { title: string; icon: IconName; unread?: number; children: (close: () => void) => ReactNode; onOpen?: () => void }) {
  const id = useId();
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  useEffect(() => { setOpen(false); }, [location.pathname, location.search]);
  useEffect(() => {
    const outside = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false); };
    const another = (event: Event) => { if ((event as CustomEvent).detail !== id) setOpen(false); };
    document.addEventListener('pointerdown', outside);
    window.addEventListener('og-utility-open', another);
    return () => { document.removeEventListener('pointerdown', outside); window.removeEventListener('og-utility-open', another); };
  }, [id]);
  return <div className="og-utility-popover" ref={root} onKeyDown={event => { if (event.key === 'Escape' && open) { event.stopPropagation(); setOpen(false); trigger.current?.focus(); } }}>
    <button ref={trigger} type="button" className="og-utility-button" title={title} aria-label={unread ? `${title}, ${unread} chưa đọc` : title} aria-haspopup="dialog" aria-expanded={open} aria-controls={id} onClick={() => { if (!open) { window.dispatchEvent(new CustomEvent('og-utility-open', { detail: id })); onOpen?.(); } setOpen(value => !value); }}><Icon name={icon} />{unread > 0 && <span className="og-utility-badge">{unread > 99 ? '99+' : unread}</span>}</button>
    {open && <div id={id} className="og-utility-panel" role="dialog" aria-label={title}><header><h2>{title}</h2><button type="button" className="og-chat-icon-button" aria-label={`Đóng ${title.toLowerCase()}`} onClick={() => { setOpen(false); trigger.current?.focus(); }}><Icon name="close" size={18} /></button></header>{children(() => setOpen(false))}</div>}
  </div>;
}
