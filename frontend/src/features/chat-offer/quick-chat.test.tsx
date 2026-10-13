import { useContext } from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Link, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ChatProvider } from './ChatProvider';
import { ChatContext } from './chatContext';
import { MessageBell } from './MessageBell';
import { MessagesPage } from '../../pages/MessagesPage';
import { chatApi, type ChatDetail, type ChatMessage } from './chatApi';

const actor = vi.hoisted(() => ({ userId: 1, roles: ['BUYER'] }));
vi.mock('../auth', () => ({ useAuth: () => ({ user: actor }) }));
vi.mock('./chatApi', async original => ({ ...await original<typeof import('./chatApi')>(), chatApi: { list: vi.fn(), detail: vi.fn(), start: vi.fn(), send: vi.fn(), read: vi.fn(), media: vi.fn() } }));
const detail: ChatDetail = { conversation: { id: 3, buyerId: 1, sellerId: 2, productId: 10, title: 'Máy ảnh', thumbnail: null, partnerName: 'Lan', role: 'BUYER', lastActivity: '2026-10-13T01:00:00Z', preview: '', unread: 0, blocked: false }, product: { id: 10, sellerId: 2, title: 'Máy ảnh', price: 100000, thumbnail: null, status: 'ACTIVE', version: 7, revisionId: 10, available: 1, returnAllowed: true, requiresEkyc: false, deleted: false }, messages: { items: [], nextBefore: null }, offers: [], peerReadMessageId: 0, canSend: true, canOffer: true, restrictionReason: null, quota: { images: 0, videos: 0, bytes: 0, maxImages: 5, maxVideos: 1, maxBytes: 47185920, date: '2026-10-13' }, serverTime: '2026-10-13T01:00:00Z' };
function Controls() { const chat = useContext(ChatContext)!; return <><button onClick={() => chat.open({ conversationId: 3 })}>Hỏi người bán</button><MessageBell /><Link to="/other">Trang khác</Link></>; }
function App() { return <MemoryRouter initialEntries={['/marketplace?categoryId=2']}><ChatProvider><Controls /><Routes><Route path="/messages" element={<MessagesPage />} /><Route path="*" element={<h1>Trang sản phẩm</h1>} /></Routes></ChatProvider></MemoryRouter>; }
beforeEach(() => {
  vi.resetAllMocks(); actor.userId = 1; actor.roles = ['BUYER'];
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
  vi.mocked(chatApi.list).mockResolvedValue({ items: [detail.conversation], page: 0, size: 20, total: 1, unread: 0 });
  vi.mocked(chatApi.detail).mockResolvedValue(detail); vi.mocked(chatApi.read).mockResolvedValue();
});

describe('Quick chat and inbox handoff', () => {
  it('keeps drafts across minimize, routes, close/reopen and full inbox', async () => {
    render(<App />); fireEvent.click(screen.getByRole('button', { name: 'Hỏi người bán' }));
    const editor = await screen.findByRole('textbox', { name: 'Soạn tin nhắn' });
    fireEvent.change(editor, { target: { value: 'Còn phụ kiện không?' } });
    fireEvent.click(screen.getByRole('button', { name: 'Thu nhỏ chat' }));
    expect(screen.queryByRole('textbox', { name: 'Soạn tin nhắn' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('link', { name: 'Trang khác' }));
    fireEvent.click(screen.getByRole('button', { name: 'Mở lại chat với Lan' }));
    expect(screen.getByRole('textbox', { name: 'Soạn tin nhắn' })).toHaveValue('Còn phụ kiện không?');
    fireEvent.click(screen.getByRole('button', { name: 'Đóng chat' }));
    fireEvent.click(screen.getByRole('button', { name: 'Hỏi người bán' }));
    expect(await screen.findByRole('textbox', { name: 'Soạn tin nhắn' })).toHaveValue('Còn phụ kiện không?');
    fireEvent.click(screen.getByRole('button', { name: 'Mở hộp thư đầy đủ' }));
    expect(await screen.findByRole('textbox', { name: 'Soạn tin nhắn' })).toHaveValue('Còn phụ kiện không?');
    expect(screen.getByRole('link', { name: 'Trở lại trang đang xem' })).toHaveAttribute('href', '/other');
  });
  it('reuses the failed command after switching from quick chat to the inbox', async () => {
    vi.mocked(chatApi.send).mockRejectedValue(new Error('Mất kết nối'));
    render(<App />); fireEvent.click(screen.getByRole('button', { name: 'Hỏi người bán' }));
    fireEvent.change(await screen.findByRole('textbox', { name: 'Soạn tin nhắn' }), { target: { value: 'Bạn gửi thêm ảnh nhé' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi' }));
    await screen.findByText('Mất kết nối');
    fireEvent.click(screen.getByRole('button', { name: 'Mở hộp thư đầy đủ' }));
    await screen.findByRole('textbox', { name: 'Soạn tin nhắn' });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi' }));
    await waitFor(() => expect(chatApi.send).toHaveBeenCalledTimes(2));
    expect(vi.mocked(chatApi.send).mock.calls[0]).toEqual(vi.mocked(chatApi.send).mock.calls[1]);
  });
  it('keeps the selected attachment when opening the full inbox', async () => {
    render(<App />); fireEvent.click(screen.getByRole('button', { name: 'Hỏi người bán' }));
    await screen.findByRole('textbox', { name: 'Soạn tin nhắn' });
    const file = new File(['photo'], 'detail.jpg', { type: 'image/jpeg' });
    const input = document.querySelector('input[type=file]')!;
    fireEvent.change(input, { target: { files: [file] } });
    fireEvent.click(screen.getByRole('button', { name: 'Mở hộp thư đầy đủ' }));
    expect(await screen.findByText(/detail.jpg/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Gửi' }));
    await waitFor(() => expect(chatApi.media).toHaveBeenCalledWith(3, expect.any(String), file, ''));
  });
  it('does not fetch or mark new messages in a minimized conversation as read', async () => {
    const first: ChatMessage = { id: 5, senderId: 2, type: 'TEXT', content: 'Tin ban đầu', createdAt: detail.serverTime, replyTo: null, offerId: null, media: null };
    vi.mocked(chatApi.detail).mockResolvedValue({ ...detail, messages: { items: [first], nextBefore: null } });
    render(<App />); fireEvent.click(screen.getByRole('button', { name: 'Hỏi người bán' }));
    await screen.findByRole('textbox', { name: 'Soạn tin nhắn' });
    await waitFor(() => expect(chatApi.read).toHaveBeenCalledWith(3, 5));
    fireEvent.click(screen.getByRole('button', { name: 'Thu nhỏ chat' }));
    const calls = vi.mocked(chatApi.detail).mock.calls.length;
    vi.mocked(chatApi.detail).mockResolvedValue({ ...detail, messages: { items: [first, { ...first, id: 6, content: 'Tin mới khi thu nhỏ' }], nextBefore: null } });
    fireEvent(window, new Event('og-chat-updated'));
    expect(chatApi.detail).toHaveBeenCalledTimes(calls);
    expect(chatApi.read).not.toHaveBeenCalledWith(3, 6);
    fireEvent.click(screen.getByRole('button', { name: 'Mở lại chat với Lan' }));
    await waitFor(() => expect(chatApi.read).toHaveBeenCalledWith(3, 6));
  });
  it('prevents a second send during a handoff and clears the shared draft after acknowledgement', async () => {
    let acknowledge!: (message: ChatMessage) => void;
    vi.mocked(chatApi.send).mockReturnValue(new Promise(resolve => { acknowledge = resolve; }));
    render(<App />); fireEvent.click(screen.getByRole('button', { name: 'Hỏi người bán' }));
    fireEvent.change(await screen.findByRole('textbox', { name: 'Soạn tin nhắn' }), { target: { value: 'Đang gửi một lần' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi' }));
    fireEvent.click(screen.getByRole('button', { name: 'Mở hộp thư đầy đủ' }));
    expect(await screen.findByRole('textbox', { name: 'Soạn tin nhắn' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'Đang gửi...' }));
    expect(chatApi.send).toHaveBeenCalledTimes(1);
    acknowledge({ id: 7, senderId: 1, type: 'TEXT', content: 'Đang gửi một lần', createdAt: detail.serverTime, replyTo: null, offerId: null, media: null });
    await waitFor(() => expect(screen.getByRole('textbox', { name: 'Soạn tin nhắn' })).toHaveValue(''));
    expect(screen.getByRole('textbox', { name: 'Soạn tin nhắn' })).toBeEnabled();
  });
  it('discards the previous owner draft and cached inbox when the account changes', async () => {
    const { rerender } = render(<App />); fireEvent.click(screen.getByRole('button', { name: 'Hỏi người bán' }));
    fireEvent.change(await screen.findByRole('textbox', { name: 'Soạn tin nhắn' }), { target: { value: 'Bản soạn riêng' } });
    actor.userId = 8; rerender(<App />);
    expect(screen.queryByRole('textbox', { name: 'Soạn tin nhắn' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Hỏi người bán' }));
    expect(await screen.findByRole('textbox', { name: 'Soạn tin nhắn' })).toHaveValue('');
  });
  it('opens full-screen chat on mobile and exposes no customer chat for staff', async () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
    const { rerender } = render(<App />); fireEvent.click(screen.getByRole('button', { name: 'Hỏi người bán' }));
    expect(await screen.findByRole('textbox', { name: 'Soạn tin nhắn' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Thu nhỏ chat' })).not.toBeInTheDocument();
    actor.roles = ['KTV']; rerender(<App />);
    expect(screen.queryByRole('button', { name: /^Tin nhắn/ })).not.toBeInTheDocument();
  });
  it('shows a retry state when the message menu cannot load', async () => {
    vi.mocked(chatApi.list).mockRejectedValue(new Error('Chưa tải hộp thư'));
    render(<App />); fireEvent.click(screen.getByRole('button', { name: 'Tin nhắn' }));
    const popup = screen.getByRole('dialog', { name: 'Tin nhắn' });
    expect(await within(popup).findByText('Chưa tải hộp thư')).toBeInTheDocument();
    expect(within(popup).getByRole('button', { name: 'Thử lại' })).toBeInTheDocument();
  });
});
