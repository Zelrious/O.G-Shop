import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { OfferDialog } from './OfferDialog';
import { OfferCard } from './OfferCard';
import { PrivateAttachment } from './PrivateAttachment';
import { MessagesPage } from '../../pages/MessagesPage';
import { ConfirmDialog } from '../../shared/components/Dialog/ConfirmDialog';
import { chatApi, type ChatDetail, type ChatOffer, type ChatMessage } from './chatApi';
import { notificationTarget } from '../notifications/notificationApi';
const actor = vi.hoisted(() => ({ userId: 1, roles: ['BUYER'] }));
vi.mock('../auth', () => ({ useAuth: () => ({ user: actor }) }));
vi.mock('./chatApi', async original => ({ ...await original<typeof import('./chatApi')>(), chatApi: { list: vi.fn(), detail: vi.fn(), start: vi.fn(), send: vi.fn(), read: vi.fn(), propose: vi.fn(), decide: vi.fn(), mediaBlob: vi.fn() } }));
const offer: ChatOffer = { id: 9, conversationId: 3, proposerId: 1, parentId: null, price: 80000, status: 'PENDING', createdAt: '2026-10-12T01:00:00Z', expiresAt: null, respondedAt: null, respondedBy: null, version: 2, productVersion: 7, orderId: null, buyerFee: 0, sellerFee: 8000, allowedActions: ['ACCEPT', 'COUNTER', 'REJECT'], canCheckout: false };
const detail: ChatDetail = { conversation: { id: 3, buyerId: 1, sellerId: 2, productId: 10, title: 'Máy ảnh', thumbnail: null, partnerName: 'Lan', role: 'BUYER', lastActivity: '2026-10-12T01:00:00Z', preview: '', unread: 0, blocked: false }, product: { id: 10, sellerId: 2, title: 'Máy ảnh', price: 100000, thumbnail: null, status: 'ACTIVE', version: 7, revisionId: 10, available: 1, returnAllowed: true, requiresEkyc: false, deleted: false }, messages: { items: [], nextBefore: null }, offers: [], peerReadMessageId: 0, canSend: true, canOffer: true, restrictionReason: null, quota: { images: 0, videos: 0, bytes: 0, maxImages: 5, maxVideos: 1, maxBytes: 47185920, date: '2026-10-12' }, serverTime: '2026-10-12T01:00:00Z' };
const image: ChatMessage = { id: 5, senderId: 2, type: 'IMAGE', content: '', createdAt: detail.serverTime, replyTo: null, offerId: null, media: { mime: 'image/png', bytes: 100, duration: null, available: true } };
beforeEach(() => { vi.resetAllMocks(); actor.userId = 1; vi.mocked(chatApi.list).mockResolvedValue({ items: [detail.conversation], page: 0, size: 20, total: 1, unread: 0 }); vi.mocked(chatApi.detail).mockResolvedValue(detail); vi.mocked(chatApi.read).mockResolvedValue(); });
describe('Negotiation and private chat', () => {
  it('opens a product conversation and clears its creation spinner after URL replacement', async () => {
    vi.mocked(chatApi.start).mockResolvedValue(detail);
    render(<MemoryRouter initialEntries={['/messages?productId=10']}><MessagesPage /></MemoryRouter>);
    expect(await screen.findByRole('textbox', { name: 'Soạn tin nhắn' })).toBeEnabled();
    expect(screen.queryByText('Đang mở hội thoại...')).not.toBeInTheDocument();
    expect(chatApi.start).toHaveBeenCalledWith(10, undefined);
  });
  it('keeps the same proposal command on retry and changes it for a changed price', async () => {
    vi.mocked(chatApi.propose).mockRejectedValue(new Error('Mất kết nối'));
    render(<OfferDialog detail={detail} onClose={vi.fn()} onDone={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: 'Gửi đề nghị' })); await screen.findByText('Mất kết nối');
    fireEvent.click(screen.getByRole('button', { name: 'Gửi đề nghị' })); await waitFor(() => expect(chatApi.propose).toHaveBeenCalledTimes(2));
    expect(vi.mocked(chatApi.propose).mock.calls[0][1].clientId).toBe(vi.mocked(chatApi.propose).mock.calls[1][1].clientId);
    fireEvent.change(screen.getByLabelText('Giá bạn đề nghị (đồng)'), { target: { value: '85000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi đề nghị' })); await waitFor(() => expect(chatApi.propose).toHaveBeenCalledTimes(3));
    expect(vi.mocked(chatApi.propose).mock.calls[2][1].clientId).not.toBe(vi.mocked(chatApi.propose).mock.calls[0][1].clientId);
  });
  it('sends the offer version once during double confirmation and keeps retry identity', async () => {
    vi.mocked(chatApi.decide).mockRejectedValue(new Error('Chưa ghi nhận'));
    render(<MemoryRouter><OfferCard offer={offer} productId={10} sellerView onCounter={vi.fn()} onChanged={vi.fn()} /></MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: 'Chấp nhận giá' })); fireEvent.click(screen.getByRole('button', { name: 'Xác nhận' })); fireEvent.click(screen.getByRole('button', { name: 'Đang ghi nhận...' }));
    await screen.findByText('Chưa ghi nhận'); expect(chatApi.decide).toHaveBeenCalledOnce();
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận' })); await waitFor(() => expect(chatApi.decide).toHaveBeenCalledTimes(2));
    expect(vi.mocked(chatApi.decide).mock.calls[0][1]).toEqual(vi.mocked(chatApi.decide).mock.calls[1][1]);
    expect(vi.mocked(chatApi.decide).mock.calls[0][1].expectedVersion).toBe(2);
  });
  it('renders only server-authorized actions and accepted checkout source', () => {
    const { rerender } = render(<MemoryRouter><OfferCard offer={{ ...offer, allowedActions: [] }} productId={10} sellerView onCounter={vi.fn()} onChanged={vi.fn()} /></MemoryRouter>);
    expect(screen.queryByRole('button', { name: 'Chấp nhận giá' })).not.toBeInTheDocument();
    rerender(<MemoryRouter><OfferCard offer={{ ...offer, status: 'ACCEPTED', allowedActions: [], canCheckout: true }} productId={10} sellerView={false} onCounter={vi.fn()} onChanged={vi.fn()} /></MemoryRouter>);
    expect(screen.getByRole('link', { name: 'Đặt mua theo giá này' })).toHaveAttribute('href', '/checkout?productId=10&offerId=9');
  });
  it('preserves a failed message draft and its command for retry', async () => {
    vi.mocked(chatApi.send).mockRejectedValue(new Error('Chưa gửi được'));
    render(<MemoryRouter initialEntries={['/messages?conversationId=3']}><MessagesPage /></MemoryRouter>);
    const editor = await screen.findByRole('textbox', { name: 'Soạn tin nhắn' }); fireEvent.change(editor, { target: { value: 'Còn phụ kiện không?' } }); fireEvent.click(screen.getByRole('button', { name: 'Gửi' }));
    await screen.findByText('Chưa gửi được'); expect(editor).toHaveValue('Còn phụ kiện không?'); fireEvent.click(screen.getByRole('button', { name: 'Gửi' }));
    await waitFor(() => expect(chatApi.send).toHaveBeenCalledTimes(2)); expect(vi.mocked(chatApi.send).mock.calls[0]).toEqual(vi.mocked(chatApi.send).mock.calls[1]);
  });
  it('uses authenticated blobs, retries failed loads, and revokes URLs', async () => {
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:private-qa'), revokeObjectURL: vi.fn() }));
    vi.mocked(chatApi.mediaBlob).mockRejectedValueOnce(new Error('Chưa tải được')).mockResolvedValue(new Blob(['image']));
    const { unmount } = render(<PrivateAttachment message={image} userId={1} />); await screen.findByText('Chưa tải được'); fireEvent.click(screen.getByRole('button', { name: 'Tải lại tệp' }));
    expect(await screen.findByAltText('Hình ảnh được gửi trong hội thoại')).toHaveAttribute('src', 'blob:private-qa'); unmount(); expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:private-qa');
  });
  it('never fetches expired attachments', () => {
    render(<PrivateAttachment message={{ ...image, media: { ...image.media!, available: false } }} userId={1} />); expect(screen.getByText('Tệp không còn khả dụng')).toBeInTheDocument(); expect(chatApi.mediaBlob).not.toHaveBeenCalled();
  });
  it('routes conversation notifications only to customer participants', () => {
    const notice = { id: 1, type: 'CHAT_MESSAGE', title: 'Tin mới', content: 'Tin mới', read: false, createdAt: detail.serverTime, destination: 'CONVERSATION' as const, productId: null, conversationId: 3 };
    expect(notificationTarget(notice, ['BUYER'])).toBe('/messages?conversationId=3'); expect(notificationTarget(notice, ['KTV'])).toBeNull();
  });
  it('traps confirmation focus and restores it without resetting on rerender', () => {
    const cancel = vi.fn(); render(<button>Trigger</button>); const trigger = screen.getByRole('button', { name: 'Trigger' }); trigger.focus();
    const { rerender, unmount } = render(<ConfirmDialog isOpen title="Chặn" message="QA" onConfirm={vi.fn()} onCancel={cancel} />);
    const first = screen.getByRole('button', { name: 'Hủy bỏ' }), last = screen.getByRole('button', { name: 'Xác nhận' }); expect(first).toHaveFocus(); last.focus(); fireEvent.keyDown(last, { key: 'Tab' }); expect(first).toHaveFocus();
    last.focus(); rerender(<ConfirmDialog isOpen title="Chặn" message="QA updated" onConfirm={vi.fn()} onCancel={cancel} />); expect(last).toHaveFocus(); fireEvent.keyDown(last, { key: 'Escape' }); expect(cancel).toHaveBeenCalledOnce(); unmount(); expect(trigger).toHaveFocus();
  });
});
