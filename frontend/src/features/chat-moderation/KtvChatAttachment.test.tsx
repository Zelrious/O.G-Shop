import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { KtvChatAttachment } from './KtvChatAttachment';
import { chatModerationApi } from './chatModerationApi';

vi.mock('./chatModerationApi', () => ({
  chatModerationApi: {
    getMessageMediaBlob: vi.fn(),
  },
}));

describe('KtvChatAttachment Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    global.URL.createObjectURL = vi.fn(() => 'blob:http://localhost/test-media');
    global.URL.revokeObjectURL = vi.fn();
  });

  it('renders image attachment and triggers lightbox on click without opening new window', async () => {
    const fakeBlob = new Blob(['fake image data'], { type: 'image/jpeg' });
    vi.mocked(chatModerationApi.getMessageMediaBlob).mockResolvedValueOnce(fakeBlob);

    const onOpenLightbox = vi.fn();

    render(
      <KtvChatAttachment
        messageId={101}
        type="IMAGE"
        caption="Ảnh bằng chứng"
        onOpenLightbox={onOpenLightbox}
      />
    );

    // Initial loading indicator
    expect(screen.getByText('Đang tải hình ảnh...')).toBeInTheDocument();

    // After loading resolves
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Xem ảnh #101' })).toBeInTheDocument();
    });

    const imgBtn = screen.getByRole('button', { name: 'Xem ảnh #101' });
    fireEvent.click(imgBtn);

    expect(onOpenLightbox).toHaveBeenCalledTimes(1);
    expect(onOpenLightbox).toHaveBeenCalledWith('blob:http://localhost/test-media', 'IMAGE', 'Ảnh bằng chứng');
  });

  it('renders video attachment and allows opening in lightbox', async () => {
    const fakeBlob = new Blob(['fake video data'], { type: 'video/mp4' });
    vi.mocked(chatModerationApi.getMessageMediaBlob).mockResolvedValueOnce(fakeBlob);

    const onOpenLightbox = vi.fn();

    render(
      <KtvChatAttachment
        messageId={102}
        type="VIDEO"
        caption="Video vi phạm"
        onOpenLightbox={onOpenLightbox}
      />
    );

    await waitFor(() => {
      expect(screen.getByLabelText('Video tin nhắn #102')).toBeInTheDocument();
    });

    const expandBtn = screen.getByRole('button', { name: 'Xem video phóng to' });
    fireEvent.click(expandBtn);

    expect(onOpenLightbox).toHaveBeenCalledTimes(1);
    expect(onOpenLightbox).toHaveBeenCalledWith('blob:http://localhost/test-media', 'VIDEO', 'Video vi phạm');
  });

  it('renders error state and handles retry button', async () => {
    vi.mocked(chatModerationApi.getMessageMediaBlob)
      .mockRejectedValueOnce(new Error('Lỗi mạng khi tải tệp'))
      .mockResolvedValueOnce(new Blob(['retry ok'], { type: 'image/png' }));

    render(
      <KtvChatAttachment
        messageId={103}
        type="IMAGE"
        onOpenLightbox={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByText('Không thể tải ảnh #103')).toBeInTheDocument();
    });

    const retryBtn = screen.getByRole('button', { name: 'Thử lại' });
    fireEvent.click(retryBtn);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Xem ảnh #103' })).toBeInTheDocument();
    });
  });
});

