import React, { useState } from 'react';
import { useWebcam } from '../hooks/useWebcam';
import { BiometricScanResult } from '../types';
import { verificationApi } from '../verificationApi';
import { Button, Alert, Badge } from '../../../shared/components';

interface CameraCaptureProps {
  onScanComplete: (result: BiometricScanResult) => void;
  cardFile: File;
}

export const CameraCapture: React.FC<CameraCaptureProps> = ({
  onScanComplete,
  cardFile,
}) => {
  const { videoRef, isStreaming, cameraError, startCamera, stopCamera, captureFrame } = useWebcam();
  const [capturedPhoto, setCapturedPhoto] = useState<string | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const [scanResult, setScanResult] = useState<BiometricScanResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleStart = async () => {
    setError(null);
    setCapturedPhoto(null);
    setScanResult(null);
    await startCamera();
  };

  const handleCaptureAndMatch = async () => {
    const frameBase64 = captureFrame();
    if (!frameBase64) {
      setError('Không thể chụp hình ảnh từ webcam.');
      return;
    }

    setCapturedPhoto(frameBase64);
    stopCamera();
    setIsProcessing(true);
    setError(null);

    try {
      const result = await verificationApi.verifyIdentity(cardFile, frameBase64);
      setScanResult(result);
      if (result.isMatch) {
        onScanComplete(result);
      } else {
        setError(
          result.userInstruction ||
          `Khuôn mặt chụp được không khớp với ảnh trên thẻ CCCD (Khoảng cách: ${result.distance} > 0.50). Vui lòng thử lại.`
        );
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Có lỗi khi so khớp sinh trắc học.');
    } finally {
      setIsProcessing(false);
    }
  };

  const handleRetake = async () => {
    setCapturedPhoto(null);
    setScanResult(null);
    setError(null);
    await startCamera();
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '20px', textAlign: 'center' }}>
      {cameraError && <Alert type="danger">{cameraError}</Alert>}
      {error && <Alert type="danger">{error}</Alert>}

      {/* Vùng Camera tròn */}
      <div className="og-webcam-container">
        {capturedPhoto ? (
          <img
            src={capturedPhoto}
            alt="Ảnh selfie đã chụp"
            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
          />
        ) : (
          <>
            <video
              ref={videoRef}
              autoPlay
              playsInline
              muted
              className="og-webcam-video"
              style={{ display: isStreaming ? 'block' : 'none' }}
            />
            {isStreaming && <div className="og-webcam-guide" />}
            {!isStreaming && (
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  height: '100%',
                  color: '#ffffff',
                  padding: '20px',
                }}
              >
                <span style={{ fontSize: '3rem', marginBottom: '8px' }}>👤</span>
                <span style={{ fontSize: '0.9rem', opacity: 0.85 }}>Camera đang tắt</span>
              </div>
            )}
          </>
        )}
      </div>

      {/* Hướng dẫn người dùng */}
      <div style={{ maxWidth: '420px', fontSize: '0.9rem', color: 'var(--og-color-text-secondary)' }}>
        {!isStreaming && !capturedPhoto && (
          <p>Nhấn nút bên dưới để mở camera. Vui lòng căn chỉnh khuôn mặt thẳng vào khung tròn và đủ ánh sáng.</p>
        )}
        {isStreaming && (
          <p style={{ color: 'var(--og-color-primary)', fontWeight: 600 }}>
            Giữ khuôn mặt ổn định trong vòng tròn và nhấn &quot;Chụp & So khớp&quot;.
          </p>
        )}
      </div>

      {/* Trạng thái AI phân tích */}
      {isProcessing && (
        <Alert type="info">
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span className="og-spinner" style={{ borderColor: 'var(--og-color-info)' }} />
            <span>Mô hình ArcFace đang trích xuất đặc trưng sinh trắc học và đo khoảng cách Cosine...</span>
          </div>
        </Alert>
      )}

      {/* Hiển thị kết quả so khớp thành công */}
      {scanResult && scanResult.isMatch && (
        <Alert type="success">
          <div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px', marginBottom: '6px' }}>
              <strong>✓ Xác thực khuôn mặt thành công!</strong>
              <Badge variant="neutral">
                {scanResult.isSimulated ? 'eKYC mô phỏng — ArcFace' : 'eKYC sandbox'}
              </Badge>
            </div>
            <div style={{ fontSize: '0.85rem' }}>
              • Độ sai lệch Cosine: <code>{scanResult.distance}</code> (Ngưỡng yêu cầu: &le; 0.50)<br />
              {scanResult.confidenceScore !== undefined && (
                <>• Độ tin cậy sinh trắc học: <strong>{(scanResult.confidenceScore * 100).toFixed(1)}%</strong><br /></>
              )}
              • Tình trạng: Trùng khớp với ảnh chân dung trên thẻ CCCD.
            </div>
          </div>
        </Alert>
      )}

      {/* Nút điều khiển */}
      <div style={{ display: 'flex', gap: '12px', justifyContent: 'center' }}>
        {!isStreaming && !capturedPhoto && (
          <Button type="button" variant="primary" onClick={handleStart}>
            Bật Camera Quét Khuôn Mặt
          </Button>
        )}

        {isStreaming && (
          <Button type="button" variant="gold" onClick={handleCaptureAndMatch} isLoading={isProcessing}>
            Chụp &amp; So khớp Khuôn mặt
          </Button>
        )}

        {capturedPhoto && (
          <Button type="button" variant="outline" onClick={handleRetake} disabled={isProcessing}>
            Chụp lại ảnh khác
          </Button>
        )}
      </div>
    </div>
  );
};
