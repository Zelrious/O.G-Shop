import { useEffect, useState } from 'react';
import { authApi, AuthRequestError } from '../authApi';
import type { AuthChallenge } from '../types';
import { Alert, Button } from '../../../shared/components';
import { OtpBoxes } from './OtpBoxes';

interface Props {
  challenge: AuthChallenge;
  onChange: (challenge: AuthChallenge) => void;
  onVerify: (code: string) => Promise<void>;
  onCancel: () => void;
  genericNotice?: boolean;
}

export function AuthOtpForm({ challenge, onChange, onVerify, onCancel, genericNotice }: Props) {
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [now, setNow] = useState(Date.now);
  const [retryAt, setRetryAt] = useState(0);
  const [exhausted, setExhausted] = useState(false);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const wait = Math.max(0, Math.ceil((Math.max(Date.parse(challenge.resendAt), retryAt) - now) / 1000));
  const remainingExpiresSeconds = Math.max(0, Math.ceil((Date.parse(challenge.expiresAt) - now) / 1000));
  const expired = Date.parse(challenge.expiresAt) <= now;

  const formatMmSs = (secs: number) => {
    const m = Math.floor(secs / 60);
    const s = secs % 60;
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  const run = async (action: () => Promise<void>) => {
    if (busy) return;
    setBusy(true); setError('');
    try { await action(); }
    catch (e) {
      setError(e instanceof Error ? e.message : 'Không xử lý được yêu cầu. Hãy thử lại.');
      if (e instanceof AuthRequestError) {
        if (e.retryAfter) { const received = Date.now(); setNow(received); setRetryAt(received + e.retryAfter * 1000); }
        if (e.code === 'OTP_ATTEMPTS_EXHAUSTED' || e.code === 'OTP_UNAVAILABLE') setExhausted(true);
      }
    } finally { setBusy(false); }
  };

  return (
    <div className="og-step-enter">
      <Alert type="info">
        {genericNotice
          ? 'Nếu email thuộc tài khoản Người mua hoặc Người bán đủ điều kiện, bạn sẽ nhận được mã đặt lại mật khẩu.'
          : `Nhập mã xác thực gửi đến ${challenge.email} để ${
              challenge.purpose === 'GOOGLE_LINK' ? 'liên kết Google' : 'hoàn tất đăng nhập'
            }.`}
      </Alert>

      {error && <Alert type="danger">{error}</Alert>}
      {expired && <Alert type="warning">Mã đã hết hạn. Hãy gửi mã mới.</Alert>}

      <form
        key={challenge.challengeId}
        onSubmit={(e) => {
          e.preventDefault();
          if (!expired && !exhausted && /^[0-9]{6}$/.test(code)) void run(() => onVerify(code));
        }}
      >
        <OtpBoxes
          id="auth-otp-input"
          label="Mã OTP"
          value={code}
          onChange={setCode}
          disabled={busy || expired || exhausted}
          autoFocus
        />

        <div className="og-otp-status-row">
          <div className="og-otp-expires-text">
            {expired ? (
              <span style={{ color: 'var(--og-color-danger)' }}>Mã đã hết hạn. Vui lòng bấm gửi lại mã.</span>
            ) : (
              <>
                Mã có hiệu lực trong: <strong>{formatMmSs(remainingExpiresSeconds)}</strong>
              </>
            )}
          </div>
        </div>

        <Button
          type="submit"
          fullWidth
          isLoading={busy}
          disabled={expired || exhausted}
        >
          Xác thực mã
        </Button>
      </form>

      <div role="timer" aria-live="off" className="og-otp-timer-notice" style={{ marginTop: '12px', fontSize: '0.88rem', color: 'var(--og-color-text-secondary)', textAlign: 'center' }}>
        {wait ? `Có thể gửi lại sau ${wait} giây.` : 'Bạn có thể gửi lại mã.'}
      </div>

      <div className="og-step-actions og-step-actions--between" style={{ marginTop: '16px' }}>
        <Button
          type="button"
          variant="outline"
          aria-label="Gửi lại mã"
          disabled={busy || wait > 0}
          onClick={() =>
            void run(async () => {
              onChange(await authApi.resendChallenge(challenge.challengeId));
              setCode('');
              setExhausted(false);
              setRetryAt(0);
              setNow(Date.now());
            })
          }
        >
          {wait > 0 ? `Gửi lại sau ${wait}s` : 'Gửi lại mã'}
        </Button>

        <Button
          type="button"
          variant="ghost"
          disabled={busy}
          onClick={() =>
            void run(async () => {
              await authApi.cancelChallenge(challenge.challengeId);
              onCancel();
            })
          }
        >
          Quay lại
        </Button>
      </div>
    </div>
  );
}
