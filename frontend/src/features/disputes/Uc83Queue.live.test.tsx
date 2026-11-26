declare const process: { env: Record<string, string | undefined> };
import { AdminDashboardPage } from '../../pages/admin/AdminDashboardPage';
import type { ReturnsReportResponse } from '../management/adminDashboardApi';
import { MemoryRouter } from 'react-router-dom';
import { waitFor, configure, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { Uc83SellerReturnPanel } from './Uc83SellerReturnPanel';
import { KtvUc83QueueSection } from './KtvUc83QueueSection';
import { Uc83BuyerOrderPanel } from './Uc83BuyerOrderPanel';

it.skipIf(!process.env.OG_UI_R1)('claims and decides both rounds and registers the approved return shipment', async () => {
  configure({asyncUtilTimeout:10000});
  const env = process.env;
  const base = new URL(import.meta.env.VITE_API_BASE_URL);
  const fetchHttp = globalThis.fetch.bind(globalThis);
  let token = env.OG_UI_KTV;
  const claimStatuses: number[] = [];
  vi.stubGlobal('fetch', async (url: string, init: RequestInit) => {
    if (base.hostname !== '127.0.0.1' || new URL(url).origin !== base.origin) throw new Error('Unexpected origin');
    const headers = new Headers(init?.headers);
    headers.set('Authorization', 'Bearer ' + token);
    if (init?.body instanceof FormData) {
      // jsdom File and FormData belong to another realm than Node's HTTP transport.
      const boundary = 'uc83-' + crypto.randomUUID();
      const enc = new TextEncoder();
      const parts: Uint8Array[] = [];
      for (const [key, value] of init.body.entries()) {
        parts.push(enc.encode('--' + boundary + '\r\nContent-Disposition: form-data; name="' + key + '"'));
        if (typeof value === 'string') parts.push(enc.encode('\r\n\r\n' + value + '\r\n'));
        else {
          const bytes = await new Promise<ArrayBuffer>((resolve,reject) => {
            const reader = new FileReader(); reader.onload = () => resolve(reader.result as ArrayBuffer);
            reader.onerror = reject; reader.readAsArrayBuffer(value);
          });
          parts.push(enc.encode('; filename="' + value.name + '"\r\nContent-Type: ' + value.type + '\r\n\r\n'),new Uint8Array(bytes),enc.encode('\r\n'));
        }
      }
      parts.push(enc.encode('--' + boundary + '--\r\n'));
      const totalLen = parts.reduce((sum, p) => sum + p.byteLength, 0);
      const body = new Uint8Array(totalLen);
      let offset = 0;
      for (const p of parts) { body.set(p, offset); offset += p.byteLength; }
      headers.set('Content-Type','multipart/form-data; boundary=' + boundary);
      return fetchHttp(url,{...init,headers,body});
    }
    const response = await fetchHttp(url, { ...init, headers });
    if (!response.ok && response.status !== 409) console.error(new URL(url).pathname, response.status, await response.clone().text());
    if (url.endsWith('/assign')) claimStatuses.push(response.status);
    return response;
  });
  const openCase = async (id: string, round: number) => {
    const label = new RegExp('^Case #' + id + ' ·');
    const row = await screen.findByText(label, {}, { timeout: 20000 });
    if (round === 1) {
      const response = await fetchHttp(base.href + '/ktv/cases/' + id + '/assign', {
        method:'POST', headers:{Authorization:'Bearer ' + env.OG_UI_OTHER_KTV}
      });
      expect(response.ok).toBe(true);
    }
    fireEvent.click(within(row.parentElement!.parentElement!).getByRole('button',{name:'Nhận xử lý'}));
    if (round === 1) {
      await screen.findByRole('alert');
      expect(claimStatuses).toContain(409);
      expect(screen.queryByText(label)).not.toBeInTheDocument();
      token = env.OG_UI_OTHER_KTV;
      fireEvent.click(screen.getByRole('button',{name:'Hồ sơ của tôi'}));
    }
    fireEvent.click(await screen.findByRole('button',{name:'Mở xử lý Vòng ' + round}, {timeout:10000}));
    fireEvent.change(screen.getByRole('textbox'),{target:{value:'Đã kiểm tra đầy đủ bằng chứng giao nhận.'}});
  };
  const upload = async () => {
    const bytes = Uint8Array.from(atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aRt0AAAAASUVORK5CYII='),c => c.charCodeAt(0));
    fireEvent.change(screen.getByLabelText(/Chọn tệp/),{target:{files:[new File([bytes],'proof.png',{type:'image/png'})]}});
    await screen.findByRole('link',{name:/Xem trước/});
  };
  const reopen = async (round: number) => {
    cleanup(); token = round === 1 ? env.OG_UI_OTHER_KTV : env.OG_UI_KTV;
    render(<KtvUc83QueueSection />);
    if (round === 2) fireEvent.click(screen.getByRole('button',{name:/Vòng 2: Hậu trả/}));
    fireEvent.click(screen.getByRole('button',{name:'Hồ sơ của tôi'}));
    fireEvent.click(await screen.findByRole('button',{name:'Mở xử lý Vòng ' + round}));
    fireEvent.change(screen.getByRole('textbox'),{target:{value:'Đã đối chiếu bằng chứng được bổ sung.'}});
  };
  try {
    render(<KtvUc83QueueSection />);
    await openCase(env.OG_UI_R1!,1);
    fireEvent.click(screen.getByRole('button',{name:/Yêu cầu Buyer/}));
    await screen.findByText(/Đã yêu cầu Người mua/);
    cleanup(); token = env.OG_UI_BUYER;
    render(<Uc83BuyerOrderPanel orderId={Number(env.OG_UI_ORDER)} orderStatus="DELIVERED" onUpdated={() => {}}/>);
    await screen.findByText(/Yêu cầu bổ sung từ KTV/);
    fireEvent.click(screen.getByRole('button',{name:/Bổ sung bằng chứng Vòng 1/}));
    await upload();
    fireEvent.click(screen.getByRole('button',{name:'Gửi bằng chứng bổ sung'}));
    await screen.findByText(/Đã bổ sung bằng chứng Vòng 1 thành công/);
    await reopen(1);
    fireEvent.click(screen.getByRole('button',{name:/Chấp thuận trả hàng/}));
    await screen.findByText(/Đã chấp thuận trả hàng Vòng 1/);
    cleanup(); token = env.OG_UI_BUYER;
    render(<Uc83BuyerOrderPanel orderId={Number(env.OG_UI_ORDER)} orderStatus="DELIVERED" onUpdated={() => {}}/>);
    fireEvent.change(await screen.findByLabelText('Mã vận đơn'),{target:{value:'GHN-UI-' + env.OG_UI_ORDER}});
    fireEvent.click(screen.getByRole('button',{name:'Đăng ký gửi hàng trả'}));
    await screen.findByText('Đã đăng ký gửi hàng trả.');
    cleanup(); token = env.OG_UI_KTV;
    render(<KtvUc83QueueSection />);
    fireEvent.click(screen.getByRole('button',{name:/Vòng 2: Hậu trả/}));
    await openCase(env.OG_UI_R2!,2);
    fireEvent.click(screen.getByRole('button',{name:/Yêu cầu Seller/}));
    await screen.findByText(/Đã yêu cầu Người bán/);
    cleanup(); token=env.OG_UI_SELLER;
    render(<Uc83SellerReturnPanel orderId={Number(env.OG_UI_ORDER2)} orderStatus="DELIVERED" onUpdated={() => {}}/>);
    fireEvent.click(await screen.findByRole('button',{name:/Bổ sung bằng chứng theo yêu cầu/}));
    fireEvent.change(screen.getByRole('textbox'),{target:{value:'Bổ sung ảnh thiệt hại khi mở kiện.'}});
    await upload();
    fireEvent.click(screen.getByRole('button',{name:'Gửi bằng chứng bổ sung'}));
    await screen.findByText(/Đã gửi đề nghị KTV/);
    await reopen(2);
    expect(screen.getAllByRole('link',{name:/bằng chứng/}).length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole('button',{name:/Miễn hoàn tiền/}));
    await screen.findByText(/Đã quyết định miễn hoàn tiền/);
  } finally { cleanup(); vi.unstubAllGlobals(); }
},60000);

it.skipIf(!process.env.OG_UI_ADMIN)('reconciles the rendered UC81 report and downloaded CSV against four real cases',async () => {
  const env=process.env;
  const base=new URL(import.meta.env.VITE_API_BASE_URL);
  const fetchHttp=globalThis.fetch.bind(globalThis);
  let report: ReturnsReportResponse | undefined;
  let csv='';
  vi.stubGlobal('URL',class extends URL { static createObjectURL() { return 'blob:report'; } static revokeObjectURL() {} });
  const download=vi.spyOn(HTMLAnchorElement.prototype,'click').mockImplementation(() => {});
  vi.stubGlobal('fetch',async (url:string,init:RequestInit) => {
    const target=new URL(url);
    if (base.hostname!=='127.0.0.1' || target.origin!==base.origin) throw new Error('Unexpected origin');
    const headers=new Headers(init?.headers);
    headers.set('Authorization','Bearer '+env.OG_UI_ADMIN);
    const res=await fetchHttp(url,{...init,headers});
    if (target.pathname.endsWith('/returns-report') && res.ok) report=await res.clone().json();
    if (target.pathname.endsWith('/export/returns') && res.ok) {
      expect(target.searchParams.get('cutoffAt')).toBe(report!.cutoffAt);
      csv=await res.clone().text();
    }
    return res;
  });
  try {
    render(<MemoryRouter><AdminDashboardPage /></MemoryRouter>);
    const count=await screen.findByText('Hồ sơ đã chốt');
    expect(count.parentElement).toHaveTextContent(String(report!.totalResolvedCases));
    expect(screen.getByText('Hồ sơ đang chờ cuối kỳ: '+report!.openPendingCount)).toBeInTheDocument();
    for (const cat of report!.categoryBreakdown) {
      expect(screen.getByRole('meter',{name:cat.category})).toHaveAttribute('value',String(cat.totalOrders));
      expect(screen.getByLabelText('Chọn ngành hàng '+cat.category).closest('tr')).toHaveTextContent(cat.totalOrders+' hồ sơ');
    }
    fireEvent.click(screen.getByRole('button',{name:/Kết xuất báo cáo khiếu nại/}));
    await waitFor(() => expect(download).toHaveBeenCalled());
    for (const [id,type,bucket] of [[env.OG_UI_R2,'RETURN','RETURN_EXEMPT_NO_REFUND'],
        [env.OG_UI_REFUND,'RETURN','RETURN_REFUNDED'],[env.OG_UI_ISSUE,'SYSTEM_ISSUE','SYSTEM_ISSUE_CONFIRMED']]) {
      expect(report!.resolvedCases.some(row=>row.caseId===Number(id) && row.outcomeBucket===bucket)).toBe(true);
      expect(csv).toContain('\n'+id+','+type+','+bucket+',');
    }
    expect(csv).not.toContain('\n'+env.OG_UI_R1+',RETURN,');
    expect(csv).toContain(report!.cutoffAt);
  } finally { download.mockRestore(); cleanup(); vi.unstubAllGlobals(); }
},30000);
