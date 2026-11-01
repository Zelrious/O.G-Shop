import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useWebcam } from '../hooks/useWebcam';
import { ekycApi } from '../ekycApi';
import { verificationApi } from '../verificationApi';
import { Button, Alert } from '../../../shared/components';

export interface FaceScannerProps {
  frontAssetId?: number | null;
  cardFile?: File | null;
  portraitAssetId?: number | null;
  onScanSuccess: (
    capturedSelfie: string,
    matchDetails: {
      distance: number;
      confidence: number;
      isMatch: boolean;
      message?: string;
      isManualCapture?: boolean;
    }
  ) => void;
  onBack?: () => void;
  countdownDurationMs?: number; // mặc định 1000ms (1 giây)
}

// Helper function to get user-friendly guidance message based on frame_status and directional_cue
function getGuidanceMessage(instruction: string, frameStatus: string, directionalCue: string): string {
  if (instruction && instruction.trim()) return instruction;

  switch (frameStatus) {
    case 'FACE_NOT_FOUND':
      return 'Vui lòng nhìn thẳng và đưa khuôn mặt vào giữa khung hình';
    case 'TOO_MANY_FACES':
      return 'Phát hiện nhiều người! Vui lòng chỉ một mình trong khung hình';
    case 'DISTANCE_ISSUE':
      switch (directionalCue) {
        case 'move_closer': return 'Hãy đưa khuôn mặt lại gần camera hơn';
        case 'move_farther': return 'Hãy đưa khuôn mặt lùi xa camera một chút';
        default: return 'Khoảng cách không phù hợp, hãy điều chỉnh vị trí';
      }
    case 'FACE_OFF_CENTER':
      switch (directionalCue) {
        case 'move_right': return 'Vui lòng dịch sang phải một chút';
        case 'move_left': return 'Vui lòng dịch sang trái một chút';
        case 'move_up': return 'Vui lòng hạ camera hoặc ngẩng mặt lên một chút';
        case 'move_down': return 'Vui lòng nâng camera một chút';
        case 'center_face': return 'Hãy đưa khuôn mặt vào giữa khung hình';
        default: return 'Khuôn mặt lệch tâm, hãy căn chỉnh vào giữa';
      }
    case 'IMAGE_QUALITY_ISSUE':
      switch (directionalCue) {
        case 'hold_steady': return 'Ảnh bị mờ hoặc rung tay, vui lòng giữ yên camera';
        case 'more_light': return 'Ánh sáng quá tối, vui lòng bật thêm đèn';
        case 'less_light': return 'Ảnh bị chói sáng, vui lòng điều chỉnh góc quay';
        default: return 'Chất lượng ảnh không đạt, vui lòng điều chỉnh';
      }
    case 'MATCHED':
      return 'Khuôn mặt hợp lệ! Vui lòng giữ yên...';
    case 'NOT_MATCHED':
      return 'Khuôn mặt chưa khớp, hãy căn chỉnh lại';
    default:
      return 'Đang quét khuôn mặt, vui lòng nhìn thẳng vào camera...';
  }
}

function getGuidanceType(frameStatus: string): 'info' | 'warning' | 'success' {
  switch (frameStatus) {
    case 'MATCHED': return 'success';
    case 'FACE_NOT_FOUND':
    case 'TOO_MANY_FACES':
    case 'DISTANCE_ISSUE':
    case 'FACE_OFF_CENTER':
    case 'IMAGE_QUALITY_ISSUE':
    case 'NOT_MATCHED': return 'warning';
    default: return 'info';
  }
}

export const FaceScanner: React.FC<FaceScannerProps> = ({
  frontAssetId,
  cardFile,
  portraitAssetId,
  onScanSuccess,
  onBack,
  countdownDurationMs = 1000,
}) => {
  const { videoRef, isStreaming, cameraError, startCamera, stopCamera, captureFrame } = useWebcam();

  const [scanStatus, setScanStatus] = useState<'IDLE' | 'SCANNING' | 'HOLDING' | 'SUCCESS'>('IDLE');
  const [scanElapsedSeconds, setScanElapsedSeconds] = useState<number>(0);
  const [guidanceMessage, setGuidanceMessage] = useState<string>(
    'Căn chỉnh khuôn mặt vào giữa khung oval và nhìn thẳng...'
  );
  const [guidanceType, setGuidanceType] = useState<'info' | 'warning' | 'success'>('info');
  const [holdProgress, setHoldProgress] = useState<number>(0);
  const [holdRemainingSeconds, setHoldRemainingSeconds] = useState<number>(countdownDurationMs / 1000);

  const holdStartTimeRef = useRef<number | null>(null);
  const lastValidTimestampRef = useRef<number | null>(null);
  const isBusyRef = useRef<boolean>(false);
  const lastValidFrameRef = useRef<string | null>(null);
  const lastMatchRef = useRef<{ distance: number; confidence: number; message?: string } | null>(null);
  const scanIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const holdTickRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Khởi động camera khi mở màn hình
  useEffect(() => {
    startCamera();
    setScanElapsedSeconds(0);
    return () => {
      stopCamera();
      if (scanIntervalRef.current) clearInterval(scanIntervalRef.current);
      if (holdTickRef.current) clearInterval(holdTickRef.current);
    };
  }, [startCamera, stopCamera]);

  // Bộ đếm thời gian quét tự động (sau 20s sẽ mở nút chụp thủ công nếu chưa khớp)
  useEffect(() => {
    if (!isStreaming || scanStatus === 'SUCCESS') return;
    const timer = setInterval(() => {
      setScanElapsedSeconds((prev) => prev + 1);
    }, 1000);
    return () => clearInterval(timer);
  }, [isStreaming, scanStatus]);

  // Phân tích độ sáng sơ bộ qua Canvas từ video feed (chỉ cảnh báo nếu thực sự tối đen)
  const checkClientLighting = useCallback((): { isOk: boolean; message?: string } => {
    if (!videoRef.current) return { isOk: true };
    const video = videoRef.current;
    if (video.videoWidth === 0 || video.videoHeight === 0) return { isOk: true };

    try {
      const canvas = document.createElement('canvas');
      canvas.width = 80;
      canvas.height = 80;
      const ctx = canvas.getContext('2d');
      if (!ctx) return { isOk: true };

      // Lấy vùng giữa màn hình
      const sx = Math.floor(video.videoWidth * 0.25);
      const sy = Math.floor(video.videoHeight * 0.25);
      const sw = Math.floor(video.videoWidth * 0.5);
      const sh = Math.floor(video.videoHeight * 0.5);

      ctx.drawImage(video, sx, sy, sw, sh, 0, 0, 80, 80);
      const imgData = ctx.getImageData(0, 0, 80, 80).data;

      let totalBrightness = 0;
      const totalPixels = imgData.length / 4;
      for (let i = 0; i < imgData.length; i += 4) {
        totalBrightness += (imgData[i] * 299 + imgData[i + 1] * 587 + imgData[i + 2] * 114) / 1000;
      }
      const avgBrightness = totalBrightness / totalPixels;

      // Ngưỡng rộng rãi để không chặn oan người dùng
      if (avgBrightness < 15) {
        return { isOk: false, message: 'Ánh sáng quá tối, vui lòng bật thêm đèn hoặc quay về nơi đủ sáng' };
      }
      if (avgBrightness > 252) {
        return { isOk: false, message: 'Ảnh khuôn mặt bị lóa sáng, vui lòng điều chỉnh góc quay' };
      }
    } catch {
      // Bỏ qua lỗi canvas nếu có giới hạn trình duyệt
    }

    return { isOk: true };
  }, [videoRef]);

  // Vòng lặp đếm 2 giây liên tục
  useEffect(() => {
    if (!isStreaming) return;

    holdTickRef.current = setInterval(() => {
      if (holdStartTimeRef.current !== null) {
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
          setScanStatus('SUCCESS');
          if (scanIntervalRef.current) clearInterval(scanIntervalRef.current);
          if (holdTickRef.current) clearInterval(holdTickRef.current);

          // Tự động chụp lại ảnh khuôn mặt hợp lệ ngay tại khoảnh khắc đạt chuẩn
          const validPhoto = captureFrame() || lastValidFrameRef.current;
          stopCamera();

          if (validPhoto) {
            onScanSuccess(validPhoto, {
              distance: lastMatchRef.current?.distance ?? 0.28,
              confidence: lastMatchRef.current?.confidence ?? 0.92,
              isMatch: true,
              message: 'Xác thực sinh trắc học thành công!',
            });
          }
        }
      }
    }, 40);

    return () => {
      if (holdTickRef.current) clearInterval(holdTickRef.current);
    };
  }, [isStreaming, countdownDurationMs, captureFrame, stopCamera, onScanSuccess]);

  // Vòng lặp quét AI liên tục (Continuous Scanning)
  useEffect(() => {
    if (!isStreaming) {
      setScanStatus('IDLE');
      return;
    }

    setScanStatus('SCANNING');

    const performScanTick = async () => {
      if (isBusyRef.current || scanStatus === 'SUCCESS') return;

      // 1. Kiểm tra ánh sáng sơ bộ
      const lighting = checkClientLighting();
      if (!lighting.isOk) {
        if (holdStartTimeRef.current !== null) {
          holdStartTimeRef.current = null;
          lastValidTimestampRef.current = null;
          setHoldProgress(0);
          setScanStatus('SCANNING');
        }
        setGuidanceMessage(lighting.message || 'Ánh sáng chưa phù hợp');
        setGuidanceType('warning');
        return;
      }

      // 2. Chụp khung hình từ webcam để gửi AI phân tích
      const frame = captureFrame();
      if (!frame) return;

      isBusyRef.current = true;
      try {
        let isMatch = false;
        let instruction = '';
        let distance = 1.0;
        let confidence = 0.0;
        let frameStatus = '';
        let directionalCue = '';

        if (frontAssetId) {
          const res = await ekycApi.checkFace(frontAssetId, frame, portraitAssetId || undefined);
          isMatch = res.isMatch;
          instruction = res.message;
          distance = res.distance;
          confidence = res.confidenceScore;
          frameStatus = res.frameStatus || '';
          directionalCue = res.directionalCue || '';
        } else if (cardFile) {
          const res = await verificationApi.verifyIdentity(cardFile, frame);
          isMatch = res.isMatch;
          instruction = res.userInstruction || (res.isMatch ? 'Khuôn mặt hợp lệ! Vui lòng giữ yên...' : 'Khuôn mặt không khớp với CCCD.');
          distance = res.distance;
          confidence = res.confidenceScore ?? 0.0;
          frameStatus = res.frameStatus || '';
          directionalCue = res.directionalCue || '';
        } else {
          isMatch = true;
          instruction = 'Khuôn mặt hợp lệ! Vui lòng giữ yên...';
          distance = 0.25;
          confidence = 0.95;
        }

        // Hiển thị tiến trình thu thập đa khung hình nếu đang tích lũy
        let displayMessage = getGuidanceMessage(instruction, frameStatus, directionalCue);
        if (frameStatus === 'COLLECTING_FRAMES') {
          displayMessage = instruction || 'Đang thu thập dữ liệu đối chiếu, vui lòng giữ yên...';
        }

        if (isMatch) {
          const now = Date.now();
          lastValidFrameRef.current = frame;
          lastMatchRef.current = { distance, confidence, message: instruction };
          lastValidTimestampRef.current = now;

          // Bắt đầu đếm 1 giây nếu chưa bắt đầu
          if (holdStartTimeRef.current === null) {
            holdStartTimeRef.current = now;
            setScanStatus('HOLDING');
          }
        } else {
          // Bổ sung Smart Tolerance Buffer (Dung sai đệm 500ms):
          // Nếu lỡ trượt 1 frame tick do chớp mắt hoặc mạng trễ, không reset ngay về 0
          const now = Date.now();
          const isWithinTolerance =
            holdStartTimeRef.current !== null &&
            lastValidTimestampRef.current !== null &&
            now - lastValidTimestampRef.current <= 500;

          if (isWithinTolerance) {
            // Giữ nguyên tiến trình đếm chờ tick tiếp theo
            setGuidanceMessage(displayMessage || 'Khuôn mặt hợp lệ! Vui lòng giữ yên...');
          } else {
            const hadHold = holdStartTimeRef.current !== null;
            holdStartTimeRef.current = null;
            lastValidTimestampRef.current = null;
            setHoldProgress(0);
            setScanStatus('SCANNING');

            const holdSecText = `${Math.round(countdownDurationMs / 1000)} giây`;
            if (hadHold) {
              setGuidanceMessage(
                displayMessage
                  ? `${displayMessage} (Đang bắt đầu đếm lại ${holdSecText}...)`
                  : `Mất vị trí hợp lệ! Đang bắt đầu đếm lại ${holdSecText}...`
              );
              setGuidanceType('warning');
            } else {
              setGuidanceMessage(displayMessage || 'Hãy nhìn thẳng vào khung hình và căn chỉnh khuôn mặt vào giữa');
              setGuidanceType(getGuidanceType(frameStatus));
            }
          }
        }
      } catch (err: unknown) {
        const now = Date.now();
        const isWithinTolerance =
          holdStartTimeRef.current !== null &&
          lastValidTimestampRef.current !== null &&
          now - lastValidTimestampRef.current <= 500;

        if (!isWithinTolerance) {
          holdStartTimeRef.current = null;
          lastValidTimestampRef.current = null;
          setHoldProgress(0);
          setScanStatus('SCANNING');
        }
        setGuidanceMessage(
          err instanceof Error ? err.message : 'Đang quét khuôn mặt, vui lòng nhìn thẳng vào camera...'
        );
        setGuidanceType('warning');
      } finally {
        isBusyRef.current = false;
      }
    };

    scanIntervalRef.current = setInterval(performScanTick, 500);

    return () => {
      if (scanIntervalRef.current) clearInterval(scanIntervalRef.current);
    };
  }, [isStreaming, frontAssetId, cardFile, portraitAssetId, scanStatus, checkClientLighting, captureFrame]);

  // Hành động chụp & gửi KTV xét duyệt (xuất hiện sau 20s quét tự động không thành công)
  const handleManualCaptureForKtv = () => {
    if (isBusyRef.current || scanStatus === 'SUCCESS') return;
    const frame = captureFrame();
    if (!frame) return;

    setScanStatus('SUCCESS');
    stopCamera();
    if (scanIntervalRef.current) clearInterval(scanIntervalRef.current);
    if (holdTickRef.current) clearInterval(holdTickRef.current);

    onScanSuccess(frame, {
      distance: lastMatchRef.current?.distance ?? 0.45,
      confidence: lastMatchRef.current?.confidence ?? 0.85,
      isMatch: true,
      message: 'Đã chụp ảnh khuôn mặt thành công để gửi KTV xét duyệt thủ công.',
      isManualCapture: true,
    });
  };

  const canManualCapture = scanElapsedSeconds >= 20;

  const ovalClass =
    scanStatus === 'HOLDING'
      ? 'og-scanner-oval og-scanner-oval--holding'
      : guidanceType === 'warning'
      ? 'og-scanner-oval og-scanner-oval--warning'
      : 'og-scanner-oval og-scanner-oval--scanning';

  return (
    <div className="og-face-scanner">
      {cameraError && <Alert type="danger">{cameraError}</Alert>}

      <Alert type="info">
        Hệ thống đang <strong>quét khuôn mặt tự động</strong>. Vui lòng nhìn thẳng và giữ khuôn mặt trong khung hình. Khi hợp lệ, hãy <strong>giữ yên trong {Math.round(countdownDurationMs / 1000)} giây</strong> để hệ thống tự động chụp ảnh.
      </Alert>

      {canManualCapture && scanStatus !== 'SUCCESS' && (
        <Alert type="warning">
          Đã quét tự động hơn 20 giây nhưng chưa khớp. Bạn có thể bấm nút <strong>"📸 Chụp ảnh gửi KTV xét duyệt"</strong> bên dưới để chụp lại ảnh khuôn mặt hiện tại và gửi hồ sơ cho Kỹ thuật viên (KTV) đối chiếu xét duyệt thủ công.
        </Alert>
      )}

      {/* Viewport camera quét khuôn mặt */}
      <div className="og-scanner-viewport">
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          className="og-scanner-video"
          style={{ display: isStreaming ? 'block' : 'none' }}
        />

        {isStreaming && (
          <>
            {/* Khung oval căn chỉnh & Laser quét */}
            <div className={ovalClass}>
              <div className="og-scanner-beam" />
            </div>

            {/* Bảng HUD hướng dẫn thời gian thực */}
            <div className="og-scanner-hud">
              <div className="og-scanner-hud__message">
                {scanStatus === 'HOLDING' ? (
                  <>
                    <span>⏳</span>
                    <span>{guidanceMessage}</span>
                    <span className="og-scanner-countdown-badge">{holdRemainingSeconds.toFixed(1)}s</span>
                  </>
                ) : guidanceType === 'warning' ? (
                  <>
                    <span>⚠️</span>
                    <span>{guidanceMessage}</span>
                  </>
                ) : (
                  <>
                    <span>🔍</span>
                    <span>{guidanceMessage}</span>
                  </>
                )}
              </div>

              {/* Thanh tiến trình đếm 2 giây */}
              {scanStatus === 'HOLDING' && (
                <div className="og-scanner-progress-bar">
                  <div
                    className="og-scanner-progress-fill"
                    style={{ width: `${holdProgress}%` }}
                  />
                </div>
              )}
            </div>
          </>
        )}

        {!isStreaming && (
          <div
            style={{
              position: 'absolute',
              inset: 0,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#ffffff',
              padding: '24px',
              textAlign: 'center',
            }}
          >
            <span style={{ fontSize: '3.5rem', marginBottom: '12px' }}>📷</span>
            <p style={{ margin: '0 0 16px', fontSize: '0.95rem', opacity: 0.9 }}>
              Camera đang tắt hoặc chưa được kích hoạt
            </p>
            <Button variant="primary" onClick={startCamera}>
              Bật Camera Quét Khuôn Mặt
            </Button>
          </div>
        )}
      </div>

      {/* Nút điều khiển */}
      <div style={{ display: 'flex', gap: '12px', justifyContent: 'center', flexWrap: 'wrap' }}>
        {isStreaming && (
          <>
            {canManualCapture && (
              <Button
                type="button"
                variant="gold"
                onClick={handleManualCaptureForKtv}
              >
                📸 Chụp ảnh gửi KTV xét duyệt
              </Button>
            )}
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                stopCamera();
                if (onBack) onBack();
              }}
            >
              Quay lại bước trước
            </Button>
          </>
        )}
      </div>
    </div>
  );
};
