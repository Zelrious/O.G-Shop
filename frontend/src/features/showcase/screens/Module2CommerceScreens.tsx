import { useState } from 'react';
import { useDemo, useI18n } from '../../../shared/context';
import {
  MOCK_PRODUCTS,
  MOCK_CATEGORIES,
  MOCK_VOUCHERS,
  MOCK_CHATS,
  MockProduct
} from '../../../shared/data/mockData';

interface ModuleProps {
  screenId: string;
}

export function Module2CommerceScreens({ screenId }: ModuleProps) {
  const { language } = useI18n();
  const {
    cart,
    addToCart,
    removeFromCart,
    activeVoucher,
    applyVoucher,
    setActiveScreenId,
    offerState,
    currentOfferPrice,
    acceptOffer,
    rejectOffer,
    makeCounterOffer
  } = useDemo();

  const [selectedProduct, setSelectedProduct] = useState<MockProduct>(MOCK_PRODUCTS[0]);
  const [selectedConditionFilter, setSelectedConditionFilter] = useState<string>('ALL');
  const [semanticQuery, setSemanticQuery] = useState('tìm máy ảnh chụp phong cảnh còn mới sensor sạch dưới 25 triệu');
  const [counterPriceInput, setCounterPriceInput] = useState('24000000');
  const [showCounterBox, setShowCounterBox] = useState(false);
  const [activeImageIndex, setActiveImageIndex] = useState(0);

  // New listing creation form state
  const [newTitle, setNewTitle] = useState('Bàn phím Leopold FC900R PD Ash Yellow');
  const [newPrice, setNewPrice] = useState('2400000');
  const [newCondition, setNewCondition] = useState<'like_new' | 'good' | 'fair' | 'vintage'>('like_new');

  const filteredProducts = MOCK_PRODUCTS.filter(p => {
    if (selectedConditionFilter === 'ALL') return true;
    return p.condition === selectedConditionFilter;
  });

  const cartTotal = cart.reduce((sum, item) => sum + item.price, 0);
  const discountVal = activeVoucher ? activeVoucher.discountAmount : 0;
  const shippingFee = 45000;
  const finalPayment = Math.max(0, cartTotal + shippingFee - discountVal);

  switch (screenId) {
    case 'SCR-BUYER-HOME':
      return (
        <div>
          {/* Trust Escrow Pledge Banner */}
          <div
            style={{
              background: 'linear-gradient(135deg, #1d4d38, #133626)',
              color: '#ffffff',
              borderRadius: 'var(--og-radius-lg)',
              padding: '32px 24px',
              marginBottom: '28px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              flexWrap: 'wrap',
              gap: '20px',
              boxShadow: 'var(--og-shadow-md)'
            }}
          >
            <div style={{ maxWidth: '640px' }}>
              <span className="og-badge" style={{ background: '#c6933a', color: '#fff', marginBottom: '12px', fontWeight: 700 }}>
                {language === 'vi' ? '🛡️ O.G ESCROW BẢO VỆ 100%' : '🛡️ 100% ESCROW PROTECTED'}
              </span>
              <h1 style={{ fontSize: '28px', margin: '8px 0', lineHeight: 1.2 }}>
                {language === 'vi' ? 'Đồ cũ còn giá trị. Giao dịch an tâm tuyệt đối.' : 'Old but Gold. Guaranteed transparent second-hand trade.'}
              </h1>
              <p style={{ margin: 0, opacity: 0.9, fontSize: '14px' }}>
                {language === 'vi'
                  ? 'Tiền của bạn chỉ được chuyển cho người bán sau khi bạn mở hộp đồng kiểm cùng shipper và bấm Hài lòng.'
                  : 'Funds only released to seller after you co-inspect package with courier and confirm satisfaction.'}
              </p>
            </div>
            <div style={{ display: 'flex', gap: '10px' }}>
              <button
                type="button"
                className="og-button og-button--secondary"
                onClick={() => setActiveScreenId('SCR-BUYER-SEMANTIC')}
              >
                ✨ {language === 'vi' ? 'Tìm Bằng AI' : 'AI Search'}
              </button>
              <button
                type="button"
                className="og-button"
                style={{ background: '#ffffff', color: '#1d4d38' }}
                onClick={() => setActiveScreenId('SCR-BUYER-CONDITION')}
              >
                📋 {language === 'vi' ? 'Xem Thang Đo Hao Mòn' : 'Wear Standards'}
              </button>
            </div>
          </div>

          {/* Category Quick Rail */}
          <div style={{ marginBottom: '32px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h3 style={{ margin: 0, color: 'var(--og-color-primary)' }}>
                {language === 'vi' ? 'Danh Mục Đồ Cũ Nổi Bật' : 'Featured Second-Hand Categories'}
              </h3>
              <button type="button" className="og-link-button" onClick={() => setActiveScreenId('SCR-BUYER-SEARCH')}>
                {language === 'vi' ? 'Xem tất cả →' : 'View all →'}
              </button>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(130px, 1fr))', gap: '12px' }}>
              {MOCK_CATEGORIES.map(cat => (
                <button
                  key={cat.id}
                  type="button"
                  className="og-card"
                  style={{
                    padding: '16px 10px',
                    textAlign: 'center',
                    cursor: 'pointer',
                    transition: 'transform 0.2s ease',
                    border: '1px solid var(--og-color-border)'
                  }}
                  onClick={() => setActiveScreenId('SCR-BUYER-SEARCH')}
                >
                  <div style={{ fontSize: '28px', marginBottom: '6px' }}>{cat.icon}</div>
                  <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--og-color-text-primary)', lineHeight: 1.3 }}>
                    {cat.name[language]}
                  </div>
                  <div style={{ fontSize: '10px', color: 'var(--og-color-text-muted)', marginTop: '4px' }}>
                    {cat.count} {language === 'vi' ? 'món' : 'items'}
                  </div>
                </button>
              ))}
            </div>
          </div>

          {/* Curated Product Grid */}
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h3 style={{ margin: 0, color: 'var(--og-color-primary)' }}>
                🔥 {language === 'vi' ? 'Đồ Sưu Tầm Tuyển Chọn (Đã Kiểm Định)' : 'Curated Verified Listings'}
              </h3>
              <span className="og-badge og-badge--primary">{language === 'vi' ? 'Có Video Đồng Kiểm' : 'Video Co-Inspect'}</span>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: '20px' }}>
              {MOCK_PRODUCTS.map(product => (
                <div
                  key={product.id}
                  className="og-card og-product-card"
                  style={{ overflow: 'hidden', cursor: 'pointer', display: 'flex', flexDirection: 'column' }}
                  onClick={() => { setSelectedProduct(product); setActiveScreenId('SCR-BUYER-DETAIL'); }}
                >
                  <div style={{ position: 'relative', height: '180px' }}>
                    <img
                      src={product.images[0]}
                      alt={product.title[language]}
                      style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                    />
                    <span
                      style={{
                        position: 'absolute',
                        top: '10px',
                        left: '10px',
                        fontSize: '11px',
                        fontWeight: 700,
                        padding: '3px 8px',
                        borderRadius: '4px',
                        background: 'rgba(20, 35, 28, 0.85)',
                        color: '#fff',
                        backdropFilter: 'blur(4px)'
                      }}
                    >
                      {product.conditionPercent}% · {product.condition.toUpperCase()}
                    </span>
                    {product.coInspectionVideoAvailable && (
                      <span
                        style={{
                          position: 'absolute',
                          bottom: '10px',
                          right: '10px',
                          fontSize: '10px',
                          fontWeight: 700,
                          padding: '2px 6px',
                          borderRadius: '4px',
                          background: 'var(--og-color-success)',
                          color: '#fff'
                        }}
                      >
                        🎥 Video sẵn
                      </span>
                    )}
                  </div>

                  <div style={{ padding: '16px', flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                    <div>
                      <div style={{ fontSize: '11px', color: 'var(--og-color-accent-gold)', fontWeight: 600 }}>
                        {product.categoryName[language]}
                      </div>
                      <h4 style={{ margin: '4px 0 8px', fontSize: '14px', lineHeight: 1.4, color: 'var(--og-color-text-primary)' }}>
                        {product.title[language]}
                      </h4>
                    </div>

                    <div>
                      <div style={{ display: 'flex', alignItems: 'baseline', gap: '8px', marginBottom: '8px' }}>
                        <span style={{ fontSize: '18px', fontWeight: 700, color: 'var(--og-color-primary)' }}>
                          {product.price.toLocaleString()}₫
                        </span>
                        <span style={{ fontSize: '12px', color: 'var(--og-color-text-muted)', textDecoration: 'line-through' }}>
                          {product.originalPrice.toLocaleString()}₫
                        </span>
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: '11px', color: 'var(--og-color-text-muted)', borderTop: '1px solid var(--og-color-border)', paddingTop: '8px' }}>
                        <span>📍 {product.location.split(',')[0]}</span>
                        <span>⭐ {product.seller.rating} ({product.seller.reviewCount})</span>
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      );

    case 'SCR-BUYER-SEARCH':
      return (
        <div>
          <div style={{ marginBottom: '20px' }}>
            <h2 style={{ color: 'var(--og-color-primary)', margin: '0 0 12px' }}>
              🔍 {language === 'vi' ? 'Bộ Lọc Đồ Cũ & Thang Độ Hao Mòn' : 'Condition Filter & Second-hand Search'}
            </h2>

            {/* Filter Pills */}
            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', alignItems: 'center' }}>
              <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--og-color-text-muted)' }}>
                {language === 'vi' ? 'Độ hao mòn:' : 'Wear Grade:'}
              </span>
              <button
                type="button"
                className={`og-button og-button--sm ${selectedConditionFilter === 'ALL' ? 'og-button--primary' : 'og-button--outline'}`}
                onClick={() => setSelectedConditionFilter('ALL')}
              >
                {language === 'vi' ? 'Tất cả (6)' : 'All (6)'}
              </button>
              <button
                type="button"
                className={`og-button og-button--sm ${selectedConditionFilter === 'like_new' ? 'og-button--primary' : 'og-button--outline'}`}
                onClick={() => setSelectedConditionFilter('like_new')}
              >
                ✨ 99% {language === 'vi' ? 'Như mới' : 'Like New'}
              </button>
              <button
                type="button"
                className={`og-button og-button--sm ${selectedConditionFilter === 'good' ? 'og-button--primary' : 'og-button--outline'}`}
                onClick={() => setSelectedConditionFilter('good')}
              >
                👍 90-95% {language === 'vi' ? 'Khá tốt' : 'Good'}
              </button>
              <button
                type="button"
                className={`og-button og-button--sm ${selectedConditionFilter === 'vintage' ? 'og-button--primary' : 'og-button--outline'}`}
                onClick={() => setSelectedConditionFilter('vintage')}
              >
                🏺 {language === 'vi' ? 'Đồ cổ / Sưu tầm' : 'Vintage'}
              </button>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: '20px' }}>
            {filteredProducts.map(product => (
              <div
                key={product.id}
                className="og-card"
                style={{ padding: '16px', cursor: 'pointer' }}
                onClick={() => { setSelectedProduct(product); setActiveScreenId('SCR-BUYER-DETAIL'); }}
              >
                <img
                  src={product.images[0]}
                  alt={product.title[language]}
                  style={{ width: '100%', height: '160px', objectFit: 'cover', borderRadius: 'var(--og-radius-md)', marginBottom: '12px' }}
                />
                <span className="og-badge og-badge--primary">{product.conditionPercent}% · {product.condition}</span>
                <h4 style={{ margin: '8px 0 4px', fontSize: '14px' }}>{product.title[language]}</h4>
                <div style={{ fontSize: '18px', fontWeight: 700, color: 'var(--og-color-primary)' }}>
                  {product.price.toLocaleString()}₫
                </div>
              </div>
            ))}
          </div>
        </div>
      );

    case 'SCR-BUYER-SEMANTIC':
      return (
        <div style={{ maxWidth: '800px', margin: '0 auto' }}>
          <div className="og-card" style={{ padding: '28px', marginBottom: '24px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
              <span style={{ fontSize: '28px' }}>✨</span>
              <div>
                <h2 style={{ margin: 0, color: 'var(--og-color-primary)' }}>
                  {language === 'vi' ? 'Tìm Kiếm AI Bằng Ngôn Ngữ Tự Nhiên' : 'AI Semantic Second-hand Search'}
                </h2>
                <p style={{ margin: '4px 0 0', fontSize: '13px', color: 'var(--og-color-text-secondary)' }}>
                  {language === 'vi' ? 'Mô tả nhu cầu thật của bạn, AI sẽ trích xuất yêu cầu về độ bền, giá và tình trạng' : 'Describe your need naturally; AI filters by condition & price intent'}
                </p>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '8px', marginTop: '16px' }}>
              <input
                type="text"
                className="og-input"
                value={semanticQuery}
                onChange={e => setSemanticQuery(e.target.value)}
                style={{ flex: 1 }}
              />
              <button type="button" className="og-button og-button--primary">
                {language === 'vi' ? 'Phân Tích & Tìm' : 'Search AI'}
              </button>
            </div>

            {/* AI Extracted Parameters */}
            <div style={{ marginTop: '16px', background: 'var(--og-color-surface-subtle)', borderRadius: 'var(--og-radius-md)', padding: '12px 16px', fontSize: '12px' }}>
              <strong style={{ color: 'var(--og-color-primary)' }}>🤖 {language === 'vi' ? 'AI đã phân tích ngữ cảnh:' : 'AI extracted context:'}</strong>
              <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginTop: '6px' }}>
                <span className="og-badge og-badge--success">Ngành: Máy ảnh (Cameras)</span>
                <span className="og-badge og-badge--primary">Giá: &lt; 25.000.000₫</span>
                <span className="og-badge og-badge--warning">Tình trạng: &gt; 95% Sensor sạch</span>
                <span className="og-badge og-badge--info">Escrow: Bắt buộc</span>
              </div>
            </div>
          </div>

          {/* AI Result Card */}
          <div className="og-card" style={{ padding: '20px', border: '2px solid var(--og-color-primary)' }}>
            <div style={{ display: 'flex', gap: '20px', flexWrap: 'wrap' }}>
              <img
                src={MOCK_PRODUCTS[0].images[0]}
                alt="Sony A7 III"
                style={{ width: '160px', height: '140px', objectFit: 'cover', borderRadius: 'var(--og-radius-md)' }}
              />
              <div style={{ flex: 1 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <span className="og-badge og-badge--success">🎯 98% {language === 'vi' ? 'Phù hợp yêu cầu' : 'Intent Match'}</span>
                  <span style={{ fontSize: '18px', fontWeight: 700, color: 'var(--og-color-primary)' }}>24.500.000₫</span>
                </div>
                <h3 style={{ margin: '8px 0 4px', fontSize: '16px' }}>{MOCK_PRODUCTS[0].title[language]}</h3>
                <p style={{ margin: '0 0 12px', fontSize: '13px', color: 'var(--og-color-text-secondary)' }}>
                  {MOCK_PRODUCTS[0].conditionDescription[language]}
                </p>
                <button
                  type="button"
                  className="og-button og-button--primary og-button--sm"
                  onClick={() => { setSelectedProduct(MOCK_PRODUCTS[0]); setActiveScreenId('SCR-BUYER-DETAIL'); }}
                >
                  {language === 'vi' ? 'Xem Chi Tiết Món Này →' : 'View Item →'}
                </button>
              </div>
            </div>
          </div>
        </div>
      );

    case 'SCR-BUYER-DETAIL':
      return (
        <div style={{ maxWidth: '1000px', margin: '0 auto' }}>
          {/* Main Gallery & Buy Box */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '32px', marginBottom: '32px' }}>
            {/* Gallery */}
            <div>
              <div style={{ height: '360px', borderRadius: 'var(--og-radius-lg)', overflow: 'hidden', marginBottom: '12px', border: '1px solid var(--og-color-border)' }}>
                <img
                  src={selectedProduct.images[activeImageIndex] || selectedProduct.images[0]}
                  alt="Product"
                  style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                />
              </div>
              <div style={{ display: 'flex', gap: '8px' }}>
                {selectedProduct.images.map((img, idx) => (
                  <button
                    key={idx}
                    type="button"
                    style={{
                      width: '64px',
                      height: '64px',
                      borderRadius: 'var(--og-radius-md)',
                      overflow: 'hidden',
                      border: activeImageIndex === idx ? '2px solid var(--og-color-primary)' : '1px solid var(--og-color-border)',
                      padding: 0,
                      cursor: 'pointer'
                    }}
                    onClick={() => setActiveImageIndex(idx)}
                  >
                    <img src={img} alt="thumb" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                  </button>
                ))}
              </div>
            </div>

            {/* Buy Box & Specs */}
            <div>
              <span className="og-badge og-badge--primary" style={{ marginBottom: '8px' }}>
                {selectedProduct.categoryName[language]}
              </span>
              <h1 style={{ fontSize: '22px', margin: '6px 0 12px', lineHeight: 1.3, color: 'var(--og-color-text-primary)' }}>
                {selectedProduct.title[language]}
              </h1>

              <div style={{ display: 'flex', alignItems: 'baseline', gap: '12px', marginBottom: '16px' }}>
                <span style={{ fontSize: '28px', fontWeight: 700, color: 'var(--og-color-primary)' }}>
                  {selectedProduct.price.toLocaleString()}₫
                </span>
                <span style={{ fontSize: '14px', color: 'var(--og-color-text-muted)', textDecoration: 'line-through' }}>
                  {selectedProduct.originalPrice.toLocaleString()}₫
                </span>
              </div>

              {/* Wear appraisal bar */}
              <div className="og-card" style={{ padding: '14px', marginBottom: '16px', background: 'var(--og-color-surface-subtle)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
                  <strong>{language === 'vi' ? 'Độ hao mòn thẩm định:' : 'Wear Score:'}</strong>
                  <span style={{ fontWeight: 700, color: 'var(--og-color-primary)' }}>{selectedProduct.conditionPercent}% ({selectedProduct.condition.toUpperCase()})</span>
                </div>
                <div className="og-wear-meter">
                  <div className="og-wear-bar">
                    <div className="og-wear-bar__fill" style={{ width: `${selectedProduct.conditionPercent}%` }} />
                  </div>
                </div>
                <p style={{ margin: '6px 0 0', fontSize: '12px', color: 'var(--og-color-text-secondary)' }}>
                  {selectedProduct.conditionDescription[language]}
                </p>
              </div>

              {/* Defect declaration pill box */}
              {selectedProduct.defects.length > 0 && (
                <div className="og-defect-box" style={{ marginBottom: '16px' }}>
                  <strong style={{ fontSize: '12px', color: 'var(--og-color-warning)' }}>
                    ⚠️ {language === 'vi' ? 'Khuyết điểm đã minh bạch:' : 'Declared cosmetic defects:'}
                  </strong>
                  <ul style={{ margin: '4px 0 0', paddingLeft: '18px', fontSize: '12px' }}>
                    {selectedProduct.defects.map((d, i) => (
                      <li key={i}>{d[language]}</li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Co-inspection Callout */}
              <div className="og-co-inspect-card">
                <span>🎥</span>
                <div style={{ fontSize: '12px' }}>
                  <strong>{language === 'vi' ? 'Bắt buộc đồng kiểm khi nhận hàng:' : 'Mandatory Co-inspection:'}</strong>{' '}
                  {language === 'vi'
                    ? 'Bạn được mở hộp và bật nguồn kiểm tra cùng shipper. Nếu không đúng mô tả, shipper sẽ mang hàng về mà không mất phí.'
                    : 'Unbox and test before courier. If condition mismatches, parcel returns free of charge.'}
                </div>
              </div>

              {/* Action Buttons */}
              <div style={{ display: 'flex', gap: '10px', marginTop: '20px' }}>
                <button
                  type="button"
                  className="og-button og-button--primary"
                  style={{ flex: 1 }}
                  onClick={() => { addToCart(selectedProduct); setActiveScreenId('SCR-BUYER-CHECKOUT'); }}
                >
                  🛡️ {language === 'vi' ? 'Mua Ngay (Escrow Bảo Vệ)' : 'Buy Now (Escrow)'}
                </button>
                <button
                  type="button"
                  className="og-button og-button--secondary"
                  onClick={() => setActiveScreenId('SCR-CHAT-OFFER')}
                >
                  🤝 {language === 'vi' ? 'Trả Giá' : 'Make Offer'}
                </button>
                <button
                  type="button"
                  className="og-button og-button--outline"
                  onClick={() => setActiveScreenId('SCR-CHAT-PRODUCT')}
                >
                  💬 {language === 'vi' ? 'Chat' : 'Chat'}
                </button>
              </div>
            </div>
          </div>
        </div>
      );

    case 'SCR-BUYER-CONDITION':
      return (
        <div className="og-card" style={{ maxWidth: '680px', margin: '0 auto', padding: '32px' }}>
          <h2 style={{ color: 'var(--og-color-primary)', marginTop: 0, textAlign: 'center' }}>
            📋 {language === 'vi' ? 'Quy Chuẩn Thẩm Định Tình Trạng Đồ Cũ O.G' : 'O.G Wear & Condition Standards'}
          </h2>
          <p style={{ textAlign: 'center', color: 'var(--og-color-text-secondary)', fontSize: '13px', marginBottom: '24px' }}>
            {language === 'vi' ? 'Tiêu chuẩn bắt buộc áp dụng cho toàn bộ người bán trên sàn' : 'Strict classification mandatory for all platform listings'}
          </p>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <div style={{ border: '1px solid var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '16px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <strong style={{ color: 'var(--og-color-primary)' }}>✨ Như Mới 99% (Like New)</strong>
                <span className="og-badge og-badge--success">98 - 100%</span>
              </div>
              <p style={{ margin: '6px 0 0', fontSize: '13px', color: 'var(--og-color-text-secondary)' }}>
                {language === 'vi'
                  ? 'Gần như không tì vết, hoạt động 100% công năng, không có vết cấn móp, đầy đủ phụ kiện gốc.'
                  : 'Flawless condition, zero functional issues, no dents, all original accessories included.'}
              </p>
            </div>

            <div style={{ border: '1px solid var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '16px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <strong style={{ color: 'var(--og-color-primary)' }}>👍 Khá Tốt 90 - 95% (Good)</strong>
                <span className="og-badge og-badge--primary">90 - 95%</span>
              </div>
              <p style={{ margin: '6px 0 0', fontSize: '13px', color: 'var(--og-color-text-secondary)' }}>
                {language === 'vi'
                  ? 'Có vết xước dăm nhẹ do sử dụng thông thường. Phần cứng và bo mạch hoạt động chuẩn mực. Khuyết điểm bắt buộc chụp rõ.'
                  : 'Light superficial micro-scratches from normal wear. Internals 100% functional. Defects must be photographed.'}
              </p>
            </div>

            <div style={{ border: '1px solid var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '16px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <strong style={{ color: 'var(--og-color-warning)' }}>⚠️ Đã Qua Sử Dụng 80% (Fair)</strong>
                <span className="og-badge og-badge--warning">80 - 89%</span>
              </div>
              <p style={{ margin: '6px 0 0', fontSize: '13px', color: 'var(--og-color-text-secondary)' }}>
                {language === 'vi'
                  ? 'Có vết trầy xước rõ ràng hoặc chai pin nhẹ nhưng vẫn hoạt động tốt các chức năng cốt lõi.'
                  : 'Noticeable cosmetic scuffs or reduced battery health but core functionality intact.'}
              </p>
            </div>

            <div style={{ border: '1px solid var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '16px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <strong style={{ color: 'var(--og-color-accent-gold)' }}>🏺 Đồ Cổ / Sưu Tầm (Vintage)</strong>
                <span className="og-badge og-badge--warning">Collectible</span>
              </div>
              <p style={{ margin: '6px 0 0', fontSize: '13px', color: 'var(--og-color-text-secondary)' }}>
                {language === 'vi'
                  ? 'Sản phẩm ngưng sản xuất hoặc trên 10 năm tuổi. Đánh giá dựa trên độ nguyên bản (zin) của linh kiện.'
                  : 'Discontinued or >10-year-old items. Appraised based on authenticity of original parts.'}
              </p>
            </div>
          </div>

          <button
            type="button"
            className="og-button og-button--primary"
            style={{ width: '100%', marginTop: '24px' }}
            onClick={() => setActiveScreenId('SCR-BUYER-DETAIL')}
          >
            {language === 'vi' ? 'Đã Hiểu, Quay Lại Mua Hàng' : 'Understood, Back to Item'}
          </button>
        </div>
      );

    case 'SCR-BUYER-CART':
      return (
        <div style={{ maxWidth: '780px', margin: '0 auto' }}>
          <h2 style={{ color: 'var(--og-color-primary)', marginBottom: '20px' }}>
            🛒 {language === 'vi' ? 'Giỏ Hàng Đồ Cũ Của Bạn' : 'Your Second-Hand Cart'}
          </h2>

          {cart.length === 0 ? (
            <div className="og-card" style={{ padding: '40px', textAlign: 'center' }}>
              <p>{language === 'vi' ? 'Giỏ hàng đang trống.' : 'Cart is empty.'}</p>
              <button type="button" className="og-button og-button--primary" onClick={() => setActiveScreenId('SCR-BUYER-HOME')}>
                {language === 'vi' ? 'Khám phá đồ cũ' : 'Explore Items'}
              </button>
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '24px' }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {cart.map(item => (
                  <div key={item.id} className="og-card" style={{ padding: '16px', display: 'flex', gap: '14px' }}>
                    <img src={item.images[0]} alt="thumb" style={{ width: '80px', height: '80px', objectFit: 'cover', borderRadius: 'var(--og-radius-md)' }} />
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: '11px', color: 'var(--og-color-accent-gold)' }}>{item.seller.name}</div>
                      <h4 style={{ margin: '2px 0 4px', fontSize: '14px' }}>{item.title[language]}</h4>
                      <div style={{ fontSize: '16px', fontWeight: 700, color: 'var(--og-color-primary)' }}>{item.price.toLocaleString()}₫</div>
                    </div>
                    <button
                      type="button"
                      className="og-button og-button--ghost og-button--sm"
                      onClick={() => removeFromCart(item.id)}
                      title="Xóa"
                    >
                      ✕
                    </button>
                  </div>
                ))}
              </div>

              {/* Cart Summary */}
              <div className="og-card" style={{ padding: '20px', height: 'fit-content' }}>
                <h3 style={{ marginTop: 0, color: 'var(--og-color-primary)' }}>
                  {language === 'vi' ? 'Tóm Tắt Đơn Hàng' : 'Order Summary'}
                </h3>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', fontSize: '13px' }}>
                  <span>{language === 'vi' ? 'Tạm tính' : 'Subtotal'}:</span>
                  <strong>{cartTotal.toLocaleString()}₫</strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', fontSize: '13px' }}>
                  <span>{language === 'vi' ? 'Phí giao hàng đồng kiểm' : 'Co-inspection shipping'}:</span>
                  <strong>+{shippingFee.toLocaleString()}₫</strong>
                </div>
                {activeVoucher && (
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', fontSize: '13px', color: 'var(--og-color-success)' }}>
                    <span>Voucher ({activeVoucher.code}):</span>
                    <strong>-{discountVal.toLocaleString()}₫</strong>
                  </div>
                )}
                <div style={{ borderTop: '1px solid var(--og-color-border)', paddingTop: '10px', marginTop: '10px', display: 'flex', justifyContent: 'space-between', fontSize: '16px' }}>
                  <strong>{language === 'vi' ? 'Tổng thanh toán Escrow:' : 'Total Escrow Hold:'}</strong>
                  <strong style={{ color: 'var(--og-color-primary)' }}>{finalPayment.toLocaleString()}₫</strong>
                </div>

                <button
                  type="button"
                  className="og-button og-button--primary"
                  style={{ width: '100%', marginTop: '16px' }}
                  onClick={() => setActiveScreenId('SCR-BUYER-CHECKOUT')}
                >
                  🛡️ {language === 'vi' ? 'Tiến Hành Thanh Toán' : 'Proceed to Escrow Checkout'}
                </button>
              </div>
            </div>
          )}
        </div>
      );

    case 'SCR-BUYER-VOUCHER-MODAL':
      return (
        <div className="og-card" style={{ maxWidth: '540px', margin: '0 auto', padding: '24px' }}>
          <h2 style={{ color: 'var(--og-color-primary)', marginTop: 0 }}>
            🎟️ {language === 'vi' ? 'Chọn Mã Giảm Giá O.G Escrow' : 'Select Escrow Voucher'}
          </h2>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', margin: '16px 0' }}>
            {MOCK_VOUCHERS.map(v => {
              const isSelected = activeVoucher?.id === v.id;
              return (
                <div
                  key={v.id}
                  style={{
                    border: isSelected ? '2px solid var(--og-color-primary)' : '1px solid var(--og-color-border)',
                    borderRadius: 'var(--og-radius-md)',
                    padding: '14px',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    background: isSelected ? 'var(--og-color-primary-light)' : 'var(--og-color-surface)'
                  }}
                >
                  <div>
                    <code style={{ fontSize: '14px', fontWeight: 700, color: 'var(--og-color-accent-gold)' }}>{v.code}</code>
                    <div style={{ fontSize: '13px', fontWeight: 600, marginTop: '2px' }}>{v.title[language]}</div>
                    <div style={{ fontSize: '11px', color: 'var(--og-color-text-muted)' }}>{language === 'vi' ? `HSD: ${v.expiry}` : `Exp: ${v.expiry}`}</div>
                  </div>
                  <button
                    type="button"
                    className={`og-button og-button--sm ${isSelected ? 'og-button--primary' : 'og-button--outline'}`}
                    onClick={() => applyVoucher(isSelected ? null : v)}
                  >
                    {isSelected ? '✓ Đang áp dụng' : 'Áp Dụng'}
                  </button>
                </div>
              );
            })}
          </div>

          <button
            type="button"
            className="og-button og-button--primary"
            style={{ width: '100%' }}
            onClick={() => setActiveScreenId('SCR-BUYER-CHECKOUT')}
          >
            {language === 'vi' ? 'Xác Nhận & Quay Lại' : 'Done & Return'}
          </button>
        </div>
      );

    case 'SCR-BUYER-CHECKOUT':
      return (
        <div style={{ maxWidth: '780px', margin: '0 auto' }}>
          <h2 style={{ color: 'var(--og-color-primary)', marginBottom: '20px' }}>
            🛡️ {language === 'vi' ? 'Thanh Toán Ký Quỹ Escrow An Toàn' : 'Escrow Protected Checkout'}
          </h2>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '24px' }}>
            <div>
              {/* Shipping address */}
              <div className="og-card" style={{ padding: '16px', marginBottom: '16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <strong>📍 {language === 'vi' ? 'Địa Chỉ Nhận Hàng Đồng Kiểm' : 'Delivery Address'}</strong>
                  <button type="button" className="og-link-button" onClick={() => setActiveScreenId('SCR-USER-01')}>
                    {language === 'vi' ? 'Thay đổi' : 'Change'}
                  </button>
                </div>
                <div style={{ fontSize: '13px', marginTop: '6px' }}>
                  <strong>Bảo Khánh · 0908 123 456</strong>
                  <p style={{ margin: '2px 0 0', color: 'var(--og-color-text-secondary)' }}>
                    Tòa nhà Landmark 81, Phường 22, Quận Bình Thạnh, TP. Hồ Chí Minh
                  </p>
                </div>
              </div>

              {/* Payment methods */}
              <div className="og-card" style={{ padding: '16px' }}>
                <strong style={{ fontSize: '14px' }}>💳 {language === 'vi' ? 'Phương Thức Thanh Toán Escrow' : 'Payment Method'}</strong>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginTop: '12px' }}>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '10px', border: '1px solid var(--og-color-primary)', borderRadius: 'var(--og-radius-md)', padding: '12px', background: 'var(--og-color-primary-light)' }}>
                    <input type="radio" name="paymentMethod" defaultChecked />
                    <div>
                      <strong>Cổng VNPAY Escrow (QR Code / Thẻ ATM)</strong>
                      <div style={{ fontSize: '11px', color: 'var(--og-color-text-secondary)' }}>Tiền giữ an toàn trong tài khoản đảm bảo O.G</div>
                    </div>
                  </label>

                  <label style={{ display: 'flex', alignItems: 'center', gap: '10px', border: '1px solid var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '12px' }}>
                    <input type="radio" name="paymentMethod" />
                    <div>
                      <strong>Chuyển Khoản Ngân Hàng Kèm Cú Pháp Tự Động</strong>
                      <div style={{ fontSize: '11px', color: 'var(--og-color-text-secondary)' }}>Xác thực trong 30 giây</div>
                    </div>
                  </label>
                </div>
              </div>
            </div>

            {/* Bill & Pay */}
            <div className="og-card" style={{ padding: '20px', height: 'fit-content' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                <strong>{language === 'vi' ? 'Mã Giảm Giá' : 'Voucher'}:</strong>
                <button type="button" className="og-link-button" onClick={() => setActiveScreenId('SCR-BUYER-VOUCHER-MODAL')}>
                  {activeVoucher ? `${activeVoucher.code} (-${discountVal.toLocaleString()}₫)` : (language === 'vi' ? 'Chọn mã →' : 'Select →')}
                </button>
              </div>

              <div style={{ borderTop: '1px solid var(--og-color-border)', paddingTop: '10px', display: 'flex', justifyContent: 'space-between', fontSize: '18px' }}>
                <strong>{language === 'vi' ? 'Thanh Toán Ký Quỹ:' : 'Pay to Escrow:'}</strong>
                <strong style={{ color: 'var(--og-color-primary)' }}>{finalPayment.toLocaleString()}₫</strong>
              </div>

              <button
                type="button"
                className="og-button og-button--primary"
                style={{ width: '100%', marginTop: '18px' }}
                onClick={() => setActiveScreenId('SCR-BUYER-PAYMENT-SUCCESS')}
              >
                🔒 {language === 'vi' ? 'Xác Nhận & Nạp Tiền Ký Quỹ' : 'Authorize Escrow Payment'}
              </button>
            </div>
          </div>
        </div>
      );

    case 'SCR-BUYER-PAYMENT-SUCCESS':
      return (
        <div className="og-card" style={{ maxWidth: '600px', margin: '0 auto', padding: '36px', textAlign: 'center' }}>
          <div style={{ width: '72px', height: '72px', borderRadius: '50%', background: 'var(--og-color-success-bg)', color: 'var(--og-color-success)', fontSize: '36px', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px' }}>
            ✓
          </div>
          <h2 style={{ color: 'var(--og-color-primary)', margin: '0 0 8px' }}>
            {language === 'vi' ? 'Thanh Toán Escrow Thành Công!' : 'Payment Locked in Escrow!'}
          </h2>
          <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '14px', marginBottom: '24px' }}>
            {language === 'vi'
              ? 'Số tiền 24.495.000₫ đã được tạm giữ an toàn. Người bán đã nhận được thông báo để đóng gói kèm video đồng kiểm.'
              : '24,495,000₫ securely locked in Escrow. Seller notified to pack item with co-inspection footage.'}
          </p>

          <div style={{ background: 'var(--og-color-surface-subtle)', borderRadius: 'var(--og-radius-md)', padding: '16px', textAlign: 'left', marginBottom: '24px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', fontSize: '13px' }}>
              <span>{language === 'vi' ? 'Mã Đơn Hàng' : 'Order ID'}:</span>
              <strong>OG-2026-88912</strong>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', fontSize: '13px' }}>
              <span>{language === 'vi' ? 'Trạng Thái Tiền' : 'Funds Status'}:</span>
              <span className="og-badge og-badge--success">ESCROW_HOLDING</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
              <span>{language === 'vi' ? 'Bước Tiếp Theo' : 'Next Step'}:</span>
              <strong>{language === 'vi' ? 'Người bán quay video & gửi hàng' : 'Seller packaging & dispatch'}</strong>
            </div>
          </div>

          <div style={{ display: 'flex', gap: '12px', justifyContent: 'center' }}>
            <button type="button" className="og-button og-button--primary" onClick={() => setActiveScreenId('SCR-SHIP-TRACKING')}>
              📦 {language === 'vi' ? 'Theo Dõi Đơn Hàng' : 'Track Order'}
            </button>
            <button type="button" className="og-button og-button--outline" onClick={() => setActiveScreenId('SCR-BUYER-HOME')}>
              {language === 'vi' ? 'Tiếp Tục Xem Hàng' : 'Continue Shopping'}
            </button>
          </div>
        </div>
      );

    case 'SCR-CHAT-PRODUCT':
    case 'SCR-CHAT-OFFER':
    case 'SCR-CHAT-SAFETY-WARNING':
      return (
        <div style={{ maxWidth: '780px', margin: '0 auto' }}>
          <div className="og-card" style={{ display: 'flex', flexDirection: 'column', height: '620px', padding: 0, overflow: 'hidden' }}>
            {/* Pinned Product Card in Chat Header */}
            <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--og-color-border)', background: 'var(--og-color-surface-subtle)', display: 'flex', alignItems: 'center', gap: '12px' }}>
              <img src={MOCK_PRODUCTS[0].images[0]} alt="thumb" style={{ width: '48px', height: '48px', objectFit: 'cover', borderRadius: 'var(--og-radius-md)' }} />
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: '11px', color: 'var(--og-color-accent-gold)', fontWeight: 600 }}>Minh Camera Saigon · ⭐ 4.9</div>
                <div style={{ fontSize: '13px', fontWeight: 600, color: 'var(--og-color-text-primary)' }}>{MOCK_PRODUCTS[0].title[language]}</div>
                <div style={{ fontSize: '14px', fontWeight: 700, color: 'var(--og-color-primary)' }}>{MOCK_PRODUCTS[0].price.toLocaleString()}₫</div>
              </div>
              <button
                type="button"
                className="og-button og-button--secondary og-button--sm"
                onClick={() => setActiveScreenId('SCR-CHAT-OFFER')}
              >
                🤝 {language === 'vi' ? 'Trả Giá' : 'Offer'}
              </button>
            </div>

            {/* Off-platform Safety Warning Banner */}
            <div className="og-safety-warning" style={{ margin: 0, borderRadius: 0 }}>
              <strong>⚠️ {language === 'vi' ? 'CẢNH BÁO AN TOÀN ESCROW:' : 'SAFETY WARNING:'}</strong>{' '}
              {language === 'vi'
                ? 'Không bao giờ chuyển tiền cọc hoặc gửi hàng ngoài sàn. Hệ thống sẽ KHÔNG thể bảo vệ bạn nếu giao dịch ngoài O.G Shop!'
                : 'Never conduct off-platform payments. You forfeit Escrow buyer protection!'}
            </div>

            {/* Chat Body */}
            <div style={{ flex: 1, padding: '16px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {MOCK_CHATS.map(msg => (
                <div
                  key={msg.id}
                  style={{
                    alignSelf: msg.isSeller ? 'flex-start' : 'flex-end',
                    maxWidth: '80%'
                  }}
                >
                  <div style={{ fontSize: '11px', color: 'var(--og-color-text-muted)', marginBottom: '2px', textAlign: msg.isSeller ? 'left' : 'right' }}>
                    {msg.senderName} · {msg.time}
                  </div>

                  {msg.type === 'offer' && msg.offerDetails ? (
                    /* Interactive Bargaining Card */
                    <div className="og-offer-card">
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                        <span style={{ fontSize: '12px', fontWeight: 700, color: 'var(--og-color-accent-gold)' }}>🤝 ĐỀ XUẤT TRẢ GIÁ</span>
                        <span className="og-badge og-badge--warning">{offerState.toUpperCase()}</span>
                      </div>
                      <div style={{ fontSize: '12px', color: 'var(--og-color-text-muted)' }}>
                        {language === 'vi' ? 'Giá gốc:' : 'Original:'} <del>{msg.offerDetails.originalPrice.toLocaleString()}₫</del>
                      </div>
                      <div className="og-offer-card__price">
                        {currentOfferPrice.toLocaleString()}₫
                      </div>

                      {offerState === 'pending' && (
                        <div style={{ display: 'flex', gap: '8px', marginTop: '10px' }}>
                          <button type="button" className="og-button og-button--primary og-button--sm" onClick={acceptOffer}>
                            ✓ {language === 'vi' ? 'Đồng Ý Giá Này' : 'Accept'}
                          </button>
                          <button type="button" className="og-button og-button--outline og-button--sm" onClick={() => setShowCounterBox(true)}>
                            ↔ {language === 'vi' ? 'Phản Hồi Giá Khác' : 'Counter'}
                          </button>
                          <button type="button" className="og-button og-button--ghost og-button--sm" onClick={rejectOffer}>
                            ✕ {language === 'vi' ? 'Từ Chối' : 'Decline'}
                          </button>
                        </div>
                      )}

                      {showCounterBox && (
                        <div style={{ marginTop: '10px', display: 'flex', gap: '6px' }}>
                          <input
                            type="text"
                            className="og-input"
                            value={counterPriceInput}
                            onChange={e => setCounterPriceInput(e.target.value)}
                            style={{ flex: 1, fontSize: '13px' }}
                          />
                          <button
                            type="button"
                            className="og-button og-button--primary og-button--sm"
                            onClick={() => { makeCounterOffer(Number(counterPriceInput)); setShowCounterBox(false); }}
                          >
                            {language === 'vi' ? 'Gửi Giá' : 'Send'}
                          </button>
                        </div>
                      )}
                    </div>
                  ) : msg.type === 'warning' ? (
                    <div style={{ background: '#f8d7da', border: '1px solid #f5c6cb', color: '#721c24', borderRadius: 'var(--og-radius-md)', padding: '10px 14px', fontSize: '13px' }}>
                      <p style={{ margin: 0 }}>{msg.text}</p>
                      <div style={{ fontSize: '11px', marginTop: '4px', fontWeight: 600 }}>
                        ⚠️ {language === 'vi' ? 'Tin nhắn này bị hệ thống đánh dấu có thông tin liên lạc ngoài sàn.' : 'Flagged for off-platform contact.'}
                      </div>
                    </div>
                  ) : (
                    <div
                      style={{
                        background: msg.isSeller ? 'var(--og-color-surface-subtle)' : 'var(--og-color-primary)',
                        color: msg.isSeller ? 'var(--og-color-text-primary)' : '#ffffff',
                        padding: '10px 14px',
                        borderRadius: 'var(--og-radius-md)',
                        fontSize: '13px'
                      }}
                    >
                      {msg.text}
                    </div>
                  )}
                </div>
              ))}
            </div>

            {/* Chat Input */}
            <div style={{ padding: '12px', borderTop: '1px solid var(--og-color-border)', display: 'flex', gap: '8px' }}>
              <input
                type="text"
                className="og-input"
                placeholder={language === 'vi' ? 'Nhập tin nhắn trao đổi tình trạng đồ cũ...' : 'Type a message...'}
                defaultValue=""
                style={{ flex: 1 }}
              />
              <button type="button" className="og-button og-button--primary">
                {language === 'vi' ? 'Gửi' : 'Send'}
              </button>
            </div>
          </div>
        </div>
      );

    case 'SCR-SELLER-CREATE':
      return (
        <div className="og-card" style={{ maxWidth: '780px', margin: '0 auto', padding: '28px' }}>
          <h2 style={{ color: 'var(--og-color-primary)', margin: '0 0 20px' }}>
            🏷️ {language === 'vi' ? 'Đăng Bán Đồ Cũ (Khai Báo Minh Bạch)' : 'Create Second-Hand Listing'}
          </h2>

          <form onSubmit={e => { e.preventDefault(); setActiveScreenId('SCR-SELLER-AI-CHECK'); }}>
            <div className="og-form-group">
              <label className="og-label">{language === 'vi' ? 'Tên món đồ (Nêu rõ hãng & model)' : 'Listing Title'}</label>
              <input type="text" className="og-input" value={newTitle} onChange={e => setNewTitle(e.target.value)} required />
            </div>

            <div className="og-form-grid-2">
              <div className="og-form-group">
                <label className="og-label">{language === 'vi' ? 'Giá mong muốn (VNĐ)' : 'Asking Price (VND)'}</label>
                <input type="text" className="og-input" value={newPrice} onChange={e => setNewPrice(e.target.value)} required />
              </div>
              <div className="og-form-group">
                <label className="og-label">{language === 'vi' ? 'Thang đo hao mòn' : 'Condition Grade'}</label>
                <select className="og-select" value={newCondition} onChange={e => setNewCondition(e.target.value as 'like_new' | 'good' | 'fair' | 'vintage')}>
                  <option value="like_new">99% Như mới (Like New)</option>
                  <option value="good">90-95% Khá tốt (Good)</option>
                  <option value="fair">80% Đã qua sử dụng (Fair)</option>
                  <option value="vintage">Đồ cổ / Sưu tầm (Vintage)</option>
                </select>
              </div>
            </div>

            <div className="og-form-group">
              <label className="og-label">{language === 'vi' ? 'Khuyết điểm đã biết (Càng rõ càng ít bị trả hàng)' : 'Defects Declaration'}</label>
              <textarea
                className="og-textarea"
                rows={2}
                defaultValue="Vết xước dăm nhẹ 2mm ở cạnh đáy, switch phím Spacebar hơi nặng hơn phím khác."
              />
            </div>

            <div className="og-form-group">
              <label className="og-label">{language === 'vi' ? 'Video đồng kiểm (Quay ngoại hình & hoạt động)' : 'Co-inspection Video'}</label>
              <div style={{ border: '2px dashed var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '20px', textAlign: 'center' }}>
                <span>📹</span>
                <p style={{ margin: '6px 0 0', fontSize: '13px', color: 'var(--og-color-text-secondary)' }}>
                  {language === 'vi' ? 'Đã tải lên: video_test_leopold.mp4 (12 MB)' : 'Attached: video_test_leopold.mp4'}
                </p>
              </div>
            </div>

            <button type="submit" className="og-button og-button--primary" style={{ width: '100%', marginTop: '16px' }}>
              ✨ {language === 'vi' ? 'Kiểm Tra Với AI & Tiếp Tục' : 'Run AI Quality Check'}
            </button>
          </form>
        </div>
      );

    case 'SCR-SELLER-AI-CHECK':
      return (
        <div className="og-card" style={{ maxWidth: '680px', margin: '0 auto', padding: '32px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '20px' }}>
            <span style={{ fontSize: '32px' }}>✨</span>
            <div>
              <h2 style={{ margin: 0, color: 'var(--og-color-primary)' }}>
                {language === 'vi' ? 'Trợ Lý AI Thẩm Định Tin Đăng' : 'AI Listing Appraisal Assistant'}
              </h2>
              <p style={{ margin: '4px 0 0', fontSize: '13px', color: 'var(--og-color-text-secondary)' }}>
                {language === 'vi' ? 'AI kiểm tra chất lượng ảnh, độ mờ nét và gợi ý giá thị trường' : 'Validating photos, sharpness & market price'}
              </p>
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', marginBottom: '24px' }}>
            <div style={{ background: 'var(--og-color-success-bg)', border: '1px solid var(--og-color-success-border)', borderRadius: 'var(--og-radius-md)', padding: '14px' }}>
              <div style={{ fontWeight: 700, color: 'var(--og-color-success)' }}>✓ {language === 'vi' ? 'Ảnh sắc nét & đủ ánh sáng' : 'Photos Sharp & Clear'}</div>
              <p style={{ margin: '4px 0 0', fontSize: '12px' }}>{language === 'vi' ? 'Góc chụp rõ khuyết điểm cạnh đáy và mặt phím.' : 'Macro shots clearly capture declared defects.'}</p>
            </div>

            <div style={{ background: 'var(--og-color-surface-subtle)', borderRadius: 'var(--og-radius-md)', padding: '14px' }}>
              <div style={{ fontWeight: 700, color: 'var(--og-color-accent-gold)' }}>💡 {language === 'vi' ? 'Gợi ý mức giá thị trường' : 'Price Benchmark'}</div>
              <p style={{ margin: '4px 0 0', fontSize: '13px' }}>
                {language === 'vi'
                  ? 'Món đồ này trên thị trường đồ cũ thường giao dịch từ 2.200.000₫ – 2.500.000₫. Mức giá 2.400.000₫ của bạn rất hợp lý!'
                  : 'Market range: 2.2M – 2.5M. Your 2.4M price is competitive!'}
              </p>
            </div>
          </div>

          <div style={{ display: 'flex', gap: '12px' }}>
            <button
              type="button"
              className="og-button og-button--primary"
              style={{ flex: 1 }}
              onClick={() => setActiveScreenId('SCR-SELLER-LISTINGS')}
            >
              {language === 'vi' ? 'Xuất Bản Tin Đăng Ngay' : 'Publish Listing'}
            </button>
            <button
              type="button"
              className="og-button og-button--outline"
              onClick={() => setActiveScreenId('SCR-SELLER-KYC-LOCK')}
            >
              {language === 'vi' ? 'Thử Tình Huống Khóa KYC' : 'Test KYC Gate'}
            </button>
          </div>
        </div>
      );

    case 'SCR-SELLER-KYC-LOCK':
      return (
        <div className="og-card" style={{ maxWidth: '600px', margin: '0 auto', padding: '32px', textAlign: 'center' }}>
          <div style={{ width: '72px', height: '72px', borderRadius: '50%', background: '#fff3cd', color: '#856404', fontSize: '36px', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px' }}>
            🔒
          </div>
          <h2 style={{ color: 'var(--og-color-warning)', margin: '0 0 8px' }}>
            {language === 'vi' ? 'Cần Định Danh KYC Trước Khi Đăng Tin Này' : 'Seller KYC Required For High Value'}
          </h2>
          <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '14px', marginBottom: '20px' }}>
            {language === 'vi'
              ? 'Món đồ của bạn có giá trên 5.000.000₫. Để bảo vệ người mua và giải ngân Escrow, bạn cần xác thực CCCD.'
              : 'Items priced > 5,000,000₫ require verified ID to receive Escrow payouts.'}
          </p>

          <button type="button" className="og-button og-button--primary" onClick={() => setActiveScreenId('SCR-KYC-01')}>
            {language === 'vi' ? 'Định Danh KYC Ngay (2 phút)' : 'Complete KYC Now'}
          </button>
        </div>
      );

    case 'SCR-SELLER-LISTINGS':
      return (
        <div className="og-card" style={{ padding: '24px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
            <div>
              <h2 style={{ margin: 0, color: 'var(--og-color-primary)' }}>
                📦 {language === 'vi' ? 'Quản Lý Danh Sách Tin Đăng Của Bạn' : 'Seller Listings Dashboard'}
              </h2>
              <p style={{ margin: '4px 0 0', fontSize: '13px', color: 'var(--og-color-text-secondary)' }}>
                {language === 'vi' ? '2 tin đang bán · 1 tin đã bán qua Escrow' : '2 active · 1 completed via Escrow'}
              </p>
            </div>
            <button type="button" className="og-button og-button--primary og-button--sm" onClick={() => setActiveScreenId('SCR-SELLER-CREATE')}>
              + {language === 'vi' ? 'Đăng Tin Mới' : 'Post New'}
            </button>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {MOCK_PRODUCTS.slice(0, 3).map(p => (
              <div key={p.id} style={{ border: '1px solid var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '14px', display: 'flex', alignItems: 'center', gap: '16px', flexWrap: 'wrap' }}>
                <img src={p.images[0]} alt="thumb" style={{ width: '70px', height: '70px', objectFit: 'cover', borderRadius: 'var(--og-radius-md)' }} />
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                    <span className="og-badge og-badge--success">ACTIVE</span>
                    <span style={{ fontSize: '12px', color: 'var(--og-color-text-muted)' }}>{p.viewCount} lượt xem</span>
                  </div>
                  <h4 style={{ margin: '4px 0', fontSize: '14px' }}>{p.title[language]}</h4>
                  <div style={{ fontSize: '16px', fontWeight: 700, color: 'var(--og-color-primary)' }}>{p.price.toLocaleString()}₫</div>
                </div>
                <div style={{ display: 'flex', gap: '8px' }}>
                  <button type="button" className="og-button og-button--outline og-button--sm">
                    {language === 'vi' ? 'Chỉnh sửa' : 'Edit'}
                  </button>
                  <button type="button" className="og-button og-button--primary og-button--sm" onClick={() => setActiveScreenId('SCR-SELLER-ORDERS')}>
                    {language === 'vi' ? 'Xem đơn' : 'Orders'}
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      );

    case 'SCR-SELLER-ORDERS':
      return (
        <div className="og-card" style={{ padding: '24px' }}>
          <h2 style={{ color: 'var(--og-color-primary)', marginTop: 0 }}>
            📋 {language === 'vi' ? 'Đơn Hàng Cần Đóng Gói & Bàn Giao Shipper' : 'Seller Order Fulfillment'}
          </h2>

          <div style={{ border: '1px solid var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '18px', marginBottom: '16px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '8px' }}>
              <div>
                <strong>Mã đơn: OG-2026-88912</strong>
                <div style={{ fontSize: '12px', color: 'var(--og-color-text-muted)' }}>Khách mua: Huỳnh Long Bảo Khánh</div>
              </div>
              <span className="og-badge og-badge--warning">ESCROW_HOLDING: 24.495.000₫</span>
            </div>

            <div style={{ display: 'flex', gap: '14px', margin: '14px 0', alignItems: 'center' }}>
              <img src={MOCK_PRODUCTS[0].images[0]} alt="thumb" style={{ width: '60px', height: '60px', objectFit: 'cover', borderRadius: 'var(--og-radius-md)' }} />
              <div>
                <div style={{ fontSize: '14px', fontWeight: 600 }}>{MOCK_PRODUCTS[0].title[language]}</div>
                <div style={{ fontSize: '12px', color: 'var(--og-color-success)', fontWeight: 600 }}>✓ Tiền đã nằm an toàn trong Escrow sàn</div>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '10px' }}>
              <button type="button" className="og-button og-button--primary og-button--sm" onClick={() => setActiveScreenId('SCR-SELLER-PRINT-WAYBILL')}>
                🖨️ {language === 'vi' ? 'In Phiếu Gửi Hàng Đồng Kiểm' : 'Print Waybill'}
              </button>
              <button type="button" className="og-button og-button--outline og-button--sm" onClick={() => setActiveScreenId('SCR-SHIP-TRACKING')}>
                🚚 {language === 'vi' ? 'Xem Tiến Độ Giao' : 'Track Delivery'}
              </button>
            </div>
          </div>
        </div>
      );

    case 'SCR-ADMIN-ESCROW':
      return (
        <div className="og-card" style={{ padding: '24px' }}>
          <h2 style={{ color: 'var(--og-color-primary)', marginTop: 0 }}>
            🏦 {language === 'vi' ? 'Quản Trị Bể Tiền Ký Quỹ Escrow' : 'Escrow Fund Pool Management'}
          </h2>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px', marginBottom: '20px' }}>
            <div style={{ padding: '16px', background: 'var(--og-color-surface-subtle)', borderRadius: 'var(--og-radius-md)' }}>
              <div style={{ fontSize: '12px', color: 'var(--og-color-text-muted)' }}>Tổng số dư Escrow đang giữ:</div>
              <div style={{ fontSize: '20px', fontWeight: 700, color: 'var(--og-color-primary)' }}>385.200.000₫</div>
            </div>
            <div style={{ padding: '16px', background: 'var(--og-color-surface-subtle)', borderRadius: 'var(--og-radius-md)' }}>
              <div style={{ fontSize: '12px', color: 'var(--og-color-text-muted)' }}>Đang tranh chấp đóng băng:</div>
              <div style={{ fontSize: '20px', fontWeight: 700, color: 'var(--og-color-danger)' }}>18.400.000₫</div>
            </div>
          </div>

          <table className="og-table" style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
            <thead>
              <tr style={{ background: 'var(--og-color-surface-subtle)', textAlign: 'left' }}>
                <th style={{ padding: '8px 12px' }}>Mã Đơn</th>
                <th style={{ padding: '8px 12px' }}>Người Mua</th>
                <th style={{ padding: '8px 12px' }}>Người Bán</th>
                <th style={{ padding: '8px 12px' }}>Số Tiền</th>
                <th style={{ padding: '8px 12px' }}>Trạng Thái</th>
                <th style={{ padding: '8px 12px' }}>Thao Tác</th>
              </tr>
            </thead>
            <tbody>
              <tr style={{ borderBottom: '1px solid var(--og-color-border)' }}>
                <td style={{ padding: '10px 12px' }}>OG-88912</td>
                <td style={{ padding: '10px 12px' }}>Bảo Khánh</td>
                <td style={{ padding: '10px 12px' }}>Minh Camera</td>
                <td style={{ padding: '10px 12px' }}>24.495.000₫</td>
                <td style={{ padding: '10px 12px' }}><span className="og-badge og-badge--warning">HOLDING</span></td>
                <td style={{ padding: '10px 12px' }}>
                  <button type="button" className="og-button og-button--outline og-button--sm" onClick={() => alert('Chi tiết Escrow')}>
                    Kiểm tra
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      );

    case 'SCR-ADMIN-MODERATION':
      return (
        <div className="og-card" style={{ padding: '24px' }}>
          <h2 style={{ color: 'var(--og-color-primary)', marginTop: 0 }}>
            🛡️ {language === 'vi' ? 'Hàng Chờ Duyệt & Kiểm Soát Tin Đăng' : 'Listing Moderation Queue'}
          </h2>

          <div style={{ border: '1px solid var(--og-color-border)', borderRadius: 'var(--og-radius-md)', padding: '16px', display: 'flex', gap: '16px', alignItems: 'center', flexWrap: 'wrap' }}>
            <img src={MOCK_PRODUCTS[1].images[0]} alt="thumb" style={{ width: '80px', height: '80px', objectFit: 'cover', borderRadius: 'var(--og-radius-md)' }} />
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: '11px', color: 'var(--og-color-accent-gold)', fontWeight: 600 }}>Người bán: Tuấn Mech · Tin đăng mới</div>
              <h4 style={{ margin: '4px 0', fontSize: '14px' }}>{MOCK_PRODUCTS[1].title[language]}</h4>
              <div style={{ fontSize: '12px', color: 'var(--og-color-text-secondary)' }}>Khai báo 99% Like New · Đã đính kèm video test âm gõ phím</div>
            </div>
            <div style={{ display: 'flex', gap: '8px' }}>
              <button type="button" className="og-button og-button--primary og-button--sm" onClick={() => alert('Approved!')}>
                ✓ {language === 'vi' ? 'Phê Duyệt' : 'Approve'}
              </button>
              <button type="button" className="og-button og-button--danger og-button--sm" onClick={() => alert('Rejected!')}>
                ✕ {language === 'vi' ? 'Từ Chối' : 'Reject'}
              </button>
            </div>
          </div>
        </div>
      );

    default:
      return <div>Module 2: Screen {screenId}</div>;
  }
}
