import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { VerificationStatusCard } from './components/VerificationStatusCard';
import { VerificationRecord } from './types';

describe('VerificationStatusCard Component', () => {
  it('renders call to action when no verification record exists', () => {
    render(<VerificationStatusCard record={null} />);

    expect(screen.getByRole('heading', { level: 2, name: /Xác minh Người bán/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Bắt đầu xác minh eKYC ngay/i })).toBeInTheDocument();
  });

  it('renders verified badge and citizen details when record is VERIFIED', () => {
    const verifiedRecord: VerificationRecord = {
      verificationId: 101,
      userId: 1,
      verificationMethod: 'AI_EKYC',
      status: 'VERIFIED',
      ocrData: {
        cccdNumber: '079098123456',
        fullName: 'NGUYỄN VĂN AN',
        dob: '15/08/1998',
        hometown: 'Nam Định',
        address: 'TP. Hồ Chí Minh',
      },
      similarityScore: 0.245,
      submittedAt: new Date().toISOString(),
    };

    render(<VerificationStatusCard record={verifiedRecord} />);

    expect(screen.getByText(/Đã xác minh \(Verified Seller\)/i)).toBeInTheDocument();
    expect(screen.getByText('079098123456')).toBeInTheDocument();
    expect(screen.getByText('NGUYỄN VĂN AN')).toBeInTheDocument();
    expect(screen.getByText(/0.245 \(Cosine Distance\)/i)).toBeInTheDocument();
  });
});
