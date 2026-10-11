import React, { useEffect, useState } from 'react';
import { Alert, Button, Input } from '../../../shared/components';
import { authApi, AuthRequestError } from '../authApi';
import { acceptsNewPassword, NEW_PASSWORD_HINT } from '../passwordPolicy';
import type { AuthChallenge } from '../types';
import { AuthOtpForm } from './AuthOtpForm';
import { PasswordInput } from './PasswordInput';
import { ForgotPasswordStepIndicator } from './ForgotPasswordStepIndicator';

interface ForgotPasswordFormProps {
  onSuccess?: () => void;
  onNavigateToLogin?: () => void;
  onStepChange?: (step: 1 | 2 | 3) => void;
}

export const ForgotPasswordForm: React.FC<ForgotPasswordFormProps> = ({
  onNavigateToLogin,
  onSuccess,
  onStepChange,
}) => {
  const [email, setEmail] = useState('');
  const [challenge, setChallenge] = useState<AuthChallenge | null>(null);
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [retryAt, setRetryAt] = useState(0);
  const [now, setNow] = useState(Date.now);

  useEffect(() => {
    if (!retryAt) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [retryAt]);

  const wait = Math.max(0, Math.ceil((retryAt - now) / 1000));

  const currentStep: 1 | 2 | 3 = done || !challenge
    ? 1
    : challenge.status === 'OTP_REQUIRED'
    ? 2
    : 3;

  useEffect(() => {
    onStepChange?.(currentStep);
  }, [currentStep, onStepChange]);

  const run = async (action: () => Promise<void>) => {
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      await action();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Không thể đặt lại mật khẩu. Hãy thử lại.');
      if (e instanceof AuthRequestError && e.retryAfter) {
        const t = Date.now();
        setNow(t);
        setRetryAt(t + e.retryAfter * 1000);
      }
    } finally {
      setBusy(false);
    }
  };

  const handleStepClick = (targetStep: 1 | 2 | 3) => {
    if (targetStep === 1 && challenge) {
      void run(async () => {
        try {
          await authApi.cancelChallenge(challenge.challengeId);
        } catch {
          // ignore cancel error
        }
        setChallenge(null);
        setError('');
        setPassword('');
        setConfirmation('');
      });
    }
  };

  if (done) {
    return (
      <div className="og-step-enter">
        <Alert type="success">
          Đã đặt lại mật khẩu. Các phiên đăng nhập cũ đã bị thu hồi. Hãy đăng nhập bằng mật khẩu mới.
        </Alert>
        <div style={{ marginTop: '20px' }}>
          <Button
            type="button"
            fullWidth
            onClick={() => {
              onSuccess?.();
              if (!onSuccess) onNavigateToLogin?.();
            }}
          >
            Đăng nhập
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="og-forgot-form-wrapper">
      {/* 1. PROGRESS STEPPER (REUSABLE LIKE REGISTRATION) */}
      <ForgotPasswordStepIndicator
        currentStep={currentStep}
        onStepClick={handleStepClick}
      />

      {error && <Alert type="danger">{error}</Alert>}

      {/* 2. STEP CONTENT */}
      {challenge?.status === 'OTP_REQUIRED' ? (
        /* STEP 2: VERIFY OTP */
        <div className="og-step-enter">
          <AuthOtpForm
            key={challenge.challengeId}
            challenge={challenge}
            onChange={setChallenge}
            genericNotice
            onVerify={async (code) => {
              setChallenge(await authApi.verifyReset(challenge.challengeId, code));
            }}
            onCancel={() => {
              setChallenge(null);
              setError('');
            }}
          />
        </div>
      ) : challenge ? (
        /* STEP 3: NEW PASSWORD */
        <form
          key="new-password"
          className="og-step-enter"
          onSubmit={(e) => {
            e.preventDefault();
            if (!acceptsNewPassword(password)) {
              setError(NEW_PASSWORD_HINT);
              return;
            }
            if (password !== confirmation) {
              setError('Xác nhận mật khẩu không khớp.');
              return;
            }
            void run(async () => {
              await authApi.finishReset(challenge.challengeId, password, confirmation);
              setPassword('');
              setConfirmation('');
              setDone(true);
            });
          }}
        >
          <PasswordInput
            label="Mật khẩu mới"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            autoComplete="new-password"
            helperText={NEW_PASSWORD_HINT}
            disabled={busy}
          />
          <PasswordInput
            label="Xác nhận mật khẩu mới"
            value={confirmation}
            onChange={(e) => setConfirmation(e.target.value)}
            required
            autoComplete="new-password"
            disabled={busy}
          />

          <div style={{ marginTop: '20px' }}>
            <Button type="submit" fullWidth isLoading={busy}>
              Đặt lại mật khẩu
            </Button>
          </div>

          <div style={{ marginTop: '12px', textAlign: 'center' }}>
            <Button
              type="button"
              variant="ghost"
              disabled={busy}
              onClick={() =>
                void run(async () => {
                  await authApi.cancelChallenge(challenge.challengeId);
                  setChallenge(null);
                  setPassword('');
                  setConfirmation('');
                })
              }
            >
              Đổi email / bắt đầu lại
            </Button>
          </div>
        </form>
      ) : (
        /* STEP 1: ENTER EMAIL */
        <form
          key="email"
          className="og-step-enter"
          onSubmit={(e) => {
            e.preventDefault();
            if (!wait) {
              void run(async () => {
                setChallenge(await authApi.startReset(email.trim()));
              });
            }
          }}
        >
          <Input
            label="Địa chỉ Email"
            type="email"
            maxLength={320}
            required
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            disabled={busy}
            placeholder="name@example.com"
          />

          <div style={{ marginTop: '20px' }}>
            <Button
              type="submit"
              fullWidth
              aria-label="Gửi mã đặt lại mật khẩu"
              isLoading={busy}
              disabled={wait > 0}
            >
              {wait > 0 ? `Gửi lại sau ${wait} giây` : 'Gửi mã đặt lại mật khẩu'}
            </Button>
          </div>

          {wait > 0 && (
            <p role="timer" aria-live="off" style={{ fontSize: '0.88rem', color: 'var(--og-color-text-secondary)', textAlign: 'center', marginTop: '8px' }}>
              Vui lòng chờ {wait} giây trước khi gửi lại.
            </p>
          )}

          <div style={{ marginTop: '16px' }}>
            <Button
              type="button"
              variant="outline"
              fullWidth
              disabled={busy}
              onClick={onNavigateToLogin}
            >
              Quay lại Đăng nhập
            </Button>
          </div>
        </form>
      )}
    </div>
  );
};
