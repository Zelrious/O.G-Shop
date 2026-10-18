import { useEffect, useId, useRef, useState, type ReactNode, type CSSProperties } from 'react';
import { createPortal } from 'react-dom';

export interface SelectOption { value: string; label: string; description?: string; badge?: ReactNode; disabled?: boolean }
interface Props { label: string; value: string; options: SelectOption[]; onChange: (value: string) => void; size?: 'sm' | 'md'; disabled?: boolean; error?: string; searchable?: boolean; placeholder?: string; hideLabel?: boolean }

export function Select({ label, value, options, onChange, size = 'md', disabled, error, searchable, placeholder = 'Chọn…', hideLabel }: Props) {
  const id = useId(), trigger = useRef<HTMLButtonElement>(null), panel = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false), [query, setQuery] = useState(''), [active, setActive] = useState(0);
  const [position, setPosition] = useState<CSSProperties>({ top: 0, left: 0, width: 0, maxHeight: 340 });
  const filtered = options.filter(o => `${o.label} ${o.description ?? ''}`.toLocaleLowerCase('vi').includes(query.toLocaleLowerCase('vi')));
  const selected = options.find(o => o.value === value);
  function close(restore = true) { setOpen(false); if (restore) trigger.current?.focus(); }
  function show() {
    if (disabled || trigger.current?.matches(':disabled')) return;
    setQuery(''); setActive(Math.max(0, options.findIndex(o => o.value === value))); setOpen(true);
  }
  useEffect(() => {
    if (!open) return;
    function place() {
      const r = trigger.current!.getBoundingClientRect();
      const below = window.innerHeight - r.bottom - 12, above = r.top - 12;
      const desired = Math.min(340, panel.current?.scrollHeight || 340);
      const down = below >= desired || below >= above;
      const next: CSSProperties = { top: down ? r.bottom + 6 : undefined, bottom: down ? undefined : window.innerHeight - r.top + 6, left: Math.max(6, Math.min(r.left, window.innerWidth - r.width - 6)), width: Math.min(r.width, window.innerWidth - 12), maxHeight: Math.max(80, Math.min(340, down ? below : above)) };
      setPosition(previous => Object.keys(next).every(key => previous[key as keyof CSSProperties] === next[key as keyof CSSProperties]) ? previous : next);
    }
    let frame = 0;
    const track = () => { place(); frame = window.requestAnimationFrame(track); };
    track(); panel.current?.querySelector<HTMLElement>('input, [role=listbox]')?.focus();
    const observer = typeof ResizeObserver === 'undefined' ? undefined : new ResizeObserver(place);
    if (trigger.current) observer?.observe(trigger.current);
    const outside = (e: PointerEvent) => { if (!panel.current?.contains(e.target as Node) && !trigger.current?.contains(e.target as Node)) setOpen(false); };
    const scroll = (e: Event) => { if (!panel.current?.contains(e.target as Node)) place(); };
    document.addEventListener('pointerdown', outside); window.addEventListener('resize', place); window.addEventListener('scroll', scroll, true);
    return () => { window.cancelAnimationFrame(frame); observer?.disconnect(); document.removeEventListener('pointerdown', outside); window.removeEventListener('resize', place); window.removeEventListener('scroll', scroll, true); };
  }, [open]);
  useEffect(() => { if (open) { const option = document.getElementById(`${id}-option-${active}`), container = panel.current; if (option && container) { const rect = option.getBoundingClientRect(), bounds = container.getBoundingClientRect(); if (rect.bottom > bounds.bottom) container.scrollTop += rect.bottom - bounds.bottom; else if (rect.top < bounds.top) container.scrollTop -= bounds.top - rect.top; } } }, [active, open, id]);
  function choose(index: number) { const option = filtered[index]; if (option && !option.disabled) { onChange(option.value); close(); } }
  return <div className={`og-select og-select--${size}`}>
    <span id={`${id}-label`} className={hideLabel ? 'og-sr-only' : 'og-select__label'}>{label}</span>
    <button ref={trigger} type="button" className="og-select__trigger" role="combobox" aria-labelledby={`${id}-label`} aria-label={label} aria-expanded={open} aria-controls={open ? `${id}-list` : undefined} aria-haspopup="listbox" aria-invalid={!!error} aria-describedby={error ? `${id}-error` : undefined} disabled={disabled} onClick={() => open ? close() : show()} onKeyDown={e => { if (['ArrowDown', 'ArrowUp'].includes(e.key)) { e.preventDefault(); show(); } }}>
      <span id={`${id}-value`} className="og-select__value"><strong>{selected?.label ?? placeholder}</strong>{selected?.description && <small>{selected.description}</small>}</span>{selected?.badge}<span aria-hidden="true">▾</span>
    </button>
    {error && <small id={`${id}-error`} className="og-select__error">{error}</small>}
    {open && createPortal(<div ref={panel} className="og-select__popover" style={position} tabIndex={-1} onBlur={e => { if (!e.currentTarget.contains(e.relatedTarget as Node) && e.relatedTarget !== trigger.current) close(false); }} onKeyDown={e => {
      if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); close(); }
      if (e.key === 'Tab') { close(); }
      if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(e.key)) {
        e.preventDefault(); const step = e.key === 'ArrowUp' || e.key === 'End' ? -1 : 1;
        let next = e.key === 'Home' ? -1 : e.key === 'End' ? filtered.length : active;
        for (let i = 0; i < filtered.length; i++) { next = (next + step + filtered.length) % filtered.length; if (!filtered[next].disabled) break; }
        setActive(next);
      }
      if (e.key === 'Enter' || (!searchable && e.key === ' ')) { e.preventDefault(); choose(active); }
    }}>
      {searchable && <input autoFocus aria-label={`Tìm ${label.toLocaleLowerCase('vi')}`} placeholder={`Tìm ${label.toLocaleLowerCase('vi')}…`} value={query} onChange={e => { setQuery(e.target.value); setActive(0); }} role="combobox" aria-expanded="true" aria-controls={`${id}-list`} aria-activedescendant={filtered[active] ? `${id}-option-${active}` : undefined} />}
      <div id={`${id}-list`} role="listbox" tabIndex={searchable ? undefined : -1} aria-label={label} aria-activedescendant={filtered[active] ? `${id}-option-${active}` : undefined}>
        {filtered.map((o, i) => <div id={`${id}-option-${i}`} key={o.value} role="option" aria-selected={o.value === value} aria-disabled={o.disabled || undefined} className={`og-select__option ${i === active ? 'is-active' : ''}`} onPointerMove={() => setActive(i)} onMouseDown={e => e.preventDefault()} onClick={() => choose(i)}><span className="og-select__value"><strong>{o.label}</strong>{o.description && <small>{o.description}</small>}</span>{o.badge}<span aria-hidden="true">{o.value === value ? '✓' : ''}</span></div>)}
      </div>{!filtered.length && <p role="status">Không có kết quả phù hợp.</p>}
    </div>, document.body)}
  </div>;
}
