import { useState } from 'react';
import { useDemo, useI18n } from '../../../shared/context';
import { MOCK_ADMIN_METRICS, MOCK_VOUCHERS } from '../../../shared/data/mockData';

interface ModuleProps {
  screenId: string;
}

export function Module1AccountScreens({ screenId }: ModuleProps) {
  const { language } = useI18n();
  const { isSellerKycApproved, setIsSellerKycApproved, setActiveScreenId } = useDemo();

  // Local state for interactive form fields
  const [bankAccount, setBankAccount] = useState({ bank: 'Vietcombank', number: '1029384756', name: 'HUYNH LONG BAO KHANH' });
  const [is2FaEnabled, setIs2FaEnabled] = useState(false);
  const [twoFaCode, setTwoFaCode] = useState('');
  const [savedSuccess, setSavedSuccess] = useState(false);
  const [usersList, setUsersList] = useState([
    { id: 'usr-1', name: 'Minh Camera Saigon', email: 'minh.saigon@gmail.com', role: 'SELLER', kyc: 'VERIFIED', trustScore: 98, status: 'ACTIVE' },
    { id: 'usr-2', name: 'Bảo Khánh', email: 'khanh.buyer@gmail.com', role: 'BUYER', kyc: 'VERIFIED', trustScore: 95, status: 'ACTIVE' },
    { id: 'usr-3', name: 'Lan Nguyễn Sound', email: 'lan.vintage@gmail.com', role: 'SELLER', kyc: 'VERIFIED', trustScore: 94, status: 'ACTIVE' },
    { id: 'usr-4', name: 'User Vi Phạm 99', email: 'scam.test@gmail.com', role: 'SELLER', kyc: 'REJECTED', trustScore: 42, status: 'WARNED' },
  ]);
  const [selectedUserForPenalty, setSelectedUserForPenalty] = useState(usersList[3]);
  const [penaltyAction, setPenaltyAction] = useState('WARN');

  const handleSaveBank = (e: React.FormEvent) => {
    e.preventDefault();
    setSavedSuccess(true);
    setTimeout(() => setSavedSuccess(false), 3000);
  };

  const handleApplyPenalty = () => {
    setUsersList(prev => prev.map(u => u.id === selectedUserForPenalty.id ? { ...u, status: penaltyAction === 'BAN' ? 'BANNED' : 'WARNED' } : u));
    alert(language === 'vi' ? `Đã áp dụng chế tài ${penaltyAction} cho ${selectedUserForPenalty.name}` : `Enforced ${penaltyAction} penalty on ${selectedUserForPenalty.name}`);
  };

  switch (screenId) {
    case 'SCR-AUTH-01':
      return (
        <div className="og-card" style={{ maxWidth: '500px', margin: '0 auto', padding: '32px' }}>
          <div style={{ textAlign: 'center', marginBottom: '24px' }}>
            <span style={{ fontSize: '32px' }}>🪙</span>
            <h2 style={{ color: 'var(--og-color-primary)', margin: '8px 0 4px' }}>
              {language === 'vi' ? 'Đăng Ký Tài Khoản O.G Shop' : 'Create O.G Shop Account'}
            </h2>
            <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '13px', margin: 0 }}>
              {language === 'vi' ? 'Sàn giao dịch đồ cũ an tâm với bảo vệ Escrow ký quỹ' : 'Trusted second-hand marketplace with Escrow protection'}
            </p>
          </div>

          <form onSubmit={e => { e.preventDefault(); setActiveScreenId('SCR-AUTH-02'); }}>
            <div className="og-form-group">
              <label className="og-label">{language === 'vi' ? 'Họ và tên' : 'Full Name'}</label>
              <input type="text" className="og-input" defaultValue="Huỳnh Long Bảo Khánh" required />
            </div>

            <div className="og-form-group">
              <label className="og-label">{language === 'vi' ? 'Email xác thực' : 'Email Address'}</label>
              <input type="email" className="og-input" defaultValue="khanh.ogshop@gmail.com" required />
            </div>

            <div className="og-form-group">
              <label className="og-label">{language === 'vi' ? 'Mật khẩu (Tối thiểu 8 ký tự)' : 'Password (Min 8 chars)'}</label>
              <input type="password" className="og-input" defaultValue="OldButGold2026@" required />
              <div style={{ display: 'flex', gap: '4px', marginTop: '6px' }}>
                <div style={{ flex: 1, height: '4px', background: '#3ab86d', borderRadius: '2px' }} />
                <div style={{ flex: 1, height: '4px', background: '#3ab86d', borderRadius: '2px' }} />
                <div style={{ flex: 1, height: '4px', background: '#3ab86d', borderRadius: '2px' }} />
              </div>
              <span style={{ fontSize: '11px', color: '#236c3e', fontWeight: 600 }}>
                {language === 'vi' ? '✓ Mật khẩu mạnh: Có chữ hoa, số & ký tự đặc biệt' : '✓ Strong password'}
              </span>
            </div>

            <div style={{ margin: '16px 0', fontSize: '12px', color: 'var(--og-color-text-muted)' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <input type="checkbox" defaultChecked />
                <span>
                  {language === 'vi' ? 'Tôi đồng ý với Quy chế hoạt động & Cam kết Escrow' : 'I agree with Terms & Escrow Protection Agreement'}
                </span>
              </label>
            </div>

            <button type="submit" className="og-button og-button--primary" style={{ width: '100%' }}>
              {language === 'vi' ? 'Đăng Ký Thành Viên' : 'Register Account'}
            </button>

            <div style={{ textAlign: 'center', marginTop: '16px', fontSize: '13px' }}>
              <span style={{ color: 'var(--og-color-text-secondary)' }}>{language === 'vi' ? 'Đã có tài khoản?' : 'Already have an account?'} </span>
              <button type="button" className="og-link-button" onClick={() => setActiveScreenId('SCR-AUTH-02')}>
                {language === 'vi' ? 'Đăng nhập ngay' : 'Login now'}
              </button>
            </div>
          </form>
        </div>
      );

    case 'SCR-AUTH-02':
      return (
        <div className="og-card" style={{ maxWidth: '440px', margin: '0 auto', padding: '32px' }}>
          <div style={{ textAlign: 'center', marginBottom: '24px' }}>
            <span style={{ fontSize: '32px' }}>🔐</span>
            <h2 style={{ color: 'var(--og-color-primary)', margin: '8px 0 4px' }}>
              {language === 'vi' ? 'Chào Mừng Trở Lại' : 'Welcome Back'}
            </h2>
            <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '13px', margin: 0 }}>
              {language === 'vi' ? 'Đăng nhập để mua bán an toàn trên O.G Shop' : 'Login to trade safely on O.G Shop'}
            </p>
          </div>

          <form onSubmit={e => { e.preventDefault(); setActiveScreenId('SCR-BUYER-HOME'); }}>
            <div className="og-form-group">
              <label className="og-label">Email</label>
              <input type="email" className="og-input" defaultValue="huynhlongbaokhanh@gmail.com" required />
            </div>

            <div className="og-form-group">
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <label className="og-label">{language === 'vi' ? 'Mật khẩu' : 'Password'}</label>
                <button type="button" className="og-link-button" style={{ fontSize: '12px' }} onClick={() => setActiveScreenId('SCR-AUTH-03')}>
                  {language === 'vi' ? 'Quên mật khẩu?' : 'Forgot password?'}
                </button>
              </div>
              <input type="password" className="og-input" defaultValue="••••••••••••" required />
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', margin: '16px 0', fontSize: '12px' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
                <input type="checkbox" defaultChecked />
                <span>{language === 'vi' ? 'Ghi nhớ phiên đăng nhập' : 'Remember me'}</span>
              </label>
            </div>

            <button type="submit" className="og-button og-button--primary" style={{ width: '100%', marginBottom: '12px' }}>
              {language === 'vi' ? 'Đăng Nhập' : 'Sign In'}
            </button>

            <button
              type="button"
              className="og-button og-button--outline"
              style={{ width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}
              onClick={() => setActiveScreenId('SCR-BUYER-HOME')}
            >
              <span>🌐</span>
              <span>{language === 'vi' ? 'Đăng nhập với Google' : 'Sign in with Google'}</span>
            </button>
          </form>
        </div>
      );

    case 'SCR-AUTH-03':
      return (
        <div className="og-card" style={{ maxWidth: '440px', margin: '0 auto', padding: '32px' }}>
          <div style={{ textAlign: 'center', marginBottom: '20px' }}>
            <span style={{ fontSize: '32px' }}>✉️</span>
            <h2 style={{ color: 'var(--og-color-primary)', margin: '8px 0 4px' }}>
              {language === 'vi' ? 'Khôi Phục Mật Khẩu' : 'Reset Password'}
            </h2>
            <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '13px', margin: 0 }}>
              {language === 'vi' ? 'Nhập email để nhận mã OTP xác thực khôi phục tài khoản' : 'Enter your email to receive recovery OTP code'}
            </p>
          </div>

          <form onSubmit={e => { e.preventDefault(); alert(language === 'vi' ? 'Mã OTP đã được gửi đến email!' : 'OTP code sent to email!'); }}>
            <div className="og-form-group">
              <label className="og-label">Email</label>
              <input type="email" className="og-input" defaultValue="huynhlongbaokhanh@gmail.com" required />
            </div>

            <button type="submit" className="og-button og-button--primary" style={{ width: '100%', marginBottom: '12px' }}>
              {language === 'vi' ? 'Gửi Mã Xác Minh OTP' : 'Send Recovery OTP'}
            </button>

            <button type="button" className="og-button og-button--ghost" style={{ width: '100%' }} onClick={() => setActiveScreenId('SCR-AUTH-02')}>
              {language === 'vi' ? '← Quay lại Đăng nhập' : '← Back to Login'}
            </button>
          </form>
        </div>
      );

    case 'SCR-USER-01':
      return (
        <div style={{ maxWidth: '800px', margin: '0 auto' }}>
          <div className="og-card" style={{ padding: '28px', marginBottom: '20px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '20px', flexWrap: 'wrap' }}>
              <img
                src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80"
                alt="Avatar"
                style={{ width: '80px', height: '80px', borderRadius: '50%', objectFit: 'cover', border: '3px solid var(--og-color-accent-gold)' }}
              />
              <div style={{ flex: 1 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <h2 style={{ margin: 0, color: 'var(--og-color-text-primary)' }}>Huỳnh Long Bảo Khánh</h2>
                  <span className="og-badge og-badge--success">✓ KYC Verified</span>
                </div>
                <p style={{ margin: '4px 0 0', color: 'var(--og-color-text-secondary)', fontSize: '13px' }}>
                  huynhlongbaokhanh@gmail.com · {language === 'vi' ? 'Thành viên từ 10/2023' : 'Member since Oct 2023'}
                </p>
                <div style={{ display: 'flex', gap: '16px', marginTop: '10px', fontSize: '13px' }}>
                  <span>⭐ <strong>4.9/5.0</strong> ({language === 'vi' ? '38 đánh giá' : '38 reviews'})</span>
                  <span>🛡️ {language === 'vi' ? 'Điểm tín nhiệm: 98/100' : 'Trust Score: 98/100'}</span>
                </div>
              </div>
            </div>
          </div>

          <div className="og-card" style={{ padding: '24px' }}>
            <h3 style={{ color: 'var(--og-color-primary)', marginTop: 0 }}>
              {language === 'vi' ? 'Sổ Địa Chỉ Giao Nhận' : 'Saved Shipping Addresses'}
            </h3>
            <div style={{ border: '1px solid var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '16px', marginBottom: '12px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <strong>Bảo Khánh (Mặc định) · 0908 123 456</strong>
                <span className="og-badge og-badge--primary">{language === 'vi' ? 'Mặc Định' : 'Default'}</span>
              </div>
              <p style={{ margin: '6px 0 0', fontSize: '13px', color: 'var(--og-color-text-secondary)' }}>
                Tòa nhà Landmark 81, 720A Điện Biên Phủ, Phường 22, Quận Bình Thạnh, TP. Hồ Chí Minh
              </p>
            </div>
            <button type="button" className="og-button og-button--outline og-button--sm">
              + {language === 'vi' ? 'Thêm Địa Chỉ Mới' : 'Add New Address'}
            </button>
          </div>
        </div>
      );

    case 'SCR-SELLER-BANK':
      return (
        <div className="og-card" style={{ maxWidth: '600px', margin: '0 auto', padding: '28px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '20px' }}>
            <span style={{ fontSize: '28px' }}>🏦</span>
            <div>
              <h2 style={{ margin: 0, color: 'var(--og-color-primary)' }}>
                {language === 'vi' ? 'Tài Khoản Nhận Tiền Giải Ngân Escrow' : 'Escrow Payout Bank Account'}
              </h2>
              <p style={{ margin: '4px 0 0', fontSize: '13px', color: 'var(--og-color-text-secondary)' }}>
                {language === 'vi' ? 'Tiền bán hàng sẽ tự động chuyển về đây khi người mua xác nhận hài lòng' : 'Funds auto-transferred here upon buyer satisfaction'}
              </p>
            </div>
          </div>

          {savedSuccess && (
            <div className="og-badge og-badge--success" style={{ width: '100%', padding: '10px', marginBottom: '16px', justifyContent: 'center' }}>
              ✓ {language === 'vi' ? 'Đã lưu thông tin tài khoản ngân hàng thành công!' : 'Bank account saved successfully!'}
            </div>
          )}

          <form onSubmit={handleSaveBank}>
            <div className="og-form-group">
              <label className="og-label">{language === 'vi' ? 'Ngân hàng thụ hưởng' : 'Bank Name'}</label>
              <select
                className="og-select"
                value={bankAccount.bank}
                onChange={e => setBankAccount({ ...bankAccount, bank: e.target.value })}
              >
                <option value="Vietcombank">Vietcombank (Ngân hàng TMCP Ngoại thương)</option>
                <option value="Techcombank">Techcombank (Ngân hàng Kỹ thương)</option>
                <option value="MBBank">MB Bank (Ngân hàng Quân đội)</option>
                <option value="VPBank">VPBank (Ngân hàng Việt Nam Thịnh Vượng)</option>
                <option value="ACB">ACB (Ngân hàng Á Châu)</option>
              </select>
            </div>

            <div className="og-form-group">
              <label className="og-label">{language === 'vi' ? 'Số tài khoản ngân hàng' : 'Account Number'}</label>
              <input
                type="text"
                className="og-input"
                value={bankAccount.number}
                onChange={e => setBankAccount({ ...bankAccount, number: e.target.value })}
                required
              />
            </div>

            <div className="og-form-group">
              <label className="og-label">{language === 'vi' ? 'Tên chủ tài khoản (In hoa không dấu, trùng với CCCD)' : 'Account Holder Name (Matches ID)'}</label>
              <input
                type="text"
                className="og-input"
                value={bankAccount.name}
                onChange={e => setBankAccount({ ...bankAccount, name: e.target.value })}
                required
              />
            </div>

            <div className="og-co-inspect-card" style={{ fontSize: '12px' }}>
              <span>🛡️</span>
              <div>
                <strong>{language === 'vi' ? 'Bảo mật Escrow:' : 'Escrow Security:'}</strong>{' '}
                {language === 'vi'
                  ? 'Tên tài khoản ngân hàng bắt buộc phải trùng khớp với họ tên trên hồ sơ KYC người bán để phòng chống rửa tiền.'
                  : 'Account holder name must match seller KYC verified ID to prevent fraud.'}
              </div>
            </div>

            <button type="submit" className="og-button og-button--primary" style={{ width: '100%', marginTop: '16px' }}>
              {language === 'vi' ? 'Lưu Thông Tin Ngân Hàng' : 'Save Bank Details'}
            </button>
          </form>
        </div>
      );

    case 'SCR-KYC-01':
      return (
        <div className="og-card" style={{ maxWidth: '680px', margin: '0 auto', padding: '28px' }}>
          <div style={{ textAlign: 'center', marginBottom: '24px' }}>
            <span style={{ fontSize: '32px' }}>🪪</span>
            <h2 style={{ color: 'var(--og-color-primary)', margin: '8px 0 4px' }}>
              {language === 'vi' ? 'Định Danh Người Bán (KYC Seller)' : 'Seller Identity Verification (KYC)'}
            </h2>
            <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '13px', margin: 0 }}>
              {language === 'vi' ? 'Xác thực để nhận huy hiệu Người bán uy tín và mở khóa đăng tin giá trị cao' : 'Verify ID to earn Trust badge and unlock high-value listings'}
            </p>
          </div>

          <form onSubmit={e => { e.preventDefault(); setIsSellerKycApproved(true); setActiveScreenId('SCR-KYC-02'); }}>
            <div className="og-form-grid-2">
              <div className="og-form-group">
                <label className="og-label">{language === 'vi' ? 'Số CCCD / Hộ chiếu' : 'Citizen ID / Passport No.'}</label>
                <input type="text" className="og-input" defaultValue="079201008899" required />
              </div>
              <div className="og-form-group">
                <label className="og-label">{language === 'vi' ? 'Họ và tên theo CCCD' : 'Full Name on ID'}</label>
                <input type="text" className="og-input" defaultValue="HUỲNH LONG BẢO KHÁNH" required />
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '16px', margin: '20px 0' }}>
              <div style={{ border: '2px dashed var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '20px', textAlign: 'center' }}>
                <span style={{ fontSize: '24px' }}>📷</span>
                <div style={{ fontSize: '13px', fontWeight: 600, marginTop: '8px' }}>{language === 'vi' ? 'CCCD Mặt Trước' : 'ID Front'}</div>
                <div style={{ fontSize: '11px', color: 'var(--og-color-text-muted)' }}>{language === 'vi' ? 'Đã tải lên: cccd_front.jpg' : 'Uploaded: cccd_front.jpg'}</div>
              </div>

              <div style={{ border: '2px dashed var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '20px', textAlign: 'center' }}>
                <span style={{ fontSize: '24px' }}>📷</span>
                <div style={{ fontSize: '13px', fontWeight: 600, marginTop: '8px' }}>{language === 'vi' ? 'CCCD Mặt Sau' : 'ID Back'}</div>
                <div style={{ fontSize: '11px', color: 'var(--og-color-text-muted)' }}>{language === 'vi' ? 'Đã tải lên: cccd_back.jpg' : 'Uploaded: cccd_back.jpg'}</div>
              </div>

              <div style={{ border: '2px dashed var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '20px', textAlign: 'center' }}>
                <span style={{ fontSize: '24px' }}>🤳</span>
                <div style={{ fontSize: '13px', fontWeight: 600, marginTop: '8px' }}>{language === 'vi' ? 'Ảnh Selfie Chân Dung' : 'Face Selfie'}</div>
                <div style={{ fontSize: '11px', color: 'var(--og-color-text-muted)' }}>{language === 'vi' ? 'Đã tải lên: selfie.jpg' : 'Uploaded: selfie.jpg'}</div>
              </div>
            </div>

            <button type="submit" className="og-button og-button--primary" style={{ width: '100%' }}>
              {language === 'vi' ? 'Nộp Hồ Sơ Xác Thực KYC' : 'Submit KYC Documents'}
            </button>
          </form>
        </div>
      );

    case 'SCR-KYC-02':
      return (
        <div className="og-card" style={{ maxWidth: '580px', margin: '0 auto', padding: '32px', textAlign: 'center' }}>
          <div style={{ width: '72px', height: '72px', borderRadius: '50%', background: 'var(--og-color-success-bg)', color: 'var(--og-color-success)', fontSize: '36px', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px' }}>
            ✓
          </div>
          <h2 style={{ color: 'var(--og-color-primary)', margin: '0 0 8px' }}>
            {isSellerKycApproved
              ? (language === 'vi' ? 'Định Danh KYC Đã Phê Duyệt!' : 'KYC Verification Approved!')
              : (language === 'vi' ? 'Hồ Sơ Đang Chờ Admin Duyệt' : 'Verification Under Review')}
          </h2>
          <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '14px', marginBottom: '20px' }}>
            {language === 'vi'
              ? 'Tài khoản của bạn đã được cấp huy hiệu Người Bán Xác Thực. Bạn có thể đăng tin giá trị cao và rút tiền Escrow tức thì.'
              : 'Your account is verified with Verified Seller Badge. You can post high-value items and receive instant escrow payouts.'}
          </p>

          <div style={{ background: 'var(--og-color-surface-subtle)', borderRadius: 'var(--og-radius-md)', padding: '16px', textAlign: 'left', marginBottom: '24px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
              <span style={{ color: 'var(--og-color-text-muted)', fontSize: '13px' }}>{language === 'vi' ? 'Huy hiệu:' : 'Badge:'}</span>
              <span className="og-badge og-badge--success">✓ O.G Verified Seller</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
              <span style={{ color: 'var(--og-color-text-muted)', fontSize: '13px' }}>{language === 'vi' ? 'Hạn mức đăng tin:' : 'Listing Limit:'}</span>
              <strong>{language === 'vi' ? 'Không giới hạn' : 'Unlimited'}</strong>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: 'var(--og-color-text-muted)', fontSize: '13px' }}>{language === 'vi' ? 'Thời gian giải ngân Escrow:' : 'Escrow Release:'}</span>
              <strong>{language === 'vi' ? 'Tức thì khi đồng kiểm xong' : 'Instant post-inspection'}</strong>
            </div>
          </div>

          <button type="button" className="og-button og-button--primary" onClick={() => setActiveScreenId('SCR-SELLER-LISTINGS')}>
            {language === 'vi' ? 'Vào Quản Lý Tin Đăng' : 'Go to Listings'}
          </button>
        </div>
      );

    case 'SCR-AUTH-2FA':
      return (
        <div className="og-card" style={{ maxWidth: '540px', margin: '0 auto', padding: '28px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '20px' }}>
            <span style={{ fontSize: '28px' }}>🛡️</span>
            <div>
              <h2 style={{ margin: 0, color: 'var(--og-color-primary)' }}>
                {language === 'vi' ? 'Bảo Mật Hai Lớp (2FA Authenticator)' : 'Two-Factor Authentication (2FA)'}
              </h2>
              <p style={{ margin: '4px 0 0', fontSize: '13px', color: 'var(--og-color-text-secondary)' }}>
                {language === 'vi' ? 'Bảo vệ tài khoản và tiền trong ví Escrow với mã OTP 6 số' : 'Protect wallet & escrow funds with 6-digit TOTP codes'}
              </p>
            </div>
          </div>

          <div style={{ border: '1px solid var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '16px', marginBottom: '20px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <strong>{language === 'vi' ? 'Trạng thái 2FA' : '2FA Status'}</strong>
                <p style={{ margin: '2px 0 0', fontSize: '12px', color: 'var(--og-color-text-secondary)' }}>
                  {is2FaEnabled ? (language === 'vi' ? 'Đang bật (Google Authenticator)' : 'Enabled') : (language === 'vi' ? 'Đang tắt' : 'Disabled')}
                </p>
              </div>
              <button
                type="button"
                className={`og-button og-button--sm ${is2FaEnabled ? 'og-button--danger' : 'og-button--primary'}`}
                onClick={() => setIs2FaEnabled(!is2FaEnabled)}
              >
                {is2FaEnabled ? (language === 'vi' ? 'Tắt 2FA' : 'Disable') : (language === 'vi' ? 'Bật 2FA' : 'Enable')}
              </button>
            </div>
          </div>

          {is2FaEnabled && (
            <div style={{ background: 'var(--og-color-surface-subtle)', borderRadius: 'var(--og-radius-md)', padding: '20px', textAlign: 'center' }}>
              <div style={{ width: '140px', height: '140px', background: '#ffffff', border: '1px solid var(--og-color-border)', margin: '0 auto 12px', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '12px' }}>
                [ QR Code TOTP ]
              </div>
              <p style={{ fontSize: '12px', color: 'var(--og-color-text-secondary)', margin: '0 0 12px' }}>
                {language === 'vi' ? 'Quét mã QR bằng app Google Authenticator hoặc Authy' : 'Scan QR code with Google Authenticator'}
              </p>
              <div style={{ display: 'flex', gap: '8px', maxWidth: '300px', margin: '0 auto' }}>
                <input
                  type="text"
                  className="og-input"
                  placeholder="000 000"
                  maxLength={6}
                  value={twoFaCode}
                  onChange={e => setTwoFaCode(e.target.value)}
                  style={{ textAlign: 'center', letterSpacing: '4px', fontSize: '16px', fontWeight: 700 }}
                />
                <button type="button" className="og-button og-button--primary" onClick={() => alert('2FA Verified!')}>
                  {language === 'vi' ? 'Xác Nhận' : 'Verify'}
                </button>
              </div>
            </div>
          )}
        </div>
      );

    case 'SCR-SELLER-BLOCK':
      return (
        <div className="og-card" style={{ maxWidth: '600px', margin: '0 auto', padding: '32px', textAlign: 'center' }}>
          <div style={{ width: '72px', height: '72px', borderRadius: '50%', background: 'var(--og-color-danger-bg)', color: 'var(--og-color-danger)', fontSize: '36px', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px' }}>
            ⚠️
          </div>
          <h2 style={{ color: 'var(--og-color-danger)', margin: '0 0 8px' }}>
            {language === 'vi' ? 'Tài Khoản Đang Bị Tạm Khóa Giao Dịch' : 'Account Temporarily Restricted'}
          </h2>
          <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '14px', marginBottom: '20px' }}>
            {language === 'vi'
              ? 'Hệ thống phát hiện tài khoản có dấu hiệu cố tình dẫn dụ người mua giao dịch ngoài sàn (chuyển khoản trước qua Zalo) không thông qua O.G Escrow.'
              : 'Our system flagged attempts to conduct off-platform payments outside O.G Escrow protection.'}
          </p>

          <div className="og-defect-box" style={{ textAlign: 'left', marginBottom: '24px' }}>
            <strong style={{ color: 'var(--og-color-danger)' }}>{language === 'vi' ? 'Chi tiết vi phạm:' : 'Violation details:'}</strong>
            <ul style={{ margin: '6px 0 0', paddingLeft: '20px', fontSize: '13px' }}>
              <li>{language === 'vi' ? 'Thời gian vi phạm: 20/09/2026 10:22' : 'Logged at: 20/09/2026 10:22'}</li>
              <li>{language === 'vi' ? 'Nội dung chat vi phạm: "Kết bạn Zalo số 0909123456 chuyển cọc trước"' : 'Chat message: "Add Zalo 0909123456 for direct deposit"'}</li>
              <li>{language === 'vi' ? 'Chế tài: Tạm khóa đăng tin 7 ngày, đóng băng rút tiền Escrow' : 'Penalty: 7-day listing freeze, escrow payout paused'}</li>
            </ul>
          </div>

          <div style={{ display: 'flex', gap: '12px', justifyContent: 'center' }}>
            <button type="button" className="og-button og-button--outline" onClick={() => setActiveScreenId('SCR-DISPUTE-TIMELINE')}>
              {language === 'vi' ? 'Gửi Khiếu Nại Lên Admin' : 'Submit Appeal to Admin'}
            </button>
            <button type="button" className="og-button og-button--primary" onClick={() => setActiveScreenId('SCR-BUYER-HOME')}>
              {language === 'vi' ? 'Về Trang Chủ' : 'Back to Home'}
            </button>
          </div>
        </div>
      );

    case 'SCR-ADMIN-USERS':
      return (
        <div className="og-card" style={{ padding: '24px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
            <div>
              <h2 style={{ margin: 0, color: 'var(--og-color-primary)' }}>
                {language === 'vi' ? 'Quản Lý Người Dùng & Uy Tín' : 'User Directory & Trust Management'}
              </h2>
              <p style={{ margin: '4px 0 0', fontSize: '13px', color: 'var(--og-color-text-secondary)' }}>
                {language === 'vi' ? 'Tổng số: 12,450 tài khoản · 14 yêu cầu KYC đang chờ xử lý' : '12,450 total accounts · 14 pending KYC requests'}
              </p>
            </div>
            <button type="button" className="og-button og-button--primary og-button--sm" onClick={() => setActiveScreenId('SCR-ADMIN-PENALTY')}>
              ⚖️ {language === 'vi' ? 'Mở Bảng Xử Phạt' : 'Open Penalty Panel'}
            </button>
          </div>

          <div style={{ overflowX: 'auto' }}>
            <table className="og-table" style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
              <thead>
                <tr style={{ background: 'var(--og-color-surface-subtle)', textAlign: 'left' }}>
                  <th style={{ padding: '10px 14px' }}>User ID / Họ Tên</th>
                  <th style={{ padding: '10px 14px' }}>Vai Trò</th>
                  <th style={{ padding: '10px 14px' }}>KYC</th>
                  <th style={{ padding: '10px 14px' }}>Điểm Uy Tín</th>
                  <th style={{ padding: '10px 14px' }}>Trạng Thái</th>
                  <th style={{ padding: '10px 14px' }}>Thao Tác</th>
                </tr>
              </thead>
              <tbody>
                {usersList.map(u => (
                  <tr key={u.id} style={{ borderBottom: '1px solid var(--og-color-border)' }}>
                    <td style={{ padding: '12px 14px' }}>
                      <strong>{u.name}</strong>
                      <div style={{ color: 'var(--og-color-text-muted)', fontSize: '11px' }}>{u.email}</div>
                    </td>
                    <td style={{ padding: '12px 14px' }}>
                      <span className={`og-badge ${u.role === 'SELLER' ? 'og-badge--warning' : 'og-badge--primary'}`}>{u.role}</span>
                    </td>
                    <td style={{ padding: '12px 14px' }}>
                      <span className={`og-badge ${u.kyc === 'VERIFIED' ? 'og-badge--success' : 'og-badge--danger'}`}>{u.kyc}</span>
                    </td>
                    <td style={{ padding: '12px 14px' }}>
                      <strong style={{ color: u.trustScore > 90 ? 'var(--og-color-success)' : 'var(--og-color-danger)' }}>{u.trustScore}/100</strong>
                    </td>
                    <td style={{ padding: '12px 14px' }}>
                      <span className={`og-badge ${u.status === 'ACTIVE' ? 'og-badge--success' : 'og-badge--danger'}`}>{u.status}</span>
                    </td>
                    <td style={{ padding: '12px 14px' }}>
                      <button
                        type="button"
                        className="og-button og-button--outline og-button--sm"
                        onClick={() => { setSelectedUserForPenalty(u); setActiveScreenId('SCR-ADMIN-PENALTY'); }}
                      >
                        {language === 'vi' ? 'Xử phạt' : 'Penalize'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      );

    case 'SCR-ADMIN-PENALTY':
      return (
        <div className="og-card" style={{ maxWidth: '640px', margin: '0 auto', padding: '28px' }}>
          <h2 style={{ color: 'var(--og-color-danger)', margin: '0 0 16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span>⚖️</span>
            <span>{language === 'vi' ? 'Áp Dụng Chế Tài Người Dùng Vi Phạm' : 'Enforce Penalty on User'}</span>
          </h2>

          <div style={{ background: 'var(--og-color-surface-subtle)', borderRadius: 'var(--og-radius-md)', padding: '16px', marginBottom: '20px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <strong>{selectedUserForPenalty.name}</strong>
              <span className="og-badge og-badge--danger">{selectedUserForPenalty.email}</span>
            </div>
            <div style={{ fontSize: '13px', color: 'var(--og-color-text-secondary)', marginTop: '4px' }}>
              {language === 'vi' ? 'Điểm uy tín hiện tại:' : 'Current Trust Score:'} {selectedUserForPenalty.trustScore}/100
            </div>
          </div>

          <div className="og-form-group">
            <label className="og-label">{language === 'vi' ? 'Chọn hình thức chế tài' : 'Select Penalty Action'}</label>
            <select className="og-select" value={penaltyAction} onChange={e => setPenaltyAction(e.target.value)}>
              <option value="WARN">{language === 'vi' ? 'Gửi cảnh báo vi phạm qua Email/SMS (Trừ 5 điểm uy tín)' : 'Issue Warning (-5 points)'}</option>
              <option value="FREEZE_7D">{language === 'vi' ? 'Tạm đình chỉ đăng tin 7 ngày (Trừ 15 điểm uy tín)' : 'Freeze listings for 7 days (-15 points)'}</option>
              <option value="BAN">{language === 'vi' ? 'Khóa tài khoản vĩnh viễn & Đóng băng Escrow' : 'Permanent Account Ban & Freeze Escrow'}</option>
            </select>
          </div>

          <div className="og-form-group">
            <label className="og-label">{language === 'vi' ? 'Căn cứ vi phạm (Trích xuất log hệ thống)' : 'Evidence & Log Reference'}</label>
            <textarea
              className="og-textarea"
              rows={3}
              defaultValue="Phát hiện tin nhắn chứa số Zalo 0909123456 cố tình giao dịch ngoài sàn để né phí Escrow."
            />
          </div>

          <div style={{ display: 'flex', gap: '12px', marginTop: '20px' }}>
            <button type="button" className="og-button og-button--danger" style={{ flex: 1 }} onClick={handleApplyPenalty}>
              {language === 'vi' ? 'Xác Nhận Áp Dụng Chế Tài' : 'Apply Enforcement'}
            </button>
            <button type="button" className="og-button og-button--outline" onClick={() => setActiveScreenId('SCR-ADMIN-USERS')}>
              {language === 'vi' ? 'Hủy Bỏ' : 'Cancel'}
            </button>
          </div>
        </div>
      );

    case 'SCR-ADMIN-VOUCHER':
      return (
        <div className="og-card" style={{ padding: '24px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
            <h2 style={{ margin: 0, color: 'var(--og-color-primary)' }}>
              🎟️ {language === 'vi' ? 'Cấu Hình & Quản Lý Mã Khuyến Mãi' : 'Vouchers & Promotions Engine'}
            </h2>
            <button type="button" className="og-button og-button--primary og-button--sm">
              + {language === 'vi' ? 'Tạo Voucher Mới' : 'New Voucher'}
            </button>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '16px' }}>
            {MOCK_VOUCHERS.map(v => (
              <div key={v.id} style={{ border: '2px dashed var(--og-color-accent-gold)', borderRadius: 'var(--og-radius-md)', padding: '16px', background: 'var(--og-color-surface)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <code style={{ fontSize: '16px', fontWeight: 700, color: 'var(--og-color-accent-gold)' }}>{v.code}</code>
                  <span className="og-badge og-badge--success">{v.type === 'shipping' ? 'Freeship' : 'Order'}</span>
                </div>
                <h4 style={{ margin: '8px 0 4px', fontSize: '14px' }}>{v.title[language]}</h4>
                <div style={{ fontSize: '12px', color: 'var(--og-color-text-muted)' }}>
                  {language === 'vi' ? `Hạn dùng: ${v.expiry} · Giảm: ${v.discountAmount.toLocaleString()}₫` : `Expires: ${v.expiry} · Discount: ${v.discountAmount.toLocaleString()}₫`}
                </div>
              </div>
            ))}
          </div>
        </div>
      );

    case 'SCR-ADMIN-POPUP':
      return (
        <div className="og-card" style={{ maxWidth: '640px', margin: '0 auto', padding: '28px' }}>
          <h2 style={{ color: 'var(--og-color-primary)', margin: '0 0 16px' }}>
            📢 {language === 'vi' ? 'Cấu Hình Banner & Popup Cảnh Báo Sàn' : 'System Announcements & Banners'}
          </h2>

          <div className="og-form-group">
            <label className="og-label">{language === 'vi' ? 'Tiêu đề thông báo' : 'Announcement Title'}</label>
            <input type="text" className="og-input" defaultValue="CẢNH BÁO: Không giao dịch ngoài sàn để được O.G Escrow bảo vệ 100%" />
          </div>

          <div className="og-form-group">
            <label className="og-label">{language === 'vi' ? 'Loại hiển thị' : 'Display Format'}</label>
            <select className="og-select" defaultValue="POPUP">
              <option value="BANNER">{language === 'vi' ? 'Top Banner chạy chữ trên đầu trang' : 'Top Scrolling Banner'}</option>
              <option value="POPUP">{language === 'vi' ? 'Popup Modal khi người dùng vào Chat/Thanh toán' : 'Modal Popup in Chat/Checkout'}</option>
            </select>
          </div>

          <div className="og-safety-warning" style={{ marginTop: '16px' }}>
            <strong>{language === 'vi' ? 'Xem trước nội dung:' : 'Preview:'}</strong>
            <p style={{ margin: '4px 0 0' }}>
              {language === 'vi'
                ? 'Mọi giao dịch thanh toán chuyển khoản trực tiếp cho người bán mà không thông qua Escrow đều có nguy cơ bị lừa đảo không nhận được hàng!'
                : 'Direct wire transfers outside Escrow leave you unprotected from fraud and item loss!'}
            </p>
          </div>

          <button type="button" className="og-button og-button--primary" style={{ marginTop: '16px' }} onClick={() => alert('Saved!')}>
            {language === 'vi' ? 'Xuất Bản Thông Báo Lên Sàn' : 'Publish Announcement'}
          </button>
        </div>
      );

    case 'SCR-ADMIN-STATS':
      return (
        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
            <div>
              <h2 style={{ margin: 0, color: 'var(--og-color-primary)' }}>
                📊 {language === 'vi' ? 'Báo Cáo Tổng Quan Hoạt Động & GMV' : 'Platform Analytics & Escrow Metrics'}
              </h2>
              <p style={{ margin: '4px 0 0', fontSize: '13px', color: 'var(--og-color-text-secondary)' }}>
                {language === 'vi' ? 'Dữ liệu thời gian thực được đồng bộ từ Ledger O.G Escrow' : 'Real-time financial metrics from O.G Escrow Ledger'}
              </p>
            </div>
            <span className="og-badge og-badge--success">Live 20/09/2026</span>
          </div>

          {/* Metric Cards */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '16px', marginBottom: '24px' }}>
            <div className="og-card" style={{ padding: '20px', borderLeft: '4px solid var(--og-color-primary)' }}>
              <div style={{ fontSize: '12px', color: 'var(--og-color-text-muted)' }}>{language === 'vi' ? 'TỔNG GMV GIAO DỊCH' : 'TOTAL GMV'}</div>
              <div style={{ fontSize: '24px', fontWeight: 700, color: 'var(--og-color-primary)', marginTop: '4px' }}>{MOCK_ADMIN_METRICS.totalGmv}</div>
            </div>

            <div className="og-card" style={{ padding: '20px', borderLeft: '4px solid var(--og-color-accent-gold)' }}>
              <div style={{ fontSize: '12px', color: 'var(--og-color-text-muted)' }}>{language === 'vi' ? 'ESCROW ĐANG TẠM GIỮ' : 'LOCKED ESCROW POOL'}</div>
              <div style={{ fontSize: '24px', fontWeight: 700, color: 'var(--og-color-accent-gold)', marginTop: '4px' }}>{MOCK_ADMIN_METRICS.escrowHoldingBalance}</div>
            </div>

            <div className="og-card" style={{ padding: '20px', borderLeft: '4px solid var(--og-color-danger)' }}>
              <div style={{ fontSize: '12px', color: 'var(--og-color-text-muted)' }}>{language === 'vi' ? 'KHIẾU NẠI ĐANG XỬ LÝ' : 'ACTIVE DISPUTES'}</div>
              <div style={{ fontSize: '24px', fontWeight: 700, color: 'var(--og-color-danger)', marginTop: '4px' }}>{MOCK_ADMIN_METRICS.activeDisputesCount} {language === 'vi' ? 'vụ' : 'cases'}</div>
            </div>

            <div className="og-card" style={{ padding: '20px', borderLeft: '4px solid var(--og-color-info)' }}>
              <div style={{ fontSize: '12px', color: 'var(--og-color-text-muted)' }}>{language === 'vi' ? 'ĐƠN HOÀN TẤT HÔM NAY' : 'COMPLETED ORDERS TODAY'}</div>
              <div style={{ fontSize: '24px', fontWeight: 700, color: 'var(--og-color-info)', marginTop: '4px' }}>{MOCK_ADMIN_METRICS.dailyCompletedOrders} {language === 'vi' ? 'đơn' : 'orders'}</div>
            </div>
          </div>

          <div className="og-card" style={{ padding: '24px' }}>
            <h3 style={{ marginTop: 0, color: 'var(--og-color-primary)' }}>
              {language === 'vi' ? 'Tỷ Lệ Giải Ngân & Tranh Chấp Escrow' : 'Escrow Payout vs Dispute Rate'}
            </h3>
            <div style={{ height: '16px', background: 'var(--og-color-surface-subtle)', borderRadius: '8px', overflow: 'hidden', display: 'flex', margin: '16px 0' }}>
              <div style={{ width: '94%', background: 'var(--og-color-success)' }} title="94% Hoàn tất êm đẹp" />
              <div style={{ width: '4%', background: 'var(--og-color-accent-gold)' }} title="4% Đang đồng kiểm" />
              <div style={{ width: '2%', background: 'var(--og-color-danger)' }} title="2% Mở khiếu nại" />
            </div>
            <div style={{ display: 'flex', gap: '24px', fontSize: '13px' }}>
              <span>🟢 <strong>94%</strong> {language === 'vi' ? 'Người mua hài lòng & giải ngân' : 'Satisfied & Released'}</span>
              <span>🟡 <strong>4%</strong> {language === 'vi' ? 'Đang vận chuyển & đồng kiểm' : 'In Transit / Inspecting'}</span>
              <span>🔴 <strong>2%</strong> {language === 'vi' ? 'Tranh chấp cần trọng tài' : 'Arbitrated Disputes'}</span>
            </div>
          </div>
        </div>
      );

    default:
      return <div>Module 1: Screen {screenId}</div>;
  }
}
