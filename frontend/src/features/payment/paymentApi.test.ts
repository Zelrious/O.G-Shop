import { beforeEach, describe, expect, it, vi } from 'vitest';
import { authApi } from '../auth/authApi';
import { paymentApi } from './paymentApi';
vi.mock('../auth/authApi', () => ({ authApi: { authorizedFetch: vi.fn() } }));
describe('VNPAY payment request', () => {
  beforeEach(() => vi.resetAllMocks());
  it('sends only the order ID and accepts the provider sandbox URL', async () => {
    const url = 'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef=fixture';
    vi.mocked(authApi.authorizedFetch).mockResolvedValue(new Response(JSON.stringify({ paymentUrl:url }), {status:200}));
    expect(await paymentApi.createVnPayUrl(42)).toBe(url);
    expect(authApi.authorizedFetch).toHaveBeenCalledWith('/payments/vnpay-url', expect.objectContaining({ body:'{"orderId":42}' }));
  });
  it('does not redirect to an unrelated or insecure URL', async () => {
    vi.mocked(authApi.authorizedFetch).mockResolvedValue(new Response(JSON.stringify({paymentUrl:'https://example.test/phishing'}), {status:200}));
    await expect(paymentApi.createVnPayUrl(42)).rejects.toThrow('Địa chỉ thanh toán VNPAY không hợp lệ.');
  });
  it('surfaces server ownership errors', async () => {
    vi.mocked(authApi.authorizedFetch).mockResolvedValue(new Response(JSON.stringify({message:'Không có quyền thanh toán'}), {status:403}));
    await expect(paymentApi.createVnPayUrl(42)).rejects.toThrow('Không có quyền thanh toán');
  });
});
