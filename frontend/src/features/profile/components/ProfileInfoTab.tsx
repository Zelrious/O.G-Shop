import React, { useState, useRef, useEffect } from 'react';
import { UserProfile, profileApi, ChangePhoneModal } from '../index';
import { useAuth } from '../../auth';
import { Alert, Button, Input, Badge } from '../../../shared/components';

interface ProfileInfoTabProps {
  profile: UserProfile;
  onProfileUpdated: (updated: UserProfile) => void;
}

export const ProfileInfoTab: React.FC<ProfileInfoTabProps> = ({ profile, onProfileUpdated }) => {
  const [failedAvatarUrl, setFailedAvatarUrl] = useState<string | null>(null);
  const { user, reloadCurrentUser } = useAuth();
  const [fullName, setFullName] = useState(profile.fullName || '');
  const [phoneNumber, setPhoneNumber] = useState(profile.phoneNumber || '');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Phone Change Security Modal State
  const [showPhoneModal, setShowPhoneModal] = useState<boolean>(false);

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

  const handleOpenPhoneModal = () => {
    setShowPhoneModal(true);
  };

  const handlePhoneChangeSuccess = async (newPhone: string) => {
    setPhoneNumber(newPhone);
    setSuccessMsg('Đổi số điện thoại chính thành công!');
    try {
      const refreshed = await profileApi.getProfile();
      onProfileUpdated(refreshed);
      await reloadCurrentUser();
    } catch {
      // fallback local update
      onProfileUpdated({ ...profile, phoneNumber: newPhone });
    }
    setTimeout(() => setSuccessMsg(null), 4000);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!fullName.trim()) {
      setErrorMsg('Vui lòng nhập họ và tên.');
      return;
    }

    // Nếu người dùng muốn đổi số điện thoại, mở modal xác thực
    if (phoneNumber.trim() !== (profile.phoneNumber || '').trim()) {
      handleOpenPhoneModal();
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
            phoneNumber: profile.phoneNumber || undefined,
          },
          selectedFile
        );
      } else {
        updated = await profileApi.updateProfile({
          fullName: fullName.trim(),
          phoneNumber: profile.phoneNumber || undefined,
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
    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {successMsg && <Alert type="success">{successMsg}</Alert>}
      {errorMsg && <Alert type="danger">{errorMsg}</Alert>}

      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
        {/* Avatar Section */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '24px', flexWrap: 'wrap' }}>
          <div
            style={{
              width: '100px',
              height: '100px',
              borderRadius: '50%',
              backgroundColor: 'var(--og-color-surface-subtle)',
              border: '2px dashed var(--og-color-border)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              overflow: 'hidden',
              position: 'relative',
            }}
          >
            {previewUrl ? (
              <img
                src={previewUrl}
                alt="Xem trước ảnh đại diện"
                style={{ width: '100%', height: '100%', objectFit: 'cover' }}
              />
            ) : profile.avatarUrl && failedAvatarUrl !== profile.avatarUrl ? (
              <img
                src={profile.avatarUrl}
                alt="Ảnh đại diện"
                onError={() => setFailedAvatarUrl(profile.avatarUrl ?? null)}
                style={{ width: '100%', height: '100%', objectFit: 'cover' }}
              />
            ) : (
              <span style={{ fontSize: '2.5rem', color: 'var(--og-color-text-secondary)' }}>👤</span>
            )}
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => fileInputRef.current?.click()}
              >
                Chọn ảnh mới
              </Button>
              {selectedFile && (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={handleCancelSelection}
                >
                  Bỏ chọn
                </Button>
              )}
            </div>
            <input
              type="file"
              data-testid="avatar-file-input"
              ref={fileInputRef}
              onChange={handleFileChange}
              accept="image/jpeg,image/png,image/webp"
              style={{ display: 'none' }}
            />
            <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)' }}>
              Định dạng: JPEG, PNG, WebP tĩnh. Dung lượng tối đa: 5 MiB.
            </span>
          </div>
        </div>

        {/* Email */}
        <div>
          <label style={{ display: 'block', fontSize: '0.88rem', fontWeight: 600, marginBottom: '6px' }}>
            Địa chỉ Email (Định danh tài khoản)
          </label>
          <Input value={profile.email} disabled />
          <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', marginTop: '4px', display: 'block' }}>
            Email liên kết với tài khoản không thể thay đổi.
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

        {/* Số điện thoại chính */}
        <div
          style={{
            border: '1px solid var(--og-color-border)',
            borderRadius: '12px',
            padding: '16px 20px',
            backgroundColor: 'var(--og-color-surface-subtle)',
            display: 'flex',
            flexDirection: 'column',
            gap: '12px',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '8px' }}>
            <label style={{ fontSize: '0.9rem', fontWeight: 600, margin: 0, color: 'var(--og-color-text)' }}>
              Số điện thoại chính (Hotline gian hàng & Bảo mật)
            </label>
            {profile.phoneNumber ? (
              <Badge variant="success">
                Đã xác thực
              </Badge>
            ) : (
              <Badge variant="warning">
                Chưa thiết lập
              </Badge>
            )}
          </div>

          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: '10px',
              backgroundColor: 'var(--og-color-surface)',
              border: '1px solid var(--og-color-border)',
              borderRadius: '8px',
              padding: '16px',
            }}
          >
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                gap: '16px',
                flexWrap: 'wrap',
              }}
            >
              <div style={{ flex: 1, minWidth: '240px', maxWidth: '360px' }}>
                <Input
                  value={phoneNumber}
                  onChange={(e) => setPhoneNumber(e.target.value)}
                  readOnly
                  placeholder="0912345678"
                  style={{
                    fontWeight: 600,
                    fontSize: '1rem',
                    backgroundColor: 'var(--og-color-surface-subtle)',
                    cursor: 'default',
                    margin: 0,
                  }}
                />
              </div>
              <Button
                type="button"
                variant="outline"
                size="md"
                onClick={handleOpenPhoneModal}
              >
                Đổi số điện thoại
              </Button>
            </div>
            <span style={{ fontSize: '0.82rem', color: 'var(--og-color-text-secondary)', display: 'block' }}>
              Định danh duy nhất. Dùng làm hotline hiển thị trên các đơn hàng bán ra.
            </span>
          </div>
        </div>

        <div style={{ marginTop: '8px', display: 'flex', justifyContent: 'flex-end' }}>
          <Button variant="primary" type="submit" disabled={isSaving}>
            {isSaving ? 'Đang lưu...' : 'Lưu thay đổi'}
          </Button>
        </div>
      </form>

      {/* Modern, Outline Change Phone Modal with Real-time Validation */}
      <ChangePhoneModal
        isOpen={showPhoneModal}
        onClose={() => setShowPhoneModal(false)}
        onSuccess={handlePhoneChangeSuccess}
        currentPhoneNumber={profile.phoneNumber}
        currentFullName={profile.fullName}
        accountEmail={profile.email}
        isVerifiedSeller={user?.verifiedSeller ?? false}
      />
    </div>
  );
};
