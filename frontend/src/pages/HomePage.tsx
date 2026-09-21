import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import {
  Category,
  ProductSummary,
  marketplaceApi,
  ProductCard,
} from '../features/marketplace';
import { LoadingSkeleton } from '../shared/components';

export const HomePage: React.FC = () => {
  const navigate = useNavigate();
  const [categories, setCategories] = useState<Category[]>([]);
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedCategory, setSelectedCategory] = useState<number | null>(null);
  const [selectedCondition, setSelectedCondition] = useState<string>('ALL');
  const [searchKeyword, setSearchKeyword] = useState<string>('');

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

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (searchKeyword.trim()) {
      navigate(`/marketplace?query=${encodeURIComponent(searchKeyword.trim())}`);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '32px' }}>
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

        {/* 4 Trust Highlights Strip */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: '12px',
            borderTop: '1px solid rgba(255, 255, 255, 0.15)',
            paddingTop: '18px',
            marginTop: '8px',
            fontSize: '0.85rem',
            color: '#d1e6dc',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span>🛡️</span>
            <span>Người bán xác minh eKYC</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span>🔒</span>
            <span>Tiền giữ an toàn tại Escrow</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span>📦</span>
            <span>Cho xem hàng cùng bưu tá</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span>⏱️</span>
            <span>48h kiểm tra hàng sau nhận</span>
          </div>
        </div>
      </section>

      {/* 2. Category Quick Selector */}
      <section aria-label="Danh mục ngành hàng">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
          <h2 style={{ fontSize: '1.25rem', fontWeight: 700, margin: 0, color: 'var(--og-color-text-primary)' }}>
            Khám phá theo ngành hàng
          </h2>
          <Link to="/marketplace" style={{ fontSize: '0.88rem', fontWeight: 600, color: 'var(--og-color-primary)' }}>
            Xem tất cả →
          </Link>
        </div>

        <div
          style={{
            display: 'flex',
            gap: '12px',
            overflowX: 'auto',
            paddingBottom: '8px',
            scrollbarWidth: 'thin',
          }}
        >
          <button
            type="button"
            onClick={() => setSelectedCategory(null)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '8px 16px',
              borderRadius: 'var(--og-radius-full)',
              border: `1px solid ${selectedCategory === null ? 'var(--og-color-primary)' : 'var(--og-color-border)'}`,
              background: selectedCategory === null ? 'var(--og-color-primary)' : 'var(--og-color-surface)',
              color: selectedCategory === null ? '#ffffff' : 'var(--og-color-text-primary)',
              fontWeight: 600,
              fontSize: '0.88rem',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
              transition: 'all var(--og-transition-fast)',
            }}
          >
            <span>✨ Tất cả danh mục</span>
          </button>

          {categories.map((cat) => {
            const isSelected = selectedCategory === cat.categoryId;
            return (
              <button
                key={cat.categoryId}
                type="button"
                onClick={() => setSelectedCategory(isSelected ? null : cat.categoryId)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  padding: '8px 16px',
                  borderRadius: 'var(--og-radius-full)',
                  border: `1px solid ${isSelected ? 'var(--og-color-primary)' : 'var(--og-color-border)'}`,
                  background: isSelected ? 'var(--og-color-primary)' : 'var(--og-color-surface)',
                  color: isSelected ? '#ffffff' : 'var(--og-color-text-primary)',
                  fontWeight: 600,
                  fontSize: '0.88rem',
                  cursor: 'pointer',
                  whiteSpace: 'nowrap',
                  transition: 'all var(--og-transition-fast)',
                }}
              >
                <span>{cat.categoryName}</span>
              </button>
            );
          })}
        </div>
      </section>

      {/* 3. Condition Filters & Title */}
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
            <p style={{ margin: 0, fontSize: '0.88rem', color: 'var(--og-color-text-muted)' }}>
              Đồ thanh lý cá nhân, minh bạch vết xước và phụ kiện. Bấm để xem chi tiết hoặc đặt mua.
            </p>
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
    </div>
  );
};
