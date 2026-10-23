import { Icon } from '../../../shared/components/Icon/Icon';
import { benefit } from '../../vouchers/voucherApi';
import React, { useState, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { checkoutApi, CheckoutApiError } from '../checkoutApi';
import { CheckoutPreview, CreateAddressInput, OrderCreated } from '../types';
import { profileApi, GhnProvince, GhnDistrict, GhnWard } from '../../profile';
import '../checkout.css';

interface CheckoutViewProps {
  productId: number;
  offerId?: number;
  initialVoucherCode?: string;
}

export const CheckoutView: React.FC<CheckoutViewProps> = ({ productId, offerId, initialVoucherCode }) => {
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [preview, setPreview] = useState<CheckoutPreview | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [isConflict, setIsConflict] = useState(false);

  // Pricing Mode: 'LISTED' or 'OFFER'
  const [pricingMode, setPricingMode] = useState<'LISTED' | 'OFFER'>(offerId ? 'OFFER' : 'LISTED');
  const [activeOfferId, setActiveOfferId] = useState<number | undefined>(offerId);

  // Address State
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);
  const [isAddingNewAddress, setIsAddingNewAddress] = useState(false);
  const [newAddress, setNewAddress] = useState<CreateAddressInput>({
    recipientName: '',
    phoneNumber: '',
    province: '',
    district: '',
    ward: '',
    detailAddress: '',
    ghnProvinceId: null,
    ghnDistrictId: null,
    ghnWardCode: null,
    isDefault: true,
  });

  // GHN Cascading Select State for Checkout New Address
  const [provinces, setProvinces] = useState<GhnProvince[]>([]);
  const [districts, setDistricts] = useState<GhnDistrict[]>([]);
  const [wards, setWards] = useState<GhnWard[]>([]);
  const [selectedProvinceId, setSelectedProvinceId] = useState<number | ''>('');
  const [selectedDistrictId, setSelectedDistrictId] = useState<number | ''>('');
  const [selectedWardCode, setSelectedWardCode] = useState<string>('');
  const [isLoadingProvinces, setIsLoadingProvinces] = useState(false);
  const [isLoadingDistricts, setIsLoadingDistricts] = useState(false);
  const [isLoadingWards, setIsLoadingWards] = useState(false);

  // Voucher State
  const [voucherInput, setVoucherInput] = useState(initialVoucherCode || '');
  const [appliedVoucherCode, setAppliedVoucherCode] = useState<string | null>(initialVoucherCode || null);
  const [voucherError, setVoucherError] = useState<string | null>(null);

  // Payment Method State
  const [paymentMethod, setPaymentMethod] = useState<'BANK_TRANSFER_MOCK' | 'COD_MOCK' | 'E_WALLET_MOCK'>('BANK_TRANSFER_MOCK');

  // Order Success State
  const [createdOrder, setCreatedOrder] = useState<OrderCreated | null>(null);

  // Load preview data
  const loadPreview = async (codeToApply?: string, targetOfferId?: number | null) => {
    try {
      setLoading(true);
      setErrorMsg(null);
      setIsConflict(false);
      const effOffer = targetOfferId === null ? undefined : (targetOfferId !== undefined ? targetOfferId : activeOfferId);
      const data = await checkoutApi.getCheckoutPreview(productId, codeToApply, effOffer);
      setPreview(data);
      if (data.acceptedOfferId) {
        setPricingMode('OFFER');
        setActiveOfferId(data.acceptedOfferId);
      }
      if (data.selectedAddress && !selectedAddressId) {
        setSelectedAddressId(data.selectedAddress.addressId);
      } else if (data.addresses.length === 0) {
        setIsAddingNewAddress(true);
      }
      if (data.appliedVoucher) {
        setAppliedVoucherCode(data.appliedVoucher.code);
        setVoucherInput(data.appliedVoucher.code);
        setVoucherError(null);
      } else if (codeToApply) {
        setAppliedVoucherCode('');
        setVoucherError(data.voucherRejectionReason || 'Voucher chưa có trong ví hoặc không đủ điều kiện cho đơn này.');
      } else {
        setAppliedVoucherCode('');
      }
    } catch (err: unknown) {
      const error = err as CheckoutApiError;
      if (error.code === 'PRODUCT_STATE_CONFLICT' || error.code === 'PRODUCT_UNAVAILABLE') {
        setIsConflict(true);
        setErrorMsg('Món đồ này hiện đang được người khác giữ chỗ hoặc đã bán.');
      } else {
        setErrorMsg(error.message || 'Không thể tải thông tin đặt hàng.');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (productId) {
      loadPreview(initialVoucherCode || appliedVoucherCode || undefined, offerId);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [productId, offerId, initialVoucherCode]);

  const handleApplyVoucher = (code: string) => {
    if (!code.trim()) return;
    setVoucherError(null);
    loadPreview(code.trim());
  };

  const handleRemoveVoucher = () => {
    setAppliedVoucherCode(null);
    setVoucherInput('');
    setVoucherError(null);
    loadPreview();
  };

  const loadProvinces = useCallback(async () => {
    if (provinces.length > 0) return provinces;
    setIsLoadingProvinces(true);
    try {
      const list = await profileApi.getProvinces();
      setProvinces(list);
      return list;
    } catch {
      setErrorMsg('Không thể tải danh mục Tỉnh/Thành phố từ GHN. Vui lòng thử lại.');
      return [];
    } finally {
      setIsLoadingProvinces(false);
    }
  }, [provinces]);

  useEffect(() => {
    if (isAddingNewAddress && provinces.length === 0) {
      loadProvinces();
    }
  }, [isAddingNewAddress, provinces.length, loadProvinces]);

  const handleProvinceChange = async (provId: number | '') => {
    setSelectedProvinceId(provId);
    setSelectedDistrictId('');
    setSelectedWardCode('');
    setDistricts([]);
    setWards([]);

    if (provId === '') {
      setNewAddress((prev) => ({
        ...prev,
        province: '',
        district: '',
        ward: '',
        ghnProvinceId: null,
        ghnDistrictId: null,
        ghnWardCode: null,
      }));
      return;
    }

    const p = provinces.find((item) => item.provinceId === provId);
    setNewAddress((prev) => ({
      ...prev,
      province: p ? p.provinceName : '',
      district: '',
      ward: '',
      ghnProvinceId: Number(provId),
      ghnDistrictId: null,
      ghnWardCode: null,
    }));

    setIsLoadingDistricts(true);
    try {
      const distList = await profileApi.getDistricts(provId);
      setDistricts(distList);
    } catch {
      setErrorMsg('Không thể tải danh mục Quận/Huyện từ GHN.');
    } finally {
      setIsLoadingDistricts(false);
    }
  };

  const handleDistrictChange = async (distId: number | '') => {
    setSelectedDistrictId(distId);
    setSelectedWardCode('');
    setWards([]);

    if (distId === '') {
      setNewAddress((prev) => ({
        ...prev,
        district: '',
        ward: '',
        ghnDistrictId: null,
        ghnWardCode: null,
      }));
      return;
    }

    const d = districts.find((item) => item.districtId === distId);
    setNewAddress((prev) => ({
      ...prev,
      district: d ? d.districtName : '',
      ward: '',
      ghnDistrictId: Number(distId),
      ghnWardCode: null,
    }));

    setIsLoadingWards(true);
    try {
      const wardList = await profileApi.getWards(distId);
      setWards(wardList);
    } catch {
      setErrorMsg('Không thể tải danh mục Phường/Xã từ GHN.');
    } finally {
      setIsLoadingWards(false);
    }
  };

  const handleWardChange = (wardCode: string) => {
    setSelectedWardCode(wardCode);
    if (!wardCode) {
      setNewAddress((prev) => ({
        ...prev,
        ward: '',
        ghnWardCode: null,
      }));
      return;
    }

    const w = wards.find((item) => item.wardCode === wardCode);
    setNewAddress((prev) => ({
      ...prev,
      ward: w ? w.wardName : '',
      ghnWardCode: wardCode,
    }));
  };

  const selectedAddress = !isAddingNewAddress
    ? preview?.addresses.find((a) => a.addressId === selectedAddressId) || preview?.selectedAddress || null
    : null;

  const isSelectedAddressGhnValid = Boolean(
    selectedAddress &&
    selectedAddress.ghnDistrictId &&
    selectedAddress.ghnWardCode &&
    selectedAddress.ghnWardCode.trim() !== ''
  );

  const isNewAddressGhnValid = Boolean(
    isAddingNewAddress &&
    newAddress.ghnDistrictId &&
    newAddress.ghnWardCode &&
    newAddress.ghnWardCode.trim() !== ''
  );

  const isCheckoutAddressValid = isAddingNewAddress
    ? isNewAddressGhnValid
    : isSelectedAddressGhnValid;

  const handleConfirmOrder = async () => {
    if (!preview) return;

    if (!isAddingNewAddress && !selectedAddressId) {
      setErrorMsg('Vui lòng chọn hoặc thêm địa chỉ nhận hàng.');
      return;
    }

    if (isAddingNewAddress) {
      if (
        !newAddress.recipientName.trim() ||
        !newAddress.phoneNumber.trim() ||
        !newAddress.detailAddress.trim() ||
        !newAddress.province.trim() ||
        !newAddress.district.trim() ||
        !newAddress.ward.trim()
      ) {
        setErrorMsg('Vui lòng điền đầy đủ họ tên, số điện thoại và địa chỉ nhận hàng.');
        return;
      }

      if (!newAddress.ghnDistrictId || !newAddress.ghnWardCode) {
        setErrorMsg('Vui lòng chọn đầy đủ Tỉnh/Thành, Quận/Huyện, Phường/Xã theo chuẩn GHN.');
        return;
      }
    } else {
      if (!selectedAddress) {
        setErrorMsg('Vui lòng chọn hoặc thêm địa chỉ nhận hàng.');
        return;
      }
      if (!isSelectedAddressGhnValid) {
        setErrorMsg('Địa chỉ đang chọn chưa chuẩn hóa theo mã bưu chính GHN. Vui lòng thêm địa chỉ mới hoặc cập nhật tại Sổ địa chỉ.');
        return;
      }
    }

    try {
      setSubmitting(true);
      setErrorMsg(null);

      const finalOfferId = pricingMode === 'OFFER' ? (offerId ?? activeOfferId ?? preview.eligibleOfferId ?? undefined) : undefined;
      const order = await checkoutApi.buyNow({
        productId,
        offerId: finalOfferId,
        addressId: !isAddingNewAddress ? selectedAddressId! : undefined,
        newAddress: isAddingNewAddress ? newAddress : undefined,
        voucherCode: appliedVoucherCode || undefined,
      });

      setCreatedOrder(order);
      // Chuyển hướng người dùng trực tiếp đến màn hình thanh toán gồm Bill và mã QR VietQR (1 giờ đếm ngược)
      navigate(`/checkout/payment?orderId=${order.orderId}&method=${paymentMethod}`);
    } catch (err: unknown) {
      const error = err as CheckoutApiError;
      if (error.code === 'PRODUCT_STATE_CONFLICT' || error.code === 'PRODUCT_UNAVAILABLE') {
        setIsConflict(true);
        setErrorMsg('Rất tiếc! Món đồ này vừa được người mua khác giữ chỗ thanh toán.');
      } else {
        setErrorMsg(error.message || 'Đặt hàng không thành công. Vui lòng thử lại.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const formatVnd = (num: number) => `${new Intl.NumberFormat('vi-VN').format(num)} đ`;

  // Render Loading
  if (loading && !preview) {
    return (
      <div style={{ maxWidth: 880, margin: '40px auto', padding: 24, textAlign: 'center' }}>
        <div style={{ fontSize: 36, marginBottom: 16 }}>⏳</div>
        <p style={{ color: 'var(--og-color-text-secondary)', fontSize: 16 }}>Đang chuẩn bị thông tin thanh toán ký quỹ...</p>
      </div>
    );
  }

  // Render Product Conflict Error
  if (isConflict) {
    return (
      <div style={{ maxWidth: 640, margin: '60px auto', padding: 32, background: 'var(--og-color-surface)', borderRadius: 16, boxShadow: '0 4px 20px rgba(0,0,0,0.06)', textAlign: 'center' }}>
        <div style={{ fontSize: 56, marginBottom: 16 }}><Icon name="lock" style={{ width: '1em', height: '1em' }} /></div>
        <h2 style={{ fontSize: 22, fontWeight: 700, color: 'var(--og-color-text-primary)', marginBottom: 12 }}>
          Món đồ không còn khả dụng
        </h2>
        <p style={{ color: 'var(--og-color-text-secondary)', lineHeight: 1.6, marginBottom: 28 }}>
          {errorMsg || 'Món đồ này hiện đang được một người mua khác giữ chỗ thanh toán hoặc đã bán.'}
        </p>
        <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
          <button
            type="button"
            className="og-btn-primary"
            style={{ padding: '12px 24px', background: '#e25b29', color: '#fff', borderRadius: 8, border: 'none', fontWeight: 600, cursor: 'pointer' }}
            onClick={() => navigate('/marketplace')}
          >
            Quay lại Chợ đồ cũ
          </button>
        </div>
      </div>
    );
  }

  // Render Order Success State
  if (createdOrder) {
    return (
      <div style={{ maxWidth: 640, margin: '40px auto', padding: 32, background: 'var(--og-color-surface)', borderRadius: 16, boxShadow: '0 4px 24px rgba(0,0,0,0.08)' }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: 64, height: 64, background: 'var(--og-color-success-bg)', borderRadius: '50%', color: 'var(--og-color-success)', fontSize: 32, marginBottom: 16 }}>
            ✓
          </div>
          <h2 style={{ fontSize: 24, fontWeight: 700, color: 'var(--og-color-text-primary)', margin: 0 }}>
            Đặt hàng & Giữ hàng thành công!
          </h2>
          <p style={{ color: 'var(--og-color-text-secondary)', marginTop: 8 }}>
            Mã đơn hàng: <strong style={{ color: 'var(--og-color-text-primary)' }}>#{createdOrder.orderId}</strong>
          </p>
        </div>

        <div style={{ background: '#fef3c7', border: '1px solid #fde68a', borderRadius: 12, padding: 16, marginBottom: 24 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#92400e', fontWeight: 600 }}>
            <span>⏱️</span>
            <span>Thời hạn thanh toán giữ hàng: 1 giờ</span>
          </div>
          <p style={{ color: '#b45309', fontSize: 13, margin: '6px 0 0', lineHeight: 1.5 }}>
            Sản phẩm đã được khóa giữ chỗ độc quyền cho bạn. Vui lòng hoàn tất thanh toán trước khi hết hạn để tránh đơn bị hủy tự động.
          </p>
        </div>

        <div style={{ border: '1px solid var(--og-color-border)', borderRadius: 12, padding: 20, marginBottom: 24 }}>
          <h3 style={{ fontSize: 15, fontWeight: 600, color: 'var(--og-color-text-secondary)', margin: '0 0 12px' }}>Chi tiết đơn hàng</h3>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8, fontSize: 14 }}>
            <span style={{ color: 'var(--og-color-text-secondary)' }}>Sản phẩm:</span>
            <span style={{ fontWeight: 600, color: 'var(--og-color-text-primary)' }}>{createdOrder.productTitle}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8, fontSize: 14 }}>
            <span style={{ color: 'var(--og-color-text-secondary)' }}>Người nhận:</span>
            <span style={{ color: 'var(--og-color-text-primary)' }}>{createdOrder.recipientName} ({createdOrder.phoneNumber})</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8, fontSize: 14 }}>
            <span style={{ color: 'var(--og-color-text-secondary)' }}>Địa chỉ giao hàng:</span>
            <span style={{ color: 'var(--og-color-text-primary)', textAlign: 'right', maxWidth: 320 }}>{createdOrder.fullAddress}</span>
          </div>
          <div style={{ borderTop: '1px dashed var(--og-color-border)', margin: '12px 0' }} />
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 16 }}>
            <span style={{ fontWeight: 600, color: 'var(--og-color-text-secondary)' }}>Tổng thanh toán Ký quỹ:</span>
            <span style={{ fontWeight: 700, color: '#e25b29', fontSize: 18 }}>{formatVnd(createdOrder.totalAmount)}</span>
          </div>
        </div>

        <div style={{ display: 'flex', gap: 12 }}>
          <button
            type="button"
            style={{
              flex: 2,
              padding: '12px 16px',
              background: '#e25b29',
              color: '#fff',
              borderRadius: 8,
              border: 'none',
              fontWeight: 700,
              cursor: 'pointer',
              fontSize: 15,
            }}
            onClick={() => navigate(`/checkout/payment?orderId=${createdOrder.orderId}`)}
          >
             Thanh toán Ký quỹ ngay
          </button>
          <button
            type="button"
            style={{
              flex: 1,
              padding: '12px 16px',
              background: 'var(--og-color-surface-subtle)',
              color: 'var(--og-color-text-secondary)',
              borderRadius: 8,
              border: '1px solid var(--og-color-border-strong)',
              fontWeight: 600,
              cursor: 'pointer',
              fontSize: 14,
            }}
            onClick={() => navigate('/orders')}
          >
             Quản lý Đơn mua
          </button>
        </div>
      </div>
    );
  }

  if (!preview) return <section className="og-checkout-page" style={{ maxWidth: 640, margin: '40px auto', padding: 24 }}>
    <h1>Chưa thể mở checkout</h1><p role="alert">{errorMsg || 'Chưa tải được thông tin đặt hàng.'}</p>
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12 }}>
      <button className="og-button og-button--primary" onClick={() => void loadPreview(appliedVoucherCode || undefined)}>Thử lại</button>
      <Link className="og-button og-button--outline" to={offerId ? '/messages?productId=' + productId : '/products/' + productId}>{offerId ? 'Về hội thoại để thương lượng lại' : 'Về món đồ'}</Link>
    </div>
  </section>;

  return (
    <div className="og-checkout-page" style={{ maxWidth: 960, margin: '24px auto', padding: '0 16px 40px' }}>
      <div style={{ marginBottom: 24 }}>
        <Link to={`/products/${preview.productId}`} style={{ color: 'var(--og-color-text-secondary)', textDecoration: 'none', fontSize: 14 }}>
          &larr; Quay lại chi tiết sản phẩm
        </Link>
        <h1 style={{ fontSize: 26, fontWeight: 700, color: 'var(--og-color-text-primary)', margin: '8px 0 4px' }}>
           Xác nhận Mua ngay & Thanh toán Ký quỹ
        </h1>
        <p style={{ color: 'var(--og-color-text-secondary)', fontSize: 14, margin: 0 }}>
          Giao dịch an toàn qua tài khoản Ký quỹ Escrow — Tiền chỉ giải ngân sau khi bạn nhận và đồng kiểm món đồ.
        </p>
      </div>

      {errorMsg && (
        <div style={{ background: '#fef2f2', border: '1px solid #fecaca', color: '#991b1b', padding: '12px 16px', borderRadius: 8, marginBottom: 20, fontSize: 14 }}>
          {errorMsg}
        </div>
      )}

      <div className="og-checkout-columns">
        {/* Left Column: Product & Address & Voucher */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          {/* 1. Product Summary Card */}
          <div style={{ background: 'var(--og-color-surface)', border: '1px solid var(--og-color-border)', borderRadius: 12, padding: 20, display: 'flex', gap: 16 }}>
            {preview.thumbnailUrl ? (
              <img
                src={preview.thumbnailUrl}
                alt={preview.productTitle}
                style={{ width: 88, height: 88, objectFit: 'cover', borderRadius: 8, flexShrink: 0 }}
              />
            ) : (
              <div style={{ width: 88, height: 88, background: 'var(--og-color-surface-subtle)', borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--og-color-text-muted)', fontSize: 24, flexShrink: 0 }}><Icon name="package" style={{ width: '1em', height: '1em' }} /></div>
            )}
            <div style={{ flex: 1 }}>
              <span style={{ fontSize: 12, background: 'var(--og-color-surface-subtle)', color: 'var(--og-color-text-secondary)', padding: '2px 8px', borderRadius: 4, fontWeight: 500 }}>
                Người bán: {preview.sellerName}
              </span>
              <h2 style={{ fontSize: 17, fontWeight: 600, color: 'var(--og-color-text-primary)', margin: '6px 0 8px', lineHeight: 1.4 }}>
                {preview.productTitle}
              </h2>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, flexWrap: 'wrap' }}>
                <span style={{ fontSize: 20, fontWeight: 700, color: '#e25b29' }}>
                  {formatVnd(pricingMode === 'OFFER' ? (preview.agreedPrice ?? preview.eligibleOfferPrice ?? preview.listedPrice) : preview.listedPrice)}
                  {pricingMode === 'OFFER' && (preview.acceptedOfferId || preview.eligibleOfferId) && (
                    <small style={{ display: 'block', color: 'var(--og-color-primary)', fontSize: 12, fontWeight: 500, lineHeight: 1.5 }}>
                      Giá đã thương lượng cho 1 món · Giá niêm yết {formatVnd(preview.listedPrice)}
                    </small>
                  )}
                </span>
                <span style={{ fontSize: 12, color: 'var(--og-color-primary)', background: 'var(--og-color-primary-light)', padding: '2px 6px', borderRadius: 4, fontWeight: 500 }}>
                  ✓ Hỗ trợ Đồng kiểm tận tay
                </span>
              </div>

              {/* Option to choose between Listed Price and Negotiated Price if an eligible offer exists */}
              {(preview.eligibleOfferId || (offerId && preview.acceptedOfferId)) && (
                <div style={{ marginTop: 12, padding: '10px 14px', background: 'var(--og-color-bg, #f9fafb)', borderRadius: 8, border: '1px solid var(--og-color-border, #e5e7eb)' }}>
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--og-color-text-primary)', marginBottom: 8 }}>
                    Lựa chọn mức giá áp dụng:
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13 }}>
                      <input
                        type="radio"
                        name="checkoutPricingOption"
                        checked={pricingMode === 'LISTED'}
                        onChange={() => {
                          setPricingMode('LISTED');
                          setActiveOfferId(undefined);
                          void loadPreview(appliedVoucherCode || undefined, null);
                        }}
                        style={{ accentColor: '#e25b29' }}
                      />
                      <span>
                        <strong>Giá niêm yết:</strong> {formatVnd(preview.listedPrice)}
                      </span>
                    </label>
                    <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13 }}>
                      <input
                        type="radio"
                        name="checkoutPricingOption"
                        checked={pricingMode === 'OFFER'}
                        onChange={() => {
                          setPricingMode('OFFER');
                          const optId = preview.eligibleOfferId ?? preview.acceptedOfferId ?? offerId;
                          setActiveOfferId(optId ?? undefined);
                          void loadPreview(appliedVoucherCode || undefined, optId);
                        }}
                        style={{ accentColor: '#e25b29' }}
                      />
                      <span>
                        <strong style={{ color: 'var(--og-color-primary, #047857)' }}>Giá thương lượng đã chấp nhận:</strong>{' '}
                        {formatVnd(preview.eligibleOfferPrice ?? preview.agreedPrice ?? preview.listedPrice)}
                        <span style={{ marginLeft: 6, fontSize: 11, background: '#def7ec', color: '#03543f', padding: '1px 6px', borderRadius: 4, fontWeight: 600 }}>
                          ✓ Đã duyệt
                        </span>
                      </span>
                    </label>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* 2. Shipping Address Card */}
          <div style={{ background: 'var(--og-color-surface)', border: '1px solid var(--og-color-border)', borderRadius: 12, padding: 20 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <h3 style={{ fontSize: 16, fontWeight: 600, color: 'var(--og-color-text-primary)', margin: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
                <span><Icon name="pin" style={{ width: '1em', height: '1em' }} /></span>
                <span>Địa chỉ nhận hàng</span>
              </h3>
              {preview.addresses.length > 0 && (
                <button
                  type="button"
                  style={{ background: 'none', border: 'none', color: '#e25b29', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}
                  onClick={() => setIsAddingNewAddress(!isAddingNewAddress)}
                >
                  {isAddingNewAddress ? 'Chọn địa chỉ có sẵn' : '+ Thêm địa chỉ mới'}
                </button>
              )}
            </div>

            {/* Saved Address Selection */}
            {!isAddingNewAddress && preview.addresses.length > 0 ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {preview.addresses.map((addr) => {
                  const isSelected = selectedAddressId === addr.addressId;
                  const isGhnValid = Boolean(addr.ghnDistrictId && addr.ghnWardCode && addr.ghnWardCode.trim() !== '');

                  return (
                    <label
                      key={addr.addressId}
                      style={{
                        display: 'flex',
                        alignItems: 'flex-start',
                        gap: 12,
                        padding: 12,
                        border: isSelected ? '2px solid #e25b29' : '1px solid #e5e7eb',
                        borderRadius: 8,
                        background: isSelected ? '#fff9f5' : '#fff',
                        cursor: 'pointer',
                      }}
                    >
                      <input
                        type="radio"
                        name="shippingAddress"
                        checked={isSelected}
                        onChange={() => setSelectedAddressId(addr.addressId)}
                        style={{ marginTop: 3, accentColor: '#e25b29' }}
                      />
                      <div style={{ flex: 1 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4, flexWrap: 'wrap' }}>
                          <strong style={{ fontSize: 14, color: 'var(--og-color-text-primary)' }}>{addr.recipientName}</strong>
                          <span style={{ fontSize: 13, color: 'var(--og-color-text-secondary)' }}>({addr.phoneNumber})</span>
                          {addr.isDefault && (
                            <span style={{ fontSize: 11, background: 'var(--og-color-border)', color: 'var(--og-color-text-secondary)', padding: '1px 6px', borderRadius: 4 }}>
                              Mặc định
                            </span>
                          )}
                          {isGhnValid ? (
                            <span
                              style={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: 3,
                                padding: '1px 6px',
                                borderRadius: 4,
                                fontSize: 11,
                                fontWeight: 600,
                                background: '#def7ec',
                                color: '#03543f',
                                border: '1px solid #bcf0da',
                              }}
                            >
                              ✓ Chuẩn GHN
                            </span>
                          ) : (
                            <span
                              style={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: 3,
                                padding: '1px 6px',
                                borderRadius: 4,
                                fontSize: 11,
                                fontWeight: 600,
                                background: '#fef2f2',
                                color: '#991b1b',
                                border: '1px solid #fecaca',
                              }}
                            >
                              ⚠️ Chưa chuẩn GHN
                            </span>
                          )}
                        </div>
                        <p style={{ margin: 0, fontSize: 13, color: 'var(--og-color-text-secondary)' }}>
                          {addr.detailAddress}, {addr.ward}, {addr.district}, {addr.province}
                        </p>
                      </div>
                    </label>
                  );
                })}

                {/* Legacy Address Warning Banner */}
                {selectedAddress && !isSelectedAddressGhnValid && (
                  <div
                    style={{
                      marginTop: 10,
                      padding: '12px 16px',
                      borderRadius: 8,
                      background: '#fffbeb',
                      border: '1px solid #fde68a',
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: 10,
                      color: '#92400e',
                      fontSize: 13,
                    }}
                  >
                    <span style={{ fontSize: 18, flexShrink: 0 }}>⚠️</span>
                    <div>
                      <strong style={{ display: 'block', marginBottom: 2 }}>
                        Địa chỉ nhận hàng đang chọn chưa chuẩn hóa theo mã bưu chính GHN
                      </strong>
                      <span>
                        Hệ thống cần mã Quận/Huyện và Phường/Xã GHN để tự động tính phí vận chuyển và xuất mã vận đơn. Vui lòng{' '}
                        <button
                          type="button"
                          onClick={() => {
                            setIsAddingNewAddress(true);
                            loadProvinces();
                          }}
                          style={{
                            background: 'none',
                            border: 'none',
                            padding: 0,
                            color: '#d97706',
                            fontWeight: 700,
                            textDecoration: 'underline',
                            cursor: 'pointer',
                          }}
                        >
                          thêm địa chỉ mới chuẩn GHN
                        </button>{' '}
                        hoặc cập nhật tại Sổ địa chỉ cá nhân trước khi hoàn tất đặt mua.
                      </span>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              /* New Address Input Form with Cascading 3-tier GHN select */
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <div className="og-checkout-address-fields">
                  <div>
                    <label style={{ display: 'block', fontSize: 13, fontWeight: 500, color: 'var(--og-color-text-secondary)', marginBottom: 4 }}>
                      Tên người nhận *
                    </label>
                    <input
                      type="text"
                      placeholder="Nguyễn Văn A"
                      value={newAddress.recipientName}
                      onChange={(e) => setNewAddress({ ...newAddress, recipientName: e.target.value })}
                      style={{ width: '100%', padding: '8px 12px', border: '1px solid var(--og-color-border-strong)', borderRadius: 6, fontSize: 14, boxSizing: 'border-box' }}
                    />
                  </div>
                  <div>
                    <label style={{ display: 'block', fontSize: 13, fontWeight: 500, color: 'var(--og-color-text-secondary)', marginBottom: 4 }}>
                      Số điện thoại *
                    </label>
                    <input
                      type="text"
                      placeholder="0901234567"
                      value={newAddress.phoneNumber}
                      onChange={(e) => setNewAddress({ ...newAddress, phoneNumber: e.target.value })}
                      style={{ width: '100%', padding: '8px 12px', border: '1px solid var(--og-color-border-strong)', borderRadius: 6, fontSize: 14, boxSizing: 'border-box' }}
                    />
                  </div>
                </div>

                {/* Cascading Select: Province -> District -> Ward */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
                  <div>
                    <label style={{ display: 'block', fontSize: 13, fontWeight: 500, color: 'var(--og-color-text-secondary)', marginBottom: 4 }}>
                      Tỉnh/Thành phố *
                    </label>
                    <select
                      value={selectedProvinceId}
                      onChange={(e) => handleProvinceChange(e.target.value ? Number(e.target.value) : '')}
                      disabled={isLoadingProvinces}
                      style={{
                        width: '100%',
                        padding: '8px 12px',
                        border: '1px solid var(--og-color-border-strong, #d1d5db)',
                        borderRadius: 6,
                        fontSize: 14,
                        background: 'var(--og-color-surface, #fff)',
                        color: 'var(--og-color-text-primary, #111827)',
                        boxSizing: 'border-box',
                      }}
                    >
                      <option value="">{isLoadingProvinces ? 'Đang tải...' : '-- Chọn Tỉnh/Thành --'}</option>
                      {provinces.map((p) => (
                        <option key={p.provinceId} value={p.provinceId}>
                          {p.provinceName}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label style={{ display: 'block', fontSize: 13, fontWeight: 500, color: 'var(--og-color-text-secondary)', marginBottom: 4 }}>
                      Quận/Huyện *
                    </label>
                    <select
                      value={selectedDistrictId}
                      onChange={(e) => handleDistrictChange(e.target.value ? Number(e.target.value) : '')}
                      disabled={!selectedProvinceId || isLoadingDistricts}
                      style={{
                        width: '100%',
                        padding: '8px 12px',
                        border: '1px solid var(--og-color-border-strong, #d1d5db)',
                        borderRadius: 6,
                        fontSize: 14,
                        background: !selectedProvinceId ? 'var(--og-color-bg, #f9fafb)' : 'var(--og-color-surface, #fff)',
                        color: 'var(--og-color-text-primary, #111827)',
                        boxSizing: 'border-box',
                      }}
                    >
                      <option value="">
                        {isLoadingDistricts
                          ? 'Đang tải...'
                          : !selectedProvinceId
                          ? '-- Chọn Tỉnh trước --'
                          : '-- Chọn Quận/Huyện --'}
                      </option>
                      {districts.map((d) => (
                        <option key={d.districtId} value={d.districtId}>
                          {d.districtName}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label style={{ display: 'block', fontSize: 13, fontWeight: 500, color: 'var(--og-color-text-secondary)', marginBottom: 4 }}>
                      Phường/Xã *
                    </label>
                    <select
                      value={selectedWardCode}
                      onChange={(e) => handleWardChange(e.target.value)}
                      disabled={!selectedDistrictId || isLoadingWards}
                      style={{
                        width: '100%',
                        padding: '8px 12px',
                        border: '1px solid var(--og-color-border-strong, #d1d5db)',
                        borderRadius: 6,
                        fontSize: 14,
                        background: !selectedDistrictId ? 'var(--og-color-bg, #f9fafb)' : 'var(--og-color-surface, #fff)',
                        color: 'var(--og-color-text-primary, #111827)',
                        boxSizing: 'border-box',
                      }}
                    >
                      <option value="">
                        {isLoadingWards
                          ? 'Đang tải...'
                          : !selectedDistrictId
                          ? '-- Chọn Huyện trước --'
                          : '-- Chọn Phường/Xã --'}
                      </option>
                      {wards.map((w) => (
                        <option key={w.wardCode} value={w.wardCode}>
                          {w.wardName}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 500, color: 'var(--og-color-text-secondary)', marginBottom: 4 }}>
                    Địa chỉ chi tiết (Số nhà, tên đường, tòa nhà) *
                  </label>
                  <input
                    type="text"
                    placeholder="Số 123 Đường Lê Duẩn..."
                    value={newAddress.detailAddress}
                    onChange={(e) => setNewAddress({ ...newAddress, detailAddress: e.target.value })}
                    style={{ width: '100%', padding: '8px 12px', border: '1px solid var(--og-color-border-strong)', borderRadius: 6, fontSize: 14, boxSizing: 'border-box' }}
                  />
                </div>

                {/* GHN Validation Feedback */}
                <div>
                  {newAddress.ghnDistrictId && newAddress.ghnWardCode ? (
                    <span
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 4,
                        padding: '4px 10px',
                        borderRadius: 6,
                        fontSize: 12,
                        fontWeight: 600,
                        background: '#def7ec',
                        color: '#03543f',
                        border: '1px solid #bcf0da',
                      }}
                    >
                      ✓ Đã chuẩn hóa mã bưu chính GHN (Quận: {newAddress.ghnDistrictId}, Phường: {newAddress.ghnWardCode})
                    </span>
                  ) : (
                    <span
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 4,
                        padding: '4px 10px',
                        borderRadius: 6,
                        fontSize: 12,
                        fontWeight: 500,
                        background: '#fef3c7',
                        color: '#92400e',
                        border: '1px solid #fde68a',
                      }}
                    >
                      ℹ️ Vui lòng chọn đủ 3 cấp Tỉnh/Thành → Quận/Huyện → Phường/Xã để lấy mã bưu chính GHN
                    </span>
                  )}
                </div>
              </div>
            )}
          </div>

          {/* 3. Voucher & Promotions Card */}
          <div style={{ background: 'var(--og-color-surface)', border: '1px solid var(--og-color-border)', borderRadius: 12, padding: 20 }}>
            <h3 style={{ fontSize: 16, fontWeight: 600, color: 'var(--og-color-text-primary)', margin: '0 0 12px', display: 'flex', alignItems: 'center', gap: 8 }}>
              <span><Icon name="tag" style={{ width: '1em', height: '1em' }} /></span>
              <span>Mã giảm giá O.G Shop</span>
            </h3>

            {/* Input & Apply row */}
            <p style={{ fontSize: 14 }}>
              <Link to={`/vouchers?productId=${productId}${pricingMode === 'OFFER' && (activeOfferId ?? preview.eligibleOfferId ?? offerId) ? `&offerId=${activeOfferId ?? preview.eligibleOfferId ?? offerId}` : ''}${appliedVoucherCode ? `&voucherCode=${appliedVoucherCode}` : ''}`}>
                Nhận ưu đãi và xem ví voucher
              </Link>
            </p>
            <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
              <input
                type="text"
                placeholder="Nhập mã (ví dụ: WELCOMEOG, OGFREESHIP)"
                value={voucherInput}
                onChange={(e) => setVoucherInput(e.target.value.toUpperCase())}
                style={{ flex: 1, padding: '8px 12px', border: '1px solid var(--og-color-border-strong)', borderRadius: 6, fontSize: 14, textTransform: 'uppercase' }}
              />
              {appliedVoucherCode ? (
                <button
                  type="button"
                  onClick={handleRemoveVoucher}
                  style={{ padding: '8px 16px', background: 'var(--og-color-surface-subtle)', color: 'var(--og-color-text-secondary)', border: '1px solid var(--og-color-border-strong)', borderRadius: 6, fontWeight: 600, cursor: 'pointer' }}
                >
                  Gỡ bỏ
                </button>
              ) : (
                <button
                  type="button"
                  onClick={() => handleApplyVoucher(voucherInput)}
                  style={{ padding: '8px 16px', background: 'var(--og-color-text-primary)', color: '#fff', border: 'none', borderRadius: 6, fontWeight: 600, cursor: 'pointer' }}
                >
                  Áp dụng
                </button>
              )}
            </div>

            {voucherError && (
              <p style={{ color: '#dc2626', fontSize: 13, margin: '0 0 8px' }}>{voucherError}</p>
            )}

            {preview.appliedVoucher && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'var(--og-color-primary-light)', color: '#065f46', padding: '6px 12px', borderRadius: 6, fontSize: 13, marginBottom: 12 }}>
                <span>✓</span>
                <span>Đã áp dụng: <strong>{preview.appliedVoucher.code}</strong> — {preview.appliedVoucher.title}</span>
              </div>
            )}

            {/* Available Vouchers Pills */}
            {preview.availableVouchers.length > 0 && (
              <div>
                <span style={{ fontSize: 12, color: 'var(--og-color-text-secondary)', display: 'block', marginBottom: 6 }}>
                  Gợi ý mã khuyến mãi dành cho bạn:
                </span>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                  {preview.availableVouchers.map((v) => (
                    <button
                      key={v.voucherId}
                      type="button"
                      onClick={() => handleApplyVoucher(v.code)}
                      style={{
                        background: appliedVoucherCode === v.code ? '#ffedd5' : 'var(--og-color-bg)',
                        border: appliedVoucherCode === v.code ? '1px solid #e25b29' : '1px dashed #d1d5db',
                        color: appliedVoucherCode === v.code ? '#c2410c' : 'var(--og-color-text-secondary)',
                        padding: '4px 10px',
                        borderRadius: 6,
                        fontSize: 12,
                        fontWeight: 500,
                        cursor: 'pointer',
                      }}
                    >
                      {v.code} ({benefit({ ...v, maxDiscountAmount: v.maxDiscountAmount ?? null, voucherType: v.voucherType === 'SHIPPING_DISCOUNT' ? 'SHIPPING_DISCOUNT' : 'ORDER_DISCOUNT', discountType: v.discountType === 'PERCENTAGE' ? 'PERCENTAGE' : 'FIXED_AMOUNT' })})
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* 4. Phương thức thanh toán */}
          <div style={{ background: 'var(--og-color-surface)', border: '1px solid var(--og-color-border)', borderRadius: 12, padding: 20 }}>
            <h3 style={{ fontSize: 16, fontWeight: 600, color: 'var(--og-color-text-primary)', margin: '0 0 14px', display: 'flex', alignItems: 'center', gap: 8 }}>
              <span><Icon name="credit" style={{ width: '1em', height: '1em' }} /></span>
              <span>Phương thức thanh toán</span>
            </h3>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              <label
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  padding: 12,
                  border: paymentMethod === 'BANK_TRANSFER_MOCK' ? '2px solid #e25b29' : '1px solid #e5e7eb',
                  borderRadius: 8,
                  background: paymentMethod === 'BANK_TRANSFER_MOCK' ? '#fff9f5' : '#fff',
                  cursor: 'pointer',
                }}
              >
                <input
                  type="radio"
                  name="checkoutPaymentMethod"
                  checked={paymentMethod === 'BANK_TRANSFER_MOCK'}
                  onChange={() => setPaymentMethod('BANK_TRANSFER_MOCK')}
                  style={{ accentColor: '#e25b29' }}
                />
                <span style={{ fontSize: 20 }}><Icon name="credit" style={{ width: '1em', height: '1em' }} /></span>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--og-color-text-primary)' }}>
                    Chuyển khoản Ngân hàng (Mã QR VietQR)
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--og-color-text-secondary)' }}>
                    Tự động tạo mã QR VietQR, tiền được giữ tại Escrow bảo đảm
                  </div>
                </div>
                <span style={{ fontSize: 11, background: '#dbeafe', color: '#1e40af', padding: '2px 6px', borderRadius: 4, fontWeight: 600 }}>
                  Khuyên Dùng
                </span>
              </label>

              <label
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  padding: 12,
                  border: paymentMethod === 'E_WALLET_MOCK' ? '2px solid #e25b29' : '1px solid #e5e7eb',
                  borderRadius: 8,
                  background: paymentMethod === 'E_WALLET_MOCK' ? '#fff9f5' : '#fff',
                  cursor: 'pointer',
                }}
              >
                <input
                  type="radio"
                  name="checkoutPaymentMethod"
                  checked={paymentMethod === 'E_WALLET_MOCK'}
                  onChange={() => setPaymentMethod('E_WALLET_MOCK')}
                  style={{ accentColor: '#e25b29' }}
                />
                <span style={{ fontSize: 20 }}><Icon name="phone" style={{ width: '1em', height: '1em' }} /></span>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--og-color-text-primary)' }}>
                    Ví điện tử (MoMo / ZaloPay / VNPay)
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--og-color-text-secondary)' }}>
                    Thanh toán nhanh chóng, an toàn qua ví điện tử
                  </div>
                </div>
              </label>

              <label
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  padding: 12,
                  border: paymentMethod === 'COD_MOCK' ? '2px solid #e25b29' : '1px solid #e5e7eb',
                  borderRadius: 8,
                  background: paymentMethod === 'COD_MOCK' ? '#fff9f5' : '#fff',
                  cursor: 'pointer',
                }}
              >
                <input
                  type="radio"
                  name="checkoutPaymentMethod"
                  checked={paymentMethod === 'COD_MOCK'}
                  onChange={() => setPaymentMethod('COD_MOCK')}
                  style={{ accentColor: '#e25b29' }}
                />
                <span style={{ fontSize: 20 }}><Icon name="credit" style={{ width: '1em', height: '1em' }} /></span>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--og-color-text-primary)' }}>
                    Thanh toán khi nhận hàng (COD Đồng kiểm)
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--og-color-text-secondary)' }}>
                    Kiểm tra hàng tận tay cùng shipper rồi thanh toán tiền mặt
                  </div>
                </div>
              </label>
            </div>
          </div>
        </div>

        {/* Right Column: Cost Breakdown & Confirm Button */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16, position: 'sticky', top: 24 }}>
          <div style={{ background: 'var(--og-color-surface)', border: '1px solid var(--og-color-border)', borderRadius: 12, padding: 20 }}>
            <h3 style={{ fontSize: 16, fontWeight: 600, color: 'var(--og-color-text-primary)', margin: '0 0 16px' }}>
              Tóm tắt thanh toán
            </h3>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 10, fontSize: 14 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--og-color-text-secondary)' }}>
                <span>Tiền hàng</span>
                <span style={{ color: 'var(--og-color-text-primary)', fontWeight: 500 }}>{formatVnd(preview.agreedPrice ?? preview.listedPrice)}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--og-color-text-secondary)' }}>
                <span>Phí vận chuyển đồng kiểm</span>
                <span style={{ color: 'var(--og-color-text-primary)', fontWeight: 500 }}>{formatVnd(preview.shippingFee)}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--og-color-text-secondary)' }}>
                <span>Phí bảo vệ người mua Escrow</span>
                <span style={{ color: 'var(--og-color-primary)', fontWeight: 500 }}>Miễn phí (0 đ)</span>
              </div>

              {preview.voucherDiscount > 0 && (
                <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--og-color-primary)' }}>
                  <span>Giảm giá Voucher</span>
                  <span style={{ fontWeight: 600 }}>-{formatVnd(preview.voucherDiscount)}</span>
                </div>
              )}

              {preview.shippingDiscount > 0 && (
                <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--og-color-primary)' }}>
                  <span>Giảm giá Vận chuyển</span>
                  <span style={{ fontWeight: 600 }}>-{formatVnd(preview.shippingDiscount)}</span>
                </div>
              )}

              <div style={{ borderTop: '1px solid var(--og-color-border)', margin: '8px 0 0' }} />

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', paddingTop: 6 }}>
                <span style={{ fontSize: 16, fontWeight: 600, color: 'var(--og-color-text-primary)' }}>Tổng thanh toán</span>
                <span style={{ fontSize: 22, fontWeight: 700, color: '#e25b29' }}>
                  {formatVnd(preview.totalAmount)}
                </span>
              </div>
            </div>

            <button
              type="button"
              disabled={submitting || !isCheckoutAddressValid}
              onClick={handleConfirmOrder}
              style={{
                width: '100%',
                padding: '14px 16px',
                background: submitting || !isCheckoutAddressValid ? 'var(--og-color-text-muted, #9ca3af)' : '#e25b29',
                color: '#fff',
                border: 'none',
                borderRadius: 8,
                fontSize: 16,
                fontWeight: 700,
                marginTop: 20,
                cursor: submitting || !isCheckoutAddressValid ? 'not-allowed' : 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 8,
                boxShadow: submitting || !isCheckoutAddressValid ? 'none' : '0 2px 8px rgba(226, 91, 41, 0.3)',
              }}
            >
              {submitting
                ? 'Đang xử lý đặt hàng...'
                : !isCheckoutAddressValid
                ? '⚠️ Cần chọn địa chỉ chuẩn hóa GHN'
                : '⚡ Xác nhận Mua ngay & Giữ hàng'}
            </button>
            {!isCheckoutAddressValid && (
              <p style={{ color: '#b91c1c', fontSize: 12, textAlign: 'center', margin: '8px 0 0', lineHeight: 1.4 }}>
                Địa chỉ nhận hàng chưa chuẩn hóa GHN. Vui lòng chọn địa chỉ hợp lệ hoặc thêm địa chỉ mới để tiếp tục.
              </p>
            )}
          </div>

          {/* Trust Guarantees Badge */}
          <div style={{ background: 'var(--og-color-bg)', border: '1px solid var(--og-color-surface-subtle)', borderRadius: 12, padding: 16, fontSize: 12, color: 'var(--og-color-text-secondary)', lineHeight: 1.6 }}>
            <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
              <span style={{ fontSize: 16 }}><Icon name="shield" style={{ width: '1em', height: '1em' }} /></span>
              <span><strong>Ký quỹ Escrow bảo vệ:</strong> Tiền được giữ an toàn tại O.G Shop. Người bán chỉ nhận tiền khi bạn đồng kiểm thành công.</span>
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <span style={{ fontSize: 16 }}>⏱️</span>
              <span><strong>Khóa giữ hàng 1 giờ:</strong> Ngăn chặn người khác mua trùng sản phẩm trong lúc bạn hoàn tất thanh toán.</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
