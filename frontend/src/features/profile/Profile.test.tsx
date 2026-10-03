import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ProfileInfoTab } from './components/ProfileInfoTab';
import { SecurityTab } from './components/SecurityTab';
import { SellerBankTab } from './components/SellerBankTab';
import { UserProfile } from './types';
import { profileApi } from './profileApi';
import { MemoryRouter } from 'react-router-dom';

const mockReloadCurrentUser = vi.fn().mockResolvedValue(undefined);

vi.mock('../auth', () => ({
  useAuth: () => ({
    reloadCurrentUser: mockReloadCurrentUser,
  }),
}));

const mockProfile: UserProfile = {
  id: 1,
  email: 'buyer@example.com',
  fullName: 'Nguyễn Văn A',
  phoneNumber: '0901234567',
  avatarUrl: null,
  roles: ['BUYER'],
  bankName: 'MB Bank',
  bankAccountNumberMasked: '******4321',
  bankAccountHolder: 'NGUYEN VAN A',
  verifiedSeller: false,
  createdAt: '2026-10-01T00:00:00Z',
};

describe('Profile Feature Components', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:mock-avatar-preview');
    globalThis.URL.revokeObjectURL = vi.fn();
  });

  it('ProfileInfoTab renders user details and handles input change', () => {
    const onUpdate = vi.fn();
    render(<ProfileInfoTab profile={mockProfile} onProfileUpdated={onUpdate} />);

    expect(screen.getByDisplayValue('Nguyễn Văn A')).toBeInTheDocument();
    expect(screen.getByDisplayValue('buyer@example.com')).toBeInTheDocument();
    expect(screen.getByDisplayValue('0901234567')).toBeInTheDocument();

    const nameInput = screen.getByDisplayValue('Nguyễn Văn A');
    fireEvent.change(nameInput, { target: { value: 'Nguyễn Văn B' } });
    expect(screen.getByDisplayValue('Nguyễn Văn B')).toBeInTheDocument();
  });

  it('ProfileInfoTab handles avatar file selection, preview, and cancel', () => {
    const onUpdate = vi.fn();
    render(<ProfileInfoTab profile={mockProfile} onProfileUpdated={onUpdate} />);

    const fileInput = screen.getByTestId('avatar-file-input');
    const validFile = new File(['mock content'], 'avatar.png', { type: 'image/png' });

    fireEvent.change(fileInput, { target: { files: [validFile] } });

    expect(globalThis.URL.createObjectURL).toHaveBeenCalledWith(validFile);
    expect(screen.getByAltText('Xem trước ảnh đại diện')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Bỏ chọn' })).toBeInTheDocument();

    // Click cancel
    fireEvent.click(screen.getByRole('button', { name: 'Bỏ chọn' }));
    expect(globalThis.URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-avatar-preview');
    expect(screen.queryByAltText('Xem trước ảnh đại diện')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Bỏ chọn' })).toBeNull();
  });

  it('ProfileInfoTab rejects files exceeding 5 MiB or with invalid format', () => {
    const onUpdate = vi.fn();
    render(<ProfileInfoTab profile={mockProfile} onProfileUpdated={onUpdate} />);

    const fileInput = screen.getByTestId('avatar-file-input');

    // 1. Oversized file
    const oversizedFile = new File(['a'.repeat(100)], 'huge.jpg', { type: 'image/jpeg' });
    Object.defineProperty(oversizedFile, 'size', { value: 6 * 1024 * 1024 });
    fireEvent.change(fileInput, { target: { files: [oversizedFile] } });
    expect(screen.getByText('Ảnh đại diện tối đa 5 MiB.')).toBeInTheDocument();

    // 2. Invalid format
    const invalidFile = new File(['video content'], 'clip.mp4', { type: 'video/mp4' });
    fireEvent.change(fileInput, { target: { files: [invalidFile] } });
    expect(screen.getByText('Chọn ảnh JPEG, PNG hoặc WebP tĩnh hợp lệ.')).toBeInTheDocument();
  });

  it('ProfileInfoTab uploads avatar via multipart API and synchronizes principal', async () => {
    const onUpdate = vi.fn();
    const updatedUser: UserProfile = {
      ...mockProfile,
      fullName: 'Nguyễn Văn Đã Đổi',
      avatarUrl: 'https://res.cloudinary.com/og/image/upload/v1/new-avatar.jpg',
    };
    const updateSpy = vi.spyOn(profileApi, 'updateProfileWithAvatar').mockResolvedValue(updatedUser);

    render(<ProfileInfoTab profile={mockProfile} onProfileUpdated={onUpdate} />);

    const nameInput = screen.getByDisplayValue('Nguyễn Văn A');
    fireEvent.change(nameInput, { target: { value: 'Nguyễn Văn Đã Đổi' } });

    const fileInput = screen.getByTestId('avatar-file-input');
    const validFile = new File(['image-bytes'], 'avatar.jpg', { type: 'image/jpeg' });
    fireEvent.change(fileInput, { target: { files: [validFile] } });

    const submitBtn = screen.getByRole('button', { name: 'Lưu thay đổi' });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(updateSpy).toHaveBeenCalledWith(
        { fullName: 'Nguyễn Văn Đã Đổi', phoneNumber: '0901234567' },
        validFile
      );
      expect(onUpdate).toHaveBeenCalledWith(updatedUser);
      expect(mockReloadCurrentUser).toHaveBeenCalled();
      expect(screen.getByText('Cập nhật thông tin hồ sơ thành công!')).toBeInTheDocument();
    });
  });

  it('SecurityTab validates password length and match', () => {
    render(<SecurityTab />);

    const currentPass = screen.getByPlaceholderText('Nhập mật khẩu đang dùng');
    const newPass = screen.getByPlaceholderText('Tối thiểu 6 ký tự');
    const confirmPass = screen.getByPlaceholderText('Nhập lại mật khẩu mới');
    const submitBtn = screen.getByRole('button', { name: 'Cập nhật mật khẩu' });

    fireEvent.change(currentPass, { target: { value: 'current123' } });
    fireEvent.change(newPass, { target: { value: '123' } });
    fireEvent.change(confirmPass, { target: { value: '123' } });
    fireEvent.click(submitBtn);

    expect(screen.getByText('Mật khẩu mới phải có ít nhất 6 ký tự.')).toBeInTheDocument();

    fireEvent.change(newPass, { target: { value: 'validpass123' } });
    fireEvent.change(confirmPass, { target: { value: 'mismatchpass' } });
    fireEvent.click(submitBtn);

    expect(screen.getByText('Mật khẩu xác nhận không khớp với mật khẩu mới.')).toBeInTheDocument();
  });

  it('SellerBankTab displays masked bank account and seller status', () => {
    const onUpdate = vi.fn();
    render(
      <MemoryRouter>
        <SellerBankTab profile={mockProfile} onProfileUpdated={onUpdate} />
      </MemoryRouter>
    );

    expect(screen.getByText(/MB Bank/)).toBeInTheDocument();
    expect(screen.getByText('******4321')).toBeInTheDocument();
    expect(screen.getByText('NGUYEN VAN A')).toBeInTheDocument();
    expect(screen.getByText('Tài khoản Người mua')).toBeInTheDocument();
  });
});
