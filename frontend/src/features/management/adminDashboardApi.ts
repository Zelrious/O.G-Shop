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

const API_BASE = '/api/v1/admin/dashboard';

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
    const res = await fetch(`${API_BASE}/stats`, {
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

  async downloadRevenueCsv(): Promise<void> {
    const res = await fetch(`${API_BASE}/export/revenue`);
    if (!res.ok) throw new Error('Không thể xuất báo cáo doanh thu');
    const blob = await res.blob();
    triggerDownloadBlob(blob, 'bao-cao-doanh-thu-phi-san.csv');
  },

  async downloadUserGrowthCsv(): Promise<void> {
    const res = await fetch(`${API_BASE}/export/users`);
    if (!res.ok) throw new Error('Không thể xuất báo cáo người dùng');
    const blob = await res.blob();
    triggerDownloadBlob(blob, 'bao-cao-so-luong-nguoi-dung.csv');
  },

  async downloadSearchTrendsCsv(): Promise<void> {
    const res = await fetch(`${API_BASE}/export/searches`);
    if (!res.ok) throw new Error('Không thể xuất dữ liệu tìm kiếm');
    const blob = await res.blob();
    triggerDownloadBlob(blob, 'thong-ke-mat-hang-tim-mua-nhieu-it.csv');
  },

  async downloadCategoryReturnsCsv(categories?: string[]): Promise<void> {
    let url = `${API_BASE}/export/returns`;
    if (categories && categories.length > 0) {
      const params = new URLSearchParams();
      categories.forEach((cat) => params.append('categories', cat));
      url += `?${params.toString()}`;
    }
    const res = await fetch(url);
    if (!res.ok) throw new Error('Không thể xuất báo cáo hoàn trả');
    const blob = await res.blob();
    triggerDownloadBlob(blob, 'thong-ke-hang-bi-tra-va-khieu-nai.csv');
  },
};
