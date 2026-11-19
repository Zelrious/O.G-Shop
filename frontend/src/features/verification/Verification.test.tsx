import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { VerificationStatusCard } from './components/VerificationStatusCard';
import { VerificationRecord } from './types';
import { SellerVerificationPage } from '../../pages/SellerVerificationPage';
import { AuthContext } from '../auth/context';
import { AuthContextType, UserPrincipal } from '../auth/types';
import { verificationApi } from './verificationApi';
import { ekycApi } from './ekycApi';

const createAuthContextValue = (
  user: UserPrincipal | null,
  reloadCurrentUser = vi.fn().mockResolvedValue(undefined)
): AuthContextType => ({
  user,
  isAuthenticated: !!user,
  isLoading: false,
  login: vi.fn(),
  register: vi.fn(),
  verifyRegistration: vi.fn(),
  logout: vi.fn(),
  reloadCurrentUser,
});

describe('VerificationStatusCard Component', () => {
  it('requires approval for a newly registered buyer instead of offering immediate activation', () => {
    const user: UserPrincipal = { userId: 8, email: 'new@example.invalid', fullName: 'New Buyer',
      roles: ['BUYER'], createdAt: '2026-10-10', sellerApprovalRequired: true };
    render(<MemoryRouter><AuthContext.Provider value={createAuthContextValue(user)}><SellerVerificationPage /></AuthContext.Provider></MemoryRouter>);
    expect(screen.getByRole('heading', { name: 'Xác minh hồ sơ Người bán' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Kích hoạt quyền Người bán' })).not.toBeInTheDocument();
    expect(screen.getByText(/sau eKYC và kiểm duyệt/)).toBeInTheDocument();
  });
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

describe('SellerVerificationPage eKYC Rejected Flow', () => {
  const rejectedUser: UserPrincipal = {
    userId: 2,
    email: 'rejected@ogshop.vn',
    fullName: 'Lê Văn Bị Từ Chối',
    phoneNumber: '0912345678',
    roles: ['BUYER'],
    createdAt: '2026-09-20T00:00:00Z',
    sellerApprovalRequired: true,
  };

  it('renders rejection view with details, KTV reason, images, and action buttons', async () => {
    vi.spyOn(ekycApi, 'getStatus').mockResolvedValue({
      hasProfile: true,
      profileId: 99,
      revisionNo: 1,
      state: 'REJECTED',
      rejectionReason: 'Ảnh mặt trước CCCD bị lóa sáng đèn flash',
      submittedAt: '2026-10-25T10:00:00Z',
      verifiedAt: '2026-10-25T11:00:00Z',
      canSubmit: true,
      isSeller: false,
      nonmatchCount: 0,
      frontAssetId: 101,
      backAssetId: 102,
      liveAssetId: 103,
      submittedData: {
        cccdNumber: '001095012345',
        fullName: 'LÊ VĂN BỊ TỪ CHỐI',
        dob: '01/01/1990',
        gender: 'Nam',
        hometown: 'Hà Nội',
        address: '123 Đường Cũ, Hà Nội',
      },
    });

    render(
      <MemoryRouter>
        <AuthContext.Provider value={createAuthContextValue(rejectedUser)}>
          <SellerVerificationPage />
        </AuthContext.Provider>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Hồ sơ xác thực danh tính (eKYC) chưa được phê duyệt')).toBeInTheDocument();
      expect(screen.getByText('Ảnh mặt trước CCCD bị lóa sáng đèn flash')).toBeInTheDocument();
      expect(screen.getByText('001095012345')).toBeInTheDocument();
      expect(screen.getByText('LÊ VĂN BỊ TỪ CHỐI')).toBeInTheDocument();
      expect(screen.getByText('CCCD Mặt trước')).toBeInTheDocument();
      expect(screen.getByText('CCCD Mặt sau (Mã MRZ)')).toBeInTheDocument();
      expect(screen.getByText('Chân dung Webcam')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Quay lại/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Khiếu nại kết quả/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Xác thực lại hồ sơ mới/i })).toBeInTheDocument();
    });
  });

  it('allows opening complaint modal and submitting a complaint', async () => {
    vi.spyOn(ekycApi, 'getStatus').mockResolvedValue({
      hasProfile: true,
      profileId: 99,
      revisionNo: 1,
      state: 'REJECTED',
      rejectionReason: 'Ảnh bị mờ',
      canSubmit: true,
      isSeller: false,
      nonmatchCount: 0,
      frontAssetId: 101,
      backAssetId: 102,
      liveAssetId: 103,
      submittedData: {
        cccdNumber: '001095012345',
        fullName: 'LÊ VĂN BỊ TỪ CHỐI',
        dob: '01/01/1990',
        gender: 'Nam',
        hometown: 'Hà Nội',
        address: '123 Đường Cũ, Hà Nội',
      },
    });

    render(
      <MemoryRouter>
        <AuthContext.Provider value={createAuthContextValue(rejectedUser)}>
          <SellerVerificationPage />
        </AuthContext.Provider>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Khiếu nại kết quả/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Khiếu nại kết quả/i }));
    expect(screen.getByRole('dialog', { name: 'Khiếu nại kết quả eKYC' })).toBeInTheDocument();

    const contentInput = screen.getByPlaceholderText(/Mô tả chi tiết lý do bạn cho rằng hồ sơ đã hợp lệ/i);
    fireEvent.change(contentInput, { target: { value: 'Tôi đã chụp ảnh rất rõ nét, xin kiểm tra lại.' } });

    fireEvent.click(screen.getByRole('button', { name: 'Gửi khiếu nại' }));

    await waitFor(() => {
      expect(screen.getByText(/Khiếu nại #CMP-.*của bạn đã được gửi thành công đến Ban Quản Trị/i)).toBeInTheDocument();
    });
  });

  it('allows starting reverification and moving to step 2', async () => {
    vi.spyOn(ekycApi, 'getStatus').mockResolvedValue({
      hasProfile: true,
      profileId: 99,
      revisionNo: 1,
      state: 'REJECTED',
      rejectionReason: 'Ảnh bị mờ',
      canSubmit: true,
      isSeller: false,
      nonmatchCount: 0,
      frontAssetId: 101,
      backAssetId: 102,
      liveAssetId: 103,
    });
    vi.spyOn(ekycApi, 'startOrResume').mockResolvedValue({
      profileId: 100,
      revisionNo: 2,
      state: 'IN_PROGRESS',
    });

    render(
      <MemoryRouter>
        <AuthContext.Provider value={createAuthContextValue(rejectedUser)}>
          <SellerVerificationPage />
        </AuthContext.Provider>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Xác thực lại hồ sơ mới/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Xác thực lại hồ sơ mới/i }));

    await waitFor(() => {
      expect(screen.getByText(/1. Mặt trước CCCD gắn chip/i)).toBeInTheDocument();
      expect(screen.getByText(/2. Mặt sau CCCD/i)).toBeInTheDocument();
    });
  });

  it('allows scanning OCR and proceeding with front-side image only', async () => {
    vi.spyOn(ekycApi, 'getStatus').mockResolvedValue({
      hasProfile: false,
      canSubmit: true,
      isSeller: false,
      nonmatchCount: 0,
      state: 'NOT_STARTED',
    });
    vi.spyOn(ekycApi, 'startOrResume').mockResolvedValue({
      profileId: 101,
      revisionNo: 1,
      state: 'IN_PROGRESS',
    });
    vi.spyOn(ekycApi, 'uploadCccd').mockResolvedValue({
      status: 'SUCCESS',
      frontAssetId: 201,
      ocrData: {
        cccdNumber: '001095012345',
        fullName: 'NGUYEN VAN A',
        dob: '01/01/1995',
        gender: 'Nam',
        hometown: 'Hà Nội',
        address: '123 Phố Huế, Hà Nội',
        mrzVerified: false,
      },
    });

    render(
      <MemoryRouter>
        <AuthContext.Provider value={createAuthContextValue(rejectedUser)}>
          <SellerVerificationPage />
        </AuthContext.Provider>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Bắt đầu xác minh eKYC/i })).toBeInTheDocument();
    });
    fireEvent.click(screen.getByRole('button', { name: /Bắt đầu xác minh eKYC/i }));

    await waitFor(() => {
      expect(screen.getByText(/1. Mặt trước CCCD gắn chip/i)).toBeInTheDocument();
    });

    const frontInput = document.getElementById('frontCccdInput') as HTMLInputElement;
    const backInput = document.getElementById('backCccdInput') as HTMLInputElement;
    const portraitInput = document.getElementById('portraitInput') as HTMLInputElement;

    const frontFile = new File(['fake-front'], 'front.jpg', { type: 'image/jpeg' });
    const backFile = new File(['fake-back'], 'back.jpg', { type: 'image/jpeg' });
    const portraitFile = new File(['fake-portrait'], 'portrait.jpg', { type: 'image/jpeg' });

    // Chỉ mới tải mặt trước -> nút vẫn bị disabled
    fireEvent.change(frontInput, { target: { files: [frontFile] } });
    const scanBtn = screen.getByRole('button', { name: /Bóc tách OCR & Đối chiếu tiếp tục/i });
    expect(scanBtn).toBeDisabled();

    // Tải thêm mặt sau -> nút vẫn bị disabled vì bắt buộc cả ảnh chân dung
    fireEvent.change(backInput, { target: { files: [backFile] } });
    expect(scanBtn).toBeDisabled();

    // Tải đủ cả 3 ảnh (Mặt trước, Mặt sau, Ảnh thẻ chân dung) -> nút được enable!
    fireEvent.change(portraitInput, { target: { files: [portraitFile] } });
    expect(scanBtn).not.toBeDisabled();
    fireEvent.click(scanBtn);

    await waitFor(() => {
      expect(ekycApi.uploadCccd).toHaveBeenCalledWith(frontFile, backFile, portraitFile);
      expect(screen.getByDisplayValue('001095012345')).toBeInTheDocument();
      expect(screen.getByDisplayValue('NGUYEN VAN A')).toBeInTheDocument();
    });
  });

  it('renders store registration form for eKYC-verified buyer and allows instant activation', async () => {
    const verifiedUser: UserPrincipal = {
      userId: 1,
      email: 'usertest1@gmail.com',
      fullName: 'User test 1',
      phoneNumber: '0901234567',
      roles: ['BUYER'],
      createdAt: '2026-09-20T00:00:00Z',
      verifiedSeller: true,
    };

    vi.spyOn(ekycApi, 'getStatus').mockResolvedValue({
      hasProfile: true,
      hasVerifiedProfile: true,
      canSubmit: false,
      isSeller: false,
      nonmatchCount: 0,
      state: 'VERIFIED',
    });

    render(
      <MemoryRouter>
        <AuthContext.Provider value={createAuthContextValue(verifiedUser)}>
          <SellerVerificationPage />
        </AuthContext.Provider>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Đăng ký mở gian hàng Người bán')).toBeInTheDocument();
      expect(screen.getByText(/Danh tính eKYC đã được xác thực thành công/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Hoàn tất đăng ký & Kích hoạt quyền Người bán/i })).toBeInTheDocument();
    });
  });
});
