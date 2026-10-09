import React, { useState, useRef, useEffect, useMemo } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../features/auth';
import { marketplaceApi, Category } from '../../features/marketplace';
import { Button } from '../components/Button/Button';
import { ConfirmDialog } from '../components/Dialog/ConfirmDialog';
import { useCart } from '../context';

interface QuickFilterPanelProps {
  categories: Category[];
  currentSearch: string;
  onApply: (params: { categoryId?: number; condition?: string; minPrice?: number; maxPrice?: number }) => void;
  onReset: () => void;
  onClose: () => void;
}

const MAX_QUICK_PRICE = 50000000;
const STEP_QUICK_PRICE = 500000;

const QuickFilterPanel: React.FC<QuickFilterPanelProps> = ({
  categories,
  currentSearch,
  onApply,
  onReset,
  onClose,
}) => {
  const searchParams = useMemo(() => new URLSearchParams(currentSearch), [currentSearch]);

  const initialCatId = useMemo(() => {
    const raw = searchParams.get('categoryId') || searchParams.get('categoryIds');
    if (!raw) return undefined;
    const num = Number(raw.split(',')[0]);
    return !isNaN(num) && num > 0 ? num : undefined;
  }, [searchParams]);

  const initialCondition = useMemo(() => {
    const raw = searchParams.get('condition') || searchParams.get('conditions');
    return raw ? raw.split(',')[0].toUpperCase() : undefined;
  }, [searchParams]);

  const initialMin = useMemo(() => {
    const raw = searchParams.get('minPrice');
    return raw && !isNaN(Number(raw)) ? Number(raw) : 0;
  }, [searchParams]);

  const initialMax = useMemo(() => {
    const raw = searchParams.get('maxPrice');
    return raw && !isNaN(Number(raw)) ? Number(raw) : MAX_QUICK_PRICE;
  }, [searchParams]);

  // Local draft state: does NOT mutate URL or trigger queries while interacting
  const [draftCatId, setDraftCatId] = useState<number | undefined>(initialCatId);
  const [draftCondition, setDraftCondition] = useState<string | undefined>(initialCondition);
  const [draftMinPrice, setDraftMinPrice] = useState<number>(initialMin);
  const [draftMaxPrice, setDraftMaxPrice] = useState<number>(initialMax);

  useEffect(() => {
    setDraftCatId(initialCatId);
    setDraftCondition(initialCondition);
    setDraftMinPrice(initialMin);
    setDraftMaxPrice(initialMax);
  }, [initialCatId, initialCondition, initialMin, initialMax]);

  const formatVnd = (n: number) => {
    return new Intl.NumberFormat('vi-VN').format(n) + ' ₫';
  };

  const minPercent = Math.min(100, Math.max(0, (draftMinPrice / MAX_QUICK_PRICE) * 100));
  const maxPercent = Math.min(100, Math.max(0, (draftMaxPrice / MAX_QUICK_PRICE) * 100));

  const handleApply = () => {
    onApply({
      categoryId: draftCatId,
      condition: draftCondition,
      minPrice: draftMinPrice > 0 ? draftMinPrice : undefined,
      maxPrice: draftMaxPrice < MAX_QUICK_PRICE ? draftMaxPrice : undefined,
    });
  };

  const handleReset = () => {
    setDraftCatId(undefined);
    setDraftCondition(undefined);
    setDraftMinPrice(0);
    setDraftMaxPrice(MAX_QUICK_PRICE);
    onReset();
  };

  return (
    <div className="og-header__filter-dropdown" role="region" aria-label="Bộ lọc nhanh">
      <div className="og-header__filter-dropdown-inner">
        {/* Header */}
        <div className="og-header__filter-dropdown-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <OutlineIcons.Filter />
            <span style={{ fontWeight: 700, fontSize: '0.96rem', color: 'var(--og-color-text-primary)' }}>
              Bộ lọc nhanh sản phẩm
            </span>
            <span style={{ fontSize: '0.78rem', color: 'var(--og-color-text-muted)' }}>
              (Chọn các tiêu chí bên dưới rồi bấm Áp dụng)
            </span>
          </div>
          <button
            type="button"
            className="og-button og-button--ghost og-button--sm"
            onClick={onClose}
            aria-label="Đóng bảng lọc nhanh"
          >
            ✕ Đóng
          </button>
        </div>

        {/* 1. Category Selection (Local draft) */}
        <div style={{ marginBottom: '12px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
            <span style={{ fontSize: '0.82rem', fontWeight: 700, color: 'var(--og-color-text-primary)' }}>
              🏷️ Ngành hàng / Danh mục:
            </span>
            {draftCatId && (
              <span style={{ fontSize: '0.78rem', color: 'var(--og-color-primary)', fontWeight: 600 }}>
                Đang chọn: {categories.find((c) => c.categoryId === draftCatId)?.categoryName}
              </span>
            )}
          </div>
          <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', maxHeight: '110px', overflowY: 'auto' }}>
            <button
              type="button"
              className={`og-filter-chip ${draftCatId === undefined ? 'og-filter-chip--active' : ''}`}
              onClick={() => setDraftCatId(undefined)}
            >
              Tất cả danh mục
            </button>
            {categories.map((cat) => {
              const isSelected = draftCatId === cat.categoryId;
              return (
                <button
                  key={cat.categoryId}
                  type="button"
                  className={`og-filter-chip ${isSelected ? 'og-filter-chip--active' : ''}`}
                  onClick={() => setDraftCatId(isSelected ? undefined : cat.categoryId)}
                >
                  {cat.categoryName}
                </button>
              );
            })}
          </div>
        </div>

        {/* 2. Condition Selection (Local draft) */}
        <div style={{ marginBottom: '14px' }}>
          <span style={{ fontSize: '0.82rem', fontWeight: 700, color: 'var(--og-color-text-primary)', display: 'block', marginBottom: '6px' }}>
            ⭐ Tình trạng sản phẩm:
          </span>
          <div style={{ display: 'flex', gap: '6px', alignItems: 'center', flexWrap: 'wrap' }}>
            {[
              { key: '', label: 'Tất cả tình trạng' },
              { key: 'LIKE_NEW', label: 'Như mới' },
              { key: 'GOOD', label: 'Khá tốt' },
              { key: 'FAIR', label: 'Chấp nhận được' },
              { key: 'VINTAGE', label: 'Đồ cổ' },
            ].map((cond) => {
              const isSelected = (!cond.key && !draftCondition) || draftCondition === cond.key;
              return (
                <button
                  key={cond.key}
                  type="button"
                  className={`og-filter-chip ${isSelected ? 'og-filter-chip--active' : ''}`}
                  onClick={() => setDraftCondition(cond.key ? cond.key : undefined)}
                >
                  {cond.label}
                </button>
              );
            })}
          </div>
        </div>

        {/* 3. Compact Price Slider (Thanh kéo giá tiền tinh giản) */}
        <div className="og-quick-filter-slider-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
            <span style={{ fontSize: '0.82rem', fontWeight: 700, color: 'var(--og-color-text-primary)' }}>
              💰 Khoảng giá:
            </span>
            <span style={{ fontSize: '0.84rem', fontWeight: 700, color: 'var(--og-color-primary)' }}>
              {draftMinPrice === 0 && draftMaxPrice >= MAX_QUICK_PRICE
                ? 'Tất cả mức giá'
                : draftMinPrice === 0
                ? `Tối đa ${formatVnd(draftMaxPrice)}`
                : draftMaxPrice >= MAX_QUICK_PRICE
                ? `Từ ${formatVnd(draftMinPrice)} trở lên`
                : `${formatVnd(draftMinPrice)} — ${formatVnd(draftMaxPrice)}`}
            </span>
          </div>

          <div className="og-quick-slider-track-wrap">
            <div className="og-quick-slider-track" />
            <div
              className="og-quick-slider-progress"
              style={{
                left: `${minPercent}%`,
                width: `${Math.max(0, maxPercent - minPercent)}%`,
              }}
            />
            <input
              type="range"
              min={0}
              max={MAX_QUICK_PRICE}
              step={STEP_QUICK_PRICE}
              value={draftMinPrice}
              onChange={(e) => {
                const val = Number(e.target.value);
                if (val <= draftMaxPrice) setDraftMinPrice(val);
              }}
              className="og-quick-range-thumb og-quick-range-thumb--min"
              aria-label="Giá tối thiểu"
            />
            <input
              type="range"
              min={0}
              max={MAX_QUICK_PRICE}
              step={STEP_QUICK_PRICE}
              value={draftMaxPrice}
              onChange={(e) => {
                const val = Number(e.target.value);
                if (val >= draftMinPrice) setDraftMaxPrice(val);
              }}
              className="og-quick-range-thumb og-quick-range-thumb--max"
              aria-label="Giá tối đa"
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '6px', fontSize: '0.72rem', color: 'var(--og-color-text-muted)' }}>
            <span>0 ₫</span>
            <span>25.000.000 ₫</span>
            <span>50.000.000+ ₫</span>
          </div>
        </div>

        {/* Footer Actions */}
        <div style={{ display: 'flex', gap: '10px', alignItems: 'center', justifyContent: 'flex-end', borderTop: '1px solid var(--og-color-border)', paddingTop: '10px', marginTop: '12px' }}>
          <button
            type="button"
            className="og-button og-button--ghost og-button--sm"
            onClick={handleReset}
          >
            Đặt lại bộ lọc
          </button>
          <button
            type="button"
            className="og-button og-button--primary og-button--sm"
            onClick={handleApply}
            style={{ fontWeight: 700, padding: '7px 20px' }}
          >
            Áp dụng bộ lọc
          </button>
        </div>
      </div>
    </div>
  );
};

const DEFAULT_CATEGORIES: Category[] = [
  { categoryId: 1, categoryName: 'Điện tử', slug: 'electronics', description: 'Thiết bị điện tử, điện thoại, máy tính, linh kiện và phụ kiện', displayOrder: 1 },
  { categoryId: 2, categoryName: 'Thời trang', slug: 'fashion', description: 'Quần áo, giày dép, phụ kiện và túi xách đã qua sử dụng', displayOrder: 2 },
  { categoryId: 3, categoryName: 'Nhà cửa & đời sống', slug: 'home-living', description: 'Đồ gia dụng, nội thất, thiết bị nhà bếp và trang trí', displayOrder: 3 },
  { categoryId: 4, categoryName: 'Sách & văn phòng phẩm', slug: 'books-stationery', description: 'Sách, giáo trình, truyện và dụng cụ học tập', displayOrder: 4 },
  { categoryId: 5, categoryName: 'Thể thao & dã ngoại', slug: 'sports-outdoors', description: 'Dụng cụ thể thao, xe đạp, phụ kiện thể hình và dã ngoại', displayOrder: 5 },
  { categoryId: 6, categoryName: 'Đồ sưu tầm', slug: 'collectibles', description: 'Mô hình, đồng hồ cổ, đồ lưu niệm và hiện vật sưu tầm', displayOrder: 6 },
  { categoryId: 7, categoryName: 'Mẹ & bé', slug: 'mother-baby', description: 'Đồ dùng cho mẹ và bé, đồ chơi an toàn, xe đẩy và nôi cũ', displayOrder: 7 },
  { categoryId: 8, categoryName: 'Khác', slug: 'other', description: 'Các mặt hàng đồ cũ và sản phẩm khác chưa phân loại', displayOrder: 8 },
];

// Clean line-art outline SVG icons (hollow fill, currentColor stroke for high contrast on any background)
const OutlineIcons = {
  Home: () => (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 9.5L12 3l9 6.5V20a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1v-4a1 1 0 0 0-1-1h-2a1 1 0 0 0-1 1v4a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9.5z" />
    </svg>
  ),
  ShoppingBag: () => (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" />
      <line x1="3" y1="6" x2="21" y2="6" />
      <path d="M16 10a4 4 0 0 1-8 0" />
    </svg>
  ),
  Store: () => (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 9l1-5h16l1 5" />
      <path d="M3 9a3 3 0 0 0 6 0 3 3 0 0 0 6 0 3 3 0 0 0 6 0" />
      <path d="M4 14v6a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-6" />
      <path d="M9 21v-4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v4" />
    </svg>
  ),
  Tag: () => (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z" />
      <line x1="7" y1="7" x2="7.01" y2="7" />
    </svg>
  ),
  Package: () => (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M16.5 9.4 7.55 4.24" />
      <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
      <polyline points="3.29 7 12 12 20.71 7" />
      <line x1="12" y1="22" x2="12" y2="12" />
    </svg>
  ),
  Shield: () => (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
    </svg>
  ),
  CategoryGrid: () => (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
    </svg>
  ),
  ChevronDown: () => (
    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <polyline points="6 9 12 15 18 9" />
    </svg>
  ),
  Search: () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="11" cy="11" r="7" />
      <line x1="21" y1="21" x2="16.65" y2="16.65" />
    </svg>
  ),
  Cart: () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="9" cy="21" r="1" />
      <circle cx="20" cy="21" r="1" />
      <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
    </svg>
  ),
  Coin: () => (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="9" />
      <circle cx="12" cy="12" r="5" />
    </svg>
  ),
  User: () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
      <circle cx="12" cy="7" r="4" />
    </svg>
  ),
  Logout: () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <polyline points="16 17 21 12 16 7" />
      <line x1="21" y1="12" x2="9" y2="12" />
    </svg>
  ),
  Menu: () => (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <line x1="3" y1="12" x2="21" y2="12" />
      <line x1="3" y1="6" x2="21" y2="6" />
      <line x1="3" y1="18" x2="21" y2="18" />
    </svg>
  ),
  Close: () => (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <line x1="18" y1="6" x2="6" y2="18" />
      <line x1="6" y1="6" x2="18" y2="18" />
    </svg>
  ),
  Filter: () => (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3" />
    </svg>
  ),
};

const renderCategoryOutlineIcon = (slug?: string, name?: string) => {
  const key = `${slug || ''} ${name || ''}`.toLowerCase();
  if (key.includes('electronic') || key.includes('điện tử') || key.includes('thoại') || key.includes('máy tính')) {
    return (
      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <rect x="2" y="3" width="20" height="14" rx="2" />
        <line x1="8" y1="21" x2="16" y2="21" />
        <line x1="12" y1="17" x2="12" y2="21" />
      </svg>
    );
  }
  if (key.includes('fashion') || key.includes('thời trang') || key.includes('áo') || key.includes('quần')) {
    return (
      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M12 4a2 2 0 0 1 2 2c0 1-1 1.5-2 2.5L2 14h20L12 8.5" />
        <line x1="2" y1="14" x2="2" y2="19" />
        <line x1="22" y1="14" x2="22" y2="19" />
        <line x1="2" y1="19" x2="22" y2="19" />
      </svg>
    );
  }
  if (key.includes('home') || key.includes('nhà') || key.includes('gia dụng') || key.includes('đời sống')) {
    return (
      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M3 10.5 12 3l9 7.5" />
        <path d="M5 9v11a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V9" />
        <path d="M9 21v-6a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v6" />
      </svg>
    );
  }
  if (key.includes('book') || key.includes('sách') || key.includes('văn phòng')) {
    return (
      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
        <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
      </svg>
    );
  }
  if (key.includes('sport') || key.includes('thể thao') || key.includes('dã ngoại') || key.includes('xe')) {
    return (
      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="9" />
        <path d="M12 3a9 9 0 0 0 0 18" />
        <path d="M12 3a9 9 0 0 1 0 18" />
        <path d="M3 12h18" />
      </svg>
    );
  }
  if (key.includes('collect') || key.includes('sưu tầm') || key.includes('cổ')) {
    return (
      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M6 3h12l4 6-10 12L2 9z" />
        <path d="M2 9h20" />
        <path d="M12 21 8.5 9l2-6h3l2 6-3.5 12z" />
      </svg>
    );
  }
  if (key.includes('baby') || key.includes('mẹ') || key.includes('bé') || key.includes('trẻ')) {
    return (
      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="9" />
        <path d="M8 14s1.5 2 4 2 4-2 4-2" />
        <line x1="9" y1="9" x2="9.01" y2="9" />
        <line x1="15" y1="9" x2="15.01" y2="9" />
      </svg>
    );
  }
  // Default / Tất cả / Khác
  return (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
    </svg>
  );
};

export const AppHeader: React.FC = () => {
  const { user, isAuthenticated, logout } = useAuth();
  const { totalCount: cartCount } = useCart();
  const navigate = useNavigate();
  const location = useLocation();
  const isMarketplace = location.pathname === '/marketplace';
  const [searchQuery, setSearchQuery] = useState('');
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
  const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);
  const [failedAvatarUrl, setFailedAvatarUrl] = useState<string | null>(null);
  const [isScrolled, setIsScrolled] = useState(false);
  const [isTabsExpanded, setIsTabsExpanded] = useState(false);
  const [isFilterExpanded, setIsFilterExpanded] = useState(false);
  const [categories, setCategories] = useState<Category[]>([]);
  const [isCatMenuOpen, setIsCatMenuOpen] = useState(false);
  const userMenuRef = useRef<HTMLDivElement>(null);
  const headerRef = useRef<HTMLElement>(null);
  const catTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Load categories for quick selector dropdown
  useEffect(() => {
    let isMounted = true;
    marketplaceApi
      .getCategories()
      .then((cats) => {
        if (isMounted && cats && cats.length > 0) {
          setCategories(cats);
        }
      })
      .catch((err) => {
        console.warn('Could not load categories in header:', err);
      });
    return () => {
      isMounted = false;
    };
  }, []);

  // Anti-jitter hysteresis scroll listener for compact sticky header & tab-bar collapsing
  // Hysteresis deadzone: collapse at > 80px, only restore at <= 20px (deadzone 20px - 80px)
  // Prevents the infinite oscillation loop: expand -> push layout -> scroll increases -> collapse -> repeat
  useEffect(() => {
    const SCROLL_DOWN_THRESHOLD = 80;
    const SCROLL_UP_THRESHOLD = 20;

    const handleScroll = () => {
      const currentScroll = window.scrollY || document.documentElement.scrollTop || 0;

      setIsScrolled((prevScrolled) => {
        if (!prevScrolled && currentScroll > SCROLL_DOWN_THRESHOLD) {
          return true;
        }
        if (prevScrolled && currentScroll <= SCROLL_UP_THRESHOLD) {
          setIsTabsExpanded(false);
          return false;
        }
        return prevScrolled;
      });
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  // Close floating tabs, category menu & user dropdown when route changes
  useEffect(() => {
    setIsTabsExpanded(false);
    setIsUserMenuOpen(false);
    setIsCatMenuOpen(false);
  }, [location.pathname]);

  // Close dropdowns when clicking outside or pressing Escape
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (userMenuRef.current && !userMenuRef.current.contains(event.target as Node)) {
        setIsUserMenuOpen(false);
      }
      if (
        isTabsExpanded &&
        headerRef.current &&
        !headerRef.current.contains(event.target as Node)
      ) {
        setIsTabsExpanded(false);
      }
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsUserMenuOpen(false);
        setIsTabsExpanded(false);
        setIsCatMenuOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isUserMenuOpen, isTabsExpanded]);

  const handleCatMouseEnter = () => {
    if (catTimeoutRef.current) clearTimeout(catTimeoutRef.current);
    setIsCatMenuOpen(true);
  };

  const handleCatMouseLeave = () => {
    catTimeoutRef.current = setTimeout(() => {
      setIsCatMenuOpen(false);
    }, 180);
  };

  const handleCategoryClick = (catId?: number) => {
    setIsCatMenuOpen(false);
    if (catId) {
      navigate(`/marketplace?categoryId=${catId}`);
    } else {
      navigate('/marketplace');
    }
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      navigate(`/marketplace?query=${encodeURIComponent(searchQuery.trim())}`);
    } else {
      navigate('/marketplace');
    }
  };

  const isSeller = isAuthenticated && user?.roles.includes('SELLER');
  const displayCategories = categories.length > 0 ? categories : DEFAULT_CATEGORIES;

  return (
    <>
      <header
        className={`og-header ${isScrolled ? 'og-header--scrolled' : ''}`}
        role="banner"
        ref={headerRef}
      >
        {/* Tier 1: Top Bar (Logo, Search, Quick Actions & User/Auth) */}
        <div className="og-header__top-bar">
          <div className="og-header__inner">
            {/* Brand Logo - No underline on focus/hover */}
            <Link
              to="/"
              className="og-header__brand"
              aria-label="Old but Gold Trang chủ"
              style={{ textDecoration: 'none' }}
            >
              <span className="og-header__brand-logo" aria-hidden="true">
                <OutlineIcons.Coin />
              </span>
              <span>Old but Gold</span>
              <span className="og-header__brand-dot" aria-hidden="true" />
            </Link>

            {/* Search Bar */}
            <form className="og-header__search-wrap" onSubmit={handleSearchSubmit} role="search">
              <span className="og-header__search-icon" aria-hidden="true">
                <OutlineIcons.Search />
              </span>
              <input
                type="search"
                className="og-header__search-input"
                placeholder="Tìm đồ cũ chất lượng: máy ảnh, đồ sưu tầm, công nghệ..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                aria-label="Tìm kiếm sản phẩm trên O.G Shop"
              />
            </form>

            {/* Tier 1 Actions */}
            <div className="og-header__actions">
              {/* Menu Toggle Button (appears when scrolled down - Hình 3) */}
              {isScrolled && (
                <button
                  type="button"
                  className={`og-header__toggle-tabs-btn ${isTabsExpanded ? 'og-header__toggle-tabs-btn--active' : ''}`}
                  onClick={() => setIsTabsExpanded(!isTabsExpanded)}
                  aria-expanded={isTabsExpanded}
                  aria-label={isTabsExpanded ? 'Đóng menu tab' : 'Mở menu tab'}
                  title="Hiện hoặc ẩn danh mục chuyển tab"
                >
                  <span className="og-header__toggle-tabs-icon" aria-hidden="true">
                    {isTabsExpanded ? <OutlineIcons.Close /> : <OutlineIcons.Menu />}
                  </span>
                  <span className="og-header__toggle-tabs-text">Tab</span>
                </button>
              )}

              {/* Sticky Quick Filter Button on Marketplace page (Requirement 4) */}
              {isScrolled && isMarketplace && (
                <button
                  type="button"
                  className={`og-header__toggle-tabs-btn og-header__toggle-filter-btn ${isFilterExpanded ? 'og-header__toggle-tabs-btn--active' : ''}`}
                  onClick={() => {
                    setIsFilterExpanded(!isFilterExpanded);
                    if (isTabsExpanded) setIsTabsExpanded(false);
                  }}
                  aria-expanded={isFilterExpanded}
                  aria-label={isFilterExpanded ? 'Đóng bộ lọc nhanh' : 'Mở bộ lọc nhanh'}
                  title="Bộ lọc nhanh sản phẩm"
                >
                  <span className="og-header__toggle-tabs-icon" aria-hidden="true">
                    {isFilterExpanded ? <OutlineIcons.Close /> : <OutlineIcons.Filter />}
                  </span>
                  <span className="og-header__toggle-tabs-text">Lọc</span>
                </button>
              )}

              {/* Quick Cart shortcut button pointing to /cart (Requirement 5) */}
              <Link
                to="/cart"
                className="og-header__quick-btn"
                title="Giỏ hàng"
                aria-label="Giỏ hàng"
              >
                <span className="og-header__quick-icon" aria-hidden="true">
                  <OutlineIcons.Cart />
                </span>
                {cartCount > 0 && (
                  <span className="og-header__quick-badge" aria-label={`${cartCount} sản phẩm trong giỏ`}>
                    {cartCount}
                  </span>
                )}
              </Link>

              {isAuthenticated ? (
                /* User Dropdown Menu with Profile & Logout */
                <div className="og-header__user-menu-wrap" ref={userMenuRef}>
                  <button
                    type="button"
                    className={`og-header__user-trigger ${isUserMenuOpen ? 'og-header__user-trigger--active' : ''}`}
                    onClick={() => setIsUserMenuOpen(!isUserMenuOpen)}
                    aria-expanded={isUserMenuOpen}
                    aria-haspopup="true"
                    aria-label={`Menu người dùng: ${user?.fullName || 'Tài khoản'}`}
                  >
                    <span className="og-header__user-avatar" aria-hidden="true">
                      {user?.avatarUrl && user.avatarUrl !== failedAvatarUrl ? (
                        <img
                          key={user.avatarUrl}
                          src={user.avatarUrl}
                          alt={user?.fullName || 'Avatar'}
                          style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: '50%' }}
                          onError={() => setFailedAvatarUrl(user.avatarUrl || null)}
                        />
                      ) : null}
                      <span
                        className="og-header__user-avatar-fallback"
                        style={{ display: user?.avatarUrl && user.avatarUrl !== failedAvatarUrl ? 'none' : 'inline' }}
                      >
                        {user?.fullName?.charAt(0).toUpperCase() || 'U'}
                      </span>
                    </span>
                    <span className="og-header__user-name">
                      {user?.fullName || 'Tài khoản'}
                    </span>
                    <span className="og-header__user-chevron" aria-hidden="true">
                      <OutlineIcons.ChevronDown />
                    </span>
                  </button>

                  {isUserMenuOpen && (
                    <div className="og-header__dropdown-menu" role="menu">
                      <div className="og-header__dropdown-header">
                        <div className="og-header__dropdown-user-info">
                          <div className="og-header__dropdown-name">{user?.fullName}</div>
                          <div className="og-header__dropdown-email">{user?.email}</div>
                        </div>
                      </div>

                      <div className="og-header__dropdown-divider" />

                      <Link
                        to="/profile"
                        className="og-header__dropdown-item"
                        role="menuitem"
                        onClick={() => setIsUserMenuOpen(false)}
                      >
                        <span className="og-header__dropdown-icon">
                          <OutlineIcons.User />
                        </span>
                        <span>Hồ sơ & Sổ địa chỉ</span>
                      </Link>

                      <Link
                        to="/cart"
                        className="og-header__dropdown-item"
                        role="menuitem"
                        onClick={() => setIsUserMenuOpen(false)}
                      >
                        <span className="og-header__dropdown-icon">
                          <OutlineIcons.Cart />
                        </span>
                        <span>Giỏ hàng của tôi {cartCount > 0 ? `(${cartCount})` : ''}</span>
                      </Link>

                      <Link
                        to="/orders"
                        className="og-header__dropdown-item"
                        role="menuitem"
                        onClick={() => setIsUserMenuOpen(false)}
                      >
                        <span className="og-header__dropdown-icon">
                          <OutlineIcons.Package />
                        </span>
                        <span>Đơn mua của tôi</span>
                      </Link>

                      {isSeller && (
                        <Link
                          to="/seller/products"
                          className="og-header__dropdown-item"
                          role="menuitem"
                          onClick={() => setIsUserMenuOpen(false)}
                        >
                          <span className="og-header__dropdown-icon">
                            <OutlineIcons.Store />
                          </span>
                          <span>Kênh Người Bán</span>
                        </Link>
                      )}

                      <div className="og-header__dropdown-divider" />

                      <button
                        type="button"
                        className="og-header__dropdown-item og-header__dropdown-item--danger"
                        role="menuitem"
                        onClick={() => {
                          setIsUserMenuOpen(false);
                          setShowLogoutConfirm(true);
                        }}
                      >
                        <span className="og-header__dropdown-icon">
                          <OutlineIcons.Logout />
                        </span>
                        <span>Đăng xuất</span>
                      </button>
                    </div>
                  )}
                </div>
              ) : (
                <>
                  <Link to="/login" className="og-header__tab og-header__tab--ghost">
                    Đăng nhập
                  </Link>
                  <Button variant="primary" size="sm" onClick={() => navigate('/register')}>
                    Đăng ký
                  </Button>
                </>
              )}
            </div>
          </div>
        </div>

        {/* Tier 2: Bottom Navigation Bar with Centered Tabs and Quick Category Selector */}
        <div
          className={`og-header__nav-bar ${
            isScrolled
              ? isTabsExpanded
                ? 'og-header__nav-bar--expanded'
                : 'og-header__nav-bar--hidden'
              : ''
          }`}
        >
          <div className="og-header__inner og-header__inner--sub">
            {/* Quick Category Selector Dropdown (Hover auto-drop, soft background, adjacent to Trang chủ) */}
            <div
              className="og-header__category-wrap"
              onMouseEnter={handleCatMouseEnter}
              onMouseLeave={handleCatMouseLeave}
            >
              <button
                type="button"
                className={`og-header__category-btn ${isCatMenuOpen ? 'og-header__category-btn--active' : ''}`}
                onClick={() => setIsCatMenuOpen(!isCatMenuOpen)}
                aria-expanded={isCatMenuOpen}
                aria-haspopup="true"
                aria-label="Danh mục sản phẩm"
              >
                <span className="og-header__category-icon" aria-hidden="true">
                  <OutlineIcons.CategoryGrid />
                </span>
                <span>Danh mục sản phẩm</span>
                <span className="og-header__category-chevron" aria-hidden="true">
                  <OutlineIcons.ChevronDown />
                </span>
              </button>

              {isCatMenuOpen && (
                <div className="og-header__category-menu" role="menu">
                  <button
                    type="button"
                    className="og-header__category-item"
                    role="menuitem"
                    onClick={() => handleCategoryClick()}
                  >
                    <span className="og-header__category-item-icon" aria-hidden="true">
                      {renderCategoryOutlineIcon()}
                    </span>
                    <span>Tất cả sản phẩm cũ</span>
                  </button>

                  <div className="og-header__category-divider" />

                  {displayCategories.map((cat) => (
                    <button
                      key={cat.categoryId}
                      type="button"
                      className="og-header__category-item"
                      role="menuitem"
                      onClick={() => handleCategoryClick(cat.categoryId)}
                    >
                      <span className="og-header__category-item-icon" aria-hidden="true">
                        {renderCategoryOutlineIcon(cat.slug, cat.categoryName)}
                      </span>
                      <span>{cat.categoryName}</span>
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* Centered Navigation Tabs */}
            <nav className="og-header__tabs" aria-label="Điều hướng chính">
              {/* Tab Trang chủ */}
              <Link
                to="/"
                className={`og-header__tab ${
                  location.pathname === '/' ? 'og-header__tab--active' : ''
                }`}
              >
                <span className="og-header__tab-icon" aria-hidden="true">
                  <OutlineIcons.Home />
                </span>
                <span>Trang chủ</span>
              </Link>

              {/* Tab Mua sắm */}
              <Link
                to="/marketplace"
                className={`og-header__tab ${
                  location.pathname === '/marketplace' ? 'og-header__tab--active' : ''
                }`}
              >
                <span className="og-header__tab-icon" aria-hidden="true">
                  <OutlineIcons.ShoppingBag />
                </span>
                <span>Mua sắm</span>
              </Link>

              {/* Tab Kênh Người Bán / Bán đồ cũ */}
              {isAuthenticated ? (
                <>
                  {isSeller ? (
                    <Link
                      to="/seller/products"
                      className={`og-header__tab ${
                        location.pathname.startsWith('/seller') ? 'og-header__tab--active' : ''
                      }`}
                    >
                      <span className="og-header__tab-icon" aria-hidden="true">
                        <OutlineIcons.Store />
                      </span>
                      <span>Kênh Người Bán</span>
                    </Link>
                  ) : (
                    <Link
                      to="/seller-verification"
                      className={`og-header__tab ${
                        location.pathname === '/seller-verification' ? 'og-header__tab--active' : ''
                      }`}
                    >
                      <span className="og-header__tab-icon" aria-hidden="true">
                        <OutlineIcons.Tag />
                      </span>
                      <span>Bán đồ cũ</span>
                    </Link>
                  )}

                  {/* Tab Đơn mua */}
                  <Link
                    to="/orders"
                    className={`og-header__tab ${
                      location.pathname.startsWith('/orders') ? 'og-header__tab--active' : ''
                    }`}
                    title="Đơn mua của tôi"
                  >
                    <span className="og-header__tab-icon" aria-hidden="true">
                      <OutlineIcons.Package />
                    </span>
                    <span>Đơn mua</span>
                  </Link>
                </>
              ) : (
                <Link
                  to="/seller-verification"
                  className={`og-header__tab ${
                    location.pathname === '/seller-verification' ? 'og-header__tab--active' : ''
                  }`}
                >
                  <span className="og-header__tab-icon" aria-hidden="true">
                    <OutlineIcons.Tag />
                  </span>
                  <span>Bán đồ cũ</span>
                </Link>
              )}
            </nav>
          </div>
        </div>

        {/* Quick Filter Dropdown when scrolled on Marketplace (Requirement 4) */}
        {isScrolled && isMarketplace && isFilterExpanded && (
          <QuickFilterPanel
            categories={displayCategories}
            currentSearch={location.search}
            onApply={(params) => {
              const sp = new URLSearchParams(location.search);
              if (params.categoryId) {
                sp.set('categoryId', String(params.categoryId));
                sp.delete('categoryIds');
              } else {
                sp.delete('categoryId');
                sp.delete('categoryIds');
              }

              if (params.condition) {
                sp.set('condition', params.condition);
                sp.delete('conditions');
              } else {
                sp.delete('condition');
                sp.delete('conditions');
              }

              if (params.minPrice) {
                sp.set('minPrice', String(params.minPrice));
              } else {
                sp.delete('minPrice');
              }

              if (params.maxPrice) {
                sp.set('maxPrice', String(params.maxPrice));
              } else {
                sp.delete('maxPrice');
              }

              sp.delete('page');
              navigate(`/marketplace?${sp.toString()}`);
              setIsFilterExpanded(false);
            }}
            onReset={() => {
              navigate('/marketplace');
              setIsFilterExpanded(false);
            }}
            onClose={() => setIsFilterExpanded(false)}
          />
        )}
      </header>

      {/* Confirmation Dialog for Logout (SY-02) */}
      <ConfirmDialog
        isOpen={showLogoutConfirm}
        title="Xác nhận đăng xuất"
        message="Bạn có chắc chắn muốn đăng xuất khỏi tài khoản Old but Gold?"
        confirmText="Đăng xuất"
        cancelText="Ở lại"
        variant="primary"
        onConfirm={async () => {
          setShowLogoutConfirm(false);
          await logout();
          navigate('/login');
        }}
        onCancel={() => setShowLogoutConfirm(false)}
      />
    </>
  );
};
