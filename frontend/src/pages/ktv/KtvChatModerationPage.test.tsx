import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { KtvChatModerationPage } from './KtvChatModerationPage';
import { chatModerationApi, type ReportDetail } from '../../features/chat-moderation/chatModerationApi';

vi.mock('../../features/chat-moderation/chatModerationApi', () => ({
  chatModerationApi: {
    getQueue: vi.fn(),
    getDetail: vi.fn(),
    submitDecision: vi.fn(),
    getStats: vi.fn(),
    getConversationMessages: vi.fn(),
    getUserViolationHistory: vi.fn(),
    manualFlag: vi.fn(),
    batchDecision: vi.fn(),
    getPendingViolations: vi.fn(),
  },
}));

describe('KtvChatModerationPage Component', () => {
  const mockDetail: ReportDetail = {
    reportId: 101,
    conversationId: 50,
    flaggedMessageId: 2,
    offenderId: 201,
    offenderName: 'Nguyễn Văn Gian',
    offenderEmail: 'gian@example.com',
    offenderViolationScore: 5,
    offenderReputation: 80,
    offenderChatRestrictedUntil: null,
    reporterId: null,
    violationType: 'POLITICAL_SUBVERSIVE',
    severityLevel: 'CRITICAL',
    status: 'PENDING',
    flaggedContent: '*** kêu gọi lật đổ ***',
    contextSnippet: JSON.stringify([
      { messageId: 1, senderId: 201, type: 'TEXT', content: 'Chào bạn', createdAt: '2026-11-08T10:00:00Z' },
      { messageId: 2, senderId: 201, type: 'TEXT', content: '*** kêu gọi lật đổ ***', createdAt: '2026-11-08T10:01:00Z' },
    ]),
    decisionAction: null,
    reputationPenalty: 0,
    decisionNote: null,
    resolvedBy: null,
    resolverName: null,
    resolvedAt: null,
    createdAt: '2026-11-08T10:01:05Z',
  };

  const mockChunkResponse = {
    conversationId: 50,
    messages: [
      {
        messageId: 1,
        conversationId: 50,
        senderId: 201,
        senderName: 'Nguyễn Văn Gian',
        type: 'TEXT',
        content: 'Chào bạn',
        rawContent: 'Chào bạn',
        isMaskedForUsers: false,
        hasViolation: false,
        createdAt: '2026-11-08T10:00:00Z',
      },
      {
        messageId: 2,
        conversationId: 50,
        senderId: 201,
        senderName: 'Nguyễn Văn Gian',
        type: 'TEXT',
        content: '***',
        rawContent: 'kêu gọi lật đổ',
        isMaskedForUsers: true,
        hasViolation: true,
        violationType: 'POLITICAL_SUBVERSIVE',
        severityLevel: 'CRITICAL',
        reportStatus: 'PENDING',
        createdAt: '2026-11-08T10:01:00Z',
      },
    ],
    hasOlder: true,
    hasNewer: false,
  };

  const mockHistoryResponse = {
    userId: 201,
    fullName: 'Nguyễn Văn Gian',
    email: 'gian@example.com',
    reputationScore: 80,
    reports: [
      {
        reportId: 101,
        conversationId: 50,
        flaggedMessageId: 2,
        violationType: 'POLITICAL_SUBVERSIVE',
        severityLevel: 'CRITICAL',
        status: 'PENDING',
        flaggedContent: 'kêu gọi lật đổ',
        reputationPenalty: 0,
        createdAt: '2026-11-08T10:01:05Z',
      },
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
    (chatModerationApi.getStats as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      pendingCount: 1,
    });
    (chatModerationApi.getQueue as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      items: [
        {
          reportId: 101,
          conversationId: 50,
          offenderId: 201,
          offenderName: 'Nguyễn Văn Gian',
          offenderEmail: 'gian@example.com',
          reporterId: null,
          violationType: 'POLITICAL_SUBVERSIVE',
          severityLevel: 'CRITICAL',
          status: 'PENDING',
          flaggedContent: '*** kêu gọi lật đổ ***',
          createdAt: '2026-11-08T10:01:05Z',
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      pendingCount: 1,
      total: 1,
    });
    (chatModerationApi.getDetail as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockDetail);
    (chatModerationApi.getConversationMessages as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockChunkResponse);
    (chatModerationApi.getUserViolationHistory as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockHistoryResponse);
    (chatModerationApi.submitDecision as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ...mockDetail,
      status: 'RESOLVED',
      decisionAction: 'RESTRICT_CHAT',
      reputationPenalty: 5,
    });
    (chatModerationApi.manualFlag as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      reportId: 102,
      status: 'RESOLVED',
    });
    (chatModerationApi.batchDecision as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      resolvedCount: 2,
      offenderId: 201,
      action: 'RESTRICT_CHAT',
      reputationPenalty: 5,
      message: 'Đã xử phạt gộp thành công 2 tin nhắn.',
    });
  });

  it('renders heading, queue item, and loads detail with chunked stream', async () => {
    render(
      <MemoryRouter>
        <KtvChatModerationPage />
      </MemoryRouter>
    );

    expect(
      screen.getByRole('heading', { level: 1, name: /Kiểm duyệt tin nhắn & An toàn hội thoại/i })
    ).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getAllByText('Nguyễn Văn Gian').length).toBeGreaterThan(0);
      expect(screen.getAllByText(/CHÍNH TRỊ \/ KHỦNG BỐ/i).length).toBeGreaterThan(0);
      expect(screen.getAllByText('CRITICAL').length).toBeGreaterThan(0);
    });

    // Verify detail loaded
    await waitFor(() => {
      expect(screen.getByText(/Văn bản gốc \(Raw Text\)/i)).toBeInTheDocument();
      expect(screen.getByText(/Dòng tin nhắn hội thoại/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Xử phạt/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Bỏ qua/i })).toBeInTheDocument();
    });

    // Verify chunk stream messages and buttons
    await waitFor(() => {
      expect(screen.getByText('kêu gọi lật đổ')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Tải tin cũ hơn/i })).toBeInTheDocument();
    });
  });

  it('opens and displays User Violation History Modal', async () => {
    render(
      <MemoryRouter>
        <KtvChatModerationPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Lịch sử vi phạm/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Lịch sử vi phạm/i }));

    await waitFor(() => {
      expect(screen.getByText(/Lịch sử vi phạm tài khoản/i)).toBeInTheDocument();
      expect(screen.getByText(/Hồ sơ #101/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Xử lý ngay/i })).toBeInTheDocument();
    });
  });

  it('opens and submits Manual Flag Modal from 3-dot menu', async () => {
    render(
      <MemoryRouter>
        <KtvChatModerationPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getAllByTitle(/Tùy chọn thao tác/i).length).toBeGreaterThan(0);
    });

    // Click 3-dot menu on message #1
    fireEvent.click(screen.getAllByTitle(/Tùy chọn thao tác/i)[0]);

    await waitFor(() => {
      expect(screen.getByText(/Xử lý tin nhắn này/i)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText(/Xử lý tin nhắn này/i));

    await waitFor(() => {
      expect(screen.getByText(/Gắn cờ vi phạm thủ công \(UC74\)/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Xác nhận gắn cờ & Thi hành/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Xác nhận gắn cờ & Thi hành/i }));

    await waitFor(() => {
      expect(chatModerationApi.manualFlag).toHaveBeenCalled();
    });
  });

  it('submits sanction decision with shortened button and notes', async () => {
    render(
      <MemoryRouter>
        <KtvChatModerationPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Xử phạt/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Xử phạt/i }));

    await waitFor(() => {
      expect(screen.getByText(/Quyết định xử lý vi phạm/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Xác nhận thi hành/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Xác nhận thi hành/i }));

    await waitFor(() => {
      expect(chatModerationApi.submitDecision).toHaveBeenCalled();
    });
  });

  it('toggles advanced filters and provides copy evidence capability', async () => {
    Object.assign(navigator, {
      clipboard: {
        writeText: vi.fn().mockResolvedValue(undefined),
      },
    });

    render(
      <MemoryRouter>
        <KtvChatModerationPage />
      </MemoryRouter>
    );

    // Advanced filters toggle
    const toggleBtn = screen.getByRole('button', { name: /Bộ lọc nâng cao/i });
    expect(screen.queryByText(/Trừ điểm uy tín:/i)).not.toBeInTheDocument();

    fireEvent.click(toggleBtn);
    expect(screen.getByText(/Trừ điểm uy tín:/i)).toBeInTheDocument();
    expect(screen.getByText(/Điểm vi phạm ≥:/i)).toBeInTheDocument();

    // Copy evidence button
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Sao chép/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Sao chép/i }));
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('*** kêu gọi lật đổ ***');
  });

  it('displays pre-marked violation badges for flagged messages', async () => {
    render(
      <MemoryRouter>
        <KtvChatModerationPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText(/\[Vi phạm: Chính trị \/ Khủng bố\]/i)).toBeInTheDocument();
    });
  });

  it('activates multi-select mode via "Chọn tất cả tin vi phạm" and submits batch decision', async () => {
    render(
      <MemoryRouter>
        <KtvChatModerationPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('kêu gọi lật đổ')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Chọn tất cả tin vi phạm/i })).toBeInTheDocument();
    });

    // Click "Chọn tất cả tin vi phạm"
    fireEvent.click(screen.getByRole('button', { name: /Chọn tất cả tin vi phạm/i }));

    // Verify floating action bar appears with selected count
    await waitFor(() => {
      expect(screen.getByText(/Đã chọn: 1 tin nhắn/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Xử lý chung các tin đã chọn/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Thoát chọn nhiều/i })).toBeInTheDocument();
    });

    // Click "Xử lý chung các tin đã chọn"
    fireEvent.click(screen.getByRole('button', { name: /Xử lý chung các tin đã chọn/i }));

    // Verify Batch Sanction Modal opens
    await waitFor(() => {
      expect(screen.getByText(/Xử lý chung các tin nhắn vi phạm/i)).toBeInTheDocument();
      expect(screen.getByText(/Điểm phạt uy tín sẽ được trừ/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Xác nhận xử lý chung/i })).toBeInTheDocument();
    });

    // Confirm batch execution
    fireEvent.click(screen.getByRole('button', { name: /Xác nhận xử lý chung/i }));

    await waitFor(() => {
      expect(chatModerationApi.batchDecision).toHaveBeenCalledWith(
        expect.objectContaining({
          conversationId: 50,
          messageIds: [2],
          action: 'RESTRICT_CHAT',
          reputationPenalty: 5,
        })
      );
    });
  });

  it('opens 3-dot menu and activates multi-select from message row', async () => {
    render(
      <MemoryRouter>
        <KtvChatModerationPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getAllByTitle(/Tùy chọn thao tác/i).length).toBeGreaterThan(0);
    });

    // Click the 3-dot button on message #1
    fireEvent.click(screen.getAllByTitle(/Tùy chọn thao tác/i)[0]);

    await waitFor(() => {
      expect(screen.getByText(/Chọn nhiều tin nhắn/i)).toBeInTheDocument();
      expect(screen.getByText(/Xử lý tin nhắn này/i)).toBeInTheDocument();
    });

    // Click "Chọn nhiều tin nhắn"
    fireEvent.click(screen.getByText(/Chọn nhiều tin nhắn/i));

    // Floating action bar appears
    await waitFor(() => {
      expect(screen.getByText(/Đã chọn: 1 tin nhắn/i)).toBeInTheDocument();
    });

    // Press Escape to exit multi-select mode
    fireEvent.keyDown(window, { key: 'Escape' });

    await waitFor(() => {
      expect(screen.queryByText(/Đã chọn: 1 tin nhắn/i)).not.toBeInTheDocument();
    });
  });

  it('marks resolved messages with [Đã xử lý] and prevents them from being selected', async () => {
    const resolvedChunk = {
      conversationId: 50,
      messages: [
        {
          messageId: 1,
          conversationId: 50,
          senderId: 201,
          senderName: 'Nguyễn Văn Gian',
          type: 'TEXT',
          content: 'Chào bạn',
          rawContent: 'Chào bạn',
          isMaskedForUsers: false,
          hasViolation: false,
          createdAt: '2026-11-08T10:00:00Z',
        },
        {
          messageId: 2,
          conversationId: 50,
          senderId: 201,
          senderName: 'Nguyễn Văn Gian',
          type: 'TEXT',
          content: '***',
          rawContent: 'kêu gọi lật đổ',
          isMaskedForUsers: true,
          hasViolation: true,
          violationType: 'POLITICAL_SUBVERSIVE',
          severityLevel: 'CRITICAL',
          moderationStatus: 'RESOLVED',
          createdAt: '2026-11-08T10:01:00Z',
        },
      ],
      hasOlder: false,
      hasNewer: false,
    };
    (chatModerationApi.getConversationMessages as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce(resolvedChunk);

    render(
      <MemoryRouter>
        <KtvChatModerationPage />
      </MemoryRouter>
    );

    // Verify resolved tag is displayed
    await waitFor(() => {
      expect(screen.getByText(/\[Đã xử lý: Chính trị \/ Khủng bố\]/i)).toBeInTheDocument();
    });

    // Click "Chọn tất cả tin vi phạm"
    fireEvent.click(screen.getByRole('button', { name: /Chọn tất cả tin vi phạm/i }));

    // Resolved message cannot be selected, feedback message shown
    await waitFor(() => {
      expect(screen.getByText(/Tất cả tin nhắn vi phạm trong hội thoại này đã được xử lý xong/i)).toBeInTheDocument();
    });
  });
});
