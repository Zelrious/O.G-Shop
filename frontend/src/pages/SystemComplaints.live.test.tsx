declare const process: { env: Record<string, string | undefined> };
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { NotificationsPage } from './NotificationsPage';
import { VoucherWallet } from '../features/vouchers/VoucherWallet';
vi.mock('../features/auth', async original => ({ ...await original<typeof import('../features/auth')>(), useAuth: () => ({ user: { roles: ['BUYER'] } }) }));
import { SystemComplaintsPage } from './SystemComplaintsPage';
import { KtvComplaintsPage } from './ktv/KtvComplaintsPage';

it.skipIf(!process.env.OG_UI_BUYER || !process.env.OG_UI_KTV).each(['OG_UI_BUYER', 'OG_UI_SELLER'])('%s submits, supplements and reads the decision over HTTP', async (role) => {
  const base = new URL(import.meta.env.VITE_API_BASE_URL);
  expect(base.hostname).toBe('127.0.0.1');
  const fetchHttp = globalThis.fetch.bind(globalThis);
  let token = process.env[role];
  vi.stubGlobal('fetch', (url: string, init: RequestInit) => {
    if (new URL(url).origin !== base.origin) throw new Error('Unexpected origin');
    const headers = new Headers(init?.headers);
    headers.set('Authorization', 'Bearer ' + token);
    return fetchHttp(url, { ...init, headers });
  });
  try {
    render(<SystemComplaintsPage />);
    const send = screen.getByRole('button', { name: 'Gửi khiếu nại' });
    await waitFor(() => expect(send).toBeEnabled());
    fireEvent.change(screen.getByLabelText('Mô tả sự việc'), {target:{value:'Phí đăng tin đã được thu hai lần'}});
    fireEvent.click(send);
    const heading = await screen.findByRole('heading', { name: /Hồ sơ #[0-9]+ · Đã gửi/ });
    const label = heading.textContent!.split(' · ')[0];
    cleanup(); token = process.env.OG_UI_KTV;
    render(<KtvComplaintsPage />);
    const card = within(await screen.findByRole('article', {name: label}));
    fireEvent.click(card.getByRole('button',{name:'Tiếp nhận'}));
    fireEvent.change(await card.findByLabelText('Nội dung xử lý'),{target:{value:'Xin bổ sung mã biên nhận.'}});
    fireEvent.click(card.getByRole('button',{name:'Yêu cầu bổ sung'}));
    await card.findByText('Đang chờ người gửi bổ sung');
    cleanup(); token = process.env[role];
    render(<SystemComplaintsPage />);
    fireEvent.change(await screen.findByLabelText('Nội dung bổ sung'),{target:{value:'Mã biên nhận tại quầy 481.'}});
    fireEvent.click(screen.getByRole('button',{name:'Gửi bổ sung'}));
    await screen.findByText('Đã bổ sung bằng chứng');
    cleanup(); token = process.env.OG_UI_KTV;
    render(<KtvComplaintsPage />);
    const review = within(await screen.findByRole('article',{name:label}));
    expect(await review.findByText('Mã biên nhận tại quầy 481.')).toBeInTheDocument();
    fireEvent.change(review.getByLabelText('Nội dung xử lý'),{target:{value:'Đã xác minh giao dịch thu trùng.'}});
    fireEvent.click(review.getByRole('button',{name:'Xác nhận sự cố'}));
    await review.findByText(/Đã chấp nhận · Đã xác minh/);
    cleanup(); token = process.env[role];
    render(<SystemComplaintsPage />);
    await screen.findByText('Kết quả xử lý · Đã xác minh giao dịch thu trùng.');
  } finally { cleanup(); vi.unstubAllGlobals(); }
}, 30000);

it.skipIf(!process.env.OG_UI_VOUCHER_CODE)('opens the granted compensation in the real wallet from its notification', async () => {
  const base = new URL(import.meta.env.VITE_API_BASE_URL);
  const fetchHttp = globalThis.fetch.bind(globalThis);
  vi.stubGlobal('fetch', (url: string, init: RequestInit) => {
    if (base.hostname !== '127.0.0.1' || new URL(url).origin !== base.origin) throw new Error('Unexpected origin');
    const headers = new Headers(init?.headers);
    headers.set('Authorization', 'Bearer ' + process.env.OG_UI_BUYER);
    return fetchHttp(url, { ...init, headers });
  });
  try {
    render(<MemoryRouter initialEntries={['/notifications']}><Routes>
      <Route path="/notifications" element={<NotificationsPage />} /><Route path="/vouchers" element={<VoucherWallet />} />
    </Routes></MemoryRouter>);
    fireEvent.click(await screen.findByRole('button',{name:/Mở thông báo: Bạn nhận được voucher bồi thường/}));
    const code = await screen.findAllByText(process.env.OG_UI_VOUCHER_CODE!);
    expect(code.some(node => node.closest('article')?.querySelector('a')?.textContent === 'Chọn khi thanh toán')).toBe(true);
  } finally { cleanup(); vi.unstubAllGlobals(); }
});
