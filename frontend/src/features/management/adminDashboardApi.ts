import { authApi } from '../auth/authApi';

// Admin Dashboard & Analytics Reporting API

export interface MonthlyRevenuePoint {
  month: string;
  gmv: number;
  revenue: number;
  orderCount: number;
}

export interface MonthlyUserGrowthPoint {
  month: string;
  newUsers: number;
  totalUsers: number;
}

export interface SearchTrendItem {
  name: string;
  searches: number;
  orders?: number;
  rate?: string;
  note?: string;
}

export interface CategoryReturnStat {
  category: string;
  totalOrders: number;
  returnCount: number;
  complaintCount: number;
  returnRate: string;
  primaryIssue: string;
}

export interface ReturnReportRow {
  caseId: number;
  caseType: string;
  outcomeBucket: string;
  finalState: string;
  orderId: number | null;
  category: string;
  closedAt: string;
}

export interface ReturnsReportResponse {
  cutoffAt: string;
  timezone: string;
  fromDate: string;
  toDate: string;
  caseTypeFilter: 'ALL' | 'RETURN' | 'SYSTEM_ISSUE';
  totalResolvedCases: number;
  returnRefundedCount: number;
  returnExemptNoRefundCount: number;
  returnRejectedCount: number;
  returnWithdrawnCount: number;
  systemIssueConfirmedCount: number;
  systemIssueRejectedCount: number;
  openPendingCount?: number;
  categoryBreakdown: CategoryReturnStat[];
  resolvedCases: ReturnReportRow[];
}

export interface DashboardStats {
  totalRevenueVnd: number;
  totalGmvVnd: number;
  escrowBalanceVnd: number;
  totalUsers: number;
  newUsersThisMonth: number;
  disputeRate: number;
  disputeCount: number;
  revenueChangePercent: number;
  trendLabel: string;
  monthlyRevenue: MonthlyRevenuePoint[];
  userGrowth: MonthlyUserGrowthPoint[];
  topSearchedItems: SearchTrendItem[];
  leastSearchedItems: SearchTrendItem[];
  categoryReturns: CategoryReturnStat[];
}


const triggerDownloadBlob = (blob: Blob, filename: string) => {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', filename);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};

export const adminDashboardApi = {
  async getDashboardStats(): Promise<DashboardStats> {
    const res = await authApi.authorizedFetch(`/admin/dashboard/stats`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
      },
    });

    if (!res.ok) {
      throw new Error(`Không thể tải dữ liệu thống kê quản trị (${res.status})`);
    }

    return res.json();
  },

  async getReturnsReport(params: {
    fromDate?: string;
    toDate?: string;
    timezone?: string;
    caseType?: 'ALL' | 'RETURN' | 'SYSTEM_ISSUE';
    categories?: string[];
  }): Promise<ReturnsReportResponse> {
    const qs = new URLSearchParams();
    if (params.fromDate) qs.set('fromDate', params.fromDate);
    if (params.toDate) qs.set('toDate', params.toDate);
    if (params.timezone) qs.set('timezone', params.timezone);
    if (params.caseType) qs.set('caseType', params.caseType);
    params.categories?.forEach((c) => qs.append('categories', c));

    const res = await authApi.authorizedFetch(`/admin/dashboard/returns-report?${qs.toString()}`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
    });
    if (!res.ok) {
      const body = (await res.json().catch(() => ({}))) as { message?: string };
      throw new Error(body.message || `Chưa tổng hợp được dữ liệu thống kê (${res.status}).`);
    }
    return res.json() as Promise<ReturnsReportResponse>;
  },

  async downloadRevenueCsv(): Promise<void> {
    const res = await authApi.authorizedFetch(`/admin/dashboard/export/revenue`);
    if (!res.ok) throw new Error('Không thể xuất báo cáo doanh thu');
    const blob = await res.blob();
    triggerDownloadBlob(blob, 'bao-cao-doanh-thu-phi-san.csv');
  },

  async downloadUserGrowthCsv(): Promise<void> {
    const res = await authApi.authorizedFetch(`/admin/dashboard/export/users`);
    if (!res.ok) throw new Error('Không thể xuất báo cáo người dùng');
    const blob = await res.blob();
    triggerDownloadBlob(blob, 'bao-cao-so-luong-nguoi-dung.csv');
  },

  async downloadSearchTrendsCsv(): Promise<void> {
    const res = await authApi.authorizedFetch(`/admin/dashboard/export/searches`);
    if (!res.ok) throw new Error('Không thể xuất dữ liệu tìm kiếm');
    const blob = await res.blob();
    triggerDownloadBlob(blob, 'thong-ke-mat-hang-tim-mua-nhieu-it.csv');
  },

  async downloadCategoryReturnsCsv(
    categories?: string[],
    options?: {
      fromDate?: string;
      toDate?: string;
      timezone?: string;
      caseType?: string;
      cutoffAt?: string;
    },
  ): Promise<void> {
    const params = new URLSearchParams();
    if (categories && categories.length > 0) {
      categories.forEach((cat) => params.append('categories', cat));
    }
    if (options?.fromDate) params.set('fromDate', options.fromDate);
    if (options?.toDate) params.set('toDate', options.toDate);
    if (options?.timezone) params.set('timezone', options.timezone);
    if (options?.caseType) params.set('caseType', options.caseType);
    if (options?.cutoffAt) params.set('cutoffAt', options.cutoffAt);

    const qs = params.toString();
    const res = await authApi.authorizedFetch(`/admin/dashboard/export/returns${qs ? `?${qs}` : ''}`);
    if (!res.ok) throw new Error('Chưa xuất được tệp báo cáo hoàn trả.');
    const blob = await res.blob();
    triggerDownloadBlob(blob, 'thong-ke-hang-bi-tra-va-khieu-nai.csv');
  },
};
