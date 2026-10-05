import React from 'react';
import { VnPayReturnView } from '../features/payment/components/VnPayReturnView';

export const VnPayReturnPage: React.FC = () => {
  return (
    <div className="og-container" style={{ padding: '24px 16px' }}>
      <VnPayReturnView />
    </div>
  );
};
