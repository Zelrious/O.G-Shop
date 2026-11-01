import { render, screen, act } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { FaceScanner } from './components/FaceScanner';
import { ekycApi } from './ekycApi';

// Mock useWebcam
const mockStartCamera = vi.fn().mockResolvedValue(undefined);
const mockStopCamera = vi.fn();
const mockCaptureFrame = vi.fn().mockReturnValue('data:image/jpeg;base64,mockValidFrame123');

vi.mock('./hooks/useWebcam', () => ({
  useWebcam: () => ({
    videoRef: { current: { videoWidth: 640, videoHeight: 480 } },
    isStreaming: true,
    cameraError: null,
    startCamera: mockStartCamera,
    stopCamera: mockStopCamera,
    captureFrame: mockCaptureFrame,
  }),
}));

describe('FaceScanner Component', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders scanning instructions and guidance HUD in streaming state', () => {
    render(
      <FaceScanner
        frontAssetId={100}
        onScanSuccess={vi.fn()}
      />
    );

    expect(screen.getByText(/quét khuôn mặt tự động/i)).toBeInTheDocument();
    expect(screen.getByText(/giữ yên trong 1 giây/i)).toBeInTheDocument();
    expect(screen.getByText(/Căn chỉnh khuôn mặt vào giữa khung oval/i)).toBeInTheDocument();
  });

  it('updates guidance when receiving positioning instructions from eKYC checkFace API', async () => {
    vi.spyOn(ekycApi, 'checkFace').mockResolvedValueOnce({
      isMatch: false,
      matchStatus: 'DISTANCE_ISSUE',
      distance: 1.0,
      confidenceScore: 0.0,
      message: 'Hãy đưa khuôn mặt lại gần hơn',
    });

    render(
      <FaceScanner
        frontAssetId={100}
        onScanSuccess={vi.fn()}
      />
    );

    await act(async () => {
      await vi.advanceTimersByTimeAsync(600);
    });

    expect(screen.getByText(/Hãy đưa khuôn mặt lại gần hơn/i)).toBeInTheDocument();
  });

  it('resets 2-second hold counter if a face becomes invalid before 2 seconds are reached', async () => {
    const onScanSuccess = vi.fn();

    // Lần 1: Khớp hợp lệ -> Bắt đầu đếm 2s
    vi.spyOn(ekycApi, 'checkFace')
      .mockResolvedValueOnce({
        isMatch: true,
        matchStatus: 'MATCHED',
        distance: 0.25,
        confidenceScore: 0.94,
        message: 'Khuôn mặt hợp lệ! Vui lòng giữ yên...',
      })
      // Lần 2 (khi chưa đủ 2s): Bị lỗi lệch mặt hoặc khoảng cách -> Bắt đầu đếm lại!
      .mockResolvedValueOnce({
        isMatch: false,
        matchStatus: 'DISTANCE_ISSUE',
        distance: 0.8,
        confidenceScore: 0.0,
        message: 'Hãy đưa khuôn mặt ra xa hơn',
      });

    render(
      <FaceScanner
        frontAssetId={100}
        onScanSuccess={onScanSuccess}
        countdownDurationMs={2000}
      />
    );

    // Kích hoạt lần quét 1
    await act(async () => {
      await vi.advanceTimersByTimeAsync(600);
    });

    // Tiến hành đếm được 400ms (chưa đủ 2s)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(400);
    });

    expect(screen.getByText(/Khuôn mặt hợp lệ! Vui lòng giữ yên/i)).toBeInTheDocument();

    // Kích hoạt lần quét 2 bị lỗi
    await act(async () => {
      await vi.advanceTimersByTimeAsync(600);
    });

    // Phải báo lỗi và bắt đầu đếm 2s lại từ đầu
    expect(screen.getByText(/Hãy đưa khuôn mặt ra xa hơn/i)).toBeInTheDocument();
    expect(screen.getByText(/Đang bắt đầu đếm lại 2 giây/i)).toBeInTheDocument();
    expect(onScanSuccess).not.toHaveBeenCalled();
  });

  it('automatically captures valid face photo and completes when held valid for full 2 seconds', async () => {
    const onScanSuccess = vi.fn();

    // Trả về khớp hợp lệ liên tục
    vi.spyOn(ekycApi, 'checkFace').mockResolvedValue({
      isMatch: true,
      matchStatus: 'MATCHED',
      distance: 0.22,
      confidenceScore: 0.96,
      message: 'Khuôn mặt hợp lệ! Vui lòng giữ yên...',
    });

    render(
      <FaceScanner
        frontAssetId={100}
        onScanSuccess={onScanSuccess}
        countdownDurationMs={2000}
      />
    );

    // Bắt đầu quét và kích hoạt trạng thái giữ
    await act(async () => {
      await vi.advanceTimersByTimeAsync(600);
    });

    // Giữ yên liên tục đủ 2 giây (2000ms)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2100);
    });

    expect(onScanSuccess).toHaveBeenCalledTimes(1);
    expect(onScanSuccess).toHaveBeenCalledWith(
      'data:image/jpeg;base64,mockValidFrame123',
      expect.objectContaining({
        isMatch: true,
        distance: 0.22,
      })
    );
    expect(mockStopCamera).toHaveBeenCalled();
  });

  it('reveals manual capture button for KTV review after 20 seconds of scanning', async () => {
    const onScanSuccess = vi.fn();

    // AI trả về chưa khớp
    vi.spyOn(ekycApi, 'checkFace').mockResolvedValue({
      isMatch: false,
      matchStatus: 'NOT_MATCHED',
      distance: 0.65,
      confidenceScore: 0.20,
      message: 'Khuôn mặt chưa trùng khớp với ảnh CCCD',
    });

    render(
      <FaceScanner
        frontAssetId={100}
        onScanSuccess={onScanSuccess}
      />
    );

    // Trong 19 giây đầu, nút chụp thủ công chưa xuất hiện
    await act(async () => {
      await vi.advanceTimersByTimeAsync(19000);
    });
    expect(screen.queryByText(/Chụp ảnh gửi KTV xét duyệt/i)).not.toBeInTheDocument();

    // Qua 20 giây: Nút chụp thủ công và cảnh báo cứu hộ xuất hiện!
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    const captureBtn = screen.getByRole('button', { name: /Chụp ảnh gửi KTV xét duyệt/i });
    expect(captureBtn).toBeInTheDocument();
    expect(screen.getByText(/Đã quét tự động hơn 20 giây nhưng chưa khớp/i)).toBeInTheDocument();

    // Bấm nút chụp thủ công -> Gọi onScanSuccess với isManualCapture: true
    await act(async () => {
      captureBtn.click();
    });

    expect(onScanSuccess).toHaveBeenCalledWith(
      'data:image/jpeg;base64,mockValidFrame123',
      expect.objectContaining({
        isMatch: true,
        isManualCapture: true,
      })
    );
    expect(mockStopCamera).toHaveBeenCalled();
  });
});
