import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ListingFeeDialog } from './ListingFeeDialog';
import { listingApi } from '../listingApi';
import type { ListingFeeSummary } from '../types';

vi.mock('../listingApi', () => ({ listingApi: { getListingFee: vi.fn(), payListingFee: vi.fn() } }));
const fee: ListingFeeSummary = { productId: 10, title: 'Máy ảnh Canon', status: 'APPROVED_AWAITING_FEE', version: 4, quantity: 1,
  chargeId: '369167e0-69b3-4f87-9198-c4f70974dfca', policyCode: 'LISTING_INITIAL_V1', listedTotal: 80000,
  computedFee: 10000, totalPaid: 0, amountDue: 10000, paymentState: 'PENDING' };
beforeEach(() => { vi.resetAllMocks(); vi.mocked(listingApi.getListingFee).mockResolvedValue(fee); });
describe('Listing fee payment', () => {
  it('uses the server charge and reuses its command after an uncertain network result', async () => {
    const paid = vi.fn();
    vi.mocked(listingApi.payListingFee).mockRejectedValueOnce(new Error('Chưa xác nhận được kết quả')).mockResolvedValue({ ...fee, status: 'ACTIVE', amountDue: 0, paymentState: 'CONFIRMED' });
    render(<ListingFeeDialog productId={10} onClose={vi.fn()} onPaid={paid} />);
    const button = await screen.findByRole('button', { name: 'Xác nhận thanh toán mô phỏng' });
    await waitFor(() => expect(button).toBeEnabled());
    fireEvent.click(button);
    await screen.findByText('Chưa xác nhận được kết quả');
    expect(paid).not.toHaveBeenCalled();
    fireEvent.click(button);
    await waitFor(() => expect(paid).toHaveBeenCalledTimes(1));
    const first = vi.mocked(listingApi.payListingFee).mock.calls[0];
    expect(first[1]).toMatchObject({ chargeId: fee.chargeId, expectedVersion: 4 });
    expect(first[1]).not.toHaveProperty('amount');
    expect(vi.mocked(listingApi.payListingFee).mock.calls[1]).toEqual(first);
  });
  it('prevents double submission while confirmation is pending', async () => {
    let finish!: (result: ListingFeeSummary) => void;
    vi.mocked(listingApi.payListingFee).mockReturnValue(new Promise(resolve => { finish = resolve; }));
    const paid = vi.fn();
    render(<ListingFeeDialog productId={10} onClose={vi.fn()} onPaid={paid} />);
    const button = await screen.findByRole('button', { name: 'Xác nhận thanh toán mô phỏng' });
    await waitFor(() => expect(button).toBeEnabled());
    fireEvent.click(button); fireEvent.click(button);
    expect(listingApi.payListingFee).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: 'Đang xác nhận...' })).toBeDisabled();
    finish({ ...fee, status: 'ACTIVE', amountDue: 0 });
    await waitFor(() => expect(paid).toHaveBeenCalledTimes(1));
  });
  it('does not offer payment for an already confirmed or zero-due charge', async () => {
    vi.mocked(listingApi.getListingFee).mockResolvedValue({ ...fee, status: 'ACTIVE', amountDue: 0, totalPaid: 10000, paymentState: 'CONFIRMED' });
    render(<ListingFeeDialog productId={10} onClose={vi.fn()} onPaid={vi.fn()} />);
    await screen.findByText('Đang bán');
    expect(screen.getByRole('button', { name: 'Xác nhận thanh toán mô phỏng' })).toBeDisabled();
    expect(listingApi.payListingFee).not.toHaveBeenCalled();
  });
  it('retries a failed quote and keeps keyboard focus inside the dialog', async () => {
    vi.mocked(listingApi.getListingFee).mockRejectedValueOnce(new Error('Khoản phí tạm thời chưa tải được')).mockResolvedValue(fee);
    const close = vi.fn();
    render(<ListingFeeDialog productId={10} onClose={close} onPaid={vi.fn()} />);
    await screen.findByText('Khoản phí tạm thời chưa tải được');
    fireEvent.click(screen.getByRole('button', { name: 'Tải lại khoản phí' }));
    await screen.findByText('Máy ảnh Canon');
    const dialog = screen.getByRole('dialog');
    dialog.focus(); fireEvent.keyDown(dialog, { key: 'Tab' });
    expect(screen.getByRole('button', { name: 'Đóng khoản phí' })).toHaveFocus();
    fireEvent.keyDown(dialog, { key: 'Escape' }); expect(close).toHaveBeenCalledOnce();
  });
});
