import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { UserProfile, profileApi } from '../index';
import { Alert, Badge, Button, Card, Input } from '../../../shared/components';

interface SellerBankTabProps {
  profile: UserProfile;
  onProfileUpdated: (updated: UserProfile) => void;
}

export const SellerBankTab: React.FC<SellerBankTabProps> = ({ profile, onProfileUpdated }) => {
  const navigate = useNavigate();
  const isSeller = profile.roles.includes('SELLER');

  const [bankName, setBankName] = useState(profile.bankName || '');
  const [bankAccountNumber, setBankAccountNumber] = useState('');
  const [bankAccountHolder, setBankAccountHolder] = useState(profile.bankAccountHolder || '');
  const [isEditingBank, setIsEditingBank] = useState(!profile.bankAccountNumberMasked);
  const [isSaving, setIsSaving] = useState(false);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleSaveBank = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!bankName.trim() || !bankAccountNumber.trim() || !bankAccountHolder.trim()) {
      setErrorMsg('Vui lòng điền đầy đủ Tên ngân hàng, Số tài khoản và Tên chủ tài khoản.');
      return;
    }

    setIsSaving(true);
    setSuccessMsg(null);
    setErrorMsg(null);

    try {
      const updated = await profileApi.updateBankAccount({
        bankName: bankName.trim(),
        bankAccountNumber: bankAccountNumber.trim(),
        bankAccountHolder: bankAccountHolder.trim().toUpperCase(),
      });
      onProfileUpdated(updated);
      setIsEditingBank(false);
      setBankAccountNumber('');
      setSuccessMsg('Cập nhật thông tin tài khoản ngân hàng thành công!');
      setTimeout(() => setSuccessMsg(null), 4000);
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Cập nhật tài khoản thất bại.');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="og-profile-tab" style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {/* Thẻ Trạng thái Người bán & eKYC */}
      <Card title="Quyền Người bán & Trạng thái Định danh eKYC">
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
            {isSeller ? (
              <Badge variant="seller">Đã kích hoạt Người bán</Badge>
            ) : (
              <Badge variant="buyer">Tài khoản Người mua</Badge>
            )}

            {profile.verifiedSeller ? (
              <Badge variant="seller">✓ Đã xác minh eKYC CCCD</Badge>
            ) : (
              <span style={{ fontSize: '0.88rem', color: 'var(--og-color-text-secondary)' }}>
                Chưa hoàn tất định danh eKYC
              </span>
            )}
          </div>

          <p style={{ margin: 0, fontSize: '0.92rem', color: 'var(--og-color-text-secondary)' }}>
            {isSeller
              ? 'Tài khoản của bạn đã có quyền đăng tin bán đồ cũ trên sàn O.G Shop.'
              : 'Để đăng bán sản phẩm và mở gian hàng, bạn cần kích hoạt quyền Người bán và xác minh danh tính CCCD.'}
          </p>

          {!isSeller && (
            <div style={{ marginTop: '6px' }}>
              <Button variant="gold" size="sm" onClick={() => navigate('/seller-verification')}>
                Kích hoạt quyền Người bán ngay
              </Button>
            </div>
          )}
        </div>
      </Card>

      {/* Thẻ Tài khoản Ngân hàng (Payout Profile) */}
      <Card title="Tài khoản Ngân hàng nhận tiền (Payout)">
        {successMsg && <Alert type="success">{successMsg}</Alert>}
        {errorMsg && <Alert type="danger">{errorMsg}</Alert>}

        <p style={{ margin: '0 0 16px', fontSize: '0.88rem', color: 'var(--og-color-text-secondary)' }}>
          Thông tin tài khoản ngân hàng dùng để nhận tiền khi bạn bán món đồ thành công. Để bảo vệ an toàn thông tin, số tài khoản được che hiển thị.
        </p>

        {profile.bankAccountNumberMasked && !isEditingBank ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <div style={{ padding: '14px', backgroundColor: 'var(--og-color-surface-sunken)', borderRadius: '8px', border: '1px solid var(--og-color-border-subtle)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                <strong style={{ fontSize: '1rem', color: 'var(--og-color-text-primary)' }}>
                  🏦 {profile.bankName}
                </strong>
                <Badge variant="buyer">Đã liên kết</Badge>
              </div>
              <p style={{ margin: '0 0 4px', fontSize: '0.95rem', letterSpacing: '1px' }}>
                Số tài khoản: <strong>{profile.bankAccountNumberMasked}</strong>
              </p>
              <p style={{ margin: 0, fontSize: '0.88rem', color: 'var(--og-color-text-secondary)' }}>
                Chủ tài khoản: <strong>{profile.bankAccountHolder}</strong>
              </p>
            </div>

            <div>
              <Button variant="outline" size="sm" onClick={() => setIsEditingBank(true)}>
                Thay đổi tài khoản ngân hàng
              </Button>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSaveBank} style={{ display: 'flex', flexDirection: 'column', gap: '14px', maxWidth: '480px' }}>
            <div>
              <label style={{ display: 'block', fontSize: '0.88rem', fontWeight: 600, marginBottom: '4px' }}>
                Tên ngân hàng *
              </label>
              <Input
                value={bankName}
                onChange={(e) => setBankName(e.target.value)}
                placeholder="Ví dụ: MB Bank, Vietcombank, Techcombank..."
                required
              />
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '0.88rem', fontWeight: 600, marginBottom: '4px' }}>
                Số tài khoản ngân hàng *
              </label>
              <Input
                value={bankAccountNumber}
                onChange={(e) => setBankAccountNumber(e.target.value)}
                placeholder="Nhập số tài khoản ngân hàng"
                required
              />
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '0.88rem', fontWeight: 600, marginBottom: '4px' }}>
                Tên chủ tài khoản (Viết hoa không dấu) *
              </label>
              <Input
                value={bankAccountHolder}
                onChange={(e) => setBankAccountHolder(e.target.value)}
                placeholder="Ví dụ: NGUYEN VAN A"
                required
              />
            </div>

            <div style={{ display: 'flex', gap: '10px', marginTop: '6px' }}>
              {profile.bankAccountNumberMasked && (
                <Button variant="outline" type="button" onClick={() => setIsEditingBank(false)}>
                  Hủy bỏ
                </Button>
              )}
              <Button variant="primary" type="submit" disabled={isSaving}>
                {isSaving ? 'Đang lưu...' : 'Lưu tài khoản ngân hàng'}
              </Button>
            </div>
          </form>
        )}
      </Card>
    </div>
  );
};
