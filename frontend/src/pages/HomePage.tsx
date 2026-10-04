import React, { useState, useEffect } from 'react';
import { useNavigate, Link, useSearchParams } from 'react-router-dom';
import {
  Category,
  ProductSummary,
  marketplaceApi,
  ProductCard,
} from '../features/marketplace';
import { LoadingSkeleton } from '../shared/components';

export const HomePage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const pendingOrderId = searchParams.get('pendingOrder');
  const [dismissPending, setDismissPending] = useState(false);

  const [categories, setCategories] = useState<Category[]>([]);
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedCategory, setSelectedCategory] = useState<number | null>(null);
  const [selectedCondition, setSelectedCondition] = useState<string>('ALL');
  const [searchKeyword, setSearchKeyword] = useState<string>('');
  const [activeCategoryItem, setActiveCategoryItem] = useState<string>('all');

  useEffect(() => {
    let ignore = false;
    marketplaceApi.getCategories()
      .then((cats) => {
        if (!ignore) setCategories(cats);
      })
      .catch((err) => console.error('Không thể tải categories:', err));

    return () => {
      ignore = true;
    };
  }, []);

  useEffect(() => {
    let ignore = false;
    marketplaceApi.getProducts({
      categoryId: selectedCategory || undefined,
      condition: selectedCondition !== 'ALL' ? selectedCondition : undefined,
      query: searchKeyword || undefined,
      size: 16,
    })
      .then((data) => {
        if (!ignore) {
          setProducts(data.items);
          setIsLoading(false);
        }
      })
      .catch((err) => {
        if (!ignore) {
          console.error('Không thể tải sản phẩm:', err);
          setIsLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [selectedCategory, selectedCondition, searchKeyword]);

  const electronicsCat = categories.find((c) => c.slug === 'electronics' || c.categoryName.toLowerCase().includes('điện tử'));
  const fashionCat = categories.find((c) => c.slug === 'fashion' || c.categoryName.toLowerCase().includes('thời trang'));
  const homeCat = categories.find((c) => c.slug === 'home-living' || c.categoryName.toLowerCase().includes('nhà'));
  const booksCat = categories.find((c) => c.slug === 'books-stationery' || c.categoryName.toLowerCase().includes('sách'));
  const sportsCat = categories.find((c) => c.slug === 'sports-outdoors' || c.categoryName.toLowerCase().includes('thể thao'));
  const collectiblesCat = categories.find((c) => c.slug === 'collectibles' || c.categoryName.toLowerCase().includes('sưu tầm'));
  const babyCat = categories.find((c) => c.slug === 'mother-baby' || c.categoryName.toLowerCase().includes('mẹ') || c.categoryName.toLowerCase().includes('bé'));
  const otherCat = categories.find((c) => c.slug === 'other' || c.categoryName.toLowerCase().includes('khác'));

  const showcaseCategories = [
    { id: 'all', name: 'Tất cả danh mục', icon: '/categories/all.svg', categoryId: null, keyword: '' },
    { id: 'phone-accessories', name: 'Điện thoại & Phụ kiện', icon: '/categories/phone.svg', categoryId: electronicsCat?.categoryId, keyword: 'điện thoại' },
    { id: 'computer-laptop', name: 'Máy tính & Laptop', icon: '/categories/laptop.svg', categoryId: electronicsCat?.categoryId, keyword: 'máy tính' },
    { id: 'electronics', name: 'Thiết bị điện tử', icon: '/categories/electronics.svg', categoryId: electronicsCat?.categoryId, keyword: '' },
    { id: 'home-appliances', name: 'Đồ gia dụng', icon: '/categories/appliances.svg', categoryId: homeCat?.categoryId, keyword: 'gia dụng' },
    { id: 'furniture', name: 'Nội thất', icon: '/categories/furniture.svg', categoryId: homeCat?.categoryId, keyword: 'nội thất' },
    { id: 'fashion', name: 'Thời trang', icon: '/categories/fashion.svg', categoryId: fashionCat?.categoryId, keyword: '' },
    { id: 'shoes-accessories', name: 'Giày dép & Phụ kiện', icon: '/categories/shoes.svg', categoryId: fashionCat?.categoryId, keyword: 'giày' },
    { id: 'books-stationery', name: 'Sách & Đồ dùng học tập', icon: '/categories/books.svg', categoryId: booksCat?.categoryId, keyword: '' },
    { id: 'sports-outdoors', name: 'Thể thao & Dã ngoại', icon: '/categories/sports.svg', categoryId: sportsCat?.categoryId, keyword: '' },
    { id: 'vehicles', name: 'Xe cộ & Phụ kiện', icon: '/categories/vehicle.svg', categoryId: otherCat?.categoryId, keyword: 'xe' },
    { id: 'gaming', name: 'Gaming & Giải trí', icon: '/categories/gaming.svg', categoryId: electronicsCat?.categoryId, keyword: 'game' },
    { id: 'toys-collectibles', name: 'Đồ chơi & Sưu tầm', icon: '/categories/toys.svg', categoryId: collectiblesCat?.categoryId, keyword: '' },
    { id: 'instruments', name: 'Nhạc cụ', icon: '/categories/instruments.svg', categoryId: collectiblesCat?.categoryId || otherCat?.categoryId, keyword: 'nhạc cụ' },
    { id: 'baby-kids', name: 'Đồ trẻ em', icon: '/categories/baby.svg', categoryId: babyCat?.categoryId, keyword: '' },
    { id: 'other', name: 'Khác', icon: '/categories/other.svg', categoryId: otherCat?.categoryId, keyword: '' },
  ];

  const handleCategoryItemClick = (item: (typeof showcaseCategories)[0]) => {
    if (activeCategoryItem === item.id) {
      setActiveCategoryItem('all');
      setSelectedCategory(null);
      setSearchKeyword('');
    } else {
      setActiveCategoryItem(item.id);
      setSelectedCategory(item.categoryId ?? null);
      setSearchKeyword(item.keyword || '');
    }
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (searchKeyword.trim()) {
      navigate(`/marketplace?query=${encodeURIComponent(searchKeyword.trim())}`);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '32px' }}>
      {/* Pending Payment Notification Banner (DP-18) */}
      {pendingOrderId && !dismissPending && (
        <section
          style={{
            background: 'linear-gradient(135deg, #fff7ed 0%, #ffedd5 100%)',
            border: '2px solid #ea580c',
            borderRadius: 'var(--og-radius-lg)',
            padding: '20px 24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '16px',
            flexWrap: 'wrap',
            boxShadow: '0 6px 20px rgba(234, 88, 12, 0.15)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '14px', flex: 1, minWidth: '280px' }}>
            <span style={{ fontSize: '32px' }}>⚠️</span>
            <div>
              <div style={{ fontWeight: 800, color: '#9a3412', fontSize: '1.05rem', marginBottom: '4px' }}>
                Đơn hàng #{pendingOrderId} chưa hoàn tất thanh toán!
              </div>
              <div style={{ fontSize: '0.9rem', color: '#c2410c', lineHeight: 1.4 }}>
                Sản phẩm của bạn đang được khóa giữ chỗ độc quyền trong vòng <strong>1 giờ</strong>. Hãy hoàn tất thanh toán ngay để tránh bị hệ thống tự động hủy đơn và giải phóng hàng.
              </div>
            </div>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <Link
              to={`/checkout/payment?orderId=${pendingOrderId}`}
              className="og-button og-button--primary"
              style={{ padding: '10px 18px', fontWeight: 700, fontSize: '0.92rem', whiteSpace: 'nowrap' }}
            >
              ⚡ Thanh toán ngay
            </Link>
            <Link
              to={`/orders/${pendingOrderId}`}
              className="og-button og-button--secondary"
              style={{ padding: '10px 18px', fontWeight: 600, fontSize: '0.92rem', whiteSpace: 'nowrap', background: '#fff' }}
            >
              📦 Xem đơn hàng
            </Link>
            <button
              type="button"
              onClick={() => setDismissPending(true)}
              style={{
                background: 'none',
                border: 'none',
                fontSize: '20px',
                color: '#9a3412',
                cursor: 'pointer',
                padding: '4px 8px',
                marginLeft: '4px',
              }}
              title="Đóng thông báo"
            >
              ✕
            </button>
          </div>
        </section>
      )}

      {/* 1. Mall Banner: Escrow Protection & Trust Welcome */}
      <section
        style={{
          background: 'linear-gradient(135deg, #1d4d38 0%, #102d21 100%)',
          color: '#ffffff',
          borderRadius: 'var(--og-radius-lg)',
          padding: '36px 28px',
          boxShadow: 'var(--og-shadow-md)',
          display: 'flex',
          flexDirection: 'column',
          gap: '18px',
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        <div style={{ maxWidth: '720px', zIndex: 1 }}>
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              background: 'rgba(198, 147, 58, 0.25)',
              border: '1px solid #c6933a',
              color: '#f5d596',
              padding: '4px 12px',
              borderRadius: 'var(--og-radius-full)',
              fontSize: '0.82rem',
              fontWeight: 700,
              marginBottom: '10px',
            }}
          >
            <span>🛡️ SÀN MUA BÁN ĐỒ CŨ BẢO VỆ 100% ESCROW</span>
          </div>

          <h1
            style={{
              fontFamily: 'var(--og-font-serif)',
              fontSize: 'clamp(1.8rem, 4vw, 2.6rem)',
              lineHeight: 1.15,
              margin: '0 0 10px',
              color: '#ffffff',
            }}
          >
            Old but Gold
          </h1>

          <p style={{ margin: '0 0 18px', color: '#b9d4c7', fontSize: '1rem', lineHeight: 1.5 }}>
            Đồ cũ còn giá trị. Giao dịch an tâm tuyệt đối với bảo vệ 100% Escrow và đồng kiểm cùng bưu tá.
          </p>

          {/* Quick Mall Search Bar */}
          <form onSubmit={handleSearchSubmit} style={{ display: 'flex', gap: '10px', maxWidth: '560px' }}>
            <input
              type="search"
              className="og-input"
              placeholder="Tìm theo món đồ, thương hiệu (Sony A7, Keychron, Seiko 5...)"
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              style={{
                height: '46px',
                borderRadius: 'var(--og-radius-full)',
                background: '#ffffff',
                border: 'none',
                color: 'var(--og-color-text-primary)',
                fontSize: '0.95rem',
                padding: '0 20px',
              }}
            />
            <button
              type="submit"
              className="og-button og-button--gold"
              style={{ borderRadius: 'var(--og-radius-full)', padding: '0 22px', fontWeight: 700 }}
            >
              Tìm kiếm
            </button>
          </form>
        </div>

        {/* 4 Trust Highlights Pillars (Item 2) */}
        <div className="og-hero-trust-grid">
          <div className="og-hero-trust-card">
            <div className="og-hero-trust-icon-wrap" aria-hidden="true">
              <span>🛡️</span>
            </div>
            <div className="og-hero-trust-content">
              <div className="og-hero-trust-title">Người bán xác minh eKYC</div>
              <div className="og-hero-trust-desc">Định danh CCCD chính chủ, an tâm giao dịch</div>
            </div>
          </div>

          <div className="og-hero-trust-card">
            <div className="og-hero-trust-icon-wrap" aria-hidden="true">
              <span>🔒</span>
            </div>
            <div className="og-hero-trust-content">
              <div className="og-hero-trust-title">Tiền giữ an toàn tại Escrow</div>
              <div className="og-hero-trust-desc">Bảo vệ 100% tiền mua đến khi xác nhận nhận hàng</div>
            </div>
          </div>

          <div className="og-hero-trust-card">
            <div className="og-hero-trust-icon-wrap" aria-hidden="true">
              <span>📦</span>
            </div>
            <div className="og-hero-trust-content">
              <div className="og-hero-trust-title">Cho xem hàng cùng bưu tá</div>
              <div className="og-hero-trust-desc">Đồng kiểm ngoại quan thực tế trước khi nhận</div>
            </div>
          </div>

          <div className="og-hero-trust-card">
            <div className="og-hero-trust-icon-wrap" aria-hidden="true">
              <span>⏱️</span>
            </div>
            <div className="og-hero-trust-content">
              <div className="og-hero-trust-title">48h kiểm tra hàng sau nhận</div>
              <div className="og-hero-trust-desc">Thời gian trải nghiệm và khiếu nại minh bạch</div>
            </div>
          </div>
        </div>
      </section>

      {/* 2. Món đồ cũ mới lên sàn hôm nay (Sản phẩm nổi bật) */}
      <section aria-label="Danh sách món đồ cũ">
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: '12px',
            marginBottom: '18px',
          }}
        >
          <div>
            <h2 style={{ fontSize: '1.35rem', fontWeight: 800, margin: '0 0 4px', color: 'var(--og-color-text-primary)' }}>
              Món đồ cũ mới lên sàn hôm nay
            </h2>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
              <p style={{ margin: 0, fontSize: '0.88rem', color: 'var(--og-color-text-muted)' }}>
                Đồ thanh lý cá nhân, minh bạch vết xước và phụ kiện. Bấm để xem chi tiết hoặc đặt mua.
              </p>
              {activeCategoryItem !== 'all' && (
                <span
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '6px',
                    background: 'var(--og-color-primary-light)',
                    color: 'var(--og-color-primary)',
                    padding: '2px 10px',
                    borderRadius: 'var(--og-radius-full)',
                    fontSize: '0.8rem',
                    fontWeight: 700,
                  }}
                >
                  Đang lọc: {showcaseCategories.find((c) => c.id === activeCategoryItem)?.name}
                  <button
                    type="button"
                    onClick={() => {
                      setActiveCategoryItem('all');
                      setSelectedCategory(null);
                      setSearchKeyword('');
                    }}
                    style={{
                      background: 'none',
                      border: 'none',
                      color: 'var(--og-color-primary)',
                      cursor: 'pointer',
                      fontWeight: 700,
                      padding: '0 2px',
                      fontSize: '0.85rem',
                    }}
                    title="Xóa bộ lọc danh mục"
                  >
                    ✕
                  </button>
                </span>
              )}
            </div>
          </div>

          {/* Condition Pills */}
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
            {[
              { key: 'ALL', label: 'Tất cả' },
              { key: 'LIKE_NEW', label: '✨ Như mới' },
              { key: 'GOOD', label: '👌 Khá tốt' },
              { key: 'FAIR', label: '👍 Chấp nhận được' },
              { key: 'VINTAGE', label: '🏺 Đồ cổ' },
            ].map((cond) => (
              <button
                key={cond.key}
                type="button"
                onClick={() => setSelectedCondition(cond.key)}
                style={{
                  padding: '6px 12px',
                  borderRadius: 'var(--og-radius-sm)',
                  fontSize: '0.8rem',
                  fontWeight: selectedCondition === cond.key ? 700 : 500,
                  border: '1px solid var(--og-color-border)',
                  background: selectedCondition === cond.key ? 'var(--og-color-primary-light)' : 'var(--og-color-surface)',
                  color: selectedCondition === cond.key ? 'var(--og-color-primary)' : 'var(--og-color-text-secondary)',
                  cursor: 'pointer',
                }}
              >
                {cond.label}
              </button>
            ))}
          </div>
        </div>

        {/* Product Feed Grid */}
        {isLoading ? (
          <LoadingSkeleton type="product-grid" count={8} />
        ) : products.length > 0 ? (
          <div className="og-marketplace-grid" role="feed" aria-label="Danh sách sản phẩm">
            {products.map((product) => (
              <ProductCard key={product.productId} product={product} />
            ))}
          </div>
        ) : (
          <div
            style={{
              textAlign: 'center',
              padding: '60px 20px',
              background: 'var(--og-color-surface)',
              borderRadius: 'var(--og-radius-md)',
              border: '1px dashed var(--og-color-border)',
            }}
          >
            <div style={{ fontSize: '3rem', marginBottom: '12px' }}>🔍</div>
            <h3 style={{ margin: '0 0 6px', color: 'var(--og-color-text-primary)' }}>
              Không tìm thấy món đồ phù hợp
            </h3>
            <p style={{ margin: '0 0 16px', color: 'var(--og-color-text-secondary)', fontSize: '0.9rem' }}>
              Hãy thử chọn danh mục khác hoặc nới lỏng bộ lọc tình trạng.
            </p>
            <button
              type="button"
              className="og-button og-button--outline og-button--sm"
              onClick={() => {
                setActiveCategoryItem('all');
                setSelectedCategory(null);
                setSelectedCondition('ALL');
                setSearchKeyword('');
              }}
            >
              Đặt lại bộ lọc
            </button>
          </div>
        )}
      </section>

      {/* 3. Category Showcase with Circular Cards (Đặt dưới danh sách sản phẩm theo yêu cầu) */}
      <section aria-label="Danh mục sản phẩm">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
          <div>
            <h2 style={{ fontSize: '1.35rem', fontWeight: 800, margin: '0 0 4px', color: 'var(--og-color-text-primary)' }}>
              Khám phá theo danh mục
            </h2>
            <p style={{ margin: 0, fontSize: '0.88rem', color: 'var(--og-color-text-muted)' }}>
              Đa dạng các ngành hàng đồ cũ chất lượng với bảo vệ Escrow 100%
            </p>
          </div>
          <Link
            to="/marketplace"
            style={{ fontSize: '0.88rem', fontWeight: 700, color: 'var(--og-color-primary)', textDecoration: 'none' }}
          >
            Xem tất cả ngành hàng →
          </Link>
        </div>

        <div className="og-category-grid-wrap">
          <div className="og-category-grid">
            {showcaseCategories.map((item) => {
              const isActive = activeCategoryItem === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  className={`og-category-item ${isActive ? 'og-category-item--active' : ''}`}
                  onClick={() => handleCategoryItemClick(item)}
                  aria-pressed={isActive}
                  title={item.name}
                >
                  <div className="og-category-circle">
                    <img
                      src={item.icon}
                      alt={item.name}
                      className="og-category-image"
                      loading="lazy"
                    />
                  </div>
                  <span className="og-category-name">{item.name}</span>
                  <span className="og-category-active-dot" aria-hidden="true" />
                </button>
              );
            })}
          </div>
        </div>
      </section>
    </div>
  );
};
