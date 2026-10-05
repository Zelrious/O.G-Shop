import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { paymentApi } from '../features/payment/paymentApi';
import { VnPayReturnPage } from './VnPayReturnPage';

vi.mock('../features/payment/paymentApi', () => ({ paymentApi: { verifyVnPayReturn: vi.fn() } }));

const show = (query = 'vnp_TxnRef=OG_42_test&vnp_ResponseCode=00&vnp_TransactionStatus=00') =>
  render(<MemoryRouter initialEntries={[`/payment/vnpay-return?${query}`]}><VnPayReturnPage /></MemoryRouter>);

describe('VNPAY browser return', () => {
  beforeEach(() => vi.resetAllMocks());

  it('does not trust browser success parameters while IPN is pending', async () => {
    vi.mocked(paymentApi.verifyVnPayReturn).mockResolvedValue({ status: 'PENDING_CONFIRMATION', orderId: 42, txnRef: 'OG_42_test', message: 'Đang chờ IPN' });
    show();
    expect(await screen.findByText(/Đang Chờ Xác Nhận Từ Cổng VNPay/i)).toBeInTheDocument();
    expect(screen.queryByText(/Thanh toán Ký quỹ Thành công/i)).not.toBeInTheDocument();
    expect(paymentApi.verifyVnPayReturn).toHaveBeenCalledWith(expect.stringContaining('vnp_TxnRef=OG_42_test'));
  });

  it('checks the server again and shows only its confirmed result', async () => {
    vi.mocked(paymentApi.verifyVnPayReturn)
      .mockResolvedValueOnce({ status: 'PENDING_CONFIRMATION', orderId: 42, txnRef: 'OG_42_test', message: 'Đang chờ IPN' })
      .mockResolvedValueOnce({ status: 'SUCCESS', orderId: 42, txnRef: 'OG_42_test', message: 'Đã xác nhận' });
    show();
    await screen.findByText(/Đang Chờ Xác Nhận Từ Cổng VNPay/i);
    fireEvent.click(screen.getByRole('button', { name: /Kiểm tra Lại Trạng Thái/i }));
    expect(await screen.findByText(/Thanh toán Ký quỹ Thành công/i)).toBeInTheDocument();
  });

  it('shows late-payment reconciliation separately from success', async () => {
    vi.mocked(paymentApi.verifyVnPayReturn).mockResolvedValue({ status: 'RECONCILIATION_PENDING', orderId: 42, txnRef: 'OG_42_test', message: 'Cần đối soát hoàn tiền' });
    show();
    expect(await screen.findByRole('status')).toHaveTextContent('Giao dịch cần đối soát hoàn tiền');
    expect(screen.queryByText(/Thanh toán Ký quỹ Thành công/i)).not.toBeInTheDocument();
  });

  it('shows the server rejection for an invalid return', async () => {
    vi.mocked(paymentApi.verifyVnPayReturn).mockResolvedValue({ status: 'INVALID_SIGNATURE', orderId: null, txnRef: null, message: 'Chữ ký số không hợp lệ.' });
    show('vnp_TxnRef=invalid');
    expect(await screen.findByText('Chữ ký số không hợp lệ.')).toBeInTheDocument();
    expect(screen.queryByText(/Thanh toán Ký quỹ Thành công/i)).not.toBeInTheDocument();
  });
});
