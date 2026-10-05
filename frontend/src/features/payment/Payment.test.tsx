import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { PaymentView } from './components/PaymentView';
import { paymentApi } from './paymentApi';

vi.mock('./paymentApi', () => ({
  paymentApi: {
    getPaymentInfo: vi.fn(),
    processMockPayment: vi.fn(),
  },
}));

describe('PaymentView Component', () => {
  it('renders payment countdown and VietQR mock details correctly', async () => {
    vi.mocked(paymentApi.getPaymentInfo).mockResolvedValue({
      orderId: 101,
      paymentId: null,
      productTitle: 'Máy ảnh cơ Canon QL17 GIII',
      productThumbnail: null,
      totalAmount: 530000,
      currency: 'VND',
      orderStatus: 'PAYMENT_PENDING',
      paymentStatus: 'CHUA_KHOI_TAO',
      paymentMethod: 'BANK_TRANSFER_MOCK',
      transactionCode: null,
      paymentDueAt: new Date(Date.now() + 900000).toISOString(),
      remainingSeconds: 900,
      qrCodeMockUrl: 'https://img.vietqr.io/mock.png',
      bankName: 'Ngân hàng Quân Đội (MB Bank)',
      accountNumber: '0388654321',
      accountName: 'CONG TY CP OLD BUT GOLD',
      transferContent: 'OGSHOP 101',
    });

    render(
      <MemoryRouter>
        <PaymentView orderId={101} />
      </MemoryRouter>
    );

    expect(screen.getByText(/Đang tải cổng thanh toán Ký quỹ/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText(/Cổng Thanh toán Ký quỹ Escrow/i)).toBeInTheDocument();
    });

    expect(screen.getByText('Máy ảnh cơ Canon QL17 GIII')).toBeInTheDocument();
    expect(screen.getByText(/0388654321/i)).toBeInTheDocument();
    expect(screen.getByText(/OGSHOP 101/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Mô phỏng thanh toán thành công/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Thanh toán qua VNPAY' })).toBeInTheDocument();
  });
});
