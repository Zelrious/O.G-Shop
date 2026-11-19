import { render, screen, act, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { QuickFaceAuthModal } from './QuickFaceAuthModal';
import { quickAuthApi } from '../quickAuthApi';

const mockStartCamera = vi.fn().mockResolvedValue(undefined);
const mockStopCamera = vi.fn();
const mockCaptureFrame = vi.fn().mockReturnValue('data:image/jpeg;base64,mockValidFaceFrame');

vi.mock('../hooks/useWebcam', () => ({
  useWebcam: () => ({
    videoRef: { current: { videoWidth: 640, videoHeight: 480 } },
    isStreaming: true,
    cameraError: null,
    startCamera: mockStartCamera,
    stopCamera: mockStopCamera,
    captureFrame: mockCaptureFrame,
  }),
}));

describe('QuickFaceAuthModal Component', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders nothing when isOpen is false', () => {
    const { container } = render(
      <QuickFaceAuthModal isOpen={false} onClose={vi.fn()} onSuccess={vi.fn()} />
    );
    expect(container.firstChild).toBeNull();
  });

  it('handles Flow 2a: automatically calls onSuccess when alreadyVerified is true', async () => {
    const onSuccess = vi.fn();
    const onClose = vi.fn();

    vi.spyOn(quickAuthApi, 'init').mockResolvedValueOnce({
      alreadyVerified: true,
      resultId: 'existing-result-uuid-123',
      message: 'Đã có kết quả xác thực hợp lệ.',
    });

    render(
      <QuickFaceAuthModal isOpen={true} onClose={onClose} onSuccess={onSuccess} />
    );

    await act(async () => {
      await Promise.resolve();
    });

    expect(screen.getByText(/Đã xác thực danh tính hợp lệ/i)).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(700);
    });

    expect(onSuccess).toHaveBeenCalledWith('existing-result-uuid-123');
    expect(onClose).toHaveBeenCalled();
  });

  it('initializes session and starts continuous auto-scan when verification is required', async () => {
    vi.spyOn(quickAuthApi, 'init').mockResolvedValueOnce({
      alreadyVerified: false,
      sessionId: 'session-uuid-456',
      message: 'Vui lòng nhìn thẳng vào camera.',
    });

    render(
      <QuickFaceAuthModal isOpen={true} onClose={vi.fn()} onSuccess={vi.fn()} />
    );

    await act(async () => {
      await Promise.resolve();
    });

    expect(screen.getByText(/Đang quét tự động liên tục/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Hủy xác thực/i })).toBeInTheDocument();
    expect(mockStartCamera).toHaveBeenCalled();
  });

  it('executes continuous auto-scan successfully with 2s hold and calls onSuccess with resultId', async () => {
    const onSuccess = vi.fn();
    const onClose = vi.fn();

    vi.spyOn(quickAuthApi, 'init').mockResolvedValueOnce({
      alreadyVerified: false,
      sessionId: 'session-uuid-456',
      message: 'Vui lòng nhìn thẳng vào camera.',
    });

    vi.spyOn(quickAuthApi, 'verify').mockResolvedValue({
      match: true,
      resultId: 'new-result-uuid-789',
      distance: 0.2,
      confidenceScore: 95.0,
      remainingAttempts: 15,
      message: 'Xác thực khuôn mặt thành công!',
    });

    render(
      <QuickFaceAuthModal isOpen={true} onClose={onClose} onSuccess={onSuccess} countdownDurationMs={500} />
    );

    await act(async () => {
      await Promise.resolve();
    });

    // Advance time so continuous auto-scan tick executes and triggers HOLDING
    await act(async () => {
      vi.advanceTimersByTime(500);
      await Promise.resolve();
    });

    // Advance time for countdown (500ms) to complete
    await act(async () => {
      vi.advanceTimersByTime(800);
      await Promise.resolve();
    });

    expect(screen.getByText(/Thành công!/i)).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(900);
    });

    expect(onSuccess).toHaveBeenCalledWith('new-result-uuid-789');
    expect(onClose).toHaveBeenCalled();
  });

  it('displays real-time guidance message and continues continuous scanning when face needs adjustment', async () => {
    vi.spyOn(quickAuthApi, 'init').mockResolvedValueOnce({
      alreadyVerified: false,
      sessionId: 'session-uuid-456',
      message: 'Vui lòng nhìn thẳng vào camera.',
    });

    vi.spyOn(quickAuthApi, 'verify').mockResolvedValueOnce({
      match: false,
      frameStatus: 'DISTANCE_ISSUE',
      directionalCue: 'move_closer',
      remainingAttempts: 15,
      message: 'Hãy đưa khuôn mặt lại gần camera hơn',
    });

    render(
      <QuickFaceAuthModal isOpen={true} onClose={vi.fn()} onSuccess={vi.fn()} />
    );

    await act(async () => {
      await Promise.resolve();
    });

    // Advance timer so auto-scan triggers verify call
    await act(async () => {
      vi.advanceTimersByTime(600);
      await Promise.resolve();
    });

    expect(screen.getByText(/Hãy đưa khuôn mặt lại gần camera hơn/i)).toBeInTheDocument();
    // Bỏ cơ chế số lần thử: không hiển thị "Còn lại: X lần thử"
    expect(screen.queryByText(/Còn lại:.*lần thử/i)).toBeNull();
  });

  it('shows NEED_EKYC screen when user has no verified profile or reference expired', async () => {
    vi.spyOn(quickAuthApi, 'init').mockRejectedValueOnce(
      new Error('Tài khoản chưa hoàn tất eKYC hoặc mẫu sinh trắc học đã hết hạn.')
    );

    render(
      <QuickFaceAuthModal isOpen={true} onClose={vi.fn()} onSuccess={vi.fn()} />
    );

    await act(async () => {
      await Promise.resolve();
    });

    expect(screen.getByText(/Yêu cầu hoàn tất eKYC trước/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Đến trang eKYC/i })).toBeInTheDocument();
  });

  it('calls onClose and stops camera when cancel button is clicked', async () => {
    const onClose = vi.fn();

    vi.spyOn(quickAuthApi, 'init').mockResolvedValueOnce({
      alreadyVerified: false,
      sessionId: 'session-uuid-456',
      message: 'Vui lòng nhìn thẳng vào camera.',
    });

    render(
      <QuickFaceAuthModal isOpen={true} onClose={onClose} onSuccess={vi.fn()} />
    );

    await act(async () => {
      await Promise.resolve();
    });

    const cancelBtn = screen.getByRole('button', { name: /Hủy xác thực/i });
    fireEvent.click(cancelBtn);

    expect(onClose).toHaveBeenCalled();
    expect(mockStopCamera).toHaveBeenCalled();
  });

  it('preserves hold progress within 1200ms Smart Tolerance Buffer when a single tick drops', async () => {
    vi.spyOn(quickAuthApi, 'init').mockResolvedValueOnce({
      alreadyVerified: false,
      sessionId: 'session-uuid-456',
      message: 'Vui lòng nhìn thẳng vào camera.',
    });

    // Tick 1: match = true -> starts HOLDING
    vi.spyOn(quickAuthApi, 'verify').mockResolvedValueOnce({
      match: true,
      resultId: 'result-123',
      distance: 0.25,
      confidenceScore: 90.0,
      remainingAttempts: 15,
      message: 'Khớp khuôn mặt',
    });

    render(
      <QuickFaceAuthModal isOpen={true} onClose={vi.fn()} onSuccess={vi.fn()} countdownDurationMs={2000} />
    );

    await act(async () => {
      await Promise.resolve();
    });

    // Advance 60ms to trigger initial tick (50ms)
    await act(async () => {
      vi.advanceTimersByTime(60);
      await Promise.resolve();
    });

    // Should be in HOLDING state
    expect(screen.getByText(/Giữ yên/i)).toBeInTheDocument();

    // Tick 2: transient non-match (e.g. blink) 400ms later (total 460ms < 1200ms tolerance)
    vi.spyOn(quickAuthApi, 'verify').mockResolvedValueOnce({
      match: false,
      frameStatus: 'DISTANCE_ISSUE',
      directionalCue: 'move_closer',
      remainingAttempts: 15,
      message: 'Hãy đưa khuôn mặt lại gần',
    });

    await act(async () => {
      vi.advanceTimersByTime(400);
      await Promise.resolve();
    });

    // Within 1200ms tolerance: hold progress is NOT reset to 0, still in HOLDING
    expect(screen.getAllByText(/Giữ yên/i).length).toBeGreaterThan(0);
  });

  it('immediately triggers success when test bypass button is clicked', async () => {
    const onSuccess = vi.fn();
    const onClose = vi.fn();

    vi.spyOn(quickAuthApi, 'init').mockResolvedValueOnce({
      alreadyVerified: false,
      sessionId: 'session-uuid-456',
      message: 'Vui lòng nhìn thẳng vào camera.',
    });

    vi.spyOn(quickAuthApi, 'verify').mockResolvedValueOnce({
      match: true,
      resultId: 'bypass-result-uuid-999',
      distance: 0.15,
      confidenceScore: 98.0,
      remainingAttempts: 15,
      message: 'Xác thực thử nghiệm thành công!',
    });

    render(
      <QuickFaceAuthModal isOpen={true} onClose={onClose} onSuccess={onSuccess} />
    );

    await act(async () => {
      await Promise.resolve();
    });

    const testBtn = screen.getByRole('button', { name: /Chấp thuận để Test luồng/i });
    expect(testBtn).toBeInTheDocument();

    await act(async () => {
      fireEvent.click(testBtn);
      await Promise.resolve();
    });

    act(() => {
      vi.advanceTimersByTime(600);
    });

    expect(onSuccess).toHaveBeenCalledWith('bypass-result-uuid-999');
    expect(onClose).toHaveBeenCalled();
  });
});
