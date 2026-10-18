import { Category, PageResponse, ProductDetail, ProductFilterParams, ProductSummary } from './types';
import { MOCK_CATEGORIES, MOCK_PRODUCTS } from '../../shared/data/mockData';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

// Fallback categories mapped from mockData
const FALLBACK_CATEGORIES: Category[] = MOCK_CATEGORIES.map((cat, idx) => ({
  categoryId: idx + 1,
  categoryName: cat.name.vi,
  slug: cat.slug,
  description: cat.name.en,
  displayOrder: idx + 1,
}));

// Fallback products mapped from mockData
const FALLBACK_PRODUCTS: ProductDetail[] = MOCK_PRODUCTS.map((prod, idx) => {
  const normCond =
    prod.condition === 'like_new'
      ? 'LIKE_NEW'
      : prod.condition === 'good'
      ? 'GOOD'
      : prod.condition === 'fair'
      ? 'FAIR'
      : 'POOR';

  const catMatch = FALLBACK_CATEGORIES.find((c) => c.slug === prod.category) || FALLBACK_CATEGORIES[0];

  return {
    productId: idx + 1,
    title: prod.title.vi,
    description: `${prod.conditionDescription.vi}\n\n${Object.entries(prod.attributes || {})
      .map(([k, v]) => `• ${k}: ${v.vi}`)
      .join('\n')}`,
    listedPrice: prod.price,
    currency: 'VND',
    condition: normCond,
    usageDuration: prod.postedTimeAgo.vi,
    defects: prod.defects.map((d) => d.vi).join('; '),
    repairHistory: 'Chưa qua sửa chữa, còn nguyên bản',
    includedAccessories: prod.attributes?.['Phụ kiện']?.vi || 'Đầy đủ phụ kiện theo máy',
    location: prod.location,
    thumbnailUrl: prod.images[0] || null,
    category: catMatch,
    categories: [catMatch],
    seller: {
      sellerId: 100 + idx,
      displayName: prod.seller.name,
      trustLabel: prod.seller.isKycVerified ? 'Người bán đã xác minh eKYC' : 'Người bán O.G',
    },
    media: prod.images.map((img, mIdx) => ({
      mediaId: mIdx + 1,
      mediaType: 'IMAGE',
      mediaUrl: img,
      displayOrder: mIdx + 1,
    })),
    createdAt: new Date().toISOString(),
  };
});

export const marketplaceApi = {
  async getCategories(): Promise<Category[]> {
    try {
      const res = await fetch(`${API_BASE}/categories`);
      if (res.ok) {
        return await res.json();
      }
    } catch {
      // Backend offline: fallback to rich curated mock categories
    }
    return FALLBACK_CATEGORIES;
  },

  async getProducts(params: ProductFilterParams = {}): Promise<PageResponse<ProductSummary>> {
    try {
      const queryParams = new URLSearchParams();
      if (params.categoryIds && params.categoryIds.length > 0) {
        params.categoryIds.forEach((id) => queryParams.append('categoryIds', String(id)));
      } else if (params.categoryId) {
        queryParams.set('categoryId', String(params.categoryId));
      }

      if (params.conditions && params.conditions.length > 0) {
        params.conditions.forEach((c) => queryParams.append('conditions', c));
      } else if (params.condition) {
        queryParams.set('condition', params.condition);
      }
      if (params.minPrice !== undefined && params.minPrice !== null && !isNaN(params.minPrice)) {
        queryParams.set('minPrice', String(params.minPrice));
      }
      if (params.maxPrice !== undefined && params.maxPrice !== null && !isNaN(params.maxPrice)) {
        queryParams.set('maxPrice', String(params.maxPrice));
      }
      if (params.page !== undefined) queryParams.set('page', String(params.page));
      if (params.size !== undefined) queryParams.set('size', String(params.size));
      if (params.sort) queryParams.set('sort', params.sort);

      const qs = queryParams.toString();
      const url = `${API_BASE}/products${qs ? `?${qs}` : ''}`;
      const res = await fetch(url);
      if (res.ok) {
        return await res.json();
      }
    } catch {
      // Backend offline: fallback to client-side filtering on curated mock products
    }

    let items = [...FALLBACK_PRODUCTS];

    if (params.query) {
      const q = params.query.toLowerCase().trim();
      items = items.filter(
        (p) =>
          p.title.toLowerCase().includes(q) ||
          p.description.toLowerCase().includes(q) ||
          (p.location && p.location.toLowerCase().includes(q))
      );
    }

    if (params.categoryIds && params.categoryIds.length > 0) {
      const idSet = new Set(params.categoryIds.map(Number));
      items = items.filter((p) =>
        (p.categories?.length ? p.categories : [p.category]).some((cat) => idSet.has(cat.categoryId))
      );
    } else if (params.categoryId) {
      items = items.filter((p) =>
        (p.categories?.length ? p.categories : [p.category]).some((cat) => cat.categoryId === Number(params.categoryId))
      );
    }

    if (params.conditions && params.conditions.length > 0) {
      const condSet = new Set(params.conditions.map((c) => c.toUpperCase()));
      items = items.filter((p) => condSet.has(p.condition.toUpperCase()));
    } else if (params.condition && params.condition !== 'ALL') {
      items = items.filter(
        (p) => p.condition.toUpperCase() === params.condition?.toUpperCase()
      );
    }

    if (params.minPrice !== undefined && !isNaN(params.minPrice)) {
      items = items.filter((p) => p.listedPrice >= params.minPrice!);
    }

    if (params.maxPrice !== undefined && !isNaN(params.maxPrice)) {
      items = items.filter((p) => p.listedPrice <= params.maxPrice!);
    }

    // Sort
    if (params.sort === 'price_asc') {
      items.sort((a, b) => a.listedPrice - b.listedPrice);
    } else if (params.sort === 'price_desc') {
      items.sort((a, b) => b.listedPrice - a.listedPrice);
    }

    const page = params.page || 0;
    const size = params.size || 12;
    const totalElements = items.length;
    const totalPages = Math.ceil(totalElements / size);
    const startIdx = page * size;
    const pagedItems = items.slice(startIdx, startIdx + size);
    const hasNext = (page + 1) * size < totalElements;

    return {
      items: pagedItems.map((p) => ({
        productId: p.productId,
        title: p.title,
        listedPrice: p.listedPrice,
        currency: p.currency,
        condition: p.condition,
        location: p.location,
        thumbnailUrl: p.thumbnailUrl,
        category: p.category,
        seller: p.seller,
        createdAt: p.createdAt,
      })),
      page,
      size,
      totalElements,
      totalPages,
      hasNext,
    };
  },

  async getProductDetail(productId: number | string): Promise<ProductDetail> {
    try {
      const res = await fetch(`${API_BASE}/products/${productId}`);
      if (res.status === 404) {
        throw new Error('PRODUCT_NOT_FOUND');
      }
      if (res.ok) {
        return await res.json();
      }
    } catch (err: unknown) {
      if (err instanceof Error && err.message === 'PRODUCT_NOT_FOUND') {
        throw err;
      }
      // Backend offline fallback
    }

    const found = FALLBACK_PRODUCTS.find((p) => String(p.productId) === String(productId));
    if (found) {
      return found;
    }
    return FALLBACK_PRODUCTS[0];
  },
};
