import React, { useState, useRef } from 'react';
import { CccdOcrData } from '../types';
import { verificationApi, OcrApiResult } from '../verificationApi';
import { Button, Alert, Input, Badge } from '../../../shared/components';

interface CccdUploaderProps {
  onOcrComplete: (
    data: CccdOcrData,
    extra?: { cardFile: File; isSimulated: boolean }
  ) => void;
  initialData?: CccdOcrData | null;
}

export const CccdUploader: React.FC<CccdUploaderProps> = ({ onOcrComplete, initialData }) => {
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const [ocrData, setOcrData] = useState<CccdOcrData | null>(initialData || null);
  const [isSimulated, setIsSimulated] = useState<boolean | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      setError('Vui lòng chọn file hình ảnh (JPG, PNG).');
      return;
    }

    setError(null);
    const objectUrl = URL.createObjectURL(file);
    setPreviewUrl(objectUrl);

    setIsProcessing(true);
    try {
      const result: OcrApiResult = await verificationApi.processCccdImage(file);
      setOcrData(result.ocrData);
      setIsSimulated(result.isSimulated);
      onOcrComplete(result.ocrData, {
        cardFile: result.cardFile,
        isSimulated: result.isSimulated,
      });
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Không thể bóc tách ảnh CCCD.');
    } finally {
      setIsProcessing(false);
    }
  };

  const handleUpdateField = (field: keyof CccdOcrData, value: string) => {
    if (!ocrData) return;
    const updated = { ...ocrData, [field]: value };
    setOcrData(updated);
    onOcrComplete(updated);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {error && <Alert type="danger">{error}</Alert>}

      {/* Vùng Upload / Xem trước ảnh */}
      <div
        style={{
          border: '2px dashed var(--og-color-border-strong)',
          borderRadius: 'var(--og-radius-lg)',
          padding: '28px',
          textAlign: 'center',
          backgroundColor: 'var(--og-color-surface-subtle)',
          cursor: 'pointer',
          transition: 'border-color var(--og-transition-fast)',
        }}
        onClick={() => fileInputRef.current?.click()}
      >
        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          style={{ display: 'none' }}
          onChange={handleFileChange}
        />

        {previewUrl ? (
          <div>
            <img
              src={previewUrl}
              alt="Ảnh CCCD mặt trước"
              style={{
                maxWidth: '100%',
                maxHeight: '220px',
                borderRadius: 'var(--og-radius-md)',
                objectFit: 'contain',
                boxShadow: 'var(--og-shadow-md)',
              }}
            />
            <p style={{ marginTop: '12px', fontSize: '0.85rem', color: 'var(--og-color-primary)', fontWeight: 600 }}>
              Nhấp để chọn ảnh khác
            </p>
          </div>
        ) : (
          <div>
            <div style={{ fontSize: '2.5rem', marginBottom: '8px' }}>🪪</div>
            <h3 style={{ margin: '0 0 6px', fontSize: '1.1rem', color: 'var(--og-color-text-primary)' }}>
              Tải ảnh Thẻ Căn cước công dân (Mặt trước)
            </h3>
            <p style={{ margin: 0, fontSize: '0.88rem', color: 'var(--og-color-text-muted)' }}>
              Hỗ trợ JPG, PNG. Chụp rõ 4 góc, đủ sáng và không bị lóa sáng.
            </p>
            <div style={{ marginTop: '16px' }}>
              <Button type="button" variant="outline" size="sm">
                Chọn ảnh từ máy tính
              </Button>
            </div>
          </div>
        )}
      </div>

      {/* Trạng thái đang bóc tách AI */}
      {isProcessing && (
        <Alert type="info">
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span className="og-spinner" style={{ borderColor: 'var(--og-color-info)' }} />
            <span>Đang sử dụng mô hình AI (YOLO + VietOCR + Gemini Flash) để bóc tách thông tin thẻ...</span>
          </div>
        </Alert>
      )}

      {/* Bảng kết quả bóc tách cho phép người dùng rà soát & chỉnh sửa */}
      {ocrData && !isProcessing && (
        <div
          style={{
            padding: '20px',
            backgroundColor: 'var(--og-color-surface)',
            border: '1px solid var(--og-color-border)',
            borderRadius: 'var(--og-radius-md)',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px', flexWrap: 'wrap', gap: '8px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <h4 style={{ margin: 0, color: 'var(--og-color-primary)', fontSize: '1rem' }}>
                ✓ Thông tin trích xuất từ thẻ CCCD
              </h4>
              {isSimulated !== null && (
                <Badge variant="neutral">
                  {isSimulated ? 'eKYC mô phỏng — YOLO + VietOCR' : 'eKYC sandbox'}
                </Badge>
              )}
            </div>
            <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-muted)' }}>
              Vui lòng kiểm tra lại tính chính xác
            </span>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '12px' }}>
            <Input
              label="Số CCCD"
              value={ocrData.cccdNumber}
              onChange={(e) => handleUpdateField('cccdNumber', e.target.value)}
              required
            />
            <Input
              label="Họ và tên"
              value={ocrData.fullName}
              onChange={(e) => handleUpdateField('fullName', e.target.value)}
              required
            />
            <Input
              label="Ngày sinh"
              value={ocrData.dob}
              onChange={(e) => handleUpdateField('dob', e.target.value)}
              required
            />
            <Input
              label="Quê quán"
              value={ocrData.hometown}
              onChange={(e) => handleUpdateField('hometown', e.target.value)}
            />
          </div>

          <Input
            label="Địa chỉ thường trú"
            value={ocrData.address}
            onChange={(e) => handleUpdateField('address', e.target.value)}
          />
        </div>
      )}
    </div>
  );
};
