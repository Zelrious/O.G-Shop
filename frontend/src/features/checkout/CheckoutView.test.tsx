import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { CheckoutView } from './components/CheckoutView';
import { checkoutApi } from './checkoutApi';
import { profileApi } from '../profile';
import { CheckoutPreview } from './types';

const mockValidGhnAddress = {
  addressId: 101,
  recipientName: 'Trần Người Nhận',
  phoneNumber: '0901234567',
  province: 'TP. Hồ Chí Minh',
  district: 'Quận 1',
  ward: 'Phường Bến Nghé',
  detailAddress: '123 Lê Lợi',
  ghnProvinceId: 202,
  ghnDistrictId: 1442,
  ghnWardCode: '20101',
  isDefault: true,
};

const mockLegacyAddress = {
  addressId: 102,
  recipientName: 'Người Dùng Cũ',
  phoneNumber: '0912345678',
  province: 'TP. Hồ Chí Minh',
  district: 'Quận 1',
  ward: 'Phường Bến Nghé',
  detailAddress: '456 Hai Bà Trưng',
  ghnProvinceId: null,
  ghnDistrictId: null,
  ghnWardCode: null,
  isDefault: true,
};

const createMockPreview = (selectedAddress: typeof mockValidGhnAddress | typeof mockLegacyAddress): CheckoutPreview => ({
  productId: 1,
  productTitle: 'Áo khoác Vintage',
  thumbnailUrl: '/test.jpg',
  sellerId: 99,
  sellerName: 'Shop Xưa Cũ',
  listedPrice: 350000,
  shippingFee: 30000,
  buyerSystemFee: 0,
  voucherDiscount: 0,
  shippingDiscount: 0,
  totalAmount: 380000,
  addresses: [selectedAddress],
  selectedAddress: selectedAddress,
  availableVouchers: [],
  appliedVoucher: null,
});

describe('CheckoutView Component - ADDR-03 GHN Blocking & Cascading Select', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(profileApi, 'getProvinces').mockResolvedValue([
      { provinceId: 202, provinceName: 'TP. Hồ Chí Minh' },
    ]);
    vi.spyOn(profileApi, 'getDistricts').mockResolvedValue([
      { districtId: 1442, provinceId: 202, districtName: 'Quận 1' },
    ]);
    vi.spyOn(profileApi, 'getWards').mockResolvedValue([
      { wardCode: '20101', districtId: 1442, wardName: 'Phường Bến Nghé' },
    ]);
  });

  it('Cho phép đặt hàng khi địa chỉ đang chọn đã chuẩn hóa mã GHN', async () => {
    vi.spyOn(checkoutApi, 'getCheckoutPreview').mockResolvedValue(createMockPreview(mockValidGhnAddress));

    render(
      <BrowserRouter>
        <CheckoutView productId={1} />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Áo khoác Vintage')).toBeInTheDocument();
    });

    expect(screen.getByText('✓ Chuẩn GHN')).toBeInTheDocument();

    const confirmButton = screen.getByRole('button', { name: /Xác nhận Mua ngay/i });
    expect(confirmButton).toBeEnabled();
    expect(confirmButton).not.toHaveTextContent('Cần chọn địa chỉ chuẩn hóa GHN');
  });

  it('Vô hiệu hóa nút đặt hàng và hiển thị cảnh báo khi địa chỉ nhận hàng thiếu mã GHN (Legacy Address)', async () => {
    vi.spyOn(checkoutApi, 'getCheckoutPreview').mockResolvedValue(createMockPreview(mockLegacyAddress));

    render(
      <BrowserRouter>
        <CheckoutView productId={1} />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Áo khoác Vintage')).toBeInTheDocument();
    });

    expect(screen.getByText('⚠️ Chưa chuẩn GHN')).toBeInTheDocument();
    expect(
      screen.getByText(/Địa chỉ nhận hàng đang chọn chưa chuẩn hóa theo mã bưu chính GHN/i)
    ).toBeInTheDocument();

    const confirmButton = screen.getByRole('button', { name: /Cần chọn địa chỉ chuẩn hóa GHN/i });
    expect(confirmButton).toBeDisabled();
    expect(
      screen.getByText(/Địa chỉ nhận hàng chưa chuẩn hóa GHN\. Vui lòng chọn địa chỉ hợp lệ hoặc thêm địa chỉ mới để tiếp tục\./i)
    ).toBeInTheDocument();
  });

  it('Hỗ trợ thêm địa chỉ mới với Cascading Select 3 cấp từ profileApi', async () => {
    vi.spyOn(checkoutApi, 'getCheckoutPreview').mockResolvedValue(createMockPreview(mockLegacyAddress));

    render(
      <BrowserRouter>
        <CheckoutView productId={1} />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('+ Thêm địa chỉ mới')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('+ Thêm địa chỉ mới'));

    await waitFor(() => {
      expect(profileApi.getProvinces).toHaveBeenCalled();
    });

    expect(screen.getByText('-- Chọn Tỉnh/Thành --')).toBeInTheDocument();
    expect(screen.getByText('TP. Hồ Chí Minh')).toBeInTheDocument();
  });
});
