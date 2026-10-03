import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth';
import {
  UserProfile,
  profileApi,
  ProfileInfoTab,
  AddressBookTab,
  SecurityTab,
  SellerBankTab,
} from '../features/profile';
import { Card, Badge, Button, Alert } from '../shared/components';

type TabKey = 'info' | 'addresses' | 'security' | 'seller';

export const ProfilePage: React.FC = () => {
  const { logout } = useAuth();
  const navigate = useNavigate();

  const [activeTab, setActiveTab] = useState<TabKey>('info');
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    setIsLoading(true);
    profileApi
      .getProfile()
      .then((data) => {
        if (!ignore) {
          setProfile(data);
          setErrorMsg(null);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          setErrorMsg(err instanceof Error ? err.message : 'Không thể tải thông tin hồ sơ.');
        }
      })
      .finally(() => {
        if (!ignore) {
          setIsLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, []);

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const handleProfileUpdated = (updated: UserProfile) => {
    setProfile(updated);
  };

  if (isLoading) {
    return (
      <div style={{ textAlign: 'center', padding: '60px 0' }}>
        <div className="og-spinner" style={{ margin: '0 auto 16px' }} />
        <p style={{ color: 'var(--og-color-text-secondary)' }}>Đang tải thông tin hồ sơ...</p>
      </div>
    );
  }

  if (errorMsg || !profile) {
    return (
      <div style={{ maxWidth: '800px', margin: '40px auto', padding: '0 16px' }}>
        <Alert type="danger">{errorMsg || 'Không tìm thấy hồ sơ người dùng.'}</Alert>
        <div style={{ marginTop: '16px', display: 'flex', gap: '12px' }}>
          <Button variant="outline" onClick={() => window.location.reload()}>
            Tải lại
          </Button>
          <Button variant="outline" onClick={handleLogout}>
            Đăng xuất
          </Button>
        </div>
      </div>
    );
  }

  const isSeller = profile.roles.includes('SELLER');

  const tabs: { key: TabKey; label: string; icon: string }[] = [
    { key: 'info', label: 'Thông tin cá nhân', icon: '👤' },
    { key: 'addresses', label: 'Sổ địa chỉ', icon: '📍' },
    { key: 'security', label: 'Bảo mật & Mật khẩu', icon: '🔒' },
    { key: 'seller', label: 'Gian hàng & Thanh toán', icon: '🏪' },
  ];

  return (
    <div style={{ maxWidth: '900px', margin: '24px auto', padding: '0 16px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {/* Header Profile Card */}
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <div
              style={{
                width: '64px',
                height: '64px',
                borderRadius: '50%',
                backgroundColor: 'var(--og-color-surface-sunken)',
                border: '2px solid var(--og-color-border-subtle)',
                overflow: 'hidden',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '1.8rem',
                color: 'var(--og-color-text-tertiary)',
              }}
            >
              {profile.avatarUrl ? (
                <img
                  src={profile.avatarUrl}
                  alt={profile.fullName}
                  style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                />
              ) : (
                profile.fullName ? profile.fullName.charAt(0).toUpperCase() : '👤'
              )}
            </div>

            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                <h2 style={{ margin: 0, fontSize: '1.3rem', color: 'var(--og-color-text-primary)' }}>
                  {profile.fullName}
                </h2>
                {isSeller ? (
                  <Badge variant="seller">Người bán</Badge>
                ) : (
                  <Badge variant="buyer">Người mua</Badge>
                )}
                {profile.verifiedSeller && <Badge variant="seller">✓ eKYC</Badge>}
              </div>
              <p style={{ margin: 0, color: 'var(--og-color-text-secondary)', fontSize: '0.88rem' }}>
                {profile.email} {profile.phoneNumber ? `· ${profile.phoneNumber}` : ''}
              </p>
            </div>
          </div>

          <Button variant="outline" size="sm" onClick={handleLogout}>
            Đăng xuất
          </Button>
        </div>
      </Card>

      {/* Tab Navigation */}
      <div
        style={{
          display: 'flex',
          gap: '8px',
          borderBottom: '1px solid var(--og-color-border-subtle)',
          overflowX: 'auto',
          paddingBottom: '2px',
        }}
      >
        {tabs.map((tab) => {
          const isActive = activeTab === tab.key;
          return (
            <button
              key={tab.key}
              type="button"
              onClick={() => setActiveTab(tab.key)}
              style={{
                padding: '10px 18px',
                border: 'none',
                background: 'none',
                cursor: 'pointer',
                fontSize: '0.92rem',
                fontWeight: isActive ? 600 : 500,
                color: isActive ? 'var(--og-color-primary)' : 'var(--og-color-text-secondary)',
                borderBottom: `2.5px solid ${isActive ? 'var(--og-color-primary)' : 'transparent'}`,
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                whiteSpace: 'nowrap',
                transition: 'all 0.2s ease',
              }}
            >
              <span>{tab.icon}</span>
              <span>{tab.label}</span>
            </button>
          );
        })}
      </div>

      {/* Tab Content */}
      <Card>
        {activeTab === 'info' && (
          <ProfileInfoTab profile={profile} onProfileUpdated={handleProfileUpdated} />
        )}
        {activeTab === 'addresses' && <AddressBookTab />}
        {activeTab === 'security' && <SecurityTab />}
        {activeTab === 'seller' && (
          <SellerBankTab profile={profile} onProfileUpdated={handleProfileUpdated} />
        )}
      </Card>
    </div>
  );
};
