import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, it, vi } from 'vitest';
import { CheckoutView } from './CheckoutView';
import { checkoutApi } from '../checkoutApi';
import type { CheckoutPreview } from '../types';
vi.mock('../checkoutApi', () => ({ checkoutApi: { getCheckoutPreview: vi.fn(), buyNow: vi.fn() } }));
const address = { addressId: 1, recipientName: 'QA', phoneNumber: '0900000000', province: 'P', district: 'D', ward: 'W', detailAddress: 'A', isDefault: true, ghnProvinceId: 202, ghnDistrictId: 1442, ghnWardCode: '20101' };
const preview: CheckoutPreview = { productId: 10, productTitle: 'Máy ảnh', sellerId: 2, sellerName: 'Lan', thumbnailUrl: null, listedPrice: 100000, agreedPrice: 80000, acceptedOfferId: 9, shippingFee: 30000, buyerSystemFee: 0, voucherDiscount: 0, shippingDiscount: 0, totalAmount: 110000, addresses: [address], selectedAddress: address, availableVouchers: [], appliedVoucher: null };
beforeEach(() => vi.resetAllMocks());
it('previews the accepted offer price and forwards its source to checkout', async () => {
  vi.mocked(checkoutApi.getCheckoutPreview).mockResolvedValue(preview); vi.mocked(checkoutApi.buyNow).mockRejectedValue(new Error('QA keep page'));
  render(<MemoryRouter><CheckoutView productId={10} offerId={9} /></MemoryRouter>);
  expect(await screen.findByText(/Giá đã thương lượng cho 1 món/)).toBeInTheDocument();
  expect(checkoutApi.getCheckoutPreview).toHaveBeenCalledWith(10, undefined, 9);
  fireEvent.click(screen.getByRole('button', { name: /Xác nhận Mua ngay/i }));
  await waitFor(() => expect(checkoutApi.buyNow).toHaveBeenCalledWith({ productId: 10, offerId: 9, addressId: 1, newAddress: undefined, voucherCode: undefined }));
});
it('explains a stale offer without replacing it with a sold-product error', async () => {
  vi.mocked(checkoutApi.getCheckoutPreview).mockRejectedValue(Object.assign(new Error('Tin đã thay đổi. Cần thương lượng lại.'), { status: 409, code: 'OFFER_LISTING_CHANGED' }));
  render(<MemoryRouter><CheckoutView productId={10} offerId={9} /></MemoryRouter>);
  expect(await screen.findByText('Tin đã thay đổi. Cần thương lượng lại.')).toBeInTheDocument();
  expect(screen.queryByText('Món đồ không còn khả dụng')).not.toBeInTheDocument();
});

it('allows buyer to choose between listed price and eligible accepted offer when entering checkout without offerId param', async () => {
  const eligiblePreview: CheckoutPreview = {
    ...preview,
    agreedPrice: undefined,
    acceptedOfferId: null,
    eligibleOfferId: 9,
    eligibleOfferPrice: 80000,
  };
  vi.mocked(checkoutApi.getCheckoutPreview).mockResolvedValue(eligiblePreview);
  vi.mocked(checkoutApi.buyNow).mockRejectedValue(new Error('QA keep page'));

  render(<MemoryRouter><CheckoutView productId={10} /></MemoryRouter>);

  // Shows pricing choice options
  expect(await screen.findByText(/Lựa chọn mức giá áp dụng:/)).toBeInTheDocument();
  const listedRadio = screen.getByRole('radio', { name: /Giá niêm yết:/i });
  const offerRadio = screen.getByRole('radio', { name: /Giá thương lượng đã chấp nhận:/i });
  expect(listedRadio).toBeChecked();
  expect(offerRadio).not.toBeChecked();

  // Switch to Offer price
  fireEvent.click(offerRadio);
  expect(checkoutApi.getCheckoutPreview).toHaveBeenCalledWith(10, undefined, 9);

  // Buy now submits with offerId
  fireEvent.click(screen.getByRole('button', { name: /Xác nhận Mua ngay/i }));
  await waitFor(() => expect(checkoutApi.buyNow).toHaveBeenCalledWith({ productId: 10, offerId: 9, addressId: 1, newAddress: undefined, voucherCode: undefined }));
});
