import { useEffect, useRef, useState } from 'react';
import { Alert } from '../../shared/components';
import { chatApi, money, type ChatDetail, type ChatOffer } from './chatApi';
import '../listing/review.css';

interface Props {
  detail: ChatDetail;
  counter?: ChatOffer;
  onClose: () => void;
  onDone: (offer: ChatOffer) => void;
}

export function OfferDialog({ detail, counter, onClose, onDone }: Props) {
  // Store raw integer string for submission
  const initialValue = String(counter?.price ?? detail.product.price);
  const [rawPrice, setRawPrice] = useState(initialValue.replace(/\D/g, ''));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<{ price?: string }>({});
  const dialog = useRef<HTMLDivElement>(null);
  const inFlight = useRef(false);
  const command = useRef<{ signature: string; key: string } | null>(null);

  useEffect(() => {
    const prior = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    dialog.current?.focus();
    return () => {
      document.body.style.overflow = overflow;
      prior?.focus();
    };
  }, []);

  // Format with thousand separators: e.g. "600.000"
  const formattedDisplayPrice = rawPrice
    ? new Intl.NumberFormat('vi-VN').format(Number(rawPrice))
    : '';

  const handlePriceChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const digitsOnly = e.target.value.replace(/\D/g, '');
    if (digitsOnly.length <= 15) {
      setRawPrice(digitsOnly);
      setFieldErrors(prev => ({ ...prev, price: undefined }));
    }
  };

  const submit = async () => {
    if (inFlight.current) return;
    const value = Number(rawPrice);
    const newFieldErrors: { price?: string } = {};

    if (!rawPrice || !Number.isSafeInteger(value) || value < 1 || value > 999999999999999) {
      newFieldErrors.price = 'Giá phải là số đồng nguyên dương, không dùng ký tự đặc biệt.';
    }

    if (Object.keys(newFieldErrors).length > 0) {
      setFieldErrors(newFieldErrors);
      setError(newFieldErrors.price || 'Vui lòng kiểm tra lại thông tin đề nghị.');
      return;
    }
    setFieldErrors({});

    const payload = { price: value, version: counter?.version, offer: counter?.id, conversation: detail.conversation.id };
    const signature = JSON.stringify(payload);
    if (command.current?.signature !== signature) command.current = { signature, key: crypto.randomUUID() };
    inFlight.current = true;
    setBusy(true);
    setError(null);
    try {
      const result = counter
        ? await chatApi.decide(counter.id, { commandKey: command.current.key, action: 'COUNTER', expectedVersion: counter.version, price: value })
        : await chatApi.propose(detail.conversation.id, { clientId: command.current.key, price: value });
      onDone(result);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Chưa gửi được đề nghị.');
    } finally {
      inFlight.current = false;
      setBusy(false);
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
              e.preventDefault();
              last.focus();
            } else if (!e.shiftKey && document.activeElement === last) {
              e.preventDefault();
              first.focus();
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
            <div style={{ position: 'relative', display: 'flex', alignItems: 'center', width: '100%' }}>
              <input
                id="offer-price-input"
                type="text"
                inputMode="numeric"
                placeholder="Nhập số tiền (VD: 500.000)"
                value={formattedDisplayPrice}
                onChange={handlePriceChange}
                style={{ paddingRight: '56px', fontSize: '1.05rem', fontWeight: 600 }}
                required
                disabled={busy}
              />
              <span
                style={{
                  position: 'absolute',
                  right: '12px',
                  fontWeight: 700,
                  fontSize: '0.85rem',
                  color: 'var(--og-color-primary, #047857)',
                  pointerEvents: 'none',
                }}
              >
                VNĐ
              </span>
            </div>
            {fieldErrors.price && (
              <span style={{ fontSize: '0.82rem', color: 'var(--og-color-danger, #dc2626)', display: 'block', marginTop: '4px' }}>
                {fieldErrors.price}
              </span>
            )}
          </div>

          <Alert type="warning">Chấp nhận giá chưa giữ hàng hoặc tạo đơn. Khi đặt mua vẫn kiểm tra hàng và điều kiện giao dịch.</Alert>
          {error && (
            <div role="alert">
              <Alert type="danger">{error}</Alert>
              <p>Bản soạn được giữ để thử lại. Tải lại hội thoại nếu đối tác đã phản hồi.</p>
            </div>
          )}
          <footer>
            <button type="button" className="og-button og-button--outline" onClick={onClose} disabled={busy}>Hủy</button>
            <button className="og-button og-button--primary" disabled={busy || !rawPrice}>
              {busy ? 'Đang gửi...' : counter ? 'Gửi giá khác' : 'Gửi đề nghị'}
            </button>
          </footer>
        </form>
      </div>
    </div>
  );
}
