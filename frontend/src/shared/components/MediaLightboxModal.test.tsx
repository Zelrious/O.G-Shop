import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { MediaLightboxModal } from './MediaLightboxModal';

describe('MediaLightboxModal Component', () => {
  it('does not render when isOpen is false', () => {
    render(
      <MediaLightboxModal
        isOpen={false}
        src="blob:http://localhost/image.png"
        type="IMAGE"
        onClose={vi.fn()}
      />
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('renders image lightbox and handles zoom controls', () => {
    const onClose = vi.fn();
    render(
      <MediaLightboxModal
        isOpen={true}
        src="blob:http://localhost/image.png"
        type="IMAGE"
        alt="Ảnh sản phẩm"
        caption="Ảnh mô tả chi tiết"
        onClose={onClose}
      />
    );

    const dialog = screen.getByRole('dialog', { name: 'Xem tệp đa phương tiện' });
    expect(dialog).toBeInTheDocument();

    const img = screen.getByAltText('Ảnh sản phẩm');
    expect(img).toBeInTheDocument();
    expect(screen.getByText('100%')).toBeInTheDocument();
    expect(screen.getByText('Ảnh mô tả chi tiết')).toBeInTheDocument();

    // Zoom in
    const zoomInBtn = screen.getByRole('button', { name: 'Phóng to' });
    fireEvent.click(zoomInBtn);
    expect(screen.getByText('125%')).toBeInTheDocument();

    // Zoom out
    const zoomOutBtn = screen.getByRole('button', { name: 'Thu nhỏ' });
    fireEvent.click(zoomOutBtn);
    expect(screen.getByText('100%')).toBeInTheDocument();

    // Reset zoom
    fireEvent.click(zoomInBtn);
    fireEvent.click(zoomInBtn);
    expect(screen.getByText('150%')).toBeInTheDocument();
    const resetBtn = screen.getByRole('button', { name: 'Đặt lại kích thước' });
    fireEvent.click(resetBtn);
    expect(screen.getByText('100%')).toBeInTheDocument();
  });

  it('renders video player when type is VIDEO', () => {
    render(
      <MediaLightboxModal
        isOpen={true}
        src="blob:http://localhost/video.mp4"
        type="VIDEO"
        alt="Video bằng chứng"
        onClose={vi.fn()}
      />
    );

    const video = screen.getByLabelText('Video bằng chứng');
    expect(video).toBeInTheDocument();
    expect(video.tagName).toBe('VIDEO');
  });

  it('closes on clicking close button', () => {
    const onClose = vi.fn();
    render(
      <MediaLightboxModal
        isOpen={true}
        src="blob:http://localhost/image.png"
        type="IMAGE"
        onClose={onClose}
      />
    );

    const closeBtn = screen.getByRole('button', { name: 'Đóng cửa sổ xem' });
    fireEvent.click(closeBtn);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('closes on pressing Escape key', () => {
    const onClose = vi.fn();
    render(
      <MediaLightboxModal
        isOpen={true}
        src="blob:http://localhost/image.png"
        type="IMAGE"
        onClose={onClose}
      />
    );

    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('closes on clicking backdrop overlay', () => {
    const onClose = vi.fn();
    render(
      <MediaLightboxModal
        isOpen={true}
        src="blob:http://localhost/image.png"
        type="IMAGE"
        onClose={onClose}
      />
    );

    const overlay = screen.getByRole('dialog');
    fireEvent.click(overlay);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('handles zoom toggle on clicking image', () => {
    render(
      <MediaLightboxModal
        isOpen={true}
        src="blob:http://localhost/image.png"
        type="IMAGE"
        alt="Ảnh zoom"
        onClose={vi.fn()}
      />
    );

    const img = screen.getByAltText('Ảnh zoom');
    expect(screen.getByText('100%')).toBeInTheDocument();

    // Click to zoom 2x
    fireEvent.click(img);
    expect(screen.getByText('200%')).toBeInTheDocument();

    // Click again to toggle back to 1x
    fireEvent.click(img);
    expect(screen.getByText('100%')).toBeInTheDocument();
  });
});

