import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ProductDetail, ProductDetailView, marketplaceApi } from '../features/marketplace';
import { Alert } from '../shared/components';

export const ProductDetailPage: React.FC = () => {
  const { productId } = useParams<{ productId: string }>();

  const [product, setProduct] = useState<ProductDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isNotFound, setIsNotFound] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchDetail = () => {
    if (!productId) return;
    setIsLoading(true);
    setIsNotFound(false);
    setError(null);

    marketplaceApi
      .getProductDetail(productId)
      .then((data) => {
        setProduct(data);
        setError(null);
      })
      .catch((err: unknown) => {
        if (err instanceof Error && err.message === 'PRODUCT_NOT_FOUND') {
          setIsNotFound(true);
        } else {
          setError(err instanceof Error ? err.message : 'Không thể tải chi tiết sản phẩm.');
        }
      })
      .finally(() => {
        setIsLoading(false);
      });
  };

  useEffect(() => {
    if (!productId) return;
    let ignore = false;
    marketplaceApi
      .getProductDetail(productId)
      .then((data) => {
        if (!ignore) {
          setProduct(data);
          setError(null);
          setIsLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          if (err instanceof Error && err.message === 'PRODUCT_NOT_FOUND') {
            setIsNotFound(true);
          } else {
            setError(err instanceof Error ? err.message : 'Không thể tải chi tiết sản phẩm.');
          }
          setIsLoading(false);
        }
      });
    return () => {
      ignore = true;
    };
  }, [productId]);

  if (isLoading) {
    return (
      <div className="og-detail-loading" aria-busy="true" aria-label="Đang tải thông tin sản phẩm...">
        <div className="og-spinner" />
        <p>Đang tải thông tin sản phẩm...</p>
      </div>
    );
  }

  if (isNotFound) {
    return (
      <div className="og-detail-not-found">
        <div className="og-detail-not-found__icon">📦</div>
        <h1 className="og-detail-not-found__title">Sản phẩm không tồn tại</h1>
        <p className="og-detail-not-found__desc">
          Sản phẩm này có thể đã được gỡ xuống, đã bán hoặc liên kết không chính xác.
        </p>
        <Link to="/marketplace" className="og-button og-button--primary">
          Khám phá sản phẩm khác
        </Link>
      </div>
    );
  }

  if (error) {
    return (
      <div className="og-detail-error" role="alert">
        <Alert type="danger">{error}</Alert>
        <button
          type="button"
          className="og-button og-button--primary"
          style={{ marginTop: '16px' }}
          onClick={fetchDetail}
        >
          🔄 Thử lại
        </button>
      </div>
    );
  }

  if (!product) {
    return null;
  }

  return <ProductDetailView product={product} />;
};
