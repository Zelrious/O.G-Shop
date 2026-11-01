import React, { useState, useRef } from 'react';
import { CccdOcrData } from '../types';
import { ekycApi, EkycOcrResult } from '../ekycApi';
import { Button, Alert, Input, Badge } from '../../../shared/components';

export interface CccdUploaderProps {
  onOcrComplete: (
    data: CccdOcrData,
    extra?: {
      cardFile?: File;
      backFile?: File;
      portraitFile?: File;
      frontAssetId?: number;
      backAssetId?: number;
      portraitAssetId?: number;
      isSimulated?: boolean;
    }
  ) => void;
  initialData?: CccdOcrData | null;
}

export const CccdUploader: React.FC<CccdUploaderProps> = ({ onOcrComplete, initialData }) => {
  const frontInputRef = useRef<HTMLInputElement | null>(null);
  const backInputRef = useRef<HTMLInputElement | null>(null);
  const portraitInputRef = useRef<HTMLInputElement | null>(null);

  const [frontFile, setFrontFile] = useState<File | null>(null);
  const [backFile, setBackFile] = useState<File | null>(null);
  const [portraitFile, setPortraitFile] = useState<File | null>(null);

  const [frontPreview, setFrontPreview] = useState<string | null>(null);
  const [backPreview, setBackPreview] = useState<string | null>(null);
  const [portraitPreview, setPortraitPreview] = useState<string | null>(null);

  const [isProcessing, setIsProcessing] = useState(false);
  const [ocrData, setOcrData] = useState<CccdOcrData | null>(initialData || null);
  const [error, setError] = useState<string | null>(null);

  const handleFileSelect = (
    file: File | undefined,
    setFile: React.Dispatch<React.SetStateAction<File | null>>,
    setPreview: React.Dispatch<React.SetStateAction<string | null>>,
    label: string
  ) => {
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      setError(`Vui lòng chọn tệp hình ảnh hợp lệ (JPG, PNG) cho ${label}.`);
      return;
    }
    setError(null);
    setOcrData(null);
    setFile(file);
    setPreview(URL.createObjectURL(file));
  };

  const handleProcessThreeImages = async () => {
    if (!frontFile) {
      setError('Ưu tiên 1: Vui lòng tải lên ảnh mặt trước CCCD.');
      return;
    }
    if (!backFile) {
      setError('Ưu tiên 2: Vui lòng tải lên ảnh mặt sau CCCD gắn chip.');
      return;
    }
    if (!portraitFile) {
      setError('Ưu tiên 3: Vui lòng tải lên ảnh thẻ chân dung chính chủ (3x4 hoặc 4x6).');
      return;
    }

    setIsProcessing(true);
    setError(null);

    try {
      const result: EkycOcrResult = await ekycApi.uploadCccd(frontFile, backFile, portraitFile);
      if (result.status !== 'SUCCESS') {
        throw new Error(result.errorMessage || 'Xử lý bóc tách hoặc đối chiếu khuôn mặt không thành công.');
      }

      const mapped: CccdOcrData = {
        cccdNumber: result.ocrData.cccdNumber || '',
        fullName: result.ocrData.fullName || '',
        dob: result.ocrData.dob || '',
        gender: result.ocrData.gender || 'Nam',
        hometown: result.ocrData.hometown || '',
        address: result.ocrData.address || '',
      };

      setOcrData(mapped);
      onOcrComplete(mapped, {
        cardFile: frontFile,
        backFile: backFile,
        portraitFile: portraitFile,
        frontAssetId: result.frontAssetId,
        backAssetId: result.backAssetId,
        portraitAssetId: result.portraitAssetId,
        isSimulated: true,
      });
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Lỗi trong quá trình kiểm tra và bóc tách dữ liệu eKYC.');
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

  const allImagesReady = Boolean(frontFile && backFile && portraitFile);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {error && <Alert type="danger">{error}</Alert>}

      <Alert type="info">
        Hệ thống eKYC yêu cầu đầy đủ <strong>3 ảnh bắt buộc</strong>: <strong>Mặt trước CCCD</strong>, <strong>Mặt sau CCCD</strong> và <strong>Ảnh thẻ chân dung</strong>. Các ảnh sẽ được tự động kiểm tra chất lượng, bóc tách OCR và đối chiếu sinh trắc học theo thứ tự ưu tiên nghiêm ngặt.
      </Alert>

      {/* Grid 3 ô tải ảnh bắt buộc */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
          gap: '16px',
        }}
      >
        {/* 1. Mặt trước CCCD */}
        <div
          style={{
            border: frontFile ? '2px solid var(--og-color-success)' : '2px dashed var(--og-color-border-strong)',
            borderRadius: 'var(--og-radius-lg)',
            padding: '20px',
            textAlign: 'center',
            backgroundColor: 'var(--og-color-surface-subtle)',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'space-between',
          }}
        >
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
              <span style={{ fontWeight: 600, fontSize: '0.92rem' }}>1. Mặt trước CCCD *</span>
              <Badge variant={frontFile ? 'success' : 'neutral'}>
                {frontFile ? '✓ Đã tải' : 'Bắt buộc'}
              </Badge>
            </div>
            <p style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', margin: '0 0 12px' }}>
              Chụp rõ 4 góc, đủ thông tin và ảnh chân dung
            </p>
            {frontPreview ? (
              <img
                src={frontPreview}
                alt="CCCD Mặt trước"
                style={{ width: '100%', maxHeight: '140px', objectFit: 'contain', borderRadius: '6px', marginBottom: '10px' }}
              />
            ) : (
              <div style={{ padding: '24px 0', color: 'var(--og-color-text-muted)', fontSize: '2rem' }}>🪪</div>
            )}
          </div>
          <div>
            <input
              ref={frontInputRef}
              type="file"
              accept="image/*"
              style={{ display: 'none' }}
              onChange={(e) => handleFileSelect(e.target.files?.[0], setFrontFile, setFrontPreview, 'Mặt trước CCCD')}
            />
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => frontInputRef.current?.click()}
            >
              {frontFile ? 'Đổi ảnh mặt trước' : 'Chọn ảnh mặt trước'}
            </Button>
          </div>
        </div>

        {/* 2. Mặt sau CCCD */}
        <div
          style={{
            border: backFile ? '2px solid var(--og-color-success)' : '2px dashed var(--og-color-border-strong)',
            borderRadius: 'var(--og-radius-lg)',
            padding: '20px',
            textAlign: 'center',
            backgroundColor: 'var(--og-color-surface-subtle)',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'space-between',
          }}
        >
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
              <span style={{ fontWeight: 600, fontSize: '0.92rem' }}>2. Mặt sau CCCD *</span>
              <Badge variant={backFile ? 'success' : 'neutral'}>
                {backFile ? '✓ Đã tải' : 'Bắt buộc'}
              </Badge>
            </div>
            <p style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', margin: '0 0 12px' }}>
              Rõ nét chip điện tử, con dấu và dải mã MRZ
            </p>
            {backPreview ? (
              <img
                src={backPreview}
                alt="CCCD Mặt sau"
                style={{ width: '100%', maxHeight: '140px', objectFit: 'contain', borderRadius: '6px', marginBottom: '10px' }}
              />
            ) : (
              <div style={{ padding: '24px 0', color: 'var(--og-color-text-muted)', fontSize: '2rem' }}>📄</div>
            )}
          </div>
          <div>
            <input
              ref={backInputRef}
              type="file"
              accept="image/*"
              style={{ display: 'none' }}
              onChange={(e) => handleFileSelect(e.target.files?.[0], setBackFile, setBackPreview, 'Mặt sau CCCD')}
            />
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => backInputRef.current?.click()}
            >
              {backFile ? 'Đổi ảnh mặt sau' : 'Chọn ảnh mặt sau'}
            </Button>
          </div>
        </div>

        {/* 3. Ảnh thẻ chân dung 3x4 / 4x6 */}
        <div
          style={{
            border: portraitFile ? '2px solid var(--og-color-success)' : '2px dashed var(--og-color-border-strong)',
            borderRadius: 'var(--og-radius-lg)',
            padding: '20px',
            textAlign: 'center',
            backgroundColor: 'var(--og-color-surface-subtle)',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'space-between',
          }}
        >
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
              <span style={{ fontWeight: 600, fontSize: '0.92rem' }}>3. Ảnh thẻ chân dung *</span>
              <Badge variant={portraitFile ? 'success' : 'neutral'}>
                {portraitFile ? '✓ Đã tải' : 'Bắt buộc'}
              </Badge>
            </div>
            <p style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', margin: '0 0 12px' }}>
              Ảnh chụp chân dung 3x4 hoặc 4x6 rõ mặt, nhìn thẳng
            </p>
            {portraitPreview ? (
              <img
                src={portraitPreview}
                alt="Ảnh thẻ chân dung"
                style={{ width: '100%', maxHeight: '140px', objectFit: 'contain', borderRadius: '6px', marginBottom: '10px' }}
              />
            ) : (
              <div style={{ padding: '24px 0', color: 'var(--og-color-text-muted)', fontSize: '2rem' }}>👤</div>
            )}
          </div>
          <div>
            <input
              ref={portraitInputRef}
              type="file"
              accept="image/*"
              style={{ display: 'none' }}
              onChange={(e) => handleFileSelect(e.target.files?.[0], setPortraitFile, setPortraitPreview, 'Ảnh thẻ chân dung')}
            />
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => portraitInputRef.current?.click()}
            >
              {portraitFile ? 'Đổi ảnh chân dung' : 'Chọn ảnh chân dung'}
            </Button>
          </div>
        </div>
      </div>

      {/* Nút thực hiện xử lý 3 ảnh */}
      <div style={{ textAlign: 'center', marginTop: '10px' }}>
        <Button
          type="button"
          variant="primary"
          onClick={handleProcessThreeImages}
          isLoading={isProcessing}
          disabled={!allImagesReady || isProcessing}
        >
          {allImagesReady ? 'Bóc tách OCR & Đối chiếu sinh trắc học' : 'Vui lòng tải đủ 3 ảnh bắt buộc để tiếp tục'}
        </Button>
      </div>

      {/* Trạng thái xử lý AI */}
      {isProcessing && (
        <Alert type="info">
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span className="og-spinner" style={{ borderColor: 'var(--og-color-info)' }} />
            <span>Đang kiểm tra chất lượng 3 ảnh, bóc tách OCR và đối chiếu khuôn mặt CCCD ↔ Chân dung...</span>
          </div>
        </Alert>
      )}

      {/* Bảng kết quả trích xuất cho phép rà soát */}
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
                ✓ Thông tin trích xuất từ thẻ CCCD (Đã đối chiếu khuôn mặt thành công)
              </h4>
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

          <div style={{ marginTop: '12px' }}>
            <Input
              label="Địa chỉ thường trú"
              value={ocrData.address}
              onChange={(e) => handleUpdateField('address', e.target.value)}
            />
          </div>
        </div>
      )}
    </div>
  );
};
