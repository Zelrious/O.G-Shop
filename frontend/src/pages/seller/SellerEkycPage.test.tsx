import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { SellerEkycPage } from './SellerEkycPage';
import { AuthContext } from '../../features/auth/context';
import { UserPrincipal } from '../../features/auth/types';
import { ekycApi } from '../../features/verification/ekycApi';

const createAuthContextValue = (user: UserPrincipal | null) => ({
  user,
  isAuthenticated: Boolean(user),
  isLoading: false,
  error: null,
  login: vi.fn(),
  register: vi.fn(),
  verifyRegistration: vi.fn(),
  logout: vi.fn(),
  clearError: vi.fn(),
  reloadCurrentUser: vi.fn(),
});

describe('SellerEkycPage Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const verifiedSellerUser: UserPrincipal = {
    userId: 1,
    email: 'seller@ogshop.vn',
    fullName: 'Tran Van Ban Hang',
    phoneNumber: '0987654321',
    roles: ['BUYER', 'SELLER'],
    createdAt: '2026-10-01',
    verifiedSeller: true,
  };

  it('renders seller eKYC profile details card with verified status and masked CCCD', async () => {
    vi.spyOn(ekycApi, 'getStatus').mockResolvedValueOnce({
      hasProfile: true,
      canSubmit: false,
      isSeller: true,
      nonmatchCount: 0,
      state: 'VERIFIED',
      submittedData: {
        cccdNumber: '001095012345',
        fullName: 'TRAN VAN BAN HANG',
        dob: '01/01/1995',
        gender: 'Nam',
        hometown: 'Hà Nội',
        address: '123 Ba Đình, Hà Nội',
      },
    });

    render(
      <MemoryRouter>
        <AuthContext.Provider value={createAuthContextValue(verifiedSellerUser)}>
          <SellerEkycPage />
        </AuthContext.Provider>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Hồ sơ định danh eKYC Người bán')).toBeInTheDocument();
      expect(screen.getByText(/Đã kích hoạt Người bán/i)).toBeInTheDocument();
      expect(screen.getByText(/Đã xác thực eKYC CCCD/i)).toBeInTheDocument();
      expect(screen.getByText('001******345')).toBeInTheDocument();
      expect(screen.getByText('TRAN VAN BAN HANG')).toBeInTheDocument();
      expect(screen.getByText(/Đã đối soát KTV & AI/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Cập nhật lại hồ sơ eKYC mới/i })).toBeInTheDocument();
    });
  });

  it('opens QuickFaceAuthModal when clicking update eKYC, and closing it keeps seller on the page', async () => {
    vi.spyOn(ekycApi, 'getStatus').mockResolvedValueOnce({
      hasProfile: true,
      canSubmit: false,
      isSeller: true,
      nonmatchCount: 0,
      state: 'VERIFIED',
      submittedData: {
        cccdNumber: '001095012345',
        fullName: 'TRAN VAN BAN HANG',
        dob: '01/01/1995',
        gender: 'Nam',
        hometown: 'Hà Nội',
        address: '123 Ba Đình, Hà Nội',
      },
    });

    render(
      <MemoryRouter>
        <AuthContext.Provider value={createAuthContextValue(verifiedSellerUser)}>
          <SellerEkycPage />
        </AuthContext.Provider>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Cập nhật lại hồ sơ eKYC mới/i })).toBeInTheDocument();
    });

    // Click update eKYC button
    fireEvent.click(screen.getByRole('button', { name: /Cập nhật lại hồ sơ eKYC mới/i }));

    // Modal opens
    await waitFor(() => {
      expect(screen.getByText('Xác thực khuôn mặt đối chiếu hồ sơ cũ')).toBeInTheDocument();
    });

    // Click close/cancel on modal
    const cancelBtn = screen.getByRole('button', { name: 'Đóng cửa sổ' });
    fireEvent.click(cancelBtn);

    // Modal is closed, and user remains on seller eKYC page
    await waitFor(() => {
      expect(screen.queryByText('Xác thực khuôn mặt đối chiếu hồ sơ cũ')).not.toBeInTheDocument();
      expect(screen.getByText('Hồ sơ định danh eKYC Người bán')).toBeInTheDocument();
    });
  });
});
