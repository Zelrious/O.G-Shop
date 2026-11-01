import React, { useEffect, useState, useContext } from 'react';
import { UserProfile } from '../index';
import { Alert, Button, Card } from '../../../shared/components';
import { ekycApi, EkycStatus, EkycProfileDetailsCard } from '../../verification';
import { VIETNAMESE_BANKS } from '../../auth';
import { ThemeContext } from '../../../shared/context/themeContext';
import { ChangeBankModal } from './ChangeBankModal';

interface SellerBankTabProps {
  profile: UserProfile;
  onProfileUpdated: (updated: UserProfile) => void;
}

export const SellerBankTab: React.FC<SellerBankTabProps> = ({ profile, onProfileUpdated }) => {
  const themeCtx = useContext(ThemeContext);
  const isDark = themeCtx?.resolvedTheme === 'dark' || (typeof document !== 'undefined' && document.documentElement.getAttribute('data-theme') === 'dark');
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [showBankModal, setShowBankModal] = useState(false);

  // eKYC Details State
  const [ekycStatus, setEkycStatus] = useState<EkycStatus | null>(null);

  useEffect(() => {
    void (async () => {
      try {
        const data = await ekycApi.getStatus();
        setEkycStatus(data);
      } catch {
        // Fallback if status API is not available
      }
    })();
  }, []);

  const handleOpenBankModal = () => {
    setErrorMsg(null);
    setSuccessMsg(null);
    setShowBankModal(true);
  };

  const handleBankModalSuccess = (updated: UserProfile) => {
    onProfileUpdated(updated);
    setSuccessMsg('Cập nhật tài khoản ngân hàng thành công!');
    setTimeout(() => setSuccessMsg(null), 4000);
  };

  return (
    <div className="og-profile-tab" style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {/* Thẻ Định danh eKYC & Quyền lợi giao dịch (Tái sử dụng chung giữa Profile và Kênh Người Bán) */}
      <EkycProfileDetailsCard
        user={profile}
        ekycStatus={ekycStatus}
        onStatusUpdated={() => {
          void ekycApi.getStatus().then((data) => setEkycStatus(data)).catch(() => {});
        }}
      />

      {/* Thẻ Tài khoản Ngân hàng nhận tiền (Escrow) */}
      <Card title="Tài khoản Ngân hàng nhận tiền (Escrow)">
        {successMsg && <Alert type="success">{successMsg}</Alert>}
        {errorMsg && <Alert type="danger">{errorMsg}</Alert>}

        <p style={{ margin: '0 0 18px', fontSize: '0.88rem', color: 'var(--og-color-text-secondary, #64748b)' }}>
          Thông tin tài khoản ngân hàng dùng để nhận tiền khi bạn bán món đồ thành công hoặc nhận hoàn tiền tự động khi phát sinh sự cố. Để bảo vệ an toàn thông tin, số tài khoản luôn được che hiển thị.
        </p>

        {profile.bankAccountNumberMasked ? (
          <div>
            {(() => {
              const bankInfo = profile.bankName
                ? VIETNAMESE_BANKS.find(
                    (b) =>
                      b.shortName.toLowerCase() === profile.bankName?.toLowerCase().trim() ||
                      b.code.toLowerCase() === profile.bankName?.toLowerCase().trim() ||
                      b.name.toLowerCase() === profile.bankName?.toLowerCase().trim() ||
                      profile.bankName?.toLowerCase().includes(b.shortName.toLowerCase()) ||
                      profile.bankName?.toLowerCase().includes(b.code.toLowerCase())
                  )
                : null;

              return (
                <div>
                  {/* Ô Thẻ Ngân Hàng chuẩn tỉ lệ hình chữ nhật ATM (1.586 : 1) - Tự động thích ứng Dark Mode */}
                  <div
                    className="og-seller-atm-card"
                    style={{
                      background: isDark
                        ? 'linear-gradient(135deg, #16362c 0%, #1f483b 50%, #17372d 100%)'
                        : 'linear-gradient(135deg, #ffffff 0%, #f8fafc 45%, #f1f5f9 100%)',
                      border: isDark
                        ? '1.5px solid rgba(52, 211, 153, 0.35)'
                        : '1.5px solid var(--og-color-border, #e2e8f0)',
                      boxShadow: isDark
                        ? '0 12px 30px -5px rgba(0, 0, 0, 0.55), 0 4px 14px rgba(16, 185, 129, 0.15)'
                        : '0 10px 25px -5px rgba(0, 0, 0, 0.08), 0 4px 10px rgba(0, 0, 0, 0.04)',
                      color: isDark ? '#f8fafc' : 'var(--og-color-text-primary, #0f172a)',
                    }}
                  >
                    {/* Họa tiết bảo mật watermark mờ sang trọng */}
                    <div
                      className="og-card-watermark"
                      style={{
                        position: 'absolute',
                        top: 0,
                        right: 0,
                        width: '100%',
                        height: '100%',
                        background: isDark
                          ? 'radial-gradient(circle at 85% 15%, rgba(52, 211, 153, 0.12) 0%, transparent 60%)'
                          : 'radial-gradient(circle at 85% 15%, rgba(16, 185, 129, 0.06) 0%, transparent 60%)',
                        pointerEvents: 'none',
                      }}
                    />
                    <div
                      className="og-card-ring"
                      style={{
                        position: 'absolute',
                        right: '-30px',
                        bottom: '-30px',
                        width: '180px',
                        height: '180px',
                        borderRadius: '50%',
                        border: isDark ? '18px solid rgba(52, 211, 153, 0.06)' : '18px solid rgba(15, 23, 42, 0.02)',
                        pointerEvents: 'none',
                      }}
                    />

                    {/* Dòng 1: Logo ngân hàng + Tên ngân hàng + Huy hiệu Đã liên kết */}
                    <div
                      style={{
                        position: 'relative',
                        zIndex: 1,
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        gap: '12px',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                        {/* Logo ngân hàng hiển thị trực tiếp trên thẻ, không bọc khối nền */}
                        {bankInfo?.logo ? (
                          <img
                            src={bankInfo.logo}
                            alt={profile.bankName || 'Ngân hàng'}
                            style={{
                              height: '26px',
                              maxWidth: '90px',
                              objectFit: 'contain',
                              display: 'block',
                            }}
                          />
                        ) : null}
                        <strong
                          className="og-card-text"
                          style={{
                            fontSize: '1.05rem',
                            fontWeight: 700,
                            letterSpacing: '0.2px',
                            color: isDark ? '#f8fafc' : '#0f172a',
                          }}
                        >
                          {profile.bankName}
                        </strong>
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        {/* Biểu tượng sóng Contactless */}
                        <svg
                          width="16"
                          height="16"
                          viewBox="0 0 24 24"
                          fill="none"
                          stroke="currentColor"
                          strokeWidth="2.2"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          className="og-card-label"
                          style={{ opacity: 0.85, color: isDark ? '#94a3b8' : '#64748b' }}
                          aria-hidden="true"
                        >
                          <path d="M8.5 16.5a5 5 0 0 1 0-7" />
                          <path d="M12 19a9 9 0 0 0 0-14" />
                          <path d="M15.5 21.5a13 13 0 0 0 0-19" />
                        </svg>

                        {/* Huy hiệu Đã liên kết */}
                        <span
                          className="og-card-badge"
                          style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '4px',
                            background: isDark ? 'rgba(52, 211, 153, 0.16)' : 'rgba(16, 185, 129, 0.1)',
                            border: isDark ? '1px solid rgba(52, 211, 153, 0.35)' : '1px solid rgba(16, 185, 129, 0.25)',
                            color: isDark ? '#6ee7b7' : '#059669',
                            fontSize: '0.72rem',
                            fontWeight: 600,
                            padding: '2px 8px',
                            borderRadius: '12px',
                            letterSpacing: '0.2px',
                          }}
                        >
                          <span style={{ width: '6px', height: '6px', borderRadius: '50%', backgroundColor: '#10b981' }} />
                          Đã liên kết
                        </span>
                      </div>
                    </div>

                    {/* Dòng 2: EMV Smart Chip kim loại & Số tài khoản nhận tiền */}
                    <div style={{ position: 'relative', zIndex: 1, margin: '10px 0 8px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '6px' }}>
                        {/* EMV Chip SVG */}
                        <svg
                          width="34"
                          height="25"
                          viewBox="0 0 38 28"
                          fill="none"
                          style={{ borderRadius: '4px', overflow: 'hidden', flexShrink: 0, boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }}
                          aria-hidden="true"
                        >
                          <rect width="38" height="28" rx="4" fill="url(#seller-bank-chip-grad)" />
                          <rect x="0.5" y="0.5" width="37" height="27" rx="3.5" stroke="#ca8a04" strokeOpacity="0.5" />
                          <path d="M0 14H13M25 14H38M13 5V23M25 5V23M13 9H25M13 19H25" stroke="#854d0e" strokeWidth="0.9" />
                          <defs>
                            <linearGradient id="seller-bank-chip-grad" x1="0" y1="0" x2="38" y2="28" gradientUnits="userSpaceOnUse">
                              <stop stopColor="#fef08a" />
                              <stop offset="0.5" stopColor="#eab308" />
                              <stop offset="1" stopColor="#ca8a04" />
                            </linearGradient>
                          </defs>
                        </svg>
                        <span
                          className="og-card-label"
                          style={{
                            fontSize: '0.7rem',
                            textTransform: 'uppercase',
                            letterSpacing: '1px',
                            color: isDark ? '#94a3b8' : '#64748b',
                            fontWeight: 600,
                          }}
                        >
                          Số tài khoản nhận tiền
                        </span>
                      </div>

                      <div
                        className="og-card-text"
                        style={{
                          fontFamily: 'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, "Liberation Mono", monospace',
                          fontSize: '1.32rem',
                          fontWeight: 700,
                          letterSpacing: '2.5px',
                          paddingLeft: '2px',
                          color: isDark ? '#ffffff' : '#0f172a',
                        }}
                      >
                        <strong>{profile.bankAccountNumberMasked}</strong>
                      </div>
                    </div>

                    {/* Dòng 3: Tên chủ tài khoản & Dấu bảo chứng Escrow */}
                    <div
                      style={{
                        position: 'relative',
                        zIndex: 1,
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'flex-end',
                        gap: '12px',
                      }}
                    >
                      <div>
                        <div
                          className="og-card-label"
                          style={{
                            fontSize: '0.66rem',
                            textTransform: 'uppercase',
                            letterSpacing: '1px',
                            color: isDark ? '#94a3b8' : '#64748b',
                            marginBottom: '1px',
                            fontWeight: 600,
                          }}
                        >
                          Chủ tài khoản
                        </div>
                        <strong
                          className="og-card-text"
                          style={{
                            fontSize: '0.94rem',
                            fontWeight: 700,
                            letterSpacing: '0.8px',
                            textTransform: 'uppercase',
                            display: 'block',
                            color: isDark ? '#f1f5f9' : '#0f172a',
                          }}
                        >
                          {profile.bankAccountHolder}
                        </strong>
                      </div>

                      <div
                        className="og-card-emblem"
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: '4px',
                          fontSize: '0.72rem',
                          fontWeight: 700,
                          letterSpacing: '0.6px',
                          color: isDark ? '#6ee7b7' : '#64748b',
                          opacity: isDark ? 0.95 : 0.8,
                        }}
                      >
                        <svg
                          width="13"
                          height="13"
                          viewBox="0 0 24 24"
                          fill="none"
                          stroke="currentColor"
                          strokeWidth="2.2"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          aria-hidden="true"
                        >
                          <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                        </svg>
                        <span>O.G ESCROW</span>
                      </div>
                    </div>
                  </div>

                  {/* Nút thao tác thay đổi tài khoản đặt gọn gàng bên dưới thẻ */}
                  <div style={{ marginTop: '16px', display: 'flex', alignItems: 'center', gap: '12px' }}>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={handleOpenBankModal}
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '6px',
                        fontWeight: 600,
                      }}
                    >
                      <svg
                        width="14"
                        height="14"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2.2"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        aria-hidden="true"
                      >
                        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                        <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                      </svg>
                      Thay đổi tài khoản ngân hàng
                    </Button>
                  </div>

                  {/* Phần chú thích được đưa xuống dưới, bỏ hoàn toàn lớp bọc nền xám */}
                  <div
                    style={{
                      marginTop: '20px',
                      paddingTop: '16px',
                      borderTop: '1px solid var(--og-color-border, #e2e8f0)',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '10px',
                      fontSize: '0.85rem',
                      color: 'var(--og-color-text-secondary, #64748b)',
                      lineHeight: 1.5,
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--og-color-text-primary, #1e293b)', fontWeight: 600, fontSize: '0.9rem' }}>
                      <span style={{ color: 'var(--og-color-primary, #276347)' }}>✓</span>
                      <span>Tài khoản giải ngân hợp lệ — Được bảo vệ bởi hệ thống Ký quỹ Escrow O.G Shop</span>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
                      <span style={{ color: 'var(--og-color-primary, #276347)', fontWeight: 'bold' }}>⚡</span>
                      <span>
                        <strong>Giải ngân tự động:</strong> Doanh thu bán đồ cũ được quyết toán nhanh Napas 24/7 vào số tài khoản này ngay khi đơn hàng hoàn tất.
                      </span>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
                      <span style={{ color: '#d97706', fontWeight: 'bold' }}>🔒</span>
                      <span>
                        <strong>Bảo mật tài khoản:</strong> Mọi yêu cầu thay đổi tài khoản nhận tiền đều được kiểm soát qua xác thực đa tầng (OTP / Quick Face Auth).
                      </span>
                    </div>
                  </div>
                </div>
              );
            })()}
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <Alert type="info">
              Bạn chưa liên kết tài khoản ngân hàng nhận tiền giải ngân khi bán đồ cũ.
            </Alert>
            <div>
              <Button variant="primary" size="sm" onClick={handleOpenBankModal}>
                Liên kết tài khoản ngân hàng ngay
              </Button>
            </div>
          </div>
        )}
      </Card>

      {/* Modal Thay Đổi Tài Khoản Ngân Hàng */}
      <ChangeBankModal
        isOpen={showBankModal}
        onClose={() => setShowBankModal(false)}
        onSuccess={handleBankModalSuccess}
        currentBankName={profile.bankName}
        currentBankAccountNumberMasked={profile.bankAccountNumberMasked}
        currentBankAccountHolder={profile.bankAccountHolder}
        accountEmail={profile.email}
        isVerifiedSeller={profile.verifiedSeller ?? false}
      />
    </div>
  );
};
