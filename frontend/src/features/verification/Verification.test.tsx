import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { VerificationStatusCard } from './components/VerificationStatusCard';
import { VerificationRecord } from './types';
import { SellerVerificationPage } from '../../pages/SellerVerificationPage';
import { AuthContext } from '../auth/context';
import { AuthContextType, UserPrincipal } from '../auth/types';
import { verificationApi } from './verificationApi';

const createAuthContextValue = (
  user: UserPrincipal | null,
  reloadCurrentUser = vi.fn().mockResolvedValue(undefined)
): AuthContextType => ({
  user,
  isAuthenticated: !!user,
  isLoading: false,
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
  refreshSession: vi.fn().mockResolvedValue(true),
  reloadCurrentUser,
});

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

describe('SellerVerificationPage MVP Flow', () => {
  const buyerUser: UserPrincipal = {
    userId: 1,
    email: 'buyer@ogshop.vn',
    fullName: 'Nguyen Van A',
    phoneNumber: '0901234567',
    roles: ['BUYER'],
    createdAt: '2026-09-20T00:00:00Z',
  };

  const sellerUser: UserPrincipal = {
    ...buyerUser,
    roles: ['BUYER', 'SELLER'],
  };

  it('renders MVP warning notice and activate CTA when not a seller', () => {
    render(
      <MemoryRouter>
        <AuthContext.Provider value={createAuthContextValue(buyerUser)}>
          <SellerVerificationPage />
        </AuthContext.Provider>
      </MemoryRouter>
    );

    expect(screen.getByText(/Chế độ MVP:/i)).toBeInTheDocument();
    expect(screen.getByText(/bước xác minh eKYC thật đang tạm thời được bỏ qua/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Kích hoạt quyền Người bán/i })).toBeInTheDocument();
  });

  it('calls activate API and reloads current user on click', async () => {
    const reloadMock = vi.fn().mockResolvedValue(undefined);
    vi.spyOn(verificationApi, 'activateMvpSeller').mockResolvedValue({
      verificationId: 101,
      status: 'VERIFIED',
      verificationMethod: 'MVP_BYPASS',
    });

    render(
      <MemoryRouter>
        <AuthContext.Provider value={createAuthContextValue(buyerUser, reloadMock)}>
          <SellerVerificationPage />
        </AuthContext.Provider>
      </MemoryRouter>
    );

    const activateBtn = screen.getByRole('button', { name: /Kích hoạt quyền Người bán/i });
    fireEvent.click(activateBtn);

    await waitFor(() => {
      expect(verificationApi.activateMvpSeller).toHaveBeenCalledTimes(1);
      expect(reloadMock).toHaveBeenCalledTimes(1);
      expect(screen.getByText(/Kích hoạt quyền Người bán thành công!/i)).toBeInTheDocument();
    });
  });

  it('displays backend error when activate API fails', async () => {
    vi.spyOn(verificationApi, 'activateMvpSeller').mockRejectedValue(
      new Error('Chế độ kích hoạt Seller tức thì hiện không khả dụng.')
    );

    render(
      <MemoryRouter>
        <AuthContext.Provider value={createAuthContextValue(buyerUser)}>
          <SellerVerificationPage />
        </AuthContext.Provider>
      </MemoryRouter>
    );

    const activateBtn = screen.getByRole('button', { name: /Kích hoạt quyền Người bán/i });
    fireEvent.click(activateBtn);

    await waitFor(() => {
      expect(screen.getByText('Chế độ kích hoạt Seller tức thì hiện không khả dụng.')).toBeInTheDocument();
    });
  });

  it('renders completed state instead of activation CTA when already a seller', () => {
    render(
      <MemoryRouter>
        <AuthContext.Provider value={createAuthContextValue(sellerUser)}>
          <SellerVerificationPage />
        </AuthContext.Provider>
      </MemoryRouter>
    );

    expect(screen.getByText(/Đã kích hoạt quyền Người bán/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Kích hoạt quyền Người bán/i })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Về trang Hồ sơ cá nhân/i })).toBeInTheDocument();
  });
});
