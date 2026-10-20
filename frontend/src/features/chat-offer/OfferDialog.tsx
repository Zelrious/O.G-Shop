import { useEffect, useRef, useState } from 'react';
import { Alert } from '../../shared/components';
import { chatApi, money, type ChatDetail, type ChatOffer } from './chatApi';
import '../listing/review.css';
interface Props { detail: ChatDetail; counter?: ChatOffer; onClose: () => void; onDone: (offer: ChatOffer) => void }
export function OfferDialog({ detail, counter, onClose, onDone }: Props) {
  const [price, setPrice] = useState(String(counter?.price ?? detail.product.price));
  const [end, setEnd] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<{ price?: string; end?: string }>({});
  const dialog = useRef<HTMLDivElement>(null), inFlight = useRef(false), command = useRef<{ signature: string; key: string } | null>(null);

  useEffect(() => {
    const prior = document.activeElement as HTMLElement | null, overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden'; dialog.current?.focus();
    return () => { document.body.style.overflow = overflow; prior?.focus(); };
  }, []);

  const [minDateTime] = useState(() => {
    const baseMs = Date.parse(detail.serverTime) || Date.now();
    const serverDate = new Date(baseMs);
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${serverDate.getFullYear()}-${pad(serverDate.getMonth() + 1)}-${pad(serverDate.getDate())}T${pad(serverDate.getHours())}:${pad(serverDate.getMinutes())}`;
  });
  const formattedPricePreview = Number(price) > 0 ? money(Number(price)) : null;


  const submit = async () => {
    if (inFlight.current) return;
    const value = Number(price);
    const newFieldErrors: { price?: string; end?: string } = {};

    if (!/^[0-9]+$/.test(price) || !Number.isSafeInteger(value) || value < 1 || value > 999999999999999) {
      newFieldErrors.price = 'Giá phải là số đồng nguyên dương, không dùng dấu phân cách.';
    }
    let expiresAt: string | undefined;
    if (end) {
      const date = new Date(end);
      if (!Number.isFinite(date.getTime()) || date.getTime() <= Date.parse(detail.serverTime)) {
        newFieldErrors.end = 'Hạn phản hồi phải ở tương lai.';
      } else {
        expiresAt = date.toISOString();
      }
    }
    if (Object.keys(newFieldErrors).length > 0) {
      setFieldErrors(newFieldErrors);
      setError(newFieldErrors.price || newFieldErrors.end || 'Vui lòng kiểm tra lại thông tin đề nghị.');
      return;
    }
    setFieldErrors({});

    const payload = { price: value, expiresAt, version: counter?.version, offer: counter?.id, conversation: detail.conversation.id };
    const signature = JSON.stringify(payload);
    if (command.current?.signature !== signature) command.current = { signature, key: crypto.randomUUID() };
    inFlight.current = true; setBusy(true); setError(null);
    try {
      const result = counter
        ? await chatApi.decide(counter.id, { commandKey: command.current.key, action: 'COUNTER', expectedVersion: counter.version, price: value, expiresAt })
        : await chatApi.propose(detail.conversation.id, { clientId: command.current.key, price: value, expiresAt });
      onDone(result);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Chưa gửi được đề nghị.');
    } finally {
      inFlight.current = false; setBusy(false);
    }
  };

  return (
    <div className="og-modal-backdrop" onClick={() => { if (!busy) onClose(); }}>
      <div
        ref={dialog}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-labelledby="offer-dialog-title"
        className="og-fee-dialog og-chat-offer-dialog"
        onClick={e => e.stopPropagation()}
        onKeyDown={e => {
          if (e.key === 'Escape' && !busy) onClose();
          if (e.key === 'Tab') {
            const controls = dialog.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), a[href]');
            if (!controls?.length) return;
            const first = controls[0], last = controls[controls.length - 1];
            if (e.shiftKey && (document.activeElement === first || document.activeElement === dialog.current)) {
              e.preventDefault(); last.focus();
            } else if (!e.shiftKey && document.activeElement === last) {
              e.preventDefault(); first.focus();
            }
          }
        }}
      >
        <header>
          <div>
            <span className="og-eyebrow">THƯƠNG LƯỢNG</span>
            <h2 id="offer-dialog-title">{counter ? 'Đề nghị giá khác' : 'Gửi đề nghị trả giá'}</h2>
          </div>
          <button className="og-button og-button--ghost" onClick={onClose} disabled={busy} aria-label="Đóng đề nghị">×</button>
        </header>
        <p><strong>{detail.conversation.title}</strong></p>
        <p>Giá niêm yết: {money(detail.product.price)} · Cho 1 món</p>
        <form onSubmit={e => { e.preventDefault(); void submit(); }}>
          <div>
            <label htmlFor="offer-price-input">Giá bạn đề nghị (đồng)</label>
            <input
              id="offer-price-input"
              type="number"
              inputMode="numeric"
              min="1"
              max="999999999999999"
              step="1"
              value={price}
              onChange={e => {
                setPrice(e.target.value);
                setFieldErrors(prev => ({ ...prev, price: undefined }));
              }}
              required
              disabled={busy}
            />
            {formattedPricePreview && (
              <span style={{ fontSize: '0.85rem', color: 'var(--og-color-primary, #047857)', fontWeight: 600, display: 'block', marginTop: '4px' }}>
                Xem trước: {formattedPricePreview}
              </span>
            )}
            {fieldErrors.price && (
              <span style={{ fontSize: '0.82rem', color: 'var(--og-color-danger, #dc2626)', display: 'block', marginTop: '4px' }}>
                {fieldErrors.price}
              </span>
            )}
          </div>
          <div>
            <label htmlFor="offer-end-input">Hạn phản hồi (tùy chọn)</label>
            <input
              id="offer-end-input"
              type="datetime-local"
              min={minDateTime}
              value={end}
              onChange={e => {
                setEnd(e.target.value);
                setFieldErrors(prev => ({ ...prev, end: undefined }));
              }}
              disabled={busy}
            />
            {fieldErrors.end && (
              <span style={{ fontSize: '0.82rem', color: 'var(--og-color-danger, #dc2626)', display: 'block', marginTop: '4px' }}>
                {fieldErrors.end}
              </span>
            )}
          </div>
          <small>Để trống nếu không đặt hạn phản hồi.</small>
          <Alert type="warning">Chấp nhận giá chưa giữ hàng hoặc tạo đơn. Khi đặt mua vẫn kiểm tra hàng và điều kiện giao dịch.</Alert>
          {error && (
            <div role="alert">
              <Alert type="danger">{error}</Alert>
              <p>Bản soạn được giữ để thử lại. Tải lại hội thoại nếu đối tác đã phản hồi.</p>
            </div>
          )}
          <footer>
            <button type="button" className="og-button og-button--outline" onClick={onClose} disabled={busy}>Hủy</button>
            <button className="og-button og-button--primary" disabled={busy || !price}>{busy ? 'Đang gửi...' : counter ? 'Gửi giá khác' : 'Gửi đề nghị'}</button>
          </footer>
        </form>
      </div>
    </div>
  );
}
