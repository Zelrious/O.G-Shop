import { Badge, Button } from '../../shared/components';
import React, { useState, useEffect } from 'react';
import { useManagementStore } from '../../features/management/useManagementStore';
import { adminDashboardApi, DashboardStats } from '../../features/management/adminDashboardApi';
import { ConsoleIcons } from '../../shared/layout/ConsoleIcons';
import {
  TableActionDropdown,
  BulkActionBar,
  TableCheckbox,
} from '../../shared/layout/ConsoleTableActions';

export const AdminDashboardPage: React.FC = () => {
  const {
    exportUserDataCsv,
    exportRevenueDataCsv,
    exportTopSearchCsv,
    exportReturnComplaintCsv,
  } = useManagementStore();

  const [stats, setStats] = useState<DashboardStats>({
    totalRevenueVnd: 254750000,
    totalGmvVnd: 10190000000,
    escrowBalanceVnd: 1850000000,
    totalUsers: 3890,
    newUsersThisMonth: 210,
    disputeRate: 2.7,
    disputeCount: 28,
    revenueChangePercent: -56.6,
    trendLabel: 'Giảm 56,6%',
    monthlyRevenue: [
      { month: 'T1', gmv: 450, revenue: 11.25, orderCount: 140 },
      { month: 'T2', gmv: 520, revenue: 13.0, orderCount: 165 },
      { month: 'T3', gmv: 680, revenue: 17.0, orderCount: 210 },
      { month: 'T4', gmv: 790, revenue: 19.75, orderCount: 245 },
      { month: 'T5', gmv: 920, revenue: 23.0, orderCount: 290 },
      { month: 'T6', gmv: 1150, revenue: 28.75, orderCount: 350 },
      { month: 'T7', gmv: 1340, revenue: 33.5, orderCount: 410 },
      { month: 'T8', gmv: 1620, revenue: 40.5, orderCount: 490 },
      { month: 'T9', gmv: 1890, revenue: 47.25, orderCount: 560 },
      { month: 'T10', gmv: 820, revenue: 20.5, orderCount: 240 },
    ],
    userGrowth: [
      { month: 'T1', newUsers: 120, totalUsers: 1200 },
      { month: 'T2', newUsers: 150, totalUsers: 1350 },
      { month: 'T3', newUsers: 190, totalUsers: 1540 },
      { month: 'T4', newUsers: 220, totalUsers: 1760 },
      { month: 'T5', newUsers: 260, totalUsers: 2020 },
      { month: 'T6', newUsers: 310, totalUsers: 2330 },
      { month: 'T7', newUsers: 380, totalUsers: 2710 },
      { month: 'T8', newUsers: 450, totalUsers: 3160 },
      { month: 'T9', newUsers: 520, totalUsers: 3680 },
      { month: 'T10', newUsers: 210, totalUsers: 3890 },
    ],
    topSearchedItems: [
      { name: 'iPhone 13 / 14 cũ 99%', searches: 14850, orders: 312, rate: '2.1%' },
      { name: 'Máy ảnh Fujifilm X-T series', searches: 11200, orders: 185, rate: '1.6%' },
      { name: 'Đồng hồ cơ vintage Nhật Bản', searches: 8900, orders: 142, rate: '1.6%' },
      { name: 'Tai nghe chống ồn Sony / Bose', searches: 7400, orders: 160, rate: '2.1%' },
      { name: 'Đồ da thủ công cao cấp', searches: 6100, orders: 118, rate: '1.9%' },
    ],
    leastSearchedItems: [
      { name: 'Băng đĩa cassette cũ', searches: 42, note: 'Xu hướng giảm 40%' },
      { name: 'Đầu máy VCR nội địa', searches: 56, note: 'Khó tiếp cận phụ tùng' },
      { name: 'Máy tính bỏ túi cũ', searches: 78, note: 'Nhu cầu thấp ngoài mùa thi' },
      { name: 'Tivi CRT đời cũ', searches: 19, note: 'Phí ship cồng kềnh cao' },
      { name: 'Mô hình giấy lắp ráp thủ công', searches: 92, note: 'Thị trường ngách' },
    ],
    categoryReturns: [
      { category: 'Điện thoại & Máy tính bảng', totalOrders: 420, returnCount: 14, complaintCount: 3, returnRate: '3.3%', primaryIssue: 'Pin chai hơn mô tả' },
      { category: 'Máy ảnh & Quang học', totalOrders: 290, returnCount: 11, complaintCount: 4, returnRate: '3.7%', primaryIssue: 'Nấm mốc kính ngắm / lens' },
      { category: 'Âm thanh & Tai nghe', totalOrders: 310, returnCount: 8, complaintCount: 2, returnRate: '2.5%', primaryIssue: 'Nứt khớp gập / pin sạc lỗi' },
      { category: 'Đồng hồ & Phụ kiện', totalOrders: 180, returnCount: 3, complaintCount: 1, returnRate: '1.6%', primaryIssue: 'Sai số chạy chậm mỗi ngày' },
      { category: 'Thời trang & Túi vintage', totalOrders: 250, returnCount: 5, complaintCount: 1, returnRate: '2.0%', primaryIssue: 'Vết ố lót vải bên trong' },
    ],
  });

  useEffect(() => {
    let cancelled = false;
    const loadStats = async () => {
      try {
        const data = await adminDashboardApi.getDashboardStats();
        if (!cancelled && data) {
          setStats(data);
        }
      } catch {
        // Fallback to baseline default data
      }
    };
    loadStats();
    return () => {
      cancelled = true;
    };
  }, []);

  // Multi-select for Category Returns Table
  const [selectedCategories, setSelectedCategories] = useState<Set<string>>(new Set());

  // Interactive Hover states for Line Charts
  const [hoveredRevenueIdx, setHoveredRevenueIdx] = useState<number | null>(null);
  const [hoveredUserIdx, setHoveredUserIdx] = useState<number | null>(null);

  const monthlyRevenue = stats.monthlyRevenue;
  const userGrowth = stats.userGrowth;
  const topSearchedItems = stats.topSearchedItems;
  const leastSearchedItems = stats.leastSearchedItems;
  const categoryReturns = stats.categoryReturns;

  /** Triggers client-side fallback CSV first, then attempts authenticated backend export when available. */
  const runCsvExport = async (
    localFallback: () => void,
    remoteDownload: () => Promise<void>
  ) => {
    localFallback();
    try {
      await remoteDownload();
    } catch {
      // Client-side CSV export has already run; ignore backend failure in offline/test environments
    }
  };

  const handleExportRevenue = () =>
    runCsvExport(exportRevenueDataCsv, () => adminDashboardApi.downloadRevenueCsv());

  const handleExportUsers = () =>
    runCsvExport(exportUserDataCsv, () => adminDashboardApi.downloadUserGrowthCsv());

  const handleExportTopSearch = () =>
    runCsvExport(exportTopSearchCsv, () => adminDashboardApi.downloadSearchTrendsCsv());

  const handleExportReturnComplaint = () => {
    const selectedList = selectedCategories.size > 0 ? Array.from(selectedCategories) : undefined;
    return runCsvExport(exportReturnComplaintCsv, () =>
      adminDashboardApi.downloadCategoryReturnsCsv(selectedList)
    );
  };

  const isAllCategoriesSelected =
    categoryReturns.length > 0 && selectedCategories.size === categoryReturns.length;
  const isSomeCategoriesSelected =
    selectedCategories.size > 0 && !isAllCategoriesSelected;

  const handleToggleAllCategories = () => {
    if (isAllCategoriesSelected) {
      setSelectedCategories(new Set());
    } else {
      setSelectedCategories(new Set(categoryReturns.map((c) => c.category)));
    }
  };

  const handleToggleCategory = (catName: string) => {
    const next = new Set(selectedCategories);
    if (next.has(catName)) {
      next.delete(catName);
    } else {
      next.add(catName);
    }
    setSelectedCategories(next);
  };

  const latestRevenue = monthlyRevenue.at(-1)!;
  const previousRevenue = monthlyRevenue.at(-2)!;

  // Chart Layout Calculations for Revenue Line Chart (Polyline)
  const CHART_WIDTH = 680;
  const CHART_HEIGHT = 200;
  const PADDING_TOP = 25;
  const PADDING_BOTTOM = 35;
  const PADDING_LEFT = 42;
  const PADDING_RIGHT = 32;

  const usableWidth = CHART_WIDTH - PADDING_LEFT - PADDING_RIGHT;
  const usableHeight = CHART_HEIGHT - PADDING_TOP - PADDING_BOTTOM;
  const chartBottom = CHART_HEIGHT - PADDING_BOTTOM;

  const maxRevenue = Math.max(...monthlyRevenue.map((d) => d.revenue));
  const revenueCeil = Math.ceil((maxRevenue * 1.15) / 10) * 10 || 50;

  const revenuePoints = monthlyRevenue.map((item, idx) => {
    const x = PADDING_LEFT + (idx / Math.max(1, monthlyRevenue.length - 1)) * usableWidth;
    const y = PADDING_TOP + (1 - item.revenue / revenueCeil) * usableHeight;
    return { x, y, item, idx };
  });

  const revenuePathD = revenuePoints
    .map((p, idx) => `${idx === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`)
    .join(' ');

  const revenueAreaD = `${revenuePathD} L ${revenuePoints[revenuePoints.length - 1].x.toFixed(1)} ${chartBottom} L ${revenuePoints[0].x.toFixed(1)} ${chartBottom} Z`;

  const peakRevenueIdx = monthlyRevenue.reduce(
    (maxIdx, curr, idx, arr) => (curr.revenue > arr[maxIdx].revenue ? idx : maxIdx),
    0
  );

  const activeRevenuePoint = hoveredRevenueIdx !== null ? revenuePoints[hoveredRevenueIdx] : null;
  const prevRevenueItem =
    hoveredRevenueIdx !== null && hoveredRevenueIdx > 0
      ? monthlyRevenue[hoveredRevenueIdx - 1]
      : null;
  const revenueDelta =
    activeRevenuePoint && prevRevenueItem
      ? ((activeRevenuePoint.item.revenue - prevRevenueItem.revenue) / prevRevenueItem.revenue) * 100
      : null;

  // Chart Layout Calculations for User Growth Line Chart (Polyline)
  const maxUserTotal = Math.max(...userGrowth.map((d) => d.totalUsers));
  const userCeil = Math.ceil((maxUserTotal * 1.15) / 500) * 500 || 4000;

  const userPoints = userGrowth.map((item, idx) => {
    const x = PADDING_LEFT + (idx / Math.max(1, userGrowth.length - 1)) * usableWidth;
    const y = PADDING_TOP + (1 - item.totalUsers / userCeil) * usableHeight;
    return { x, y, item, idx };
  });

  const userPathD = userPoints
    .map((p, idx) => `${idx === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`)
    .join(' ');

  const userAreaD = `${userPathD} L ${userPoints[userPoints.length - 1].x.toFixed(1)} ${chartBottom} L ${userPoints[0].x.toFixed(1)} ${chartBottom} Z`;

  const peakUserIdx = userGrowth.length - 1;
  const activeUserPoint = hoveredUserIdx !== null ? userPoints[hoveredUserIdx] : null;
  const prevUserItem =
    hoveredUserIdx !== null && hoveredUserIdx > 0 ? userGrowth[hoveredUserIdx - 1] : null;
  const userDelta =
    activeUserPoint && prevUserItem
      ? ((activeUserPoint.item.totalUsers - prevUserItem.totalUsers) / prevUserItem.totalUsers) * 100
      : null;

  return (
    <div className="og-admin-reports">
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', margin: '0 0 6px', color: 'var(--og-color-text-primary)', fontWeight: 800 }}>
            Tổng quan doanh thu & Báo cáo quản trị
          </h1>
          <p style={{ margin: 0, color: 'var(--og-color-text-secondary)', fontSize: '0.92rem' }}>
            Báo cáo tăng trưởng người dùng, doanh thu phí sàn, phân tích xu hướng mua sắm và tỷ lệ hàng hoàn trả.
          </p>
        </div>
      </div>

      <section className="og-report-overview" aria-label="Tổng quan báo cáo">
        <article className="og-report-revenue" aria-labelledby="revenue-title">
          <header><div><span className="og-report-caption">DOANH THU PHÍ SÀN</span><h2 id="revenue-title">Tổng doanh thu phí sàn (Năm 2026)</h2></div><Button variant="outline" size="sm" onClick={handleExportRevenue}><ConsoleIcons.Download size={16} />Kết xuất báo cáo doanh thu (CSV)</Button></header>
          <strong className="og-report-revenue__value">{stats.totalRevenueVnd.toLocaleString('vi-VN')} <small>VNĐ</small></strong>
          <div className="og-report-revenue__insight"><Badge variant={stats.revenueChangePercent < 0 ? 'danger' : 'success'}>{stats.trendLabel}</Badge><span>{latestRevenue.month}: {latestRevenue.revenue.toLocaleString('vi-VN')} triệu đồng, so với {previousRevenue.revenue.toLocaleString('vi-VN')} triệu đồng ở {previousRevenue.month}.</span></div>
          <figure
            className="og-report-chart"
            aria-label="Doanh thu phí sàn theo tháng, đơn vị triệu đồng"
            style={{ position: 'relative', margin: '20px 0 12px', width: '100%' }}
          >
            <div style={{ position: 'relative', width: '100%', overflow: 'visible' }}>
              <svg
                viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
                style={{ width: '100%', height: 'auto', display: 'block', overflow: 'visible' }}
              >
                <defs>
                  <linearGradient id="ogRevenueGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="var(--og-color-primary, #1d4d38)" stopOpacity="0.22" />
                    <stop offset="100%" stopColor="var(--og-color-primary, #1d4d38)" stopOpacity="0.0" />
                  </linearGradient>
                </defs>

                {/* Y-Axis Grid lines & labels */}
                {[0, 0.5, 1].map((ratio, i) => {
                  const yVal = PADDING_TOP + (1 - ratio) * usableHeight;
                  const labelVal = Math.round(ratio * revenueCeil);
                  return (
                    <g key={i}>
                      <line
                        x1={PADDING_LEFT}
                        y1={yVal}
                        x2={CHART_WIDTH - PADDING_RIGHT}
                        y2={yVal}
                        stroke="var(--og-color-border)"
                        strokeDasharray="3 3"
                        strokeWidth="1"
                        opacity="0.7"
                      />
                      <text
                        x={PADDING_LEFT - 8}
                        y={yVal + 3.5}
                        textAnchor="end"
                        fontSize="10"
                        fill="var(--og-color-text-secondary)"
                        fontWeight="500"
                      >
                        {labelVal}
                      </text>
                    </g>
                  );
                })}

                {/* Shaded Area under polyline */}
                <path d={revenueAreaD} fill="url(#ogRevenueGrad)" />

                {/* Polyline line */}
                <path
                  d={revenuePathD}
                  fill="none"
                  stroke="var(--og-color-primary, #1d4d38)"
                  strokeWidth="2.75"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />

                {/* Month labels on X-axis */}
                {revenuePoints.map((p, idx) => (
                  <text
                    key={idx}
                    x={p.x}
                    y={CHART_HEIGHT - 10}
                    textAnchor="middle"
                    fontSize="11"
                    fontWeight={hoveredRevenueIdx === idx ? 800 : 600}
                    fill={hoveredRevenueIdx === idx ? 'var(--og-color-primary, #1d4d38)' : 'var(--og-color-text-secondary)'}
                  >
                    {p.item.month}
                  </text>
                ))}

                {/* Data points (dots) */}
                {revenuePoints.map((p, idx) => (
                  <circle
                    key={idx}
                    cx={p.x}
                    cy={p.y}
                    r={hoveredRevenueIdx === idx ? 5.5 : 3.5}
                    fill="#ffffff"
                    stroke="var(--og-color-primary, #1d4d38)"
                    strokeWidth={hoveredRevenueIdx === idx ? 3 : 2}
                  />
                ))}

                {/* Active Hover vertical guide line & glow */}
                {activeRevenuePoint && (
                  <g pointerEvents="none">
                    <line
                      x1={activeRevenuePoint.x}
                      y1={PADDING_TOP}
                      x2={activeRevenuePoint.x}
                      y2={chartBottom}
                      stroke="var(--og-color-primary, #1d4d38)"
                      strokeWidth="1.5"
                      strokeDasharray="4 4"
                      opacity="0.8"
                    />
                    <circle
                      cx={activeRevenuePoint.x}
                      cy={activeRevenuePoint.y}
                      r="10"
                      fill="var(--og-color-primary, #1d4d38)"
                      opacity="0.2"
                    />
                    <circle
                      cx={activeRevenuePoint.x}
                      cy={activeRevenuePoint.y}
                      r="5.5"
                      fill="var(--og-color-primary, #1d4d38)"
                      stroke="#ffffff"
                      strokeWidth="2.5"
                    />
                  </g>
                )}

                {/* Transparent hit boxes for seamless hover */}
                {revenuePoints.map((p, idx) => {
                  const sliceWidth = usableWidth / revenuePoints.length;
                  return (
                    <rect
                      key={idx}
                      x={p.x - sliceWidth / 2}
                      y={0}
                      width={sliceWidth}
                      height={CHART_HEIGHT}
                      fill="transparent"
                      style={{ cursor: 'pointer' }}
                      onMouseEnter={() => setHoveredRevenueIdx(idx)}
                      onMouseLeave={() => setHoveredRevenueIdx(null)}
                    />
                  );
                })}
              </svg>

              {/* Peak indicator badge (when not hovering) */}
              {hoveredRevenueIdx === null && revenuePoints[peakRevenueIdx] && (
                <div
                  style={{
                    position: 'absolute',
                    left: `${(revenuePoints[peakRevenueIdx].x / CHART_WIDTH) * 100}%`,
                    top: `${(revenuePoints[peakRevenueIdx].y / CHART_HEIGHT) * 100}%`,
                    transform: 'translate(-50%, -130%)',
                    background: 'var(--og-color-accent-gold, #c6933a)',
                    color: '#ffffff',
                    borderRadius: '16px',
                    padding: '2px 9px',
                    fontSize: '0.72rem',
                    fontWeight: 700,
                    boxShadow: '0 2px 8px rgba(198, 147, 58, 0.4)',
                    pointerEvents: 'none',
                    whiteSpace: 'nowrap',
                  }}
                >
                  Đỉnh: {revenuePoints[peakRevenueIdx].item.revenue} tr
                </div>
              )}

              {/* Floating interactive tooltip card on hover */}
              {activeRevenuePoint && (
                <div
                  style={{
                    position: 'absolute',
                    left: `${Math.min(Math.max((activeRevenuePoint.x / CHART_WIDTH) * 100, 14), 86)}%`,
                    top: `${Math.max(6, (activeRevenuePoint.y / CHART_HEIGHT) * 100 - 32)}%`,
                    transform: 'translate(-50%, -100%)',
                    background: 'var(--og-color-surface)',
                    border: '1px solid var(--og-color-border)',
                    borderRadius: '8px',
                    padding: '10px 14px',
                    boxShadow: '0 8px 24px rgba(0,0,0,0.12)',
                    pointerEvents: 'none',
                    zIndex: 20,
                    minWidth: '190px',
                    whiteSpace: 'nowrap',
                  }}
                >
                  <div style={{ fontSize: '0.76rem', fontWeight: 700, color: 'var(--og-color-text-secondary)', marginBottom: '4px' }}>
                    Tháng {activeRevenuePoint.item.month} (Năm 2026)
                  </div>
                  <div style={{ fontSize: '1.05rem', fontWeight: 800, color: 'var(--og-color-primary, #1d4d38)', marginBottom: '4px' }}>
                    {activeRevenuePoint.item.revenue.toLocaleString('vi-VN')} <small style={{ fontSize: '0.72rem', color: 'var(--og-color-text-secondary)', fontWeight: 600 }}>triệu VNĐ</small>
                  </div>
                  <div style={{ fontSize: '0.76rem', color: 'var(--og-color-text-secondary)', display: 'flex', justifyContent: 'space-between', gap: '12px' }}>
                    <span>GMV: {activeRevenuePoint.item.gmv.toLocaleString('vi-VN')} tr</span>
                    <span>{activeRevenuePoint.item.orderCount} đơn</span>
                  </div>
                  {revenueDelta !== null && (
                    <div
                      style={{
                        marginTop: '6px',
                        paddingTop: '6px',
                        borderTop: '1px solid var(--og-color-border)',
                        fontSize: '0.74rem',
                        fontWeight: 700,
                        color: revenueDelta >= 0 ? 'var(--og-color-success, #236c3e)' : 'var(--og-color-danger, #c52222)',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '4px',
                      }}
                    >
                      <span>{revenueDelta >= 0 ? `▲ +${revenueDelta.toFixed(1)}%` : `▼ ${revenueDelta.toFixed(1)}%`}</span>
                      <span style={{ fontWeight: 500, color: 'var(--og-color-text-secondary)', fontSize: '0.72rem' }}>
                        so với {prevRevenueItem?.month}
                      </span>
                    </div>
                  )}
                </div>
              )}
            </div>
            <figcaption style={{ color: 'var(--og-color-text-secondary)', fontSize: '0.75rem', marginTop: '10px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>Doanh thu theo tháng · Triệu đồng</span>
              <span style={{ fontSize: '0.74rem', color: 'var(--og-color-text-secondary)' }}>● Biểu đồ đường gấp khúc (Phí sàn trung bình 2,5%)</span>
            </figcaption>
          </figure>
          <p className="og-report-note">Dữ liệu minh họa hiện có · 10 tháng năm 2026 · Phí trung bình 2,5%. Tháng 10 chưa kết thúc; mức giảm chưa phản ánh kết quả cả tháng.</p>
        </article>
        <div className="og-report-secondary">
          <article>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>Tổng giá trị hàng hóa (GMV)</span>
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '3px',
                  padding: '2px 8px',
                  borderRadius: '12px',
                  fontSize: '0.72rem',
                  fontWeight: 700,
                  background: 'var(--og-color-success-bg, #eaf5ed)',
                  color: 'var(--og-color-success, #236c3e)',
                }}
              >
                ▲ +18.4%
              </span>
            </div>
            <strong>
              {stats.totalGmvVnd.toLocaleString('vi-VN')} <small>VNĐ</small>
            </strong>
            <p>Ký quỹ an toàn qua Escrow · Tăng trưởng ổn định</p>
          </article>
          <article>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>Tổng tài khoản người dùng</span>
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '3px',
                  padding: '2px 8px',
                  borderRadius: '12px',
                  fontSize: '0.72rem',
                  fontWeight: 700,
                  background: 'var(--og-color-primary-light, #e8f3ed)',
                  color: 'var(--og-color-primary, #1d4d38)',
                }}
              >
                ▲ +5.7%
              </span>
            </div>
            <strong>{stats.totalUsers.toLocaleString('vi-VN')}</strong>
            <p>+{stats.newUsersThisMonth} tài khoản mới trong tháng 10</p>
          </article>
          <article>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>Tỷ lệ đơn hàng bị khiếu nại</span>
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '3px',
                  padding: '2px 8px',
                  borderRadius: '12px',
                  fontSize: '0.72rem',
                  fontWeight: 700,
                  background: 'var(--og-color-success-bg, #eaf5ed)',
                  color: 'var(--og-color-success, #236c3e)',
                }}
              >
                ▼ -0.8%
              </span>
            </div>
            <strong>{stats.disputeRate}%</strong>
            <p>Giảm 0,8 điểm phần trăm · Dữ liệu minh họa</p>
          </article>
        </div>
      </section>

      {/* SECTION 2: Biểu đồ & Kết xuất báo cáo số lượng người dùng */}
      <div
        style={{
          background: 'var(--og-color-surface)',
          border: '1px solid var(--og-color-border)',
          borderRadius: '12px',
          padding: '24px',
          boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px', marginBottom: '20px' }}>
          <div>
            <h2 style={{ fontSize: '1.18rem', color: 'var(--og-color-text-primary)', margin: '0 0 4px 0', fontWeight: 700 }}>
              Báo cáo tăng trưởng người dùng nền tảng
            </h2>
            <p style={{ margin: 0, fontSize: '0.84rem', color: 'var(--og-color-text-secondary)' }}>
              Theo dõi tốc độ mở rộng người mua & người bán C2C toàn hệ thống
            </p>
          </div>
          <Button variant="outline" onClick={handleExportUsers}>
            <ConsoleIcons.Download size={16} />
            <span>Kết xuất báo cáo người dùng (CSV)</span>
          </Button>
        </div>

        {/* User Growth Polyline Line Chart */}
        <div style={{ position: 'relative', width: '100%', margin: '14px 0 6px' }}>
          <div style={{ position: 'relative', width: '100%', overflow: 'visible' }}>
            <svg
              viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
              style={{ width: '100%', height: 'auto', display: 'block', overflow: 'visible' }}
            >
              <defs>
                <linearGradient id="ogUserGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="var(--og-color-accent-gold, #c6933a)" stopOpacity="0.25" />
                  <stop offset="100%" stopColor="var(--og-color-accent-gold, #c6933a)" stopOpacity="0.0" />
                </linearGradient>
              </defs>

              {/* Y-Axis Grid lines & labels */}
              {[0, 0.5, 1].map((ratio, i) => {
                const yVal = PADDING_TOP + (1 - ratio) * usableHeight;
                const labelVal = Math.round(ratio * userCeil);
                return (
                  <g key={i}>
                    <line
                      x1={PADDING_LEFT}
                      y1={yVal}
                      x2={CHART_WIDTH - PADDING_RIGHT}
                      y2={yVal}
                      stroke="var(--og-color-border)"
                      strokeDasharray="3 3"
                      strokeWidth="1"
                      opacity="0.7"
                    />
                    <text
                      x={PADDING_LEFT - 8}
                      y={yVal + 3.5}
                      textAnchor="end"
                      fontSize="10"
                      fill="var(--og-color-text-secondary)"
                      fontWeight="500"
                    >
                      {labelVal.toLocaleString('vi-VN')}
                    </text>
                  </g>
                );
              })}

              {/* Shaded Area under polyline */}
              <path d={userAreaD} fill="url(#ogUserGrad)" />

              {/* Polyline line */}
              <path
                d={userPathD}
                fill="none"
                stroke="var(--og-color-accent-gold, #c6933a)"
                strokeWidth="2.75"
                strokeLinecap="round"
                strokeLinejoin="round"
              />

              {/* Month labels on X-axis */}
              {userPoints.map((p, idx) => (
                <text
                  key={idx}
                  x={p.x}
                  y={CHART_HEIGHT - 10}
                  textAnchor="middle"
                  fontSize="11"
                  fontWeight={hoveredUserIdx === idx ? 800 : 600}
                  fill={hoveredUserIdx === idx ? 'var(--og-color-accent-gold, #c6933a)' : 'var(--og-color-text-secondary)'}
                >
                  {p.item.month}
                </text>
              ))}

              {/* Data points (dots) */}
              {userPoints.map((p, idx) => (
                <circle
                  key={idx}
                  cx={p.x}
                  cy={p.y}
                  r={hoveredUserIdx === idx ? 5.5 : 3.5}
                  fill="#ffffff"
                  stroke="var(--og-color-accent-gold, #c6933a)"
                  strokeWidth={hoveredUserIdx === idx ? 3 : 2}
                />
              ))}

              {/* Active Hover vertical guide line & glow */}
              {activeUserPoint && (
                <g pointerEvents="none">
                  <line
                    x1={activeUserPoint.x}
                    y1={PADDING_TOP}
                    x2={activeUserPoint.x}
                    y2={chartBottom}
                    stroke="var(--og-color-accent-gold, #c6933a)"
                    strokeWidth="1.5"
                    strokeDasharray="4 4"
                    opacity="0.8"
                  />
                  <circle
                    cx={activeUserPoint.x}
                    cy={activeUserPoint.y}
                    r="10"
                    fill="var(--og-color-accent-gold, #c6933a)"
                    opacity="0.2"
                  />
                  <circle
                    cx={activeUserPoint.x}
                    cy={activeUserPoint.y}
                    r="5.5"
                    fill="var(--og-color-accent-gold, #c6933a)"
                    stroke="#ffffff"
                    strokeWidth="2.5"
                  />
                </g>
              )}

              {/* Transparent hit boxes for seamless hover */}
              {userPoints.map((p, idx) => {
                const sliceWidth = usableWidth / userPoints.length;
                return (
                  <rect
                    key={idx}
                    x={p.x - sliceWidth / 2}
                    y={0}
                    width={sliceWidth}
                    height={CHART_HEIGHT}
                    fill="transparent"
                    style={{ cursor: 'pointer' }}
                    onMouseEnter={() => setHoveredUserIdx(idx)}
                    onMouseLeave={() => setHoveredUserIdx(null)}
                  />
                );
              })}
            </svg>

            {/* Peak indicator badge for User Growth (when not hovering) */}
            {hoveredUserIdx === null && userPoints[peakUserIdx] && (
              <div
                style={{
                  position: 'absolute',
                  left: `${(userPoints[peakUserIdx].x / CHART_WIDTH) * 100}%`,
                  top: `${(userPoints[peakUserIdx].y / CHART_HEIGHT) * 100}%`,
                  transform: 'translate(-50%, -130%)',
                  background: 'var(--og-color-accent-gold, #c6933a)',
                  color: '#ffffff',
                  borderRadius: '16px',
                  padding: '2px 9px',
                  fontSize: '0.72rem',
                  fontWeight: 700,
                  boxShadow: '0 2px 8px rgba(198, 147, 58, 0.4)',
                  pointerEvents: 'none',
                  whiteSpace: 'nowrap',
                }}
              >
                Hiện tại: {userPoints[peakUserIdx].item.totalUsers.toLocaleString()} users
              </div>
            )}

            {/* Floating interactive tooltip card on hover */}
            {activeUserPoint && (
              <div
                style={{
                  position: 'absolute',
                  left: `${Math.min(Math.max((activeUserPoint.x / CHART_WIDTH) * 100, 14), 86)}%`,
                  top: `${Math.max(6, (activeUserPoint.y / CHART_HEIGHT) * 100 - 32)}%`,
                  transform: 'translate(-50%, -100%)',
                  background: 'var(--og-color-surface)',
                  border: '1px solid var(--og-color-border)',
                  borderRadius: '8px',
                  padding: '10px 14px',
                  boxShadow: '0 8px 24px rgba(0,0,0,0.12)',
                  pointerEvents: 'none',
                  zIndex: 20,
                  minWidth: '200px',
                  whiteSpace: 'nowrap',
                }}
              >
                <div style={{ fontSize: '0.76rem', fontWeight: 700, color: 'var(--og-color-text-secondary)', marginBottom: '4px' }}>
                  Tháng {activeUserPoint.item.month} (Năm 2026)
                </div>
                <div style={{ fontSize: '1.05rem', fontWeight: 800, color: 'var(--og-color-gold-text, #8e6216)', marginBottom: '4px' }}>
                  {activeUserPoint.item.totalUsers.toLocaleString('vi-VN')} <small style={{ fontSize: '0.72rem', color: 'var(--og-color-text-secondary)', fontWeight: 600 }}>tổng tài khoản</small>
                </div>
                <div style={{ fontSize: '0.76rem', color: 'var(--og-color-text-secondary)', display: 'flex', justifyContent: 'space-between', gap: '12px' }}>
                  <span>Tài khoản mới:</span>
                  <span style={{ fontWeight: 700, color: 'var(--og-color-text-primary)' }}>+{activeUserPoint.item.newUsers} users</span>
                </div>
                {userDelta !== null && (
                  <div
                    style={{
                      marginTop: '6px',
                      paddingTop: '6px',
                      borderTop: '1px solid var(--og-color-border)',
                      fontSize: '0.74rem',
                      fontWeight: 700,
                      color: userDelta >= 0 ? 'var(--og-color-success, #236c3e)' : 'var(--og-color-danger, #c52222)',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '4px',
                    }}
                  >
                    <span>{userDelta >= 0 ? `▲ +${userDelta.toFixed(1)}%` : `▼ ${userDelta.toFixed(1)}%`}</span>
                    <span style={{ fontWeight: 500, color: 'var(--og-color-text-secondary)', fontSize: '0.72rem' }}>
                      tăng trưởng so với {prevUserItem?.month}
                    </span>
                  </div>
                )}
              </div>
            )}
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '10px', fontSize: '0.75rem', color: 'var(--og-color-text-secondary)' }}>
            <span>Đơn vị: Số lượng người dùng lũy kế</span>
            <span>● Biểu đồ đường gấp khúc tốc độ mở rộng người dùng C2C</span>
          </div>
        </div>
      </div>

      {/* SECTION 3: Thống kê tìm kiếm nhiều nhất & ít nhất */}
      <div
        style={{
          background: 'var(--og-color-surface)',
          border: '1px solid var(--og-color-border)',
          borderRadius: '12px',
          padding: '24px',
          boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px', marginBottom: '20px' }}>
          <div>
            <h2 style={{ fontSize: '1.18rem', color: 'var(--og-color-text-primary)', margin: '0 0 4px 0', fontWeight: 700 }}>
              Thống kê xu hướng tìm kiếm mặt hàng (Search Trends)
            </h2>
            <p style={{ margin: 0, fontSize: '0.84rem', color: 'var(--og-color-text-secondary)' }}>
              Phát hiện thị hiếu người mua đồ cũ để định hướng chiến dịch voucher và marketing
            </p>
          </div>
          <Button variant="outline" onClick={handleExportTopSearch}>
            <ConsoleIcons.Download size={16} />
            <span>Kết xuất dữ liệu tìm kiếm (CSV)</span>
          </Button>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 320px), 1fr))', gap: '20px' }}>
          {/* Top 5 Most Searched */}
          <div style={{ background: 'var(--og-color-bg)', padding: '18px', borderRadius: '10px', border: '1px solid var(--og-color-border)' }}>
            <div style={{ fontSize: '0.92rem', fontWeight: 700, color: 'var(--og-color-primary)', marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <ConsoleIcons.TrendingUp size={18} />
              <span>Top 5 mặt hàng được tìm kiếm nhiều nhất</span>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {topSearchedItems.map((item, index) => (
                <div key={index} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0', borderBottom: '1px solid var(--og-color-border)' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <span style={{ width: '22px', height: '22px', borderRadius: '50%', background: 'var(--og-color-surface-subtle)', color: 'var(--og-color-text-secondary)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.75rem', fontWeight: 800 }}>
                      {index + 1}
                    </span>
                    <span style={{ fontSize: '0.88rem', color: 'var(--og-color-text-primary)', fontWeight: 600 }}>{item.name}</span>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: '0.86rem', fontWeight: 700, color: 'var(--og-color-primary)' }}>{item.searches.toLocaleString()} lượt</div>
                    <div style={{ fontSize: '0.74rem', color: 'var(--og-color-text-secondary)' }}>{item.orders} đơn (CR: {item.rate})</div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Top 5 Least Searched */}
          <div style={{ background: 'var(--og-color-bg)', padding: '18px', borderRadius: '10px', border: '1px solid var(--og-color-border)' }}>
            <div style={{ fontSize: '0.92rem', fontWeight: 700, color: 'var(--og-color-text-secondary)', marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <ConsoleIcons.AlertTriangle size={18} />
              <span>Top 5 mặt hàng ít được tìm kiếm nhất</span>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {leastSearchedItems.map((item, index) => (
                <div key={index} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0', borderBottom: '1px solid var(--og-color-border)' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <span style={{ width: '22px', height: '22px', borderRadius: '50%', background: 'var(--og-color-surface-subtle)', color: 'var(--og-color-text-secondary)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.75rem', fontWeight: 800 }}>
                      {index + 1}
                    </span>
                    <span style={{ fontSize: '0.88rem', color: 'var(--og-color-text-secondary)' }}>{item.name}</span>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: '0.86rem', fontWeight: 600, color: 'var(--og-color-text-secondary)' }}>{item.searches} lượt</div>
                    <div style={{ fontSize: '0.74rem', color: 'var(--og-color-text-secondary)' }}>{item.note}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* SECTION 4: Thống kê số lượng hàng bị trả và khiếu nại (With Multi-select and 3-dots action) */}
      <div
        style={{
          background: 'var(--og-color-surface)',
          border: '1px solid var(--og-color-border)',
          borderRadius: '12px',
          padding: '24px',
          boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px', marginBottom: '18px' }}>
          <div>
            <h2 style={{ fontSize: '1.18rem', color: 'var(--og-color-text-primary)', margin: '0 0 4px 0', fontWeight: 700 }}>
              Thống kê số lượng hàng bị trả & Khiếu nại theo danh mục
            </h2>
            <p style={{ margin: 0, fontSize: '0.84rem', color: 'var(--og-color-text-secondary)' }}>
              Giúp quản trị viên theo dõi tỷ lệ tranh chấp và chất lượng hàng hóa được kiểm duyệt
            </p>
          </div>
          <Button variant="outline" onClick={handleExportReturnComplaint}>
            <ConsoleIcons.Download size={16} />
            <span>Kết xuất báo cáo khiếu nại (CSV)</span>
          </Button>
        </div>

        {/* Bulk action bar for selected categories */}
        <BulkActionBar
          selectedCount={selectedCategories.size}
          totalCount={categoryReturns.length}
          onClearSelection={() => setSelectedCategories(new Set())}
          actions={[
            {
              label: 'Kết xuất dữ liệu đã chọn',
              icon: <ConsoleIcons.Download size={15} />,
              variant: 'primary',
              onClick: handleExportReturnComplaint,
            },
          ]}
        />

        <div style={{ overflowX: 'auto', border: '1px solid var(--og-color-border)', borderRadius: '8px' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.88rem' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--og-color-border)', color: 'var(--og-color-text-secondary)', background: 'var(--og-color-bg)' }}>
                <th style={{ padding: '12px 14px', width: '40px', textAlign: 'center', whiteSpace: 'nowrap' }}>
                  <TableCheckbox
                    checked={isAllCategoriesSelected}
                    indeterminate={isSomeCategoriesSelected}
                    onChange={handleToggleAllCategories}
                    ariaLabel="Chọn tất cả danh mục"
                  />
                </th>
                <th style={{ padding: '12px 14px', whiteSpace: 'nowrap' }}>Ngành hàng / Danh mục</th>
                <th style={{ padding: '12px 14px', whiteSpace: 'nowrap' }}>Đơn bán</th>
                <th style={{ padding: '12px 14px', whiteSpace: 'nowrap' }}>Yêu cầu trả hàng</th>
                <th style={{ padding: '12px 14px', whiteSpace: 'nowrap' }}>Vụ khiếu nại KTV</th>
                <th style={{ padding: '12px 14px', whiteSpace: 'nowrap' }}>Tỷ lệ hoàn hàng</th>
                <th style={{ padding: '12px 14px', whiteSpace: 'nowrap' }}>Nguyên nhân phản ánh chính</th>
                <th style={{ padding: '12px 14px', textAlign: 'right', minWidth: '95px', whiteSpace: 'nowrap' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {categoryReturns.map((row, idx) => {
                const isSelected = selectedCategories.has(row.category);
                return (
                  <tr
                    key={idx}
                    style={{
                      borderBottom: '1px solid var(--og-color-surface-subtle)',
                      background: isSelected ? 'var(--og-color-primary-light)' : 'transparent',
                    }}
                  >
                    <td style={{ padding: '12px 14px', textAlign: 'center' }}>
                      <TableCheckbox
                        checked={isSelected}
                        onChange={() => handleToggleCategory(row.category)}
                        ariaLabel={`Chọn ngành hàng ${row.category}`}
                      />
                    </td>
                    <td style={{ padding: '12px 14px', fontWeight: 600, color: 'var(--og-color-text-primary)' }}>
                      {row.category}
                    </td>
                    <td style={{ padding: '12px 14px', color: 'var(--og-color-text-secondary)' }}>{row.totalOrders} đơn</td>
                    <td style={{ padding: '12px 14px', color: 'var(--og-color-text-secondary)', fontWeight: 700 }}>
                      {row.returnCount} đơn
                    </td>
                    <td style={{ padding: '12px 14px', color: 'var(--og-color-text-secondary)', fontWeight: 700 }}>
                      {row.complaintCount} vụ
                    </td>
                    <td style={{ padding: '12px 14px' }}>
                      <span
                        style={{
                          padding: '3px 8px',
                          borderRadius: '12px',
                          fontSize: '0.75rem',
                          fontWeight: 700,
                          background: 'var(--og-color-surface-subtle)',
                          color: 'var(--og-color-text-secondary)',
                          border: '1px solid var(--og-color-border)',
                        }}
                      >
                        {row.returnRate}
                      </span>
                    </td>
                    <td style={{ padding: '12px 14px', color: 'var(--og-color-text-secondary)', fontSize: '0.84rem' }}>
                      {row.primaryIssue}
                    </td>
                    <td style={{ padding: '12px 14px', textAlign: 'right' }}>
                      <TableActionDropdown
                        ariaLabel={`Thao tác danh mục ${row.category}`}
                        items={[
                          {
                            label: 'Kết xuất báo cáo ngành',
                            icon: <ConsoleIcons.Download size={16} />,
                            onClick: handleExportReturnComplaint,
                          },
                          {
                            label: 'Gửi cảnh báo siết duyệt',
                            icon: <ConsoleIcons.AlertTriangle size={16} />,
                            variant: 'danger',
                            onClick: () => alert(`Đã gửi cảnh báo siết chặt quy trình kiểm duyệt ngành ${row.category}`),
                          },
                        ]}
                      />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
