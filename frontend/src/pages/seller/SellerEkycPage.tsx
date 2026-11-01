import React, { useEffect, useState } from 'react';
import { useAuth } from '../../features/auth';
import { ekycApi, EkycStatus, EkycProfileDetailsCard } from '../../features/verification';

export const SellerEkycPage: React.FC = () => {
  const { user } = useAuth();
  const [status, setStatus] = useState<EkycStatus | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let ignore = false;
    setIsLoading(true);
    ekycApi
      .getStatus()
      .then((data) => {
        if (!ignore) setStatus(data);
      })
      .catch(() => {
        // Fallback
      })
      .finally(() => {
        if (!ignore) setIsLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, []);

  return (
    <div style={{ maxWidth: '960px', margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {isLoading && !status ? (
        <div style={{ textAlign: 'center', padding: '40px 0' }}>
          <div className="og-spinner" style={{ margin: '0 auto 12px' }} />
          <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '0.9rem' }}>Đang tải thông tin định danh eKYC...</p>
        </div>
      ) : (
        <EkycProfileDetailsCard
          user={user}
          ekycStatus={status}
          isSellerView={true}
          title="Hồ sơ định danh eKYC Người bán"
          onStatusUpdated={() => {
            void ekycApi.getStatus().then((data) => setStatus(data)).catch(() => {});
          }}
        />
      )}
    </div>
  );
};

export default SellerEkycPage;

