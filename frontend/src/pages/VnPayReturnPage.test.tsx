import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { VnPayReturnPage } from './VnPayReturnPage';
import { paymentApi } from '../features/payment/paymentApi';
import type { PaymentInfo } from '../features/payment/types';
vi.mock('../features/payment/paymentApi', () => ({ paymentApi: { getPaymentInfo: vi.fn() } }));
const info = (status: string): PaymentInfo => ({ orderId:42, paymentId:1, productTitle:'Fixture', productThumbnail:null,
  totalAmount:150000, currency:'VND', orderStatus:'PAYMENT_PENDING', paymentStatus:status, paymentMethod:'VNPAY',
  transactionCode:null, paymentDueAt:'2026-10-05T03:00:00Z', remainingSeconds:60, qrCodeMockUrl:'', bankName:'',
  accountNumber:'', accountName:'', transferContent:'' });
const show = (query = 'orderId=42&vnp_ResponseCode=00&vnp_TransactionStatus=00') => render(
  <MemoryRouter initialEntries={[`/payment/vnpay-return?${query}`]}><VnPayReturnPage /></MemoryRouter>);
describe('VNPAY browser return', () => {
  beforeEach(() => vi.resetAllMocks());
  it('does not trust browser success parameters while IPN is pending', async () => {
    vi.mocked(paymentApi.getPaymentInfo).mockResolvedValue(info('PENDING'));
    show();
    expect(await screen.findByText(/Đang chờ xác nhận từ VNPAY/)).toBeInTheDocument();
    expect(screen.queryByText('Thanh toán đã được hệ thống xác nhận.')).not.toBeInTheDocument();
    expect(paymentApi.getPaymentInfo).toHaveBeenCalledWith(42);
  });
  it('checks the server again and shows only its confirmed result', async () => {
    vi.mocked(paymentApi.getPaymentInfo).mockResolvedValueOnce(info('PENDING')).mockResolvedValueOnce(info('HELD'));
    show(); await screen.findByText(/Đang chờ xác nhận từ VNPAY/);
    fireEvent.click(screen.getByRole('button',{name:'Kiểm tra lại'}));
    expect(await screen.findByText('Thanh toán đã được hệ thống xác nhận.')).toBeInTheDocument();
  });
  it('shows late-payment reconciliation separately from success', async () => {
    vi.mocked(paymentApi.getPaymentInfo).mockResolvedValue(info('REFUND_PENDING'));
    show(); expect(await screen.findByText(/cần đối soát hoàn tiền/)).toBeInTheDocument();
  });
  it('rejects a missing or invalid order reference', async () => {
    show('orderId=invalid'); expect(await screen.findByRole('alert')).toHaveTextContent('Mã đơn hàng không hợp lệ');
    expect(paymentApi.getPaymentInfo).not.toHaveBeenCalled();
  });
});
