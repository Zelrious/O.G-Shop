import { useState } from 'react';
import { useDemo, useI18n } from '../../../shared/context';
import { MOCK_ORDERS, MOCK_WALLET } from '../../../shared/data/mockData';

interface ModuleProps {
  screenId: string;
}

export function Module3ShippingScreens({ screenId }: ModuleProps) {
  const { language } = useI18n();
  const { setActiveScreenId } = useDemo();

  const [ratingScore, setRatingScore] = useState(5);
  const [ratingComment, setRatingComment] = useState('Người bán đóng gói rất kỹ, camera đúng 97% như mô tả, test sensor không có bụi!');
  const [reviewSubmitted, setReviewSubmitted] = useState(false);
  const [coInspectPassed, setCoInspectPassed] = useState(false);

  const order = MOCK_ORDERS[0];

  switch (screenId) {
    case 'SCR-SHIP-TRACKING':
      return (
        <div style={{ maxWidth: '780px', margin: '0 auto' }}>
          <div className="og-card" style={{ padding: '24px', marginBottom: '20px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '8px', borderBottom: '1px solid var(--og-color-border)', paddingBottom: '14px', marginBottom: '16px' }}>
              <div>
                <h2 style={{ margin: 0, color: 'var(--og-color-primary)', fontSize: '18px' }}>
                  🚚 {language === 'vi' ? 'Hành Trình Giao Hàng & Đồng Kiểm' : 'Co-Inspection Delivery Tracking'}
                </h2>
                <div style={{ fontSize: '12px', color: 'var(--og-color-text-secondary)', marginTop: '2px' }}>
                  {order.carrier} · Mã vận đơn: <strong>{order.trackingNumber}</strong>
                </div>
              </div>
              <span className="og-badge og-badge--warning">
                {order.shippingStatus}
              </span>
            </div>

            {/* Co-inspection Callout Banner */}
            <div className="og-co-inspect-card" style={{ marginBottom: '20px' }}>
              <span style={{ fontSize: '24px' }}>📦</span>
              <div style={{ flex: 1 }}>
                <strong style={{ fontSize: '14px', color: 'var(--og-color-success)' }}>
                  {language === 'vi' ? 'Đơn hàng có dịch vụ Đồng Kiểm cùng Bưu tá' : 'Co-Inspection with Courier Active'}
                </strong>
                <p style={{ margin: '4px 0 8px', fontSize: '12px', color: 'var(--og-color-text-secondary)' }}>
                  {language === 'vi'
                    ? 'Bạn có quyền mở gói hàng, kiểm tra ngoại hình máy và bật nguồn trước khi ký nhận. Nếu không đúng mô tả, bưu tá sẽ lập biên bản và hoàn về miễn phí.'
                    : 'Inspect hardware & power on before courier. If defect undisclosed, return is free.'}
                </p>
                <button
                  type="button"
                  className="og-button og-button--primary og-button--sm"
                  onClick={() => setActiveScreenId('SCR-SHIP-CO-INSPECT')}
                >
                  📋 {language === 'vi' ? 'Xem Hướng Dẫn Đồng Kiểm' : 'Open Inspection Checklist'}
                </button>
              </div>
            </div>

            {/* Timeline */}
            <div style={{ paddingLeft: '8px' }}>
              {order.timeline.map((step, idx) => (
                <div key={idx} className="og-dispute-timeline-item">
                  <div style={{ fontSize: '11px', color: 'var(--og-color-accent-gold)', fontWeight: 600 }}>{step.time}</div>
                  <div style={{ fontSize: '14px', fontWeight: 600, color: 'var(--og-color-text-primary)' }}>{step.title[language]}</div>
                  <div style={{ fontSize: '12px', color: 'var(--og-color-text-secondary)', marginTop: '2px' }}>{step.description[language]}</div>
                </div>
              ))}
            </div>
          </div>

          <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
            <button
              type="button"
              className="og-button og-button--danger og-button--sm"
              onClick={() => setActiveScreenId('SCR-DISPUTE-CREATE')}
            >
              ⚠️ {language === 'vi' ? 'Hàng Không Đúng? Mở Khiếu Nại' : 'Dispute Item'}
            </button>
            <button
              type="button"
              className="og-button og-button--primary og-button--sm"
              onClick={() => setActiveScreenId('SCR-REVIEW-PARTNER')}
            >
              ⭐ {language === 'vi' ? 'Đã Nhận & Đánh Giá' : 'Confirm & Review'}
            </button>
          </div>
        </div>
      );

    case 'SCR-SHIP-CO-INSPECT':
      return (
        <div className="og-card" style={{ maxWidth: '640px', margin: '0 auto', padding: '28px' }}>
          <h2 style={{ color: 'var(--og-color-primary)', marginTop: 0, textAlign: 'center' }}>
            📋 {language === 'vi' ? 'Biên Bản & Quy Trình Đồng Kiểm Tại Chỗ' : 'Co-Inspection Protocol'}
          </h2>
          <p style={{ textAlign: 'center', fontSize: '13px', color: 'var(--og-color-text-secondary)', marginBottom: '20px' }}>
            {language === 'vi' ? 'Thực hiện cùng shipper Nguyễn Văn H. (SĐT: 0912 345 678)' : 'Execute with courier Nguyen Van H.'}
          </p>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', marginBottom: '20px' }}>
            <label style={{ display: 'flex', gap: '10px', alignItems: 'flex-start', border: '1px solid var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '12px' }}>
              <input type="checkbox" defaultChecked />
              <div style={{ fontSize: '13px' }}>
                <strong>1. Kiểm tra hộp bưu kiện bên ngoài</strong>
                <p style={{ margin: '2px 0 0', color: 'var(--og-color-text-secondary)', fontSize: '12px' }}>
                  Hộp nguyên vẹn, băng keo niêm phong O.G không có dấu hiệu bị rạch hay rách móp.
                </p>
              </div>
            </label>

            <label style={{ display: 'flex', gap: '10px', alignItems: 'flex-start', border: '1px solid var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '12px' }}>
              <input type="checkbox" defaultChecked />
              <div style={{ fontSize: '13px' }}>
                <strong>2. Đối chiếu ngoại hình máy với tin đăng</strong>
                <p style={{ margin: '2px 0 0', color: 'var(--og-color-text-secondary)', fontSize: '12px' }}>
                  Kiểm tra vết xước 2mm ở đáy máy như người bán đã khai báo. Không phát hiện nứt vỡ mới.
                </p>
              </div>
            </label>

            <label style={{ display: 'flex', gap: '10px', alignItems: 'flex-start', border: '1px solid var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '12px' }}>
              <input type="checkbox" defaultChecked />
              <div style={{ fontSize: '13px' }}>
                <strong>3. Bật nguồn & kiểm tra chức năng cơ bản</strong>
                <p style={{ margin: '2px 0 0', color: 'var(--og-color-text-secondary)', fontSize: '12px' }}>
                  Máy lên nguồn, màn hình sáng đều, các phím bấm và vòng xoay hoạt động trơn tru.
                </p>
              </div>
            </label>
          </div>

          {coInspectPassed ? (
            <div className="og-badge og-badge--success" style={{ width: '100%', padding: '12px', justifyContent: 'center', marginBottom: '16px' }}>
              ✓ {language === 'vi' ? 'Đã ký biên bản đồng kiểm thành công!' : 'Co-inspection signed!'}
            </div>
          ) : (
            <div style={{ display: 'flex', gap: '12px' }}>
              <button
                type="button"
                className="og-button og-button--primary"
                style={{ flex: 1 }}
                onClick={() => { setCoInspectPassed(true); setTimeout(() => setActiveScreenId('SCR-REVIEW-PARTNER'), 1500); }}
              >
                ✓ {language === 'vi' ? 'Hài Lòng, Xác Nhận Nhận Hàng' : 'Satisfied, Accept Item'}
              </button>
              <button
                type="button"
                className="og-button og-button--danger"
                onClick={() => setActiveScreenId('SCR-DISPUTE-CREATE')}
              >
                ✕ {language === 'vi' ? 'Từ Chối Nhận (Lỗi)' : 'Reject Delivery'}
              </button>
            </div>
          )}
        </div>
      );

    case 'SCR-SELLER-PRINT-WAYBILL':
      return (
        <div style={{ maxWidth: '640px', margin: '0 auto' }}>
          <div className="og-card" style={{ padding: '24px', background: '#ffffff', color: '#000000', border: '2px solid #000000' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '2px solid #000000', paddingBottom: '12px', marginBottom: '14px' }}>
              <div>
                <h2 style={{ margin: 0, fontSize: '20px' }}>O.G SHOP · PHIẾU GỬI HÀNG</h2>
                <div style={{ fontSize: '12px' }}>ĐỒ CŨ KÝ QUỸ ESCROW</div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <span style={{ border: '2px solid #000', padding: '4px 8px', fontWeight: 900, fontSize: '14px' }}>
                  CHO XEM HÀNG & ĐỒNG KIỂM
                </span>
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', fontSize: '12px', marginBottom: '16px' }}>
              <div>
                <strong>NGƯỜI GỬI:</strong>
                <div>Minh Camera Saigon</div>
                <div>0909 123 456</div>
                <div>Quận 1, TP. Hồ Chí Minh</div>
              </div>
              <div>
                <strong>NGƯỜI NHẬN:</strong>
                <div>Huỳnh Long Bảo Khánh</div>
                <div>0908 123 456</div>
                <div>Landmark 81, Bình Thạnh, TP.HCM</div>
              </div>
            </div>

            <div style={{ textAlign: 'center', padding: '16px 0', borderTop: '1px dashed #000', borderBottom: '1px dashed #000', margin: '14px 0' }}>
              <div style={{ fontFamily: 'monospace', fontSize: '24px', letterSpacing: '4px' }}>||| | |||| | ||||| ||| | |||</div>
              <div style={{ fontWeight: 700, fontSize: '14px', marginTop: '4px' }}>VNPOST-OG-88912-VN</div>
            </div>

            <div style={{ fontSize: '12px' }}>
              <div><strong>Nội dung:</strong> Máy ảnh Sony A7 III (Đã qua sử dụng, kèm video đóng gói)</div>
              <div><strong>Tiền thu hộ COD:</strong> 0 VNĐ (Đã thanh toán Escrow qua sàn)</div>
            </div>
          </div>

          <div style={{ display: 'flex', gap: '12px', marginTop: '16px', justifyContent: 'center' }}>
            <button type="button" className="og-button og-button--primary" onClick={() => alert('Printing Waybill...')}>
              🖨️ {language === 'vi' ? 'In Phiếu Gửi Hàng' : 'Print Label'}
            </button>
            <button type="button" className="og-button og-button--outline" onClick={() => setActiveScreenId('SCR-SELLER-ORDERS')}>
              {language === 'vi' ? 'Quay Lại Đơn Bán' : 'Back to Orders'}
            </button>
          </div>
        </div>
      );

    case 'SCR-SHIP-RETURNS':
      return (
        <div className="og-card" style={{ maxWidth: '680px', margin: '0 auto', padding: '24px' }}>
          <h2 style={{ color: 'var(--og-color-primary)', marginTop: 0 }}>
            ↩️ {language === 'vi' ? 'Theo Dõi Kiện Hàng Trả Về Người Bán' : 'Return Shipment Tracking'}
          </h2>
          <p style={{ fontSize: '13px', color: 'var(--og-color-text-secondary)', marginBottom: '16px' }}>
            {language === 'vi'
              ? 'Hồ sơ khiếu nại đã được phê duyệt. Kiện hàng đang trên đường gửi trả lại cho người bán.'
              : 'Return authorized. Parcel returning to seller.'}
          </p>

          <div style={{ background: 'var(--og-color-surface-subtle)', borderRadius: 'var(--og-radius-md)', padding: '16px', marginBottom: '16px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
              <span>Mã vận đơn trả: <strong>RET-VNPOST-77192</strong></span>
              <span className="og-badge og-badge--warning">IN_TRANSIT_RETURN</span>
            </div>
            <div style={{ fontSize: '12px', color: 'var(--og-color-text-muted)', marginTop: '4px' }}>
              Dự kiến giao lại người bán: 22/09/2026 · Tiền Escrow sẽ tự động hoàn lại cho bạn sau khi người bán nhận lại máy.
            </div>
          </div>

          <button type="button" className="og-button og-button--primary" onClick={() => setActiveScreenId('SCR-DISPUTE-TIMELINE')}>
            {language === 'vi' ? 'Xem Tiến Trình Hồ Sơ Khiếu Nại' : 'View Dispute Timeline'}
          </button>
        </div>
      );

    case 'SCR-REVIEW-PARTNER':
      return (
        <div className="og-card" style={{ maxWidth: '600px', margin: '0 auto', padding: '28px' }}>
          <h2 style={{ color: 'var(--og-color-primary)', margin: '0 0 8px', textAlign: 'center' }}>
            ⭐ {language === 'vi' ? 'Đánh Giá Người Bán & Nhận O.G Xu' : 'Rate Seller & Earn O.G Coins'}
          </h2>
          <p style={{ textAlign: 'center', fontSize: '13px', color: 'var(--og-color-text-secondary)', marginBottom: '20px' }}>
            {language === 'vi' ? 'Đánh giá chân thực giúp xây dựng cộng đồng đồ cũ minh bạch và uy tín' : 'Genuine reviews build a trusted second-hand community'}
          </p>

          {reviewSubmitted ? (
            <div style={{ textAlign: 'center', padding: '20px' }}>
              <div style={{ fontSize: '40px' }}>🪙</div>
              <h3 style={{ color: 'var(--og-color-success)' }}>
                {language === 'vi' ? 'Đã Gửi Đánh Giá Thành Công!' : 'Review Submitted!'}
              </h3>
              <p style={{ fontSize: '13px' }}>
                {language === 'vi' ? 'Bạn đã được cộng +2.500 O.G Xu vào ví thưởng!' : 'You earned +2,500 O.G Coins!'}
              </p>
              <button type="button" className="og-button og-button--primary" onClick={() => setActiveScreenId('SCR-REWARDS-WALLET')}>
                💰 {language === 'vi' ? 'Mở Ví O.G Xu' : 'Open Coins Wallet'}
              </button>
            </div>
          ) : (
            <form onSubmit={e => { e.preventDefault(); setReviewSubmitted(true); }}>
              <div style={{ textAlign: 'center', marginBottom: '16px' }}>
                <div style={{ fontSize: '28px', cursor: 'pointer', letterSpacing: '8px' }}>
                  {[1, 2, 3, 4, 5].map(star => (
                    <span key={star} onClick={() => setRatingScore(star)}>
                      {star <= ratingScore ? '★' : '☆'}
                    </span>
                  ))}
                </div>
                <div style={{ fontSize: '13px', fontWeight: 600, color: 'var(--og-color-accent-gold)', marginTop: '4px' }}>
                  {ratingScore === 5 ? (language === 'vi' ? 'Cực kỳ hài lòng (Đúng 100% mô tả)' : 'Extremely Satisfied') : `${ratingScore}/5`}
                </div>
              </div>

              <div className="og-form-group">
                <label className="og-label">{language === 'vi' ? 'Nhận xét chi tiết về tình trạng đồ cũ' : 'Your Review'}</label>
                <textarea
                  className="og-textarea"
                  rows={3}
                  value={ratingComment}
                  onChange={e => setRatingComment(e.target.value)}
                  required
                />
              </div>

              <button type="submit" className="og-button og-button--primary" style={{ width: '100%' }}>
                {language === 'vi' ? 'Gửi Đánh Giá & Nhận 2.500 O.G Xu' : 'Submit Review & Claim 2,500 Coins'}
              </button>
            </form>
          )}
        </div>
      );

    case 'SCR-REWARDS-WALLET':
      return (
        <div style={{ maxWidth: '780px', margin: '0 auto' }}>
          {/* Wallet Header Balance */}
          <div
            style={{
              background: 'linear-gradient(135deg, #c6933a, #ad7d28)',
              color: '#ffffff',
              borderRadius: 'var(--og-radius-lg)',
              padding: '28px 24px',
              marginBottom: '24px',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: '16px'
            }}
          >
            <div>
              <div style={{ fontSize: '13px', opacity: 0.9 }}>{language === 'vi' ? 'SỐ DƯ VÍ O.G XU THƯỞNG' : 'O.G REWARDS COIN BALANCE'}</div>
              <div style={{ fontSize: '32px', fontWeight: 900, marginTop: '4px' }}>{MOCK_WALLET.coinBalance.toLocaleString()} Xu</div>
              <div style={{ fontSize: '13px', opacity: 0.9 }}>Tương đương {MOCK_WALLET.vndEquivalent} (1 Xu = 1 VNĐ)</div>
            </div>
            <button
              type="button"
              className="og-button"
              style={{ background: '#ffffff', color: '#8e6216', fontWeight: 700 }}
              onClick={() => alert(language === 'vi' ? 'Mã voucher freeship 30k đã được đổi từ 10.000 xu!' : 'Redeemed!')}
            >
              🎁 {language === 'vi' ? 'Đổi Xu Lấy Freeship' : 'Redeem for Freeship'}
            </button>
          </div>

          {/* History */}
          <div className="og-card" style={{ padding: '20px' }}>
            <h3 style={{ marginTop: 0, color: 'var(--og-color-primary)' }}>
              {language === 'vi' ? 'Lịch Sử Tích Lũy & Đổi Xu' : 'Rewards Transaction History'}
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {MOCK_WALLET.history.map(item => (
                <div key={item.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0', borderBottom: '1px solid var(--og-color-border)' }}>
                  <div>
                    <div style={{ fontSize: '13px', fontWeight: 600 }}>{item.desc[language]}</div>
                    <div style={{ fontSize: '11px', color: 'var(--og-color-text-muted)' }}>{item.date}</div>
                  </div>
                  <strong style={{ color: item.type === 'in' ? 'var(--og-color-success)' : 'var(--og-color-danger)' }}>
                    {item.amount}
                  </strong>
                </div>
              ))}
            </div>
          </div>
        </div>
      );

    default:
      return <div>Module 3: Screen {screenId}</div>;
  }
}
