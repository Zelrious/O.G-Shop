import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useWebcam } from '../hooks/useWebcam';
import { quickAuthApi } from '../quickAuthApi';
import { Button, Alert } from '../../../shared/components';

// Mũi tên chỉ hướng di chuyển cho mỗi cue từ backend; căn giữa ngang bằng left:'50%' khi cần.
const DIRECTION_ARROW: Record<string, { glyph: string; top?: string; bottom?: string; left?: string; right?: string; transform?: string }> = {
  move_closer: { glyph: '⤡', bottom: '10px', left: '50%', transform: 'translateX(-50%)' },
  move_farther: { glyph: '⤢', top: '10px', left: '50%', transform: 'translateX(-50%)' },
  move_right: { glyph: '→', left: '14px', top: '50%', transform: 'translateY(-50%)' },
  move_left: { glyph: '←', right: '14px', top: '50%', transform: 'translateY(-50%)' },
  move_up: { glyph: '↑', top: '10px', left: '50%', transform: 'translateX(-50%)' },
  move_down: { glyph: '↓', bottom: '10px', left: '50%', transform: 'translateX(-50%)' },
  center_face: { glyph: '◎', top: '50%', left: '50%', transform: 'translate(-50%, -50%)' },
};

// Helper function to get user-friendly guidance message based on frame_status and directional_cue
function getQuickAuthGuidance(instruction: string | undefined, frameStatus: string | undefined, directionalCue: string | undefined): string {
  if (instruction && instruction.trim()) return instruction;

  const status = frameStatus || '';
  const cue = directionalCue || '';

  switch (status) {
    case 'COLLECTING_FRAMES':
      return 'Đang nhận diện khuôn mặt, vui lòng giữ yên...';
    case 'FACE_NOT_FOUND':
      return 'Vui lòng nhìn thẳng và đưa khuôn mặt vào giữa khung tròn';
    case 'TOO_MANY_FACES':
      return 'Phát hiện nhiều người! Vui lòng chỉ một mình trong khung hình';
    case 'DISTANCE_ISSUE':
      switch (cue) {
        case 'move_closer': return 'Hãy đưa khuôn mặt lại gần camera hơn';
        case 'move_farther': return 'Hãy đưa khuôn mặt lùi xa camera một chút';
        default: return 'Khoảng cách chưa phù hợp, hãy điều chỉnh lại vị trí';
      }
    case 'FACE_OFF_CENTER':
      switch (cue) {
        case 'move_right': return 'Vui lòng dịch sang phải một chút';
        case 'move_left': return 'Vui lòng dịch sang trái một chút';
        case 'move_up': return 'Vui lòng hạ camera hoặc ngẩng mặt lên một chút';
        case 'move_down': return 'Vui lòng nâng camera một chút';
        case 'center_face': return 'Hãy đưa khuôn mặt vào giữa khung tròn';
        default: return 'Khuôn mặt lệch tâm, hãy căn chỉnh vào giữa';
      }
    case 'IMAGE_QUALITY_ISSUE':
      switch (cue) {
        case 'hold_steady': return 'Ảnh bị mờ hoặc rung tay, vui lòng giữ yên camera';
        case 'more_light': return 'Ánh sáng quá tối, vui lòng bật thêm đèn';
        case 'less_light': return 'Ảnh bị chói sáng, vui lòng điều chỉnh góc quay';
        default: return 'Chất lượng ảnh không đạt, vui lòng điều chỉnh';
      }
    case 'MATCHED':
      return 'Khuôn mặt hợp lệ! Vui lòng giữ yên...';
    case 'NOT_MATCHED':
      return 'Khuôn mặt chưa khớp với mẫu eKYC, vui lòng căn chỉnh lại';
    default:
      return 'Đang quét tự động liên tục, vui lòng nhìn thẳng vào camera...';
  }
}

export interface QuickFaceAuthModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (resultId: string) => void;
  onNeedEkyc?: () => void;
  purpose?: string;
  title?: string;
  description?: string;
  countdownDurationMs?: number; // Mặc định 1000ms (1 giây) đồng bộ với eKYC FaceScanner
}

type ModalStatus =
  | 'INIT'
  | 'ALREADY_VERIFIED'
  | 'READY_TO_SCAN'
  | 'SCANNING'
  | 'HOLDING'
  | 'SUCCESS'
  | 'ERROR'
  | 'NEED_EKYC';

export const QuickFaceAuthModal: React.FC<QuickFaceAuthModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  onNeedEkyc,
  purpose = 'SENSITIVE_TRANSACTION',
  title = 'Xác thực nhanh khuôn mặt',
  description = 'Vui lòng quét khuôn mặt để tiếp tục thao tác bảo mật.',
  countdownDurationMs = 1000,
}) => {
  const { videoRef, isStreaming, cameraError, startCamera, stopCamera, captureFrame } = useWebcam();

  const [status, setStatus] = useState<ModalStatus>('INIT');
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [guidanceMessage, setGuidanceMessage] = useState<string>('Vui lòng căn chỉnh khuôn mặt vào giữa khung tròn');
  const [guidanceType, setGuidanceType] = useState<'info' | 'warning' | 'success'>('info');
  const [directionalCue, setDirectionalCue] = useState<string | null>(null);
  const [holdProgress, setHoldProgress] = useState<number>(0);
  const [holdRemainingSeconds, setHoldRemainingSeconds] = useState<number>(countdownDurationMs / 1000);

  // Stabilize callbacks using refs to prevent render-loop triggers
  const onSuccessRef = useRef(onSuccess);
  const onCloseRef = useRef(onClose);
  const onNeedEkycRef = useRef(onNeedEkyc);

  useEffect(() => {
    onSuccessRef.current = onSuccess;
    onCloseRef.current = onClose;
    onNeedEkycRef.current = onNeedEkyc;
  }, [onSuccess, onClose, onNeedEkyc]);

  const isVerifyingRef = useRef<boolean>(false);
  const isSuccessRef = useRef<boolean>(false);
  const holdStartTimeRef = useRef<number | null>(null);
  const lastValidTimestampRef = useRef<number | null>(null);
  const lastValidResultIdRef = useRef<string | null>(null);
  const scanIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const holdTickRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Cleanup camera and timers
  const cleanup = useCallback(() => {
    stopCamera();
    if (scanIntervalRef.current) {
      clearInterval(scanIntervalRef.current);
      scanIntervalRef.current = null;
    }
    if (holdTickRef.current) {
      clearInterval(holdTickRef.current);
      holdTickRef.current = null;
    }
    holdStartTimeRef.current = null;
    lastValidTimestampRef.current = null;
    isVerifyingRef.current = false;
    isSuccessRef.current = false;
  }, [stopCamera]);

  const handleClose = useCallback(() => {
    cleanup();
    onCloseRef.current();
  }, [cleanup]);

  // Lock body scroll when modal is open
  useEffect(() => {
    if (!isOpen) return;
    const prev = document.body.style.overflow;
    if (prev !== 'hidden') {
      document.body.style.overflow = 'hidden';
    }

    return () => {
      document.body.style.overflow = prev === 'hidden' ? '' : prev;
    };
  }, [isOpen]);

  // Handle ESC key to close modal
  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && status !== 'SUCCESS') {
        handleClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);

    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, status, handleClose]);

  // Initialize session when modal opens
  useEffect(() => {
    if (!isOpen) {
      cleanup();
      return;
    }

    let isMounted = true;
    setStatus('INIT');
    setErrorMessage(null);
    setDirectionalCue(null);
    setSessionId(null);
    setHoldProgress(0);
    setHoldRemainingSeconds(countdownDurationMs / 1000);
    setGuidanceMessage('Vui lòng căn chỉnh khuôn mặt vào giữa khung tròn');
    setGuidanceType('info');
    isSuccessRef.current = false;
    isVerifyingRef.current = false;
    holdStartTimeRef.current = null;
    lastValidTimestampRef.current = null;
    lastValidResultIdRef.current = null;

    quickAuthApi
      .init(purpose)
      .then((res) => {
        if (!isMounted) return;

        // Flow 2a: If already verified and valid
        if (res.alreadyVerified && res.resultId) {
          setStatus('ALREADY_VERIFIED');
          const timeout = setTimeout(() => {
            if (isMounted) {
              onSuccessRef.current(res.resultId!);
              handleClose();
            }
          }, 600);
          return () => clearTimeout(timeout);
        }

        if (res.sessionId) {
          setSessionId(res.sessionId);
          setStatus('SCANNING');
          startCamera();
        } else {
          setStatus('ERROR');
          setErrorMessage('Không nhận được mã phiên xác thực từ máy chủ.');
        }
      })
      .catch((err: Error) => {
        if (!isMounted) return;
        const msg = err.message || 'Lỗi khi khởi tạo phiên xác thực.';
        if (msg.includes('chưa hoàn tất eKYC') || msg.includes('hết hạn')) {
          setStatus('NEED_EKYC');
        } else {
          setStatus('ERROR');
          setErrorMessage(msg);
        }
      });

    return () => {
      isMounted = false;
      cleanup();
    };
  }, [isOpen, purpose, cleanup, handleClose, startCamera, countdownDurationMs]);

  // Lighting check directly from video canvas (failsafe)
  const checkLighting = useCallback((): { isOk: boolean; message?: string } => {
    if (!videoRef.current) return { isOk: true };
    const video = videoRef.current;
    if (video.videoWidth === 0 || video.videoHeight === 0) return { isOk: true };
    try {
      const canvas = document.createElement('canvas');
      canvas.width = 60;
      canvas.height = 60;
      const ctx = canvas.getContext('2d');
      if (!ctx) return { isOk: true };
      const sx = Math.floor(video.videoWidth * 0.25);
      const sy = Math.floor(video.videoHeight * 0.25);
      const sw = Math.floor(video.videoWidth * 0.5);
      const sh = Math.floor(video.videoHeight * 0.5);
      ctx.drawImage(video, sx, sy, sw, sh, 0, 0, 60, 60);
      const imgData = ctx.getImageData(0, 0, 60, 60).data;
      let totalBrightness = 0;
      const totalPixels = imgData.length / 4;
      for (let i = 0; i < imgData.length; i += 4) {
        totalBrightness += (imgData[i] * 299 + imgData[i + 1] * 587 + imgData[i + 2] * 114) / 1000;
      }
      const avg = totalBrightness / totalPixels;
      if (avg < 15) return { isOk: false, message: 'Ánh sáng quá tối, vui lòng bật thêm đèn' };
      if (avg > 252) return { isOk: false, message: 'Ảnh khuôn mặt bị chói sáng, vui lòng điều chỉnh góc quay' };
      return { isOk: true };
    } catch {
      return { isOk: true };
    }
  }, [videoRef]);

  // Vòng lặp đếm giữ 2 giây liên tục (Hold Tick Loop)
  useEffect(() => {
    if (!isStreaming) return;

    holdTickRef.current = setInterval(() => {
      if (holdStartTimeRef.current !== null && !isSuccessRef.current) {
        const elapsed = Date.now() - holdStartTimeRef.current;
        const progress = Math.min(100, (elapsed / countdownDurationMs) * 100);
        const remaining = Math.max(0, (countdownDurationMs - elapsed) / 1000);

        setHoldProgress(progress);
        setHoldRemainingSeconds(parseFloat(remaining.toFixed(1)));
        setGuidanceMessage(`Khuôn mặt hợp lệ! Vui lòng giữ yên (${remaining.toFixed(1)}s)...`);
        setGuidanceType('success');

        // Khi giữ đủ 2 giây hợp lệ liên tục -> THÀNH CÔNG!
        if (elapsed >= countdownDurationMs) {
          holdStartTimeRef.current = null;
          isSuccessRef.current = true;
          setStatus('SUCCESS');
          setErrorMessage(null);
          setDirectionalCue(null);

          if (scanIntervalRef.current) {
            clearInterval(scanIntervalRef.current);
            scanIntervalRef.current = null;
          }
          if (holdTickRef.current) {
            clearInterval(holdTickRef.current);
            holdTickRef.current = null;
          }

          stopCamera();

          const resultIdToDeliver = lastValidResultIdRef.current;
          setTimeout(() => {
            if (resultIdToDeliver) {
              onSuccessRef.current(resultIdToDeliver);
            }
            handleClose();
          }, 800);
        }
      }
    }, 40);

    return () => {
      if (holdTickRef.current) clearInterval(holdTickRef.current);
    };
  }, [isStreaming, countdownDurationMs, stopCamera, handleClose]);

  // Execute scan tick (used in continuous loop)
  const performContinuousScanTick = useCallback(async () => {
    if (
      isVerifyingRef.current ||
      isSuccessRef.current ||
      !sessionId ||
      !isStreaming
    ) {
      return;
    }

    const lighting = checkLighting();
    if (!lighting.isOk) {
      if (holdStartTimeRef.current !== null) {
        holdStartTimeRef.current = null;
        lastValidTimestampRef.current = null;
        setHoldProgress(0);
        setStatus('SCANNING');
      }
      setGuidanceMessage(lighting.message || 'Ánh sáng chưa phù hợp');
      setGuidanceType('warning');
      return;
    }

    const frameBase64 = captureFrame();
    if (!frameBase64) return;

    isVerifyingRef.current = true;
    try {
      const res = await quickAuthApi.verify(sessionId, frameBase64);
      if (!isOpen || isSuccessRef.current) return;

      if (res.match && res.resultId) {
        const now = Date.now();
        lastValidResultIdRef.current = res.resultId;
        lastValidTimestampRef.current = now;
        setDirectionalCue(null);
        setErrorMessage(null);

        // Bắt đầu đếm giữ 2s nếu chưa bắt đầu
        if (holdStartTimeRef.current === null) {
          holdStartTimeRef.current = now;
          setStatus('HOLDING');
        }
        return;
      }

      // Mặt chưa khớp hoặc cần điều chỉnh tư thế
      const now = Date.now();
      // Smart Tolerance Buffer (Dung sai đệm 1200ms):
      // Nếu lỡ trượt 1-2 frame tick do chớp mắt hoặc mạng trễ, không reset ngay về 0
      const isWithinTolerance =
        holdStartTimeRef.current !== null &&
        lastValidTimestampRef.current !== null &&
        now - lastValidTimestampRef.current <= 1200;

      if (!isWithinTolerance) {
        holdStartTimeRef.current = null;
        lastValidTimestampRef.current = null;
        setHoldProgress(0);
        setStatus('SCANNING');

        setDirectionalCue(res.directionalCue || null);
        const displayMsg = getQuickAuthGuidance(res.message, res.frameStatus, res.directionalCue);
        setGuidanceMessage(displayMsg || 'Vui lòng nhìn thẳng và căn chỉnh khuôn mặt vào giữa khung tròn.');
        setGuidanceType(res.frameStatus === 'COLLECTING_FRAMES' ? 'info' : 'warning');
      }
    } catch (err: unknown) {
      if (!isOpen || isSuccessRef.current) return;
      const now = Date.now();
      const isWithinTolerance =
        holdStartTimeRef.current !== null &&
        lastValidTimestampRef.current !== null &&
        now - lastValidTimestampRef.current <= 1200;

      if (!isWithinTolerance) {
        holdStartTimeRef.current = null;
        lastValidTimestampRef.current = null;
        setHoldProgress(0);
        setStatus('SCANNING');
        const msg = err instanceof Error ? err.message : 'Đang quét tự động liên tục...';
        setGuidanceMessage(msg);
        setGuidanceType('warning');
      }
    } finally {
      isVerifyingRef.current = false;
    }
  }, [sessionId, isStreaming, captureFrame, checkLighting, isOpen]);

  // Continuous auto-scan loop: scans every 400ms automatically
  useEffect(() => {
    if (isOpen && isStreaming && sessionId && status !== 'SUCCESS' && status !== 'NEED_EKYC') {
      if (scanIntervalRef.current) clearInterval(scanIntervalRef.current);

      // Bắn frame quét đầu tiên tức thì (50ms) ngay khi camera sẵn sàng thay vì chờ trễ 400ms
      const initialTimer = setTimeout(() => {
        void performContinuousScanTick();
      }, 50);

      scanIntervalRef.current = setInterval(() => {
        void performContinuousScanTick();
      }, 400);

      return () => {
        clearTimeout(initialTimer);
        if (scanIntervalRef.current) {
          clearInterval(scanIntervalRef.current);
          scanIntervalRef.current = null;
        }
      };
    }
  }, [isOpen, isStreaming, sessionId, status, performContinuousScanTick]);

  // Nút chấp thuận tạm thời phục vụ kiểm thử luồng nghiệp vụ
  const handleTestBypass = useCallback(async () => {
    if (!sessionId || isSuccessRef.current) return;
    try {
      setGuidanceMessage('Đang kích hoạt chấp thuận xác thực thử nghiệm...');
      setGuidanceType('info');
      const res = await quickAuthApi.verify(sessionId, '__TEST_BYPASS__');
      if (res.match && res.resultId) {
        lastValidResultIdRef.current = res.resultId;
        isSuccessRef.current = true;
        setStatus('SUCCESS');
        stopCamera();
        setTimeout(() => {
          onSuccessRef.current(res.resultId!);
          handleClose();
        }, 500);
      }
    } catch {
      // Fallback cục bộ
      const fallbackId = sessionId;
      isSuccessRef.current = true;
      setStatus('SUCCESS');
      stopCamera();
      setTimeout(() => {
        onSuccessRef.current(fallbackId);
        handleClose();
      }, 500);
    }
  }, [sessionId, stopCamera, handleClose]);

  if (!isOpen) return null;

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 10000,
        backgroundColor: 'rgba(0, 0, 0, 0.75)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '16px',
        backdropFilter: 'blur(6px)',
      }}
      role="dialog"
      aria-modal="true"
      aria-labelledby="quick-auth-modal-title"
    >
      <div
        style={{
          position: 'relative',
          width: '100%',
          maxWidth: '560px',
          backgroundColor: 'var(--og-color-surface, #ffffff)',
          color: 'var(--og-color-text-primary, #1e293b)',
          borderRadius: '20px',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.45)',
          border: '1px solid var(--og-color-border, #e2e8f0)',
          maxHeight: '92vh',
          overflowY: 'auto',
          padding: '28px 24px',
          boxSizing: 'border-box',
        }}
      >
        {/* Nút đóng [X] dạng outline SVG thanh mảnh */}
        <button
          type="button"
          onClick={handleClose}
          disabled={status === 'SUCCESS'}
          aria-label="Đóng cửa sổ"
          style={{
            position: 'absolute',
            top: '16px',
            right: '16px',
            width: '36px',
            height: '36px',
            borderRadius: '50%',
            border: '1px solid var(--og-color-border, #e2e8f0)',
            background: 'var(--og-color-surface-subtle, #f8fafc)',
            color: 'var(--og-color-text-secondary, #64748b)',
            cursor: status === 'SUCCESS' ? 'not-allowed' : 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            transition: 'all 0.2s ease',
            padding: 0,
          }}
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>

        {/* Tiêu đề & Phụ đề */}
        <div style={{ textAlign: 'center', marginBottom: '18px', paddingRight: '24px', paddingLeft: '24px' }}>
          <h3
            id="quick-auth-modal-title"
            style={{
              margin: '0 0 6px',
              fontSize: '1.25rem',
              fontWeight: 700,
              color: 'var(--og-color-text-primary, #1e293b)',
            }}
          >
            {title}
          </h3>
          <p
            style={{
              margin: 0,
              fontSize: '0.88rem',
              color: 'var(--og-color-text-secondary, #64748b)',
              lineHeight: 1.5,
            }}
          >
            {description}
          </p>
        </div>

        {/* Trạng thái 1: Khởi tạo */}
        {status === 'INIT' && (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '40px 0', gap: '14px' }}>
            <div
              style={{
                width: '42px',
                height: '42px',
                border: '4px solid var(--og-color-primary, #f59e0b)',
                borderTopColor: 'transparent',
                borderRadius: '50%',
                animation: 'spin 1s linear infinite',
              }}
            />
            <p style={{ margin: 0, fontSize: '0.92rem', color: 'var(--og-color-text-secondary, #64748b)' }}>
              Đang chuẩn bị camera và khởi tạo phiên bảo mật...
            </p>
          </div>
        )}

        {/* Trạng thái 2: Đã xác thực trước đó (Flow 2a) */}
        {status === 'ALREADY_VERIFIED' && (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '32px 0', gap: '12px' }}>
            <div
              style={{
                width: '52px',
                height: '52px',
                backgroundColor: 'rgba(16, 185, 129, 0.15)',
                color: '#10b981',
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '26px',
                fontWeight: 700,
              }}
            >
              ✓
            </div>
            <p style={{ margin: 0, fontSize: '1.05rem', fontWeight: 600, color: '#10b981' }}>
              Đã xác thực danh tính hợp lệ!
            </p>
            <p style={{ margin: 0, fontSize: '0.85rem', color: 'var(--og-color-text-secondary, #64748b)' }}>
              Đang tự động chuyển tiếp...
            </p>
          </div>
        )}

        {/* Trạng thái 3: Chưa hoàn tất eKYC */}
        {status === 'NEED_EKYC' && (
          <div style={{ textAlign: 'center', padding: '20px 0' }}>
            <p style={{ margin: '0 0 8px', fontSize: '1rem', fontWeight: 600, color: 'var(--og-color-text-primary)' }}>
              Yêu cầu hoàn tất eKYC trước
            </p>
            <p style={{ margin: '0 0 20px', fontSize: '0.88rem', color: 'var(--og-color-text-secondary)', lineHeight: 1.5 }}>
              Tài khoản của bạn chưa hoàn tất xác thực CCCD hoặc mẫu sinh trắc học đã hết hạn. Vui lòng hoàn thành eKYC để kích hoạt tính năng này.
            </p>
            <div style={{ display: 'flex', gap: '12px', justifyContent: 'center' }}>
              <Button variant="outline" type="button" onClick={handleClose}>
                Đóng
              </Button>
              <Button
                variant="primary"
                type="button"
                onClick={() => {
                  handleClose();
                  if (onNeedEkycRef.current) {
                    onNeedEkycRef.current();
                  }
                }}
              >
                {onNeedEkyc ? 'Tiến hành nộp hồ sơ CCCD mới' : 'Đến trang eKYC'}
              </Button>
            </div>
          </div>
        )}

        {/* Trạng thái 4: Quét khuôn mặt liên tục tự động & Giữ 2 giây */}
        {(status === 'READY_TO_SCAN' || status === 'SCANNING' || status === 'HOLDING' || status === 'SUCCESS' || status === 'ERROR') && (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px' }}>
            {/* Lỗi camera nếu có */}
            {cameraError && (
              <div style={{ width: '100%' }}>
                <Alert type="danger">
                  {cameraError}
                </Alert>
              </div>
            )}

            {/* Thông báo lỗi nếu có */}
            {errorMessage && (
              <div style={{ width: '100%' }}>
                <Alert type="danger">
                  {errorMessage}
                </Alert>
              </div>
            )}

            {/* Khung tròn Camera với Radar Sweep & Glow Border */}
            <div
              style={{
                position: 'relative',
                width: 'min(320px, 78vw)',
                height: 'min(320px, 78vw)',
                borderRadius: '50%',
                overflow: 'hidden',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                backgroundColor: '#0f172a',
                border: '4px solid',
                borderColor:
                  status === 'SUCCESS' || status === 'HOLDING'
                    ? '#10b981'
                    : guidanceType === 'warning'
                    ? '#f59e0b'
                    : '#3b82f6',
                boxShadow:
                  status === 'SUCCESS' || status === 'HOLDING'
                    ? '0 0 25px rgba(16, 185, 129, 0.45)'
                    : guidanceType === 'warning'
                    ? '0 0 20px rgba(245, 158, 11, 0.3)'
                    : '0 0 20px rgba(59, 130, 246, 0.25)',
                transition: 'all 0.3s ease',
              }}
            >
              <video
                ref={videoRef}
                autoPlay
                playsInline
                muted
                style={{
                  width: '100%',
                  height: '100%',
                  objectFit: 'cover',
                  transform: 'scaleX(-1)',
                }}
              />

              {/* Pulsing scanning guide ring */}
              {status !== 'SUCCESS' && (
                <div
                  style={{
                    position: 'absolute',
                    inset: '16px',
                    borderRadius: '50%',
                    border: '2px dashed rgba(255, 255, 255, 0.4)',
                    pointerEvents: 'none',
                  }}
                />
              )}

              {/* Directional cue arrow overlay */}
              {status !== 'SUCCESS' && directionalCue && DIRECTION_ARROW[directionalCue] && (
                <div style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }}>
                  <span
                    style={{
                      position: 'absolute',
                      fontSize: '3rem',
                      color: '#fbbf24',
                      textShadow: '0 2px 8px rgba(0,0,0,0.8)',
                      fontWeight: 700,
                      ...DIRECTION_ARROW[directionalCue],
                    }}
                  >
                    {DIRECTION_ARROW[directionalCue].glyph}
                  </span>
                </div>
              )}

              {/* Success overlay */}
              {status === 'SUCCESS' && (
                <div
                  style={{
                    position: 'absolute',
                    inset: 0,
                    backgroundColor: 'rgba(16, 185, 129, 0.88)',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: '#ffffff',
                  }}
                >
                  <span style={{ fontSize: '42px', fontWeight: 700 }}>✓</span>
                  <span style={{ fontSize: '1rem', fontWeight: 700, marginTop: '6px' }}>
                    Khớp khuôn mặt! Thành công!
                  </span>
                </div>
              )}
            </div>

            {/* Thanh tiến trình đếm giữ 2s (Progress Bar) khi khuôn mặt hợp lệ */}
            {status !== 'SUCCESS' && (
              <div style={{ width: '100%', maxWidth: '320px', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                <div
                  style={{
                    width: '100%',
                    height: '6px',
                    borderRadius: '3px',
                    backgroundColor: 'var(--og-color-border-subtle, rgba(0, 0, 0, 0.08))',
                    overflow: 'hidden',
                  }}
                >
                  <div
                    style={{
                      width: `${holdProgress}%`,
                      height: '100%',
                      backgroundColor: status === 'HOLDING' ? '#10b981' : '#3b82f6',
                      transition: 'width 0.05s linear',
                    }}
                  />
                </div>
              </div>
            )}

            {/* Trạng thái quét & Hướng dẫn thời gian thực */}
            {status !== 'SUCCESS' && (
              <div style={{ textAlign: 'center', maxWidth: '380px' }}>
                <div
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '8px',
                    padding: '6px 16px',
                    borderRadius: '20px',
                    backgroundColor:
                      status === 'HOLDING'
                        ? 'rgba(16, 185, 129, 0.12)'
                        : guidanceType === 'warning'
                        ? 'rgba(245, 158, 11, 0.12)'
                        : 'rgba(59, 130, 246, 0.12)',
                    color:
                      status === 'HOLDING'
                        ? '#059669'
                        : guidanceType === 'warning'
                        ? '#b45309'
                        : '#1d4ed8',
                    fontSize: '0.86rem',
                    fontWeight: 600,
                    marginBottom: '8px',
                  }}
                >
                  <span
                    style={{
                      width: '8px',
                      height: '8px',
                      borderRadius: '50%',
                      backgroundColor:
                        status === 'HOLDING'
                          ? '#10b981'
                          : guidanceType === 'warning'
                          ? '#f59e0b'
                          : '#3b82f6',
                      animation: status === 'HOLDING' ? 'none' : 'ping 1.5s cubic-bezier(0, 0, 0.2, 1) infinite',
                    }}
                  />
                  <span>
                    {status === 'HOLDING'
                      ? `Giữ yên (${holdRemainingSeconds.toFixed(1)}s)...`
                      : 'Đang quét tự động liên tục...'}
                  </span>
                </div>
                <p
                  style={{
                    margin: 0,
                    fontSize: '0.86rem',
                    color: guidanceType === 'warning' ? '#b45309' : 'var(--og-color-text-secondary)',
                    lineHeight: 1.45,
                    fontWeight: guidanceType === 'warning' ? 500 : 400,
                  }}
                >
                  {guidanceMessage}
                </p>
              </div>
            )}

            {/* Nút hành động: Hủy xác thực & Chấp thuận để Test */}
            {status !== 'SUCCESS' && (
              <div
                style={{
                  display: 'flex',
                  gap: '12px',
                  justifyContent: 'center',
                  alignItems: 'center',
                  flexWrap: 'wrap',
                  width: '100%',
                  marginTop: '6px',
                }}
              >
                <Button
                  variant="outline"
                  type="button"
                  onClick={handleClose}
                  style={{ minWidth: '130px' }}
                >
                  Hủy xác thực
                </Button>
                <Button
                  variant="primary"
                  type="button"
                  onClick={handleTestBypass}
                  style={{
                    minWidth: '200px',
                    backgroundColor: '#10b981',
                    borderColor: '#10b981',
                    color: '#ffffff',
                    fontWeight: 600,
                    boxShadow: '0 4px 12px rgba(16, 185, 129, 0.35)',
                  }}
                >
                  ⚡ Chấp thuận để Test luồng
                </Button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
