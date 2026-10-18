import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { AddressBookTab } from './components/AddressBookTab';
import { profileApi } from './profileApi';
import { UserAddress, GhnProvince, GhnDistrict, GhnWard } from './types';

const mockAddresses: UserAddress[] = [
  {
    id: 1,
    recipientName: 'Nguyễn Văn Seller',
    phoneNumber: '0901234567',
    province: 'TP. Hồ Chí Minh',
    district: 'Quận 1',
    ward: 'Phường Bến Nghé',
    detailAddress: '123 Lê Lợi',
    ghnProvinceId: 202,
    ghnDistrictId: 1442,
    ghnWardCode: '20101',
    isDefault: true,
    createdAt: '2026-10-01T00:00:00Z',
  },
  {
    id: 2,
    recipientName: 'Trần Văn Cũ',
    phoneNumber: '0909876543',
    province: 'Hà Nội',
    district: 'Cầu Giấy',
    ward: 'Dịch Vọng',
    detailAddress: '456 Cầu Giấy',
    ghnProvinceId: null,
    ghnDistrictId: null,
    ghnWardCode: null,
    isDefault: false,
    createdAt: '2026-10-01T00:00:00Z',
  },
];

const mockProvinces: GhnProvince[] = [
  { provinceId: 202, provinceName: 'TP. Hồ Chí Minh' },
  { provinceId: 201, provinceName: 'Hà Nội' },
];

const mockDistricts: GhnDistrict[] = [
  { districtId: 1442, provinceId: 202, districtName: 'Quận 1' },
  { districtId: 1443, provinceId: 202, districtName: 'Quận 3' },
];

const mockWards: GhnWard[] = [
  { wardCode: '20101', districtId: 1442, wardName: 'Phường Bến Nghé' },
  { wardCode: '20102', districtId: 1442, wardName: 'Phường Bến Thành' },
];

describe('AddressBookTab Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(profileApi, 'getAddresses').mockResolvedValue(mockAddresses);
    vi.spyOn(profileApi, 'getProvinces').mockResolvedValue(mockProvinces);
    vi.spyOn(profileApi, 'getDistricts').mockResolvedValue(mockDistricts);
    vi.spyOn(profileApi, 'getWards').mockResolvedValue(mockWards);
    vi.spyOn(profileApi, 'createAddress').mockResolvedValue({
      id: 3,
      recipientName: 'Địa Chỉ Mới',
      phoneNumber: '0912345678',
      province: 'TP. Hồ Chí Minh',
      district: 'Quận 1',
      ward: 'Phường Bến Nghé',
      detailAddress: '789 Nguyễn Huệ',
      ghnProvinceId: 202,
      ghnDistrictId: 1442,
      ghnWardCode: '20101',
      isDefault: false,
      createdAt: '2026-10-18T00:00:00Z',
    });
  });

  it('renders addresses list with GHN status badges and seller pickup notice', async () => {
    render(<AddressBookTab />);

    await waitFor(() => {
      expect(screen.getByText('Nguyễn Văn Seller')).toBeInTheDocument();
      expect(screen.getByText('Trần Văn Cũ')).toBeInTheDocument();
    });

    // Check Seller pickup banner notice
    expect(screen.getByText(/Địa chỉ lấy hàng cho Người bán:/)).toBeInTheDocument();

    // Check GHN normalization badges
    expect(screen.getByText('✓ Đã chuẩn hóa GHN')).toBeInTheDocument();
    expect(screen.getByText('⚠️ Cần cập nhật chuẩn GHN')).toBeInTheDocument();
  });

  it('cascades dropdowns when creating new address with GHN master data', async () => {
    render(<AddressBookTab />);

    await waitFor(() => {
      expect(screen.getByText('Nguyễn Văn Seller')).toBeInTheDocument();
    });

    // Click "+ Thêm địa chỉ mới"
    const addBtn = screen.getByRole('button', { name: '+ Thêm địa chỉ mới' });
    fireEvent.click(addBtn);

    // Form appears
    expect(screen.getByText('Thêm địa chỉ mới')).toBeInTheDocument();

    // Fill personal info
    fireEvent.change(screen.getByPlaceholderText('Ví dụ: Nguyễn Văn A'), {
      target: { value: 'Địa Chỉ Mới' },
    });
    fireEvent.change(screen.getByPlaceholderText('Ví dụ: 0987654321'), {
      target: { value: '0912345678' },
    });
    fireEvent.change(screen.getByPlaceholderText('Ví dụ: 123 Lê Lợi, Tòa nhà ABC, Phòng 402'), {
      target: { value: '789 Nguyễn Huệ' },
    });

    // Verify Province select
    const provinceSelect = await screen.findByLabelText(/Tỉnh \/ Thành phố \(GHN\)/);
    expect(profileApi.getProvinces).toHaveBeenCalled();

    // Select Province: TP. Hồ Chí Minh (202)
    fireEvent.change(provinceSelect, { target: { value: '202' } });

    // Verify District select is populated
    await waitFor(() => {
      expect(profileApi.getDistricts).toHaveBeenCalledWith(202);
    });

    const districtSelect = screen.getByLabelText(/Quận \/ Huyện \(GHN\)/);
    fireEvent.change(districtSelect, { target: { value: '1442' } });

    // Verify Ward select is populated
    await waitFor(() => {
      expect(profileApi.getWards).toHaveBeenCalledWith(1442);
    });

    const wardSelect = screen.getByLabelText(/Phường \/ Xã \(GHN\)/);
    fireEvent.change(wardSelect, { target: { value: '20101' } });

    // Submit form
    const saveBtn = screen.getByRole('button', { name: 'Lưu địa chỉ chuẩn GHN' });
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(profileApi.createAddress).toHaveBeenCalledWith({
        recipientName: 'Địa Chỉ Mới',
        phoneNumber: '0912345678',
        detailAddress: '789 Nguyễn Huệ',
        province: 'TP. Hồ Chí Minh',
        district: 'Quận 1',
        ward: 'Phường Bến Nghé',
        ghnProvinceId: 202,
        ghnDistrictId: 1442,
        ghnWardCode: '20101',
        isDefault: false,
      });
    });
  });
});
