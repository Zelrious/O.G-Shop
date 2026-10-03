import React, { useState, useRef, useEffect } from 'react';
import { UserProfile, profileApi } from '../index';
import { useAuth } from '../../auth';
import { Alert, Button, Input } from '../../../shared/components';

interface ProfileInfoTabProps {
  profile: UserProfile;
  onProfileUpdated: (updated: UserProfile) => void;
}

export const ProfileInfoTab: React.FC<ProfileInfoTabProps> = ({ profile, onProfileUpdated }) => {
  const { reloadCurrentUser } = useAuth();
  const [fullName, setFullName] = useState(profile.fullName || '');
  const [phoneNumber, setPhoneNumber] = useState(profile.phoneNumber || '');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!selectedFile) {
      setPreviewUrl(null);
      return;
    }
    const objectUrl = URL.createObjectURL(selectedFile);
    setPreviewUrl(objectUrl);
    return () => {
      URL.revokeObjectURL(objectUrl);
    };
  }, [selectedFile]);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > 5 * 1024 * 1024) {
      setErrorMsg('Ảnh đại diện tối đa 5 MiB.');
      if (fileInputRef.current) fileInputRef.current.value = '';
      return;
    }

    const allowedTypes = ['image/jpeg', 'image/png', 'image/webp'];
    const extension = file.name.split('.').pop()?.toLowerCase();
    const allowedExts = ['jpg', 'jpeg', 'png', 'webp'];
    if (!allowedTypes.includes(file.type) && (!extension || !allowedExts.includes(extension))) {
      setErrorMsg('Chọn ảnh JPEG, PNG hoặc WebP tĩnh hợp lệ.');
      if (fileInputRef.current) fileInputRef.current.value = '';
      return;
    }

    setErrorMsg(null);
    setSelectedFile(file);
  };

  const handleCancelSelection = () => {
    setSelectedFile(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!fullName.trim()) {
      setErrorMsg('Vui lòng nhập họ và tên.');
      return;
    }

    setIsSaving(true);
    setSuccessMsg(null);
    setErrorMsg(null);

    try {
      let updated: UserProfile;
      if (selectedFile) {
        updated = await profileApi.updateProfileWithAvatar(
          {
            fullName: fullName.trim(),
            phoneNumber: phoneNumber.trim() || undefined,
          },
          selectedFile
        );
      } else {
        updated = await profileApi.updateProfile({
          fullName: fullName.trim(),
          phoneNumber: phoneNumber.trim() || undefined,
          avatarUrl: profile.avatarUrl || undefined,
        });
      }

      onProfileUpdated(updated);
      setSelectedFile(null);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
      setSuccessMsg('Cập nhật thông tin hồ sơ thành công!');

      try {
        await reloadCurrentUser();
      } catch (authErr) {
        console.warn('Lỗi khi đồng bộ thông tin phiên làm việc:', authErr);
      }

      setTimeout(() => setSuccessMsg(null), 4000);
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Cập nhật thất bại. Vui lòng thử lại.');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="og-profile-tab">
      <h3 style={{ margin: '0 0 16px', fontSize: '1.2rem', color: 'var(--og-color-text-primary)' }}>
        Thông tin cá nhân
      </h3>

      {successMsg && <Alert type="success">{successMsg}</Alert>}
      {errorMsg && <Alert type="danger">{errorMsg}</Alert>}

      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px', marginTop: '16px' }}>
        {/* Avatar Preview & File Input */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '20px', marginBottom: '8px' }}>
          <div
            style={{
              width: '80px',
              height: '80px',
              borderRadius: '50%',
              backgroundColor: 'var(--og-color-surface-sunken)',
              border: '2px solid var(--og-color-border-subtle)',
              overflow: 'hidden',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '2rem',
              color: 'var(--og-color-text-tertiary)',
              flexShrink: 0,
            }}
          >
            {previewUrl ? (
              <img
                src={previewUrl}
                alt="Xem trước ảnh đại diện"
                style={{ width: '100%', height: '100%', objectFit: 'cover' }}
              />
            ) : profile.avatarUrl ? (
              <img
                src={profile.avatarUrl}
                alt="Ảnh đại diện"
                style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                onError={(e) => {
                  (e.currentTarget as HTMLImageElement).style.display = 'none';
                }}
              />
            ) : (
              profile.fullName ? profile.fullName.charAt(0).toUpperCase() : '👤'
            )}
          </div>

          <div style={{ flex: 1 }}>
            <label style={{ display: 'block', fontSize: '0.88rem', fontWeight: 600, marginBottom: '6px' }}>
              Ảnh đại diện
            </label>
            <input
              type="file"
              ref={fileInputRef}
              accept="image/jpeg,image/png,image/webp"
              style={{ display: 'none' }}
              onChange={handleFileChange}
              data-testid="avatar-file-input"
            />
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => fileInputRef.current?.click()}
                disabled={isSaving}
              >
                {selectedFile ? 'Đổi ảnh khác' : 'Chọn ảnh mới'}
              </Button>
              {selectedFile && (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={handleCancelSelection}
                  disabled={isSaving}
                  style={{ color: 'var(--og-color-danger)', borderColor: 'var(--og-color-danger)' }}
                >
                  Bỏ chọn
                </Button>
              )}
              {selectedFile && (
                <span style={{ fontSize: '0.82rem', color: 'var(--og-color-text-secondary)' }}>
                  {selectedFile.name} ({(selectedFile.size / 1024).toFixed(0)} KB)
                </span>
              )}
            </div>
            <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', marginTop: '6px', display: 'block' }}>
              Hỗ trợ định dạng JPG, PNG, WebP (Tối đa 5 MiB)
            </span>
          </div>
        </div>

        {/* Email - Read-only */}
        <div>
          <label style={{ display: 'block', fontSize: '0.88rem', fontWeight: 600, marginBottom: '6px' }}>
            Địa chỉ Email (Định danh tài khoản)
          </label>
          <Input value={profile.email} disabled />
          <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', marginTop: '4px', display: 'block' }}>
            🔒 Email liên kết với tài khoản không thể tự ý thay đổi.
          </span>
        </div>

        {/* Họ tên */}
        <div>
          <label style={{ display: 'block', fontSize: '0.88rem', fontWeight: 600, marginBottom: '6px' }}>
            Họ và tên <span style={{ color: 'var(--og-color-danger)' }}>*</span>
          </label>
          <Input
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            placeholder="Nhập họ và tên đầy đủ"
            required
          />
        </div>

        {/* Số điện thoại */}
        <div>
          <label style={{ display: 'block', fontSize: '0.88rem', fontWeight: 600, marginBottom: '6px' }}>
            Số điện thoại liên hệ
          </label>
          <Input
            value={phoneNumber}
            onChange={(e) => setPhoneNumber(e.target.value)}
            placeholder="0912 345 678"
          />
        </div>

        <div style={{ marginTop: '8px', display: 'flex', justifyContent: 'flex-end' }}>
          <Button variant="primary" type="submit" disabled={isSaving}>
            {isSaving ? 'Đang lưu...' : 'Lưu thay đổi'}
          </Button>
        </div>
      </form>
    </div>
  );
};
