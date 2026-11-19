import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Badge, Button, Card, Input } from '../../shared/components';
import { UserAddress, profileApi, GhnProvince, GhnDistrict, GhnWard, ChangePhoneModal, ChangeBankModal } from '../../features/profile';
import { useAuth } from '../../features/auth';
import { SellerSettings, sellerSettingsApi } from '../../features/seller/sellerSettingsApi';

export const SellerSettingsPage: React.FC = () => {
  const { user, reloadCurrentUser } = useAuth();

  const [settings, setSettings] = useState<SellerSettings | null>(null);
  const [addresses, setAddresses] = useState<UserAddress[]>([]);
  const [loading, setLoading] = useState(true);
  const [isSavingStore, setIsSavingStore] = useState(false);
  const [isSavingAddresses, setIsSavingAddresses] = useState(false);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Store form state
  const [storeName, setStoreName] = useState('');
  const [pickupAddressId, setPickupAddressId] = useState<number | ''>('');
  const [returnAddressId, setReturnAddressId] = useState<number | ''>('');

  // New warehouse address modal / form state
  const [showAddWarehouseModal, setShowAddWarehouseModal] = useState(false);
  const [warehouseType, setWarehouseType] = useState<'PICKUP' | 'RETURN'>('PICKUP');
  const [recipientName, setRecipientName] = useState('');
  const [recipientPhone, setRecipientPhone] = useState('');
  const [detailAddress, setDetailAddress] = useState('');
  const [provinces, setProvinces] = useState<GhnProvince[]>([]);
  const [districts, setDistricts] = useState<GhnDistrict[]>([]);
  const [wards, setWards] = useState<GhnWard[]>([]);
  const [selectedProvinceId, setSelectedProvinceId] = useState<number | ''>('');
  const [selectedDistrictId, setSelectedDistrictId] = useState<number | ''>('');
  const [selectedWardCode, setSelectedWardCode] = useState<string>('');
  const [isSavingNewAddress, setIsSavingNewAddress] = useState(false);
  const [warehouseModalError, setWarehouseModalError] = useState<string | null>(null);

  // Phone & Bank Change Security Modal States (Using unified modals)
  const [showPhoneModal, setShowPhoneModal] = useState(false);
  const [showBankModal, setShowBankModal] = useState(false);

  // Load initial data
  const loadData = async () => {
    try {
      setLoading(true);
      setErrorMsg(null);
      const [sett, addrs] = await Promise.all([
        sellerSettingsApi.getSettings(),
        profileApi.getAddresses(),
      ]);
      setSettings(sett);
      setAddresses(addrs);
      setStoreName(sett.storeName || '');
      setPickupAddressId(sett.pickupAddressId || (addrs.length > 0 ? addrs[0].id : ''));
      setReturnAddressId(sett.returnAddressId || (addrs.length > 0 ? addrs[0].id : ''));
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Không thể tải thiết lập gian hàng.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  // Load provinces on opening add warehouse modal
  const handleOpenAddWarehouse = async (type: 'PICKUP' | 'RETURN') => {
    setWarehouseType(type);
    setRecipientName(user?.fullName || '');
    setRecipientPhone(settings?.hotlinePhone || user?.phoneNumber || '');
    setDetailAddress('');
    setSelectedProvinceId('');
    setSelectedDistrictId('');
    setSelectedWardCode('');
    setDistricts([]);
    setWards([]);
    setWarehouseModalError(null);
    setShowAddWarehouseModal(true);

    try {
      const p = await profileApi.getProvinces();
      setProvinces(p);
    } catch {
      setWarehouseModalError('Không thể nạp danh sách Tỉnh/Thành từ GHN.');
    }
  };

  const handleProvinceChange = async (e: React.ChangeEvent<HTMLSelectElement>) => {
    const pId = Number(e.target.value);
    setSelectedProvinceId(pId || '');
    setSelectedDistrictId('');
    setSelectedWardCode('');
    setDistricts([]);
    setWards([]);

    if (pId) {
      try {
        const d = await profileApi.getDistricts(pId);
        setDistricts(d);
      } catch {
        setWarehouseModalError('Không thể nạp Quận/Huyện từ GHN.');
      }
    }
  };

  const handleDistrictChange = async (e: React.ChangeEvent<HTMLSelectElement>) => {
    const dId = Number(e.target.value);
    setSelectedDistrictId(dId || '');
    setSelectedWardCode('');
    setWards([]);

    if (dId) {
      try {
        const w = await profileApi.getWards(dId);
        setWards(w);
      } catch {
        setWarehouseModalError('Không thể nạp Phường/Xã từ GHN.');
      }
    }
  };

  const handleSaveWarehouse = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedProvinceId || !selectedDistrictId || !selectedWardCode) {
      setWarehouseModalError('Vui lòng chọn đầy đủ Tỉnh/Thành, Quận/Huyện và Phường/Xã GHN.');
      return;
    }
    const prov = provinces.find((p) => p.provinceId === selectedProvinceId);
    const dist = districts.find((d) => d.districtId === selectedDistrictId);
    const ward = wards.find((w) => w.wardCode === selectedWardCode);

    if (!prov || !dist || !ward) {
      setWarehouseModalError('Dữ liệu hành chính GHN không hợp lệ.');
      return;
    }

    setIsSavingNewAddress(true);
    setWarehouseModalError(null);

    try {
      const created = await profileApi.createAddress({
        recipientName,
        phoneNumber: recipientPhone,
        province: prov.provinceName,
        district: dist.districtName,
        ward: ward.wardName,
        detailAddress,
        ghnProvinceId: prov.provinceId,
        ghnDistrictId: dist.districtId,
        ghnWardCode: ward.wardCode,
      });

      const updatedAddrs = await profileApi.getAddresses();
      setAddresses(updatedAddrs);

      if (warehouseType === 'PICKUP') {
        setPickupAddressId(created.id);
        await sellerSettingsApi.updateSettings({ pickupAddressId: created.id });
      } else {
        setReturnAddressId(created.id);
        await sellerSettingsApi.updateSettings({ returnAddressId: created.id });
      }

      setShowAddWarehouseModal(false);
      setSuccessMsg(`Đã tạo và gán kho vận GHN (${warehouseType === 'PICKUP' ? 'Kho lấy hàng' : 'Kho nhận trả hàng'}) thành công!`);
      setTimeout(() => setSuccessMsg(null), 4000);
    } catch (err: unknown) {
      setWarehouseModalError(err instanceof Error ? err.message : 'Tạo kho vận thất bại.');
    } finally {
      setIsSavingNewAddress(false);
    }
  };

  const handleSaveStoreName = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!storeName.trim()) {
      setErrorMsg('Tên gian hàng không được để trống.');
      return;
    }
    setIsSavingStore(true);
    setErrorMsg(null);
    try {
      const updated = await sellerSettingsApi.updateSettings({ storeName: storeName.trim() });
      setSettings(updated);
      setSuccessMsg('Đã cập nhật tên gian hàng thành công!');
      setTimeout(() => setSuccessMsg(null), 4000);
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Cập nhật tên gian hàng thất bại.');
    } finally {
      setIsSavingStore(false);
    }
  };

  const handleSaveAddresses = async () => {
    if (!pickupAddressId || !returnAddressId) {
      setErrorMsg('Vui lòng chọn đầy đủ Kho lấy hàng và Kho trả hàng.');
      return;
    }
    setIsSavingAddresses(true);
    setErrorMsg(null);
    try {
      const updated = await sellerSettingsApi.updateSettings({
        pickupAddressId: Number(pickupAddressId),
        returnAddressId: Number(returnAddressId),
      });
      setSettings(updated);
      setSuccessMsg('Đã cập nhật địa chỉ kho vận GHN thành công!');
      setTimeout(() => setSuccessMsg(null), 4000);
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Cập nhật địa chỉ kho vận thất bại.');
    } finally {
      setIsSavingAddresses(false);
    }
  };

  if (loading && !settings) {
    return (
      <div style={{ maxWidth: 960, margin: '40px auto', padding: '24px', textAlign: 'center' }}>
        <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '1rem' }}>Đang nạp thiết lập gian hàng...</p>
      </div>
    );
  }

  const isEkycVerified = settings?.verifiedSeller ?? false;
  const hasStoreInfo = settings?.hasStoreInfo ?? (
    !!settings?.storeName &&
    !!settings?.pickupAddressId &&
    !!settings?.returnAddressId &&
    !!settings?.bankAccountNumberMasked
  );

  return (
    <div style={{ maxWidth: 980, margin: '0 auto', padding: '16px', display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Page Header */}
      <div>
        <h1 style={{ fontSize: '1.75rem', fontWeight: 800, margin: '0 0 8px', color: 'var(--og-color-text-primary)' }}>
          Thiết Lập Gian Hàng
        </h1>
        <p style={{ margin: 0, color: 'var(--og-color-text-secondary)', fontSize: '0.92rem' }}>
          Tách biệt hoàn toàn thông tin gian hàng bán hàng: Hotline liên hệ, Địa chỉ kho vận GHN, Tài khoản thụ hưởng và Định danh eKYC.
        </p>
      </div>

      {successMsg && <Alert type="success">{successMsg}</Alert>}
      {errorMsg && <Alert type="danger">{errorMsg}</Alert>}

      {/* Luồng 1: Người dùng chưa hoàn tất eKYC */}
      {!isEkycVerified && (
        <div
          style={{
            padding: '20px 24px',
            borderRadius: '16px',
            border: '1.5px solid var(--og-color-warning, #f59e0b)',
            backgroundColor: 'rgba(245, 158, 11, 0.08)',
            display: 'flex',
            flexDirection: 'column',
            gap: '12px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span style={{ fontSize: '1.4rem' }}>🛡️</span>
            <h3 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 700, color: 'var(--og-color-text-primary)' }}>
              Yêu cầu định danh eKYC trước khi mở gian hàng
            </h3>
          </div>
          <p style={{ margin: 0, fontSize: '0.9rem', color: 'var(--og-color-text-secondary)', lineHeight: 1.5 }}>
            Để đảm bảo an toàn giao dịch và phòng tránh gian lận trên nền tảng O.G Shop, người bán bắt buộc phải hoàn tất xác thực danh tính căn cước công dân (CCCD) và quét khuôn mặt trước khi đăng tin bán và nhận thanh toán.
          </p>
          <div style={{ display: 'flex', gap: '12px', marginTop: '4px' }}>
            <Link to="/ekyc/verification">
              <Button variant="gold">
                Bắt đầu xác thực eKYC ngay (Bước 1)
              </Button>
            </Link>
          </div>
        </div>
      )}

      {/* Luồng 2: Đã eKYC nhưng chưa hoàn tất thông tin gian hàng */}
      {isEkycVerified && !hasStoreInfo && (
        <div
          style={{
            padding: '20px 24px',
            borderRadius: '16px',
            border: '1.5px solid var(--og-color-primary, #3b82f6)',
            backgroundColor: 'rgba(59, 130, 246, 0.08)',
            display: 'flex',
            flexDirection: 'column',
            gap: '12px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span style={{ fontSize: '1.4rem' }}>🏪</span>
            <h3 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 700, color: 'var(--og-color-text-primary)' }}>
              Bước cuối cùng: Đăng ký thông tin gian hàng Người bán
            </h3>
          </div>
          <p style={{ margin: 0, fontSize: '0.9rem', color: 'var(--og-color-text-secondary)', lineHeight: 1.5 }}>
            Chúc mừng bạn đã hoàn tất xác thực danh tính eKYC! Vui lòng hoàn tất <strong>Tên gian hàng</strong>, chọn <strong>Kho vận GHN</strong> (lấy hàng và trả hàng) cùng <strong>Tài khoản ngân hàng nhận tiền quyết toán</strong> bên dưới để hệ thống mở khóa toàn bộ các tab của Kênh Người Bán.
          </p>
        </div>
      )}

      {/* Luồng 3: Đã hoàn tất eKYC và đã thiết lập gian hàng đầy đủ */}
      {isEkycVerified && hasStoreInfo && (
        <div
          style={{
            padding: '14px 20px',
            borderRadius: '14px',
            border: '1.5px solid rgba(16, 185, 129, 0.3)',
            backgroundColor: 'rgba(16, 185, 129, 0.08)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: '12px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span style={{ fontSize: '1.3rem', color: '#10b981' }}>✓</span>
            <div>
              <div style={{ fontWeight: 700, fontSize: '0.96rem', color: '#065f46' }}>
                Gian hàng đã kích hoạt & sẵn sàng bán hàng
              </div>
              <span style={{ fontSize: '0.82rem', color: 'var(--og-color-text-secondary)' }}>
                Tất cả các tính năng đăng tin, quản lý đơn và giao hàng GHN đã được mở khóa.
              </span>
            </div>
          </div>
          <Link to="/seller/products/new">
            <Button variant="primary" size="sm">
              + Đăng tin bán ngay
            </Button>
          </Link>
        </div>
      )}

      {/* KHU VỰC 1: Thông tin liên hệ gian hàng */}
      <Card title="1. Thông tin liên hệ & Thương hiệu gian hàng">
        <form onSubmit={handleSaveStoreName} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div>
            <label style={{ display: 'block', fontSize: '0.88rem', fontWeight: 600, marginBottom: '6px' }}>
              Tên gian hàng hiển thị trên sàn <span style={{ color: 'var(--og-color-danger)' }}>*</span>
            </label>
            <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
              <Input
                value={storeName}
                onChange={(e) => setStoreName(e.target.value)}
                placeholder="VD: Cửa hàng Máy ảnh & Đồ cổ Vintage"
                required
              />
              <Button variant="primary" type="submit" disabled={isSavingStore}>
                {isSavingStore ? 'Đang lưu...' : 'Lưu tên'}
              </Button>
            </div>
            <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', marginTop: '4px', display: 'block' }}>
              Tên gian hàng giúp người mua nhận diện thương hiệu và tìm kiếm tin đăng của bạn.
            </span>
          </div>

          <div style={{ borderTop: '1px solid var(--og-color-border-subtle)', paddingTop: '16px' }}>
            <label style={{ display: 'block', fontSize: '0.88rem', fontWeight: 600, marginBottom: '6px' }}>
              Số điện thoại Hotline chính thức của gian hàng
            </label>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <span style={{ fontSize: '1.1rem', fontWeight: 700, color: 'var(--og-color-text-primary)' }}>
                  {settings?.hotlinePhone || user?.phoneNumber || 'Chưa thiết lập'}
                </span>
                {(settings?.hotlinePhone || user?.phoneNumber) && (
                  <Badge variant="seller">✓ Số chính chủ đã xác thực</Badge>
                )}
              </div>
              <Button variant="outline" size="sm" type="button" onClick={() => setShowPhoneModal(true)}>
                Đổi số điện thoại hotline
              </Button>
            </div>
            <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', marginTop: '6px', display: 'block' }}>
              🔒 SĐT hotline đồng bộ với số điện thoại chính của tài khoản để đảm bảo tính xác thực khi bưu tá GHN và khách hàng liên hệ.
            </span>
          </div>
        </form>
      </Card>

      {/* KHU VỰC 2: Địa chỉ kho vận GHN */}
      <Card title="2. Địa chỉ kho vận Giao Hàng Nhanh (GHN)">
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <Alert type="info">
            GHN yêu cầu định danh chính xác mã Quận/Huyện và Phường/Xã cho cả <strong>Kho gửi hàng đi</strong> (bưu tá đến lấy) và <strong>Địa chỉ nhận hàng trả về</strong> (khi giao không thành công hoặc người mua hoàn trả).
          </Alert>

          {/* Kho lấy hàng */}
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
              <label style={{ fontSize: '0.9rem', fontWeight: 600 }}>
                Kho lấy hàng gửi đi (Bưu tá GHN đến lấy hàng) <span style={{ color: 'var(--og-color-danger)' }}>*</span>
              </label>
              <Button variant="outline" size="sm" type="button" onClick={() => handleOpenAddWarehouse('PICKUP')}>
                + Thêm kho gửi hàng mới
              </Button>
            </div>
            {addresses.length === 0 ? (
              <p style={{ color: 'var(--og-color-danger)', fontSize: '0.88rem' }}>
                Chưa có địa chỉ nào trong Sổ địa chỉ. Vui lòng bấm "Thêm kho gửi hàng mới".
              </p>
            ) : (
              <select
                className="og-select"
                value={pickupAddressId}
                onChange={(e) => setPickupAddressId(Number(e.target.value))}
                style={{ width: '100%', padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--og-color-border)' }}
              >
                {addresses.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.recipientName} ({a.phoneNumber}) - {a.detailAddress}, {a.ward}, {a.district}, {a.province} {a.ghnDistrictId ? '✓ [GHN OK]' : '⚠️ [Thiếu mã GHN]'}
                  </option>
                ))}
              </select>
            )}
          </div>

          {/* Kho trả hàng */}
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
              <label style={{ fontSize: '0.9rem', fontWeight: 600 }}>
                Địa chỉ nhận hàng hoàn trả (Khi phát sinh khiếu nại / đơn hoàn) <span style={{ color: 'var(--og-color-danger)' }}>*</span>
              </label>
              <Button variant="outline" size="sm" type="button" onClick={() => handleOpenAddWarehouse('RETURN')}>
                + Thêm địa chỉ trả hàng mới
              </Button>
            </div>
            {addresses.length === 0 ? (
              <p style={{ color: 'var(--og-color-danger)', fontSize: '0.88rem' }}>
                Chưa có địa chỉ nào trong Sổ địa chỉ.
              </p>
            ) : (
              <select
                className="og-select"
                value={returnAddressId}
                onChange={(e) => setReturnAddressId(Number(e.target.value))}
                style={{ width: '100%', padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--og-color-border)' }}
              >
                {addresses.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.recipientName} ({a.phoneNumber}) - {a.detailAddress}, {a.ward}, {a.district}, {a.province} {a.ghnDistrictId ? '✓ [GHN OK]' : '⚠️ [Thiếu mã GHN]'}
                  </option>
                ))}
              </select>
            )}
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '8px' }}>
            <Button variant="primary" type="button" onClick={handleSaveAddresses} disabled={isSavingAddresses}>
              {isSavingAddresses ? 'Đang lưu...' : 'Lưu cài đặt kho vận GHN'}
            </Button>
          </div>
        </div>
      </Card>

      {/* KHU VỰC 3: Tài khoản ngân hàng nhận tiền bán hàng */}
      <Card title="3. Tài khoản ngân hàng nhận tiền quyết toán (Payout Account)">
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
            <div>
              <div style={{ fontSize: '0.85rem', color: 'var(--og-color-text-secondary)', marginBottom: '4px' }}>
                Ngân hàng thụ hưởng & Số tài khoản
              </div>
              <div style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--og-color-text-primary)' }}>
                {settings?.bankName ? `${settings.bankName} — ${settings.bankAccountNumberMasked}` : 'Chưa liên kết tài khoản'}
              </div>
              {settings?.bankAccountHolder && (
                <div style={{ fontSize: '0.88rem', color: 'var(--og-color-text-secondary)', marginTop: '2px' }}>
                  Chủ tài khoản: <strong>{settings.bankAccountHolder}</strong>
                </div>
              )}
            </div>
            <Button variant="outline" size="sm" type="button" onClick={() => setShowBankModal(true)}>
              {settings?.bankAccountNumberMasked ? 'Thay đổi tài khoản ngân hàng' : 'Liên kết tài khoản ngân hàng'}
            </Button>
          </div>

          <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', display: 'block' }}>
            🛡️ Tiền bán hàng sau khi khách nghiệm thu hoặc hết thời gian khiếu nại (Escrow giải phóng) sẽ được tự động quyết toán về tài khoản này.
          </span>
        </div>
      </Card>

      {/* KHU VỰC 4: Trạng thái định danh eKYC của gian hàng */}
      <Card title="4. Trạng thái định danh eKYC của gian hàng">
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '6px' }}>
                <span style={{ fontSize: '1.05rem', fontWeight: 700 }}>Trạng thái định danh:</span>
                {settings?.verifiedSeller ? (
                  <Badge variant="seller">✓ Đã xác minh eKYC CCCD (Hợp lệ)</Badge>
                ) : settings?.ekycState === 'PENDING' ? (
                  <Badge variant="warning">⏳ Hồ sơ đang chờ KTV duyệt</Badge>
                ) : settings?.ekycState === 'REJECTED' ? (
                  <Badge variant="danger">✗ Hồ sơ eKYC bị từ chối</Badge>
                ) : (
                  <Badge variant="buyer">Chưa hoàn tất eKYC</Badge>
                )}
              </div>
              <p style={{ margin: 0, fontSize: '0.88rem', color: 'var(--og-color-text-secondary)', lineHeight: 1.5 }}>
                {settings?.verifiedSeller
                  ? 'Gian hàng đã đủ điều kiện uy tín cao. Mọi quyền lợi đăng bán đồ cũ và nhận thanh toán đã sẵn sàng.'
                  : 'Gian hàng bắt buộc phải hoàn tất định danh eKYC để được kích hoạt quyền đăng tin bán và nhận thanh toán.'}
              </p>
            </div>

            <Link to={settings?.verifiedSeller ? '/seller/verification' : '/ekyc/verification'}>
              <Button variant={settings?.verifiedSeller ? 'outline' : 'gold'}>
                {settings?.verifiedSeller ? 'Xem chi tiết hồ sơ eKYC' : 'Xác thực danh tính ngay (eKYC)'}
              </Button>
            </Link>
          </div>
        </div>
      </Card>

      {/* MODAL: Thêm kho mới với chuẩn GHN */}
      {showAddWarehouseModal && (
        <div className="og-modal-overlay" style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '16px' }}>
          <div style={{ background: 'var(--og-color-surface)', borderRadius: '12px', padding: '24px', maxWidth: '580px', width: '100%', maxHeight: '90vh', overflowY: 'auto' }}>
            <h3 style={{ margin: '0 0 16px', fontSize: '1.25rem', color: 'var(--og-color-text-primary)' }}>
              Thêm kho vận GHN ({warehouseType === 'PICKUP' ? 'Kho lấy hàng' : 'Kho nhận hàng hoàn'})
            </h3>
            {warehouseModalError && <Alert type="danger">{warehouseModalError}</Alert>}

            <form onSubmit={handleSaveWarehouse} style={{ display: 'flex', flexDirection: 'column', gap: '14px', marginTop: '12px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>Tên người liên hệ kho</label>
                <Input value={recipientName} onChange={(e) => setRecipientName(e.target.value)} required />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>Số điện thoại người phụ trách kho</label>
                <Input value={recipientPhone} onChange={(e) => setRecipientPhone(e.target.value)} required />
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>Tỉnh/Thành phố (GHN)</label>
                  <select className="og-select" value={selectedProvinceId} onChange={handleProvinceChange} required style={{ width: '100%', padding: '8px 10px', borderRadius: '6px', border: '1px solid var(--og-color-border)' }}>
                    <option value="">-- Chọn Tỉnh/Thành --</option>
                    {provinces.map((p) => (
                      <option key={p.provinceId} value={p.provinceId}>{p.provinceName}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>Quận/Huyện (GHN)</label>
                  <select className="og-select" value={selectedDistrictId} onChange={handleDistrictChange} required disabled={!selectedProvinceId} style={{ width: '100%', padding: '8px 10px', borderRadius: '6px', border: '1px solid var(--og-color-border)' }}>
                    <option value="">-- Chọn Quận/Huyện --</option>
                    {districts.map((d) => (
                      <option key={d.districtId} value={d.districtId}>{d.districtName}</option>
                    ))}
                  </select>
                </div>
              </div>
              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>Phường/Xã (GHN)</label>
                <select className="og-select" value={selectedWardCode} onChange={(e) => setSelectedWardCode(e.target.value)} required disabled={!selectedDistrictId} style={{ width: '100%', padding: '8px 10px', borderRadius: '6px', border: '1px solid var(--og-color-border)' }}>
                  <option value="">-- Chọn Phường/Xã --</option>
                  {wards.map((w) => (
                    <option key={w.wardCode} value={w.wardCode}>{w.wardName}</option>
                  ))}
                </select>
              </div>
              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>Địa chỉ chi tiết (Số nhà, tên đường, tên kho)</label>
                <Input value={detailAddress} onChange={(e) => setDetailAddress(e.target.value)} placeholder="VD: Kho A1, 123 Đường Nguyễn Huệ" required />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px' }}>
                <Button variant="outline" type="button" onClick={() => setShowAddWarehouseModal(false)} disabled={isSavingNewAddress}>
                  Hủy bỏ
                </Button>
                <Button variant="primary" type="submit" disabled={isSavingNewAddress}>
                  {isSavingNewAddress ? 'Đang lưu...' : 'Lưu địa chỉ kho'}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL: Đổi số điện thoại (Tích hợp ChangePhoneModal chuẩn O.G Shop) */}
      <ChangePhoneModal
        isOpen={showPhoneModal}
        onClose={() => setShowPhoneModal(false)}
        onSuccess={async () => {
          setSuccessMsg('Đã đổi số điện thoại hotline thành công!');
          await reloadCurrentUser();
          await loadData();
          setTimeout(() => setSuccessMsg(null), 4000);
        }}
        currentPhoneNumber={settings?.hotlinePhone || user?.phoneNumber}
        currentFullName={user?.fullName}
        accountEmail={user?.email}
        isVerifiedSeller={settings?.verifiedSeller ?? false}
      />

      {/* MODAL: Đổi tài khoản ngân hàng (Tích hợp ChangeBankModal chuẩn O.G Shop) */}
      <ChangeBankModal
        isOpen={showBankModal}
        onClose={() => setShowBankModal(false)}
        onSuccess={async () => {
          setSuccessMsg('Cập nhật tài khoản ngân hàng thành công!');
          await loadData();
          setTimeout(() => setSuccessMsg(null), 4000);
        }}
        currentBankName={settings?.bankName}
        currentBankAccountNumberMasked={settings?.bankAccountNumberMasked}
        currentBankAccountHolder={settings?.bankAccountHolder}
        accountEmail={user?.email}
        isVerifiedSeller={settings?.verifiedSeller ?? false}
      />
    </div>
  );
};
