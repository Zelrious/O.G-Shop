import { useState } from 'react';
import { useDemo, useI18n } from '../../../shared/context';
import { MOCK_DISPUTE } from '../../../shared/data/mockData';

interface ModuleProps {
  screenId: string;
}

export function Module4DisputeScreens({ screenId }: ModuleProps) {
  const { language } = useI18n();
  const { setActiveScreenId, disputeVerdict, setDisputeVerdict } = useDemo();

  const [disputeReason, setDisputeReason] = useState('DEFECT_NOT_DISCLOSED');
  const [disputeNotes, setDisputeNotes] = useState('Tai nghe Sony WH-1000XM4 bị nứt khớp gập bên tai trái khoảng 1.5cm, có nguy cơ gãy rụng khi đeo. Bài đăng người bán chỉ ghi xước dăm 92% mà không hề chụp hay nói về vết nứt này.');
  const [selectedVerdictAction, setSelectedVerdictAction] = useState('FULL_REFUND');
  const [arbitratorNote, setArbitratorNote] = useState('Căn cứ video đồng kiểm mở hộp cùng shipper của Buyer lúc 11:30, vết nứt khớp gập đã hiện diện ngay khi bóc lớp chống sốc. Phán quyết: Chấp thuận hoàn tiền 100% cho Buyer sau khi hàng hoàn về kho Seller.');

  const handleCreateDispute = (e: React.FormEvent) => {
    e.preventDefault();
    setActiveScreenId('SCR-DISPUTE-EVIDENCE');
  };

  const handleApplyVerdict = () => {
    setDisputeVerdict(selectedVerdictAction);
    alert(language === 'vi' ? `Phán quyết [${selectedVerdictAction}] đã được thực thi và gửi thông báo cho hai bên!` : `Verdict [${selectedVerdictAction}] executed!`);
    setActiveScreenId('SCR-DISPUTE-TIMELINE');
  };

  switch (screenId) {
    case 'SCR-DISPUTE-CREATE':
      return (
        <div className="og-card" style={{ maxWidth: '680px', margin: '0 auto', padding: '28px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '20px' }}>
            <span style={{ fontSize: '32px' }}>⚖️</span>
            <div>
              <h2 style={{ margin: 0, color: 'var(--og-color-danger)' }}>
                {language === 'vi' ? 'Mở Hồ Sơ Khiếu Nại & Tạm Dừng Escrow' : 'Open Dispute & Freeze Escrow'}
              </h2>
              <p style={{ margin: '4px 0 0', fontSize: '13px', color: 'var(--og-color-text-secondary)' }}>
                {language === 'vi'
                  ? 'Khi mở khiếu nại, số tiền 3.200.000₫ sẽ được đóng băng ngay lập tức, không thể giải ngân cho người bán.'
                  : 'Escrow payment 3,200,000₫ will be frozen immediately.'}
              </p>
            </div>
          </div>

          <form onSubmit={handleCreateDispute}>
            <div className="og-form-group">
              <label className="og-label">{language === 'vi' ? 'Lý do khiếu nại chính' : 'Primary Dispute Reason'}</label>
              <select className="og-select" value={disputeReason} onChange={e => setDisputeReason(e.target.value)}>
                <option value="DEFECT_NOT_DISCLOSED">{language === 'vi' ? 'Sản phẩm có khuyết điểm/nứt vỡ không được mô tả trong bài đăng' : 'Undisclosed damage / defect'}</option>
                <option value="CONDITION_MISMATCH">{language === 'vi' ? 'Tình trạng thực tế kém xa so với thang đo hao mòn cam kết' : 'Condition grade mismatch'}</option>
                <option value="WRONG_ITEM">{language === 'vi' ? 'Gửi sai model máy / thiếu phụ kiện cốt lõi' : 'Wrong item or missing core parts'}</option>
                <option value="SHIPPING_DAMAGE">{language === 'vi' ? 'Hàng bị móp méo bể vỡ trong quá trình shipper vận chuyển' : 'Damaged in transit'}</option>
              </select>
            </div>

            <div className="og-form-group">
              <label className="og-label">{language === 'vi' ? 'Mô tả chi tiết sự việc' : 'Detailed Explanation'}</label>
              <textarea
                className="og-textarea"
                rows={4}
                value={disputeNotes}
                onChange={e => setDisputeNotes(e.target.value)}
                required
              />
            </div>

            <div className="og-safety-warning">
              <strong>{language === 'vi' ? 'Quy định trọng tài Escrow:' : 'Escrow Policy:'}</strong>{' '}
              {language === 'vi'
                ? 'Bạn sẽ cần đính kèm ảnh chụp cận cảnh và video đồng kiểm lúc mở hộp ở bước tiếp theo để trọng tài O.G phân xử trong 24 giờ.'
                : 'Unboxing footage or defect photos required in the next step for 24h arbitration.'}
            </div>

            <div style={{ display: 'flex', gap: '12px', marginTop: '20px' }}>
              <button type="submit" className="og-button og-button--danger" style={{ flex: 1 }}>
                {language === 'vi' ? 'Tiếp Tục Tải Lên Bằng Chứng →' : 'Continue to Evidence Upload →'}
              </button>
              <button type="button" className="og-button og-button--outline" onClick={() => setActiveScreenId('SCR-SHIP-TRACKING')}>
                {language === 'vi' ? 'Hủy Bỏ' : 'Cancel'}
              </button>
            </div>
          </form>
        </div>
      );

    case 'SCR-DISPUTE-EVIDENCE':
      return (
        <div className="og-card" style={{ maxWidth: '680px', margin: '0 auto', padding: '28px' }}>
          <h2 style={{ color: 'var(--og-color-primary)', marginTop: 0 }}>
            📸 {language === 'vi' ? 'Tải Lên Bằng Chứng Ảnh & Video Mở Hộp' : 'Upload Dispute Evidence Photos & Video'}
          </h2>
          <p style={{ fontSize: '13px', color: 'var(--og-color-text-secondary)', marginBottom: '20px' }}>
            {language === 'vi' ? 'Bằng chứng rõ ràng giúp trọng tài viên O.G giải quyết khiếu nại nhanh chóng' : 'Clear evidence expedites 24h arbitration resolution'}
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '14px', marginBottom: '20px' }}>
            <div style={{ border: '2px solid var(--og-color-primary)', borderRadius: 'var(--og-radius-md)', padding: '14px', textAlign: 'center', background: 'var(--og-color-primary-light)' }}>
              <span>🎥</span>
              <div style={{ fontSize: '13px', fontWeight: 600, marginTop: '6px' }}>video_dong_kiem.mp4</div>
              <div style={{ fontSize: '11px', color: 'var(--og-color-success)' }}>✓ {language === 'vi' ? 'Đã tải lên (45 MB)' : 'Uploaded (45 MB)'}</div>
            </div>

            <div style={{ border: '2px solid var(--og-color-primary)', borderRadius: 'var(--og-radius-md)', padding: '14px', textAlign: 'center', background: 'var(--og-color-primary-light)' }}>
              <span>🖼️</span>
              <div style={{ fontSize: '13px', fontWeight: 600, marginTop: '6px' }}>can_canh_vet_nut_khop.jpg</div>
              <div style={{ fontSize: '11px', color: 'var(--og-color-success)' }}>✓ {language === 'vi' ? 'Đã tải lên (4.2 MB)' : 'Uploaded (4.2 MB)'}</div>
            </div>

            <div style={{ border: '2px dashed var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '14px', textAlign: 'center', cursor: 'pointer' }}>
              <span>+</span>
              <div style={{ fontSize: '13px', fontWeight: 600, marginTop: '6px' }}>{language === 'vi' ? 'Thêm ảnh khác' : 'Add photo'}</div>
              <div style={{ fontSize: '11px', color: 'var(--og-color-text-muted)' }}>{language === 'vi' ? 'Tối đa 10 tệp' : 'Up to 10 files'}</div>
            </div>
          </div>

          <div style={{ display: 'flex', gap: '12px' }}>
            <button
              type="button"
              className="og-button og-button--primary"
              style={{ flex: 1 }}
              onClick={() => setActiveScreenId('SCR-DISPUTE-TIMELINE')}
            >
              {language === 'vi' ? 'Gửi Hồ Sơ Cho Trọng Tài Viên' : 'Submit to Arbitrator'}
            </button>
            <button
              type="button"
              className="og-button og-button--outline"
              onClick={() => setActiveScreenId('SCR-ADMIN-DISPUTE-ROOM')}
            >
              ⚖️ {language === 'vi' ? 'Mô Phỏng Phòng Phân Xử Admin' : 'Simulate Admin Room'}
            </button>
          </div>
        </div>
      );

    case 'SCR-DISPUTE-TIMELINE':
      return (
        <div style={{ maxWidth: '780px', margin: '0 auto' }}>
          <div className="og-card" style={{ padding: '24px', marginBottom: '20px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '8px', borderBottom: '1px solid var(--og-color-border)', paddingBottom: '14px', marginBottom: '16px' }}>
              <div>
                <h2 style={{ margin: 0, color: 'var(--og-color-danger)', fontSize: '18px' }}>
                  ⚖️ {language === 'vi' ? 'Hồ Sơ Khiếu Nại: ' : 'Dispute Case: '}{MOCK_DISPUTE.id}
                </h2>
                <div style={{ fontSize: '12px', color: 'var(--og-color-text-secondary)', marginTop: '2px' }}>
                  Đơn hàng: <strong>{MOCK_DISPUTE.orderNumber}</strong> · Số tiền đóng băng: <strong style={{ color: 'var(--og-color-danger)' }}>{MOCK_DISPUTE.disputedAmount.toLocaleString()}₫</strong>
                </div>
              </div>
              <span className="og-badge og-badge--warning">
                {disputeVerdict ? `VERDICT_${disputeVerdict}` : MOCK_DISPUTE.status}
              </span>
            </div>

            {/* Verdict Display if decided */}
            {disputeVerdict && (
              <div style={{ background: 'var(--og-color-success-bg)', border: '1px solid var(--og-color-success-border)', borderRadius: 'var(--og-radius-md)', padding: '16px', marginBottom: '20px' }}>
                <strong style={{ color: 'var(--og-color-success)', fontSize: '14px' }}>
                  ✓ {language === 'vi' ? 'PHÁN QUYẾT CHÍNH THỨC CỦA TRỌNG TÀI VIÊN O.G:' : 'OFFICIAL ARBITRATOR VERDICT:'}
                </strong>
                <p style={{ margin: '6px 0 0', fontSize: '13px' }}>
                  {arbitratorNote}
                </p>
              </div>
            )}

            {/* Product in dispute */}
            <div style={{ display: 'flex', gap: '14px', alignItems: 'center', background: 'var(--og-color-surface-subtle)', borderRadius: 'var(--og-radius-md)', padding: '12px', marginBottom: '20px' }}>
              <img src={MOCK_DISPUTE.productImage} alt="thumb" style={{ width: '60px', height: '60px', objectFit: 'cover', borderRadius: 'var(--og-radius-md)' }} />
              <div>
                <div style={{ fontSize: '14px', fontWeight: 600 }}>{MOCK_DISPUTE.productTitle[language]}</div>
                <div style={{ fontSize: '12px', color: 'var(--og-color-danger)' }}>{MOCK_DISPUTE.reason[language]}</div>
              </div>
            </div>

            {/* Timeline */}
            <div style={{ paddingLeft: '8px' }}>
              {MOCK_DISPUTE.timeline.map((step, idx) => (
                <div key={idx} className="og-dispute-timeline-item">
                  <div style={{ fontSize: '11px', color: 'var(--og-color-accent-gold)', fontWeight: 600 }}>{step.time} · {step.by}</div>
                  <div style={{ fontSize: '13px', fontWeight: 600, color: 'var(--og-color-text-primary)' }}>{step.title[language]}</div>
                </div>
              ))}
            </div>
          </div>

          <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
            <button
              type="button"
              className="og-button og-button--primary og-button--sm"
              onClick={() => setActiveScreenId('SCR-ADMIN-DISPUTE-ROOM')}
            >
              ⚖️ {language === 'vi' ? 'Chuyển Sang Quyền Admin Phân Xử' : 'Open Admin Dispute Room'}
            </button>
          </div>
        </div>
      );

    case 'SCR-ADMIN-DISPUTE-ROOM':
      return (
        <div style={{ maxWidth: '920px', margin: '0 auto' }}>
          <div className="og-card" style={{ padding: '24px', marginBottom: '20px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <div>
                <h2 style={{ margin: 0, color: 'var(--og-color-primary)', fontSize: '20px' }}>
                  🏛️ {language === 'vi' ? 'Phòng Trọng Tài Escrow (Admin Dispute Room)' : 'Escrow Arbitration Room'}
                </h2>
                <div style={{ fontSize: '12px', color: 'var(--og-color-text-muted)' }}>
                  Hồ sơ: {MOCK_DISPUTE.id} · Đối tượng: {MOCK_DISPUTE.productTitle[language]}
                </div>
              </div>
              <button
                type="button"
                className="og-button og-button--danger og-button--sm"
                onClick={() => setActiveScreenId('SCR-ADMIN-DISPUTE-DECIDE')}
              >
                ⚖️ {language === 'vi' ? 'Ra Phán Quyết Ngay' : 'Deliver Verdict'}
              </button>
            </div>

            {/* Evidence Comparison Grid */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '20px' }}>
              {/* Buyer side */}
              <div style={{ border: '1px solid var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                  <strong>👤 NGƯỜI MUA (BUYER)</strong>
                  <span className="og-badge og-badge--primary">{MOCK_DISPUTE.buyerName}</span>
                </div>
                <p style={{ fontSize: '13px', color: 'var(--og-color-text-secondary)', margin: '0 0 12px' }}>
                  "{MOCK_DISPUTE.buyerStatement[language]}"
                </p>
                <div style={{ background: '#000', borderRadius: 'var(--og-radius-md)', height: '180px', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontSize: '12px' }}>
                  [ Video Đồng Kiểm Unbox Cùng Shipper (02:45) ]
                </div>
              </div>

              {/* Seller side */}
              <div style={{ border: '1px solid var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                  <strong>🏷️ NGƯỜI BÁN (SELLER)</strong>
                  <span className="og-badge og-badge--warning">{MOCK_DISPUTE.sellerName}</span>
                </div>
                <p style={{ fontSize: '13px', color: 'var(--og-color-text-secondary)', margin: '0 0 12px' }}>
                  "{MOCK_DISPUTE.sellerStatement ? MOCK_DISPUTE.sellerStatement[language] : ''}"
                </p>
                <div style={{ background: '#000', borderRadius: 'var(--og-radius-md)', height: '180px', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontSize: '12px' }}>
                  [ Video Đóng Gói Tại Kho Lúc Gửi Đi (01:15) ]
                </div>
              </div>
            </div>
          </div>
        </div>
      );

    case 'SCR-ADMIN-DISPUTE-DECIDE':
      return (
        <div className="og-card" style={{ maxWidth: '680px', margin: '0 auto', padding: '28px' }}>
          <h2 style={{ color: 'var(--og-color-danger)', margin: '0 0 8px' }}>
            ⚖️ {language === 'vi' ? 'Ban Hành Phán Quyết Trọng Tài & Giải Tỏa Escrow' : 'Arbitrator Final Verdict & Settlement'}
          </h2>
          <p style={{ fontSize: '13px', color: 'var(--og-color-text-secondary)', marginBottom: '20px' }}>
            {language === 'vi'
              ? 'Phán quyết này là quyết định cuối cùng từ O.G Escrow để điều chuyển dòng tiền.'
              : 'Binding resolution to disburse or refund frozen Escrow.'}
          </p>

          <div className="og-form-group">
            <label className="og-label">{language === 'vi' ? 'Quyết định phân xử' : 'Verdict Action'}</label>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '10px', border: '1px solid var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '12px' }}>
                <input
                  type="radio"
                  name="verdict"
                  checked={selectedVerdictAction === 'FULL_REFUND'}
                  onChange={() => setSelectedVerdictAction('FULL_REFUND')}
                />
                <div>
                  <strong>{language === 'vi' ? 'Chấp thuận khiếu nại — Hoàn 100% tiền cho Người mua' : 'Accept Dispute — 100% Refund to Buyer'}</strong>
                  <div style={{ fontSize: '12px', color: 'var(--og-color-text-muted)' }}>Người bán chịu phí ship hoàn hàng về</div>
                </div>
              </label>

              <label style={{ display: 'flex', alignItems: 'center', gap: '10px', border: '1px solid var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '12px' }}>
                <input
                  type="radio"
                  name="verdict"
                  checked={selectedVerdictAction === 'PARTIAL_REFUND'}
                  onChange={() => setSelectedVerdictAction('PARTIAL_REFUND')}
                />
                <div>
                  <strong>{language === 'vi' ? 'Hoàn tiền một phần (Bù trừ chi phí sửa chữa)' : 'Partial Refund Compensation'}</strong>
                  <div style={{ fontSize: '12px', color: 'var(--og-color-text-muted)' }}>Buyer giữ lại máy, Seller hoàn 500.000₫ bù lỗi nứt</div>
                </div>
              </label>

              <label style={{ display: 'flex', alignItems: 'center', gap: '10px', border: '1px solid var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '12px' }}>
                <input
                  type="radio"
                  name="verdict"
                  checked={selectedVerdictAction === 'RELEASE_SELLER'}
                  onChange={() => setSelectedVerdictAction('RELEASE_SELLER')}
                />
                <div>
                  <strong>{language === 'vi' ? 'Bác bỏ khiếu nại — Giải ngân tiền cho Người bán' : 'Reject Claim — Release Escrow to Seller'}</strong>
                  <div style={{ fontSize: '12px', color: 'var(--og-color-text-muted)' }}>Khiếu nại không có căn cứ hoặc lỗi do người mua làm rơi</div>
                </div>
              </label>
            </div>
          </div>

          <div className="og-form-group">
            <label className="og-label">{language === 'vi' ? 'Căn cứ phán quyết (Gửi cho hai bên)' : 'Arbitrator Justification'}</label>
            <textarea
              className="og-textarea"
              rows={4}
              value={arbitratorNote}
              onChange={e => setArbitratorNote(e.target.value)}
            />
          </div>

          <div style={{ display: 'flex', gap: '12px', marginTop: '20px' }}>
            <button
              type="button"
              className="og-button og-button--danger"
              style={{ flex: 1 }}
              onClick={handleApplyVerdict}
            >
              {language === 'vi' ? 'Thực Thi Phán Quyết & Điều Chuyển Tiền' : 'Execute Verdict & Disburse Funds'}
            </button>
            <button
              type="button"
              className="og-button og-button--outline"
              onClick={() => setActiveScreenId('SCR-ADMIN-DISPUTE-ROOM')}
            >
              {language === 'vi' ? 'Quay Lại' : 'Cancel'}
            </button>
          </div>
        </div>
      );

    default:
      return <div>Module 4: Screen {screenId}</div>;
  }
}
