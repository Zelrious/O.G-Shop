import React, { useState } from 'react';
import { useManagementStore } from '../../features/management/useManagementStore';
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

  // Multi-select for Category Returns Table
  const [selectedCategories, setSelectedCategories] = useState<Set<string>>(new Set());

  // Monthly Revenue & GMV Data (10 months)
  const monthlyRevenue = [
    { month: 'T1', gmv: 450, revenue: 11.25 },
    { month: 'T2', gmv: 520, revenue: 13.0 },
    { month: 'T3', gmv: 680, revenue: 17.0 },
    { month: 'T4', gmv: 790, revenue: 19.75 },
    { month: 'T5', gmv: 920, revenue: 23.0 },
    { month: 'T6', gmv: 1150, revenue: 28.75 },
    { month: 'T7', gmv: 1340, revenue: 33.5 },
    { month: 'T8', gmv: 1620, revenue: 40.5 },
    { month: 'T9', gmv: 1890, revenue: 47.25 },
    { month: 'T10', gmv: 820, revenue: 20.5 },
  ];

  // User Growth Data
  const userGrowth = [
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
  ];

  // Top Most and Least Searched Items
  const topSearchedItems = [
    { name: 'iPhone 13 / 14 cũ 99%', searches: 14850, orders: 312, rate: '2.1%' },
    { name: 'Máy ảnh Fujifilm X-T series', searches: 11200, orders: 185, rate: '1.6%' },
    { name: 'Đồng hồ cơ vintage Nhật Bản', searches: 8900, orders: 142, rate: '1.6%' },
    { name: 'Tai nghe chống ồn Sony / Bose', searches: 7400, orders: 160, rate: '2.1%' },
    { name: 'Đồ da thủ công cao cấp', searches: 6100, orders: 118, rate: '1.9%' },
  ];

  const leastSearchedItems = [
    { name: 'Băng đĩa cassette cũ', searches: 42, note: 'Xu hướng giảm 40%' },
    { name: 'Đầu máy VCR nội địa', searches: 56, note: 'Khó tiếp cận phụ tùng' },
    { name: 'Máy tính bỏ túi cũ', searches: 78, note: 'Nhu cầu thấp ngoài mùa thi' },
    { name: 'Tivi CRT đời cũ', searches: 19, note: 'Phí ship cồng kềnh cao' },
    { name: 'Mô hình giấy lắp ráp thủ công', searches: 92, note: 'Thị trường ngách' },
  ];

  // Return and Complaint Stats by Category
  const categoryReturns = [
    { category: 'Điện thoại & Máy tính bảng', totalOrders: 420, returnCount: 14, complaintCount: 3, returnRate: '3.3%', primaryIssue: 'Pin chai hơn mô tả' },
    { category: 'Máy ảnh & Quang học', totalOrders: 290, returnCount: 11, complaintCount: 4, returnRate: '3.7%', primaryIssue: 'Nấm mốc kính ngắm / lens' },
    { category: 'Âm thanh & Tai nghe', totalOrders: 310, returnCount: 8, complaintCount: 2, returnRate: '2.5%', primaryIssue: 'Nứt khớp gập / pin sạc lỗi' },
    { category: 'Đồng hồ & Phụ kiện', totalOrders: 180, returnCount: 3, complaintCount: 1, returnRate: '1.6%', primaryIssue: 'Sai số chạy chậm mỗi ngày' },
    { category: 'Thời trang & Túi vintage', totalOrders: 250, returnCount: 5, complaintCount: 1, returnRate: '2.0%', primaryIssue: 'Vết ố lót vải bên trong' },
  ];

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

  const maxRevenue = Math.max(...monthlyRevenue.map((d) => d.revenue));
  const maxUserTotal = Math.max(...userGrowth.map((d) => d.totalUsers));

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', margin: '0 0 6px', color: '#0f172a', fontWeight: 800 }}>
            Tổng quan doanh thu & Báo cáo quản trị
          </h1>
          <p style={{ margin: 0, color: '#64748b', fontSize: '0.92rem' }}>
            Báo cáo tăng trưởng người dùng, doanh thu phí sàn, phân tích xu hướng mua sắm và tỷ lệ hàng hoàn trả.
          </p>
        </div>
      </div>

      {/* KPI Overview Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '16px' }}>
        <div style={{ background: '#ffffff', padding: '20px', borderRadius: '12px', border: '1px solid #e2e8f0', boxShadow: '0 1px 3px rgba(0,0,0,0.04)' }}>
          <div style={{ fontSize: '0.82rem', color: '#64748b', fontWeight: 600 }}>Tổng doanh thu phí sàn (Năm 2026)</div>
          <div style={{ fontSize: '1.65rem', fontWeight: 800, color: '#059669', margin: '6px 0' }}>
            254,750,000 VNĐ
          </div>
          <div style={{ fontSize: '0.78rem', color: '#16a34a', fontWeight: 600 }}>+18.4% so với cùng kỳ quý trước</div>
        </div>

        <div style={{ background: '#ffffff', padding: '20px', borderRadius: '12px', border: '1px solid #e2e8f0', boxShadow: '0 1px 3px rgba(0,0,0,0.04)' }}>
          <div style={{ fontSize: '0.82rem', color: '#64748b', fontWeight: 600 }}>Tổng giá trị hàng hóa (GMV)</div>
          <div style={{ fontSize: '1.65rem', fontWeight: 800, color: '#0f172a', margin: '6px 0' }}>
            10,190,000,000 VNĐ
          </div>
          <div style={{ fontSize: '0.78rem', color: '#64748b' }}>Ký quỹ an toàn qua Escrow</div>
        </div>

        <div style={{ background: '#ffffff', padding: '20px', borderRadius: '12px', border: '1px solid #e2e8f0', boxShadow: '0 1px 3px rgba(0,0,0,0.04)' }}>
          <div style={{ fontSize: '0.82rem', color: '#64748b', fontWeight: 600 }}>Tổng tài khoản người dùng</div>
          <div style={{ fontSize: '1.65rem', fontWeight: 800, color: '#2563eb', margin: '6px 0' }}>
            3,890
          </div>
          <div style={{ fontSize: '0.78rem', color: '#2563eb', fontWeight: 600 }}>+210 tài khoản mới trong tháng 10</div>
        </div>

        <div style={{ background: '#ffffff', padding: '20px', borderRadius: '12px', border: '1px solid #e2e8f0', boxShadow: '0 1px 3px rgba(0,0,0,0.04)' }}>
          <div style={{ fontSize: '0.82rem', color: '#64748b', fontWeight: 600 }}>Tỷ lệ đơn hàng bị khiếu nại</div>
          <div style={{ fontSize: '1.65rem', fontWeight: 800, color: '#dc2626', margin: '6px 0' }}>
            2.7%
          </div>
          <div style={{ fontSize: '0.78rem', color: '#16a34a', fontWeight: 600 }}>Giảm 0.8% nhờ siết video cận cảnh KTV</div>
        </div>
      </div>

      {/* SECTION 1: Biểu đồ & Kết xuất báo cáo doanh thu */}
      <div
        style={{
          background: '#ffffff',
          border: '1px solid #e2e8f0',
          borderRadius: '12px',
          padding: '24px',
          boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px', marginBottom: '20px' }}>
          <div>
            <h2 style={{ fontSize: '1.18rem', color: '#0f172a', margin: '0 0 4px 0', fontWeight: 700 }}>
              Báo cáo doanh thu phí sàn & GMV theo tháng
            </h2>
            <p style={{ margin: 0, fontSize: '0.84rem', color: '#64748b' }}>
              Dữ liệu chu kỳ 10 tháng năm 2026 (Phí thu trung bình: 2.5% giá trị giao dịch thành công)
            </p>
          </div>
          <button
            type="button"
            onClick={exportRevenueDataCsv}
            style={{
              padding: '8px 16px',
              background: '#ecfdf5',
              color: '#047857',
              border: '1px solid #a7f3d0',
              borderRadius: '6px',
              fontSize: '0.84rem',
              fontWeight: 700,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              transition: 'background-color 0.15s ease',
            }}
            onMouseEnter={(e) => (e.currentTarget.style.background = '#d1fae5')}
            onMouseLeave={(e) => (e.currentTarget.style.background = '#ecfdf5')}
          >
            <ConsoleIcons.Download size={16} />
            <span>Kết xuất báo cáo doanh thu (CSV)</span>
          </button>
        </div>

        {/* Bar Chart Visualization */}
        <div style={{ height: '220px', display: 'flex', alignItems: 'flex-end', gap: '12px', paddingTop: '30px' }}>
          {monthlyRevenue.map((item, idx) => {
            const heightPercent = Math.round((item.revenue / maxRevenue) * 100);
            return (
              <div
                key={idx}
                style={{
                  flex: 1,
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  height: '100%',
                  justifyContent: 'flex-end',
                  gap: '6px',
                }}
              >
                <div style={{ fontSize: '0.72rem', color: '#059669', fontWeight: 700 }}>
                  {item.revenue}tr
                </div>
                <div
                  title={`Tháng ${item.month}: Doanh thu ${item.revenue}tr (GMV ${item.gmv}tr)`}
                  style={{
                    width: '100%',
                    maxWidth: '44px',
                    height: `${heightPercent}%`,
                    background: 'linear-gradient(180deg, #10b981 0%, #059669 100%)',
                    borderRadius: '6px 6px 0 0',
                    transition: 'all 0.3s ease',
                    cursor: 'pointer',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.filter = 'brightness(1.1)')}
                  onMouseLeave={(e) => (e.currentTarget.style.filter = 'none')}
                />
                <div style={{ fontSize: '0.78rem', color: '#64748b', fontWeight: 600 }}>{item.month}</div>
              </div>
            );
          })}
        </div>
      </div>

      {/* SECTION 2: Biểu đồ & Kết xuất báo cáo số lượng người dùng */}
      <div
        style={{
          background: '#ffffff',
          border: '1px solid #e2e8f0',
          borderRadius: '12px',
          padding: '24px',
          boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px', marginBottom: '20px' }}>
          <div>
            <h2 style={{ fontSize: '1.18rem', color: '#0f172a', margin: '0 0 4px 0', fontWeight: 700 }}>
              Báo cáo tăng trưởng người dùng nền tảng
            </h2>
            <p style={{ margin: 0, fontSize: '0.84rem', color: '#64748b' }}>
              Theo dõi tốc độ mở rộng người mua & người bán C2C toàn hệ thống
            </p>
          </div>
          <button
            type="button"
            onClick={exportUserDataCsv}
            style={{
              padding: '8px 16px',
              background: '#eff6ff',
              color: '#1d4ed8',
              border: '1px solid #bfdbfe',
              borderRadius: '6px',
              fontSize: '0.84rem',
              fontWeight: 700,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              transition: 'background-color 0.15s ease',
            }}
            onMouseEnter={(e) => (e.currentTarget.style.background = '#dbeafe')}
            onMouseLeave={(e) => (e.currentTarget.style.background = '#eff6ff')}
          >
            <ConsoleIcons.Download size={16} />
            <span>Kết xuất báo cáo người dùng (CSV)</span>
          </button>
        </div>

        {/* User Growth Bar Chart */}
        <div style={{ height: '220px', display: 'flex', alignItems: 'flex-end', gap: '12px', paddingTop: '30px' }}>
          {userGrowth.map((item, idx) => {
            const heightPercent = Math.round((item.totalUsers / maxUserTotal) * 100);
            return (
              <div
                key={idx}
                style={{
                  flex: 1,
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  height: '100%',
                  justifyContent: 'flex-end',
                  gap: '6px',
                }}
              >
                <div style={{ fontSize: '0.72rem', color: '#2563eb', fontWeight: 700 }}>
                  +{item.newUsers}
                </div>
                <div
                  title={`Tháng ${item.month}: Tổng ${item.totalUsers.toLocaleString()} users (+${item.newUsers} mới)`}
                  style={{
                    width: '100%',
                    maxWidth: '44px',
                    height: `${heightPercent}%`,
                    background: 'linear-gradient(180deg, #60a5fa 0%, #2563eb 100%)',
                    borderRadius: '6px 6px 0 0',
                    transition: 'all 0.3s ease',
                    cursor: 'pointer',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.filter = 'brightness(1.1)')}
                  onMouseLeave={(e) => (e.currentTarget.style.filter = 'none')}
                />
                <div style={{ fontSize: '0.78rem', color: '#64748b', fontWeight: 600 }}>{item.month}</div>
              </div>
            );
          })}
        </div>
      </div>

      {/* SECTION 3: Thống kê tìm kiếm nhiều nhất & ít nhất */}
      <div
        style={{
          background: '#ffffff',
          border: '1px solid #e2e8f0',
          borderRadius: '12px',
          padding: '24px',
          boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px', marginBottom: '20px' }}>
          <div>
            <h2 style={{ fontSize: '1.18rem', color: '#0f172a', margin: '0 0 4px 0', fontWeight: 700 }}>
              Thống kê xu hướng tìm kiếm mặt hàng (Search Trends)
            </h2>
            <p style={{ margin: 0, fontSize: '0.84rem', color: '#64748b' }}>
              Phát hiện thị hiếu người mua đồ cũ để định hướng chiến dịch voucher và marketing
            </p>
          </div>
          <button
            type="button"
            onClick={exportTopSearchCsv}
            style={{
              padding: '8px 16px',
              background: '#f8fafc',
              color: '#334155',
              border: '1px solid #cbd5e1',
              borderRadius: '6px',
              fontSize: '0.84rem',
              fontWeight: 700,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
            }}
          >
            <ConsoleIcons.Download size={16} />
            <span>Kết xuất dữ liệu tìm kiếm (CSV)</span>
          </button>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '20px' }}>
          {/* Top 5 Most Searched */}
          <div style={{ background: '#f8fafc', padding: '18px', borderRadius: '10px', border: '1px solid #e2e8f0' }}>
            <div style={{ fontSize: '0.92rem', fontWeight: 700, color: '#059669', marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <ConsoleIcons.TrendingUp size={18} />
              <span>Top 5 mặt hàng được tìm kiếm nhiều nhất</span>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {topSearchedItems.map((item, index) => (
                <div key={index} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0', borderBottom: '1px solid #e2e8f0' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <span style={{ width: '22px', height: '22px', borderRadius: '50%', background: '#dcfce7', color: '#15803d', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.75rem', fontWeight: 800 }}>
                      {index + 1}
                    </span>
                    <span style={{ fontSize: '0.88rem', color: '#0f172a', fontWeight: 600 }}>{item.name}</span>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: '0.86rem', fontWeight: 700, color: '#047857' }}>{item.searches.toLocaleString()} lượt</div>
                    <div style={{ fontSize: '0.74rem', color: '#64748b' }}>{item.orders} đơn (CR: {item.rate})</div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Top 5 Least Searched */}
          <div style={{ background: '#f8fafc', padding: '18px', borderRadius: '10px', border: '1px solid #e2e8f0' }}>
            <div style={{ fontSize: '0.92rem', fontWeight: 700, color: '#dc2626', marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <ConsoleIcons.AlertTriangle size={18} />
              <span>Top 5 mặt hàng ít được tìm kiếm nhất</span>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {leastSearchedItems.map((item, index) => (
                <div key={index} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0', borderBottom: '1px solid #e2e8f0' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <span style={{ width: '22px', height: '22px', borderRadius: '50%', background: '#fee2e2', color: '#dc2626', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.75rem', fontWeight: 800 }}>
                      {index + 1}
                    </span>
                    <span style={{ fontSize: '0.88rem', color: '#334155' }}>{item.name}</span>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: '0.86rem', fontWeight: 600, color: '#dc2626' }}>{item.searches} lượt</div>
                    <div style={{ fontSize: '0.74rem', color: '#64748b' }}>{item.note}</div>
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
          background: '#ffffff',
          border: '1px solid #e2e8f0',
          borderRadius: '12px',
          padding: '24px',
          boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px', marginBottom: '18px' }}>
          <div>
            <h2 style={{ fontSize: '1.18rem', color: '#0f172a', margin: '0 0 4px 0', fontWeight: 700 }}>
              Thống kê số lượng hàng bị trả & Khiếu nại theo danh mục
            </h2>
            <p style={{ margin: 0, fontSize: '0.84rem', color: '#64748b' }}>
              Giúp quản trị viên theo dõi tỷ lệ tranh chấp và chất lượng hàng hóa được kiểm duyệt
            </p>
          </div>
          <button
            type="button"
            onClick={exportReturnComplaintCsv}
            style={{
              padding: '8px 16px',
              background: '#fef2f2',
              color: '#dc2626',
              border: '1px solid #fecaca',
              borderRadius: '6px',
              fontSize: '0.84rem',
              fontWeight: 700,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
            }}
          >
            <ConsoleIcons.Download size={16} />
            <span>Kết xuất báo cáo khiếu nại (CSV)</span>
          </button>
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
              onClick: exportReturnComplaintCsv,
            },
          ]}
        />

        <div style={{ overflowX: 'auto', border: '1px solid #e2e8f0', borderRadius: '8px' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.88rem' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid #e2e8f0', color: '#64748b', background: '#f8fafc' }}>
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
                      borderBottom: '1px solid #f1f5f9',
                      background: isSelected ? '#f0fdf4' : 'transparent',
                    }}
                  >
                    <td style={{ padding: '12px 14px', textAlign: 'center' }}>
                      <TableCheckbox
                        checked={isSelected}
                        onChange={() => handleToggleCategory(row.category)}
                        ariaLabel={`Chọn ngành hàng ${row.category}`}
                      />
                    </td>
                    <td style={{ padding: '12px 14px', fontWeight: 600, color: '#0f172a' }}>
                      {row.category}
                    </td>
                    <td style={{ padding: '12px 14px', color: '#334155' }}>{row.totalOrders} đơn</td>
                    <td style={{ padding: '12px 14px', color: '#d97706', fontWeight: 700 }}>
                      {row.returnCount} đơn
                    </td>
                    <td style={{ padding: '12px 14px', color: '#dc2626', fontWeight: 700 }}>
                      {row.complaintCount} vụ
                    </td>
                    <td style={{ padding: '12px 14px' }}>
                      <span
                        style={{
                          padding: '3px 8px',
                          borderRadius: '12px',
                          fontSize: '0.75rem',
                          fontWeight: 700,
                          background: '#fef2f2',
                          color: '#dc2626',
                          border: '1px solid #fecaca',
                        }}
                      >
                        {row.returnRate}
                      </span>
                    </td>
                    <td style={{ padding: '12px 14px', color: '#64748b', fontSize: '0.84rem' }}>
                      {row.primaryIssue}
                    </td>
                    <td style={{ padding: '12px 14px', textAlign: 'right' }}>
                      <TableActionDropdown
                        ariaLabel={`Thao tác danh mục ${row.category}`}
                        items={[
                          {
                            label: 'Kết xuất báo cáo ngành',
                            icon: <ConsoleIcons.Download size={16} />,
                            onClick: exportReturnComplaintCsv,
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
