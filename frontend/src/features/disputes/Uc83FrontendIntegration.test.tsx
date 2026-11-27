import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { calculateAdjustedCoinsAfterVoucher } from '../checkout/checkoutApi';
import { Uc83BuyerOrderPanel } from './Uc83BuyerOrderPanel';
import { Uc83SellerReturnPanel } from './Uc83SellerReturnPanel';
import { Uc83SettlementConfirmPanel } from './Uc83SettlementConfirmPanel';
import { ordersApi } from '../orders/ordersApi';
import { returnCaseApi } from './returnCaseApi';

vi.mock('../orders/ordersApi', () => ({
  ordersApi: {
    confirmReceived: vi.fn(),
    earlyCompleteOrder: vi.fn(),
  },
}));

vi.mock('./returnCaseApi', () => ({
  returnCaseApi: {
    getActiveReturnCase: vi.fn(),
    getLatestReturnCase: vi.fn(), submitShipment: vi.fn(),
    uploadEvidence: vi.fn(),
    withdrawReturnRequest: vi.fn(),
    supplementBuyerEvidenceRound1: vi.fn(),
    confirmDirectHandover: vi.fn(),
    sellerRespondRound1: vi.fn(),
    sellerAgreeRefundRound2: vi.fn(),
    sellerSubmitDamageExemption: vi.fn(),
    listOrderSettlements: vi.fn(),
    confirmSettlementOperation: vi.fn(),
  },
}));

describe('UC83 Frontend Integration (V58 Coins, UC27, UC62 7a, Seller R2, Admin Settlement Confirm)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it.each(['CARRIER','SELF_DELIVERY'])('đăng ký hàng trả từ đơn đang tranh chấp (%s)', async method => {
    vi.mocked(returnCaseApi.getLatestReturnCase).mockResolvedValue({caseId:501,orderId:101,state:'RETURN_APPROVED'});
    vi.mocked(returnCaseApi.submitShipment).mockResolvedValue({caseId:501,orderId:101,state:'RETURNING',returnShipment:{deliveryMethod:method}});
    render(<Uc83BuyerOrderPanel orderId={101} orderStatus="DISPUTED" onUpdated={vi.fn()} />);
    fireEvent.change(await screen.findByLabelText('Phương thức trả hàng'),{target:{value:method}});
    if (method === 'CARRIER') fireEvent.change(screen.getByLabelText('Mã vận đơn'),{target:{value:'TRK-001'}});
    fireEvent.click(screen.getByRole('button',{name:'Đăng ký gửi hàng trả'}));
    await waitFor(() => expect(returnCaseApi.submitShipment).toHaveBeenCalledWith(501,method,method==='CARRIER'?'GHN':null,
      method==='CARRIER'?'TRK-001':null,expect.any(String)));
    await screen.findByText('Đã đăng ký gửi hàng trả.');
    expect(screen.queryByText('Đăng ký gửi hàng trả')).toBeNull();
  });
  it('V58 coins helper: directly offsets CARRIER shipping and auto-clamps excess coins when voucher is applied', () => {
    const directShip = calculateAdjustedCoinsAfterVoucher({
      subtotal: 100000,
      shippingFee: 30000,
      goodsVoucherDiscount: 0,
      shippingVoucherDiscount: 0,
      coinsRequested: 130000,
      coinBalance: 200000,
    });
    expect(directShip.adjustedCoins).toBe(130000);
    expect(directShip.wasAdjusted).toBe(false);
    expect(directShip.coinsOnGoods).toBe(100000);
    expect(directShip.coinsOnShipping).toBe(30000);
    expect(directShip.expectedTotal).toBe(0);
    expect(directShip.isZeroAmount).toBe(true);

    const voucherPriority = calculateAdjustedCoinsAfterVoucher({
      subtotal: 100000,
      shippingFee: 30000,
      goodsVoucherDiscount: 0,
      shippingVoucherDiscount: 30000,
      coinsRequested: 130000,
      coinBalance: 200000,
    });
    expect(voucherPriority.adjustedCoins).toBe(100000);
    expect(voucherPriority.wasAdjusted).toBe(true);
    expect(voucherPriority.coinsOnGoods).toBe(100000);
    expect(voucherPriority.coinsOnShipping).toBe(0);
    expect(voucherPriority.expectedTotal).toBe(0);
    expect(voucherPriority.isZeroAmount).toBe(true);
  });

  it('UC27 + UC62 7a Buyer panel: sends videoOption on confirmReceived and supplements Round 1 evidence', async () => {
    vi.mocked(returnCaseApi.getActiveReturnCase).mockResolvedValue({
      caseId: 501,
      orderId: 101,
      state: 'REVIEWING',
    });
    vi.mocked(ordersApi.confirmReceived).mockResolvedValue({
      orderId: 101,
      status: 'DELIVERED',
      message: 'Xác nhận đã nhận hàng thành công.',
    });
    vi.mocked(returnCaseApi.supplementBuyerEvidenceRound1).mockResolvedValue({
      caseId: 501,
      orderId: 101,
      state: 'REVIEWING',
    });

    const onUpdated = vi.fn();
    render(<Uc83BuyerOrderPanel orderId={101} orderStatus="DELIVERED" onUpdated={onUpdated} />);

    fireEvent.click(screen.getByLabelText(/Bỏ qua cảnh báo/i));
    fireEvent.click(screen.getByRole('button', { name: /Xác nhận đã nhận bưu kiện/i }));
    await waitFor(() => {
      expect(ordersApi.confirmReceived).toHaveBeenCalledWith(101, 'SKIPPED_AFTER_WARNING');
    });

    await waitFor(() => {
      expect(screen.getByTestId('uc83-buyer-active-case')).toBeTruthy();
    });
    fireEvent.click(screen.getByRole('button', { name: /Bổ sung bằng chứng Vòng 1/i }));
    vi.mocked(returnCaseApi.uploadEvidence).mockResolvedValue({ mediaType: 'IMAGE', objectKey: 'evidence/extra-photo.jpg', fileSizeBytes: 53, contentDigest: 'd'.repeat(64) });
    fireEvent.change(screen.getByLabelText(/Chọn tệp/), { target: { files: [new File(['photo'], 'photo.jpg', { type: 'image/jpeg' })] } });
    await screen.findByRole('link', { name: /Xem trước/ });
    fireEvent.click(screen.getByRole('button', { name: /Gửi bằng chứng bổ sung/i }));

    await waitFor(() => {
      expect(returnCaseApi.supplementBuyerEvidenceRound1).toHaveBeenCalledWith(
        101,
        expect.objectContaining({
          evidenceList: expect.arrayContaining([
            expect.objectContaining({ mediaType: 'IMAGE', objectKey: 'evidence/extra-photo.jpg' }),
          ]),
        }),
      );
    });
  });

  it('UC83 Seller panel: agrees refund in Round 2 (POST_RETURN)', async () => {
    vi.mocked(returnCaseApi.getActiveReturnCase).mockResolvedValue({
      caseId: 601,
      orderId: 202,
      state: 'POST_RETURN',
    });
    vi.mocked(returnCaseApi.sellerAgreeRefundRound2).mockResolvedValue({
      caseId: 601,
      orderId: 202,
      state: 'CLOSED',
    });

    const onUpdated = vi.fn();
    render(<Uc83SellerReturnPanel orderId={202} orderStatus="DELIVERED" onUpdated={onUpdated} />);

    await waitFor(() => {
      expect(screen.getByTestId('uc83-seller-return-panel')).toBeTruthy();
    });
    fireEvent.click(screen.getByRole('button', { name: /Hàng đúng, đồng ý hoàn tiền/i }));

    await waitFor(() => {
      expect(returnCaseApi.sellerAgreeRefundRound2).toHaveBeenCalledWith(202);
    });
  });

  it('UC83 KTV/Admin Settlement Confirm panel: lists REQUESTED operation and confirms refund + coins', async () => {
    vi.mocked(returnCaseApi.listOrderSettlements).mockResolvedValue([
      {
        operationId: '11111111-1111-1111-1111-111111111111',
        orderId: 303,
        componentId: '22222222-2222-2222-2222-222222222222',
        operationType: 'REFUND_BUYER',
        amount: 100000,
        state: 'REQUESTED',
      },
    ]);
    vi.mocked(returnCaseApi.confirmSettlementOperation).mockResolvedValue({
      operationId: '11111111-1111-1111-1111-111111111111',
      state: 'CONFIRMED',
      coinsCredited: 30000,
      message: 'Confirmed',
    });

    render(<Uc83SettlementConfirmPanel orderId={303} />);
    fireEvent.click(screen.getByRole('button', { name: /Kiểm tra lệnh hoàn tiền UC83/i }));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Xác nhận hoàn tiền/i })).toBeTruthy();
    });
    fireEvent.click(screen.getByRole('button', { name: /Xác nhận hoàn tiền/i }));

    await waitFor(() => {
      expect(returnCaseApi.confirmSettlementOperation).toHaveBeenCalledWith('11111111-1111-1111-1111-111111111111');
      expect(screen.getByText(/30\.000 xu/i)).toBeTruthy();
    });
  });
});
