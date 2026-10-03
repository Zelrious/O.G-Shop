import React from 'react';
import { StatusBadge } from '../../shared/components';

export const AdminDashboardPage: React.FC = () => {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', margin: '0 0 6px', color: '#ffffff' }}>
            Bảng Điều Khiển Quản Trị & Trọng Tài (AD-01)
          </h1>
          <p style={{ margin: 0, color: '#9cb8a9' }}>
            Giám sát vận hành sàn C2C, xử lý hàng chờ xác minh KYC và phân xử khiếu nại tranh chấp.
          </p>
        </div>
      </div>

      {/* Admin KPI Stats Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px' }}>
        <div style={{ background: '#182b22', padding: '16px', borderRadius: '10px', border: '1px solid #233c30' }}>
          <div style={{ fontSize: '0.8rem', color: '#89a897' }}>Tổng tài khoản người dùng</div>
          <div style={{ fontSize: '1.75rem', fontWeight: 800, color: '#ffffff', margin: '6px 0' }}>1,280</div>
          <div style={{ fontSize: '0.75rem', color: '#55b37e' }}>+18 hôm nay</div>
        </div>

        <div style={{ background: '#182b22', padding: '16px', borderRadius: '10px', border: '1px solid #233c30' }}>
          <div style={{ fontSize: '0.8rem', color: '#89a897' }}>Hồ sơ KYC chờ duyệt (AD-05)</div>
          <div style={{ fontSize: '1.75rem', fontWeight: 800, color: '#e5b357', margin: '6px 0' }}>4</div>
          <div style={{ fontSize: '0.75rem', color: '#e5b357' }}>Cần xử lý trong 24h</div>
        </div>

        <div style={{ background: '#182b22', padding: '16px', borderRadius: '10px', border: '1px solid #233c30' }}>
          <div style={{ fontSize: '0.8rem', color: '#89a897' }}>Tổng tiền đang giữ tại Escrow</div>
          <div style={{ fontSize: '1.75rem', fontWeight: 800, color: '#55b37e', margin: '6px 0' }}>86.400.000₫</div>
          <div style={{ fontSize: '0.75rem', color: '#89a897' }}>Được bảo vệ mô phỏng</div>
        </div>

        <div style={{ background: '#182b22', padding: '16px', borderRadius: '10px', border: '1px solid #233c30' }}>
          <div style={{ fontSize: '0.8rem', color: '#89a897' }}>Vụ việc khiếu nại mở (AD-15)</div>
          <div style={{ fontSize: '1.75rem', fontWeight: 800, color: '#f07171', margin: '6px 0' }}>1</div>
          <div style={{ fontSize: '0.75rem', color: '#f07171' }}>Cần trọng tài phân xử</div>
        </div>
      </div>

      {/* KYC Queue Preview */}
      <div style={{ background: '#14231c', padding: '20px', borderRadius: '12px', border: '1px solid #233c30' }}>
        <h2 style={{ fontSize: '1.15rem', color: '#ffffff', margin: '0 0 16px' }}>
          🛡️ Hàng chờ duyệt định danh người bán (AD-05 Preview)
        </h2>
        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.88rem' }}>
          <thead>
            <tr style={{ borderBottom: '1px solid #233c30', color: '#89a897' }}>
              <th style={{ padding: '10px 8px' }}>User ID</th>
              <th style={{ padding: '10px 8px' }}>Họ và tên</th>
              <th style={{ padding: '10px 8px' }}>Email</th>
              <th style={{ padding: '10px 8px' }}>Thời gian nộp</th>
              <th style={{ padding: '10px 8px' }}>Trạng thái</th>
            </tr>
          </thead>
          <tbody>
            <tr style={{ borderBottom: '1px solid rgba(255, 255, 255, 0.05)' }}>
              <td style={{ padding: '12px 8px' }}>USR-0091</td>
              <td style={{ padding: '12px 8px', fontWeight: 600, color: '#ffffff' }}>Nguyễn Văn Minh</td>
              <td style={{ padding: '12px 8px' }}>minh.nguyen@example.com</td>
              <td style={{ padding: '12px 8px' }}>Hôm nay, 14:20</td>
              <td style={{ padding: '12px 8px' }}>
                <StatusBadge category="complaint" status="REVIEWING" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
};
