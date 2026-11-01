import React, { useState } from 'react';
import { BiometricScanResult } from '../types';
import { Button, Alert, Badge } from '../../../shared/components';
import { FaceScanner } from './FaceScanner';

interface CameraCaptureProps {
  onScanComplete: (result: BiometricScanResult) => void;
  cardFile: File;
}

export const CameraCapture: React.FC<CameraCaptureProps> = ({
  onScanComplete,
  cardFile,
}) => {
  const [capturedPhoto, setCapturedPhoto] = useState<string | null>(null);
  const [scanResult, setScanResult] = useState<BiometricScanResult | null>(null);

  const handleScanSuccess = (
    selfieBase64: string,
    matchDetails: { distance: number; confidence: number; isMatch: boolean; message?: string }
  ) => {
    const result: BiometricScanResult = {
      isMatch: true,
      distance: matchDetails.distance,
      confidenceScore: matchDetails.confidence,
      capturedImageBase64: selfieBase64,
      userInstruction: matchDetails.message,
      isSimulated: true,
    };
    setCapturedPhoto(selfieBase64);
    setScanResult(result);
    onScanComplete(result);
  };

  const handleRetake = () => {
    setCapturedPhoto(null);
    setScanResult(null);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '20px', textAlign: 'center', width: '100%' }}>
      {!capturedPhoto ? (
        <FaceScanner
          cardFile={cardFile}
          onScanSuccess={handleScanSuccess}
        />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '20px', width: '100%' }}>
          <div className="og-scanner-viewport" style={{ maxWidth: '320px', height: '320px' }}>
            <img
              src={capturedPhoto}
              alt="Ảnh selfie đã chụp tự động"
              style={{ width: '100%', height: '100%', objectFit: 'cover' }}
            />
          </div>

          {scanResult && scanResult.isMatch && (
            <Alert type="success">
              <div>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px', marginBottom: '6px' }}>
                  <strong>✓ Quét &amp; Chụp khuôn mặt thành công!</strong>
                  <Badge variant="neutral">
                    {scanResult.isSimulated ? 'eKYC mô phỏng — ArcFace' : 'eKYC sandbox'}
                  </Badge>
                </div>
                <div style={{ fontSize: '0.85rem' }}>
                  • Độ sai lệch Cosine: <code>{scanResult.distance}</code> (Ngưỡng yêu cầu: &le; {scanResult.thresholdUsed ? scanResult.thresholdUsed.toFixed(2) : '0.68'})<br />
                  {scanResult.confidenceScore !== undefined && (
                    <>• Độ tin cậy sinh trắc học: <strong>{(scanResult.confidenceScore * 100).toFixed(1)}%</strong><br /></>
                  )}
                  • Tình trạng: Trùng khớp với ảnh chân dung trên thẻ CCCD.
                </div>
              </div>
            </Alert>
          )}

          <Button type="button" variant="outline" onClick={handleRetake}>
            🔄 Quét lại khuôn mặt khác
          </Button>
        </div>
      )}
    </div>
  );
};
