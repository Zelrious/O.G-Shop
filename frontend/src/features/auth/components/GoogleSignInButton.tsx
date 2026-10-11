import { useEffect, useRef, useState } from 'react';
import { authApi, AuthRequestError } from '../authApi';
import type { GoogleResponse } from '../types';
import { Alert, Button } from '../../../shared/components';

interface GoogleIdApi {
  initialize(options: { client_id: string; nonce: string; callback: (r: { credential: string }) => void; ux_mode: 'popup' }): void;
  renderButton(element: HTMLElement, options: { theme: string; size: string; text: string; locale: string; width?: number }): void;
}
declare global { interface Window { google?: { accounts: { id: GoogleIdApi } } } }
let sdk: Promise<void> | undefined;
function loadSdk(): Promise<void> {
  if (window.google?.accounts.id) return Promise.resolve();
  if (!sdk) sdk = new Promise<void>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://accounts.google.com/gsi/client'; script.async = true;
    script.onload = () => resolve();
    script.onerror = () => { script.remove(); sdk = undefined; reject(new Error('Không tải được Google. Kiểm tra kết nối và thử lại.')); };
    document.head.append(script);
  });
  return sdk;
}

interface GoogleSignInButtonProps {
  onResult: (result: GoogleResponse) => Promise<void>;
  showDivider?: boolean;
  dividerText?: string;
}

export function GoogleSignInButton({
  onResult,
  showDivider = true,
  dividerText = 'Hoặc tiếp tục với Google',
}: GoogleSignInButtonProps) {
  const container = useRef<HTMLDivElement>(null);
  const callback = useRef(onResult);
  useEffect(() => { callback.current = onResult; }, [onResult]);
  const [error, setError] = useState('');
  const [configured, setConfigured] = useState<boolean | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [retryWait, setRetryWait] = useState(0);

  useEffect(() => {
    if (!retryWait) return;
    const timer = window.setInterval(() => {
      setRetryWait((prev) => {
        if (prev <= 1) {
          setError('');
          authApi.clearGoogleNonce();
          setAttempt((n) => n + 1);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => window.clearInterval(timer);
  }, [retryWait]);

  useEffect(() => {
    let active = true;
    let processing = false;
    void (async () => {
      const config = await authApi.googleConfig();
      if (!active) return;
      setConfigured(config.enabled);
      if (!config.enabled) return;
      const [{ nonceId }] = await Promise.all([authApi.googleNonce(), loadSdk()]);
      if (!active || !container.current || !window.google) return;
      window.google.accounts.id.initialize({ client_id: config.clientId, nonce: nonceId, ux_mode: 'popup', callback: response => {
        if (!active || processing) return;
        processing = true; setBusy(true); setError('');
        void authApi.googleLogin(response.credential, nonceId).then(result => callback.current(result)).catch(e => {
          if (active) setError(e instanceof AuthRequestError && e.code === 'ACCOUNT_RESTRICTED'
            ? `${e.message} ${e.details?.accountStatus || ''}. ${e.details?.supportInstruction || ''} Chức năng hiện có: liên hệ hỗ trợ.`
            : e instanceof Error ? e.message : 'Đăng nhập Google thất bại.');
        }).finally(() => { if (active) { setBusy(false); processing = false; setAttempt(n => n + 1); } });
      } });
      container.current.replaceChildren();
      window.google.accounts.id.renderButton(container.current, {
        theme: 'outline',
        size: 'large',
        text: 'continue_with',
        locale: 'vi',
        width: 320,
      });
    })().catch(e => {
      if (!active) return;
      if (e instanceof AuthRequestError && e.retryAfter > 0) {
        setRetryWait(e.retryAfter);
        setError(`Đã đạt giới hạn kết nối Google tạm thời. Vui lòng chờ ${e.retryAfter} giây trước khi thử lại.`);
      } else {
        setError(e instanceof Error ? e.message : 'Không kết nối được Google.');
      }
    });
    return () => { active = false; };
  }, [attempt]);
  return (
    <div className="og-google-auth-section" style={{ width: '100%', marginTop: 8 }}>
      {showDivider && configured !== false && (
        <div className="og-auth-divider">
          <span>{dividerText}</span>
        </div>
      )}
      {configured === false && (
        <p style={{ textAlign: 'center', fontSize: '0.88rem', color: 'var(--og-color-text-secondary)', margin: '12px 0' }}>
          Đăng nhập Google chưa được cấu hình.
        </p>
      )}
      <div
        ref={container}
        aria-label="Tiếp tục bằng Google"
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          width: '100%',
          minHeight: configured === false ? 0 : '44px',
          ...(busy ? { pointerEvents: 'none', opacity: 0.6 } : undefined),
        }}
      />
      {busy && <p role="status" style={{ textAlign: 'center', fontSize: '0.88rem', color: 'var(--og-color-text-secondary)', marginTop: 8 }}>Đang xác minh Google…</p>}
      {error && (
        <div style={{ marginTop: 12 }}>
          <Alert type="danger">{error}</Alert>
          <Button
            type="button"
            variant="outline"
            fullWidth
            disabled={busy || retryWait > 0}
            onClick={() => {
              authApi.clearGoogleNonce();
              setError('');
              setRetryWait(0);
              setAttempt(n => n + 1);
            }}
          >
            {retryWait > 0 ? `Thử lại Google (${retryWait}s)` : 'Thử lại Google'}
          </Button>
        </div>
      )}
    </div>
  );
}
