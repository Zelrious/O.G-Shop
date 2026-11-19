import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth';
import { ekycApi, CccdData, EkycStatus, verificationApi, FaceScanner, SecureAssetImage } from '../features/verification';
import { QuickFaceAuthModal } from '../features/verification/components/QuickFaceAuthModal';
import { useManagementStore } from '../features/management/useManagementStore';
import { Card, Button, Alert, Badge, Input } from '../shared/components';
import { sellerSettingsApi, SellerSettings } from '../features/seller/sellerSettingsApi';
import { profileApi, UserAddress, UserProfile } from '../features/profile';

export const SellerVerificationPage: React.FC = () => {
  const { user, reloadCurrentUser } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const isSeller = Boolean(user?.roles?.includes('SELLER'));

  // State and refs to guard against infinite re-render loops
  const hasReloadedOnVerifiedRef = useRef<boolean>(false);
  const statusLoadedRef = useRef<boolean>(false);

  // Mode: Full eKYC workflow vs MVP Bypass
  const [showFullEkyc, setShowFullEkyc] = useState<boolean>(() => Boolean(user?.sellerApprovalRequired));
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [showReverifyQuickAuth, setShowReverifyQuickAuth] = useState<boolean>(false);
  const [reverifyQuickAuthResultId, setReverifyQuickAuthResultId] = useState<string | null>(null);

  // Full eKYC state
  const [status, setStatus] = useState<EkycStatus | null>(null);
  const [currentStep, setCurrentStep] = useState<1 | 2 | 3 | 4 | 5>(1);

  // Step 2: Upload CCCD 2 mặt & Chân dung tham chiếu
  const [frontFile, setFrontFile] = useState<File | null>(null);
  const [backFile, setBackFile] = useState<File | null>(null);
  const [portraitFile, setPortraitFile] = useState<File | null>(null);
  const [frontPreview, setFrontPreview] = useState<string | null>(null);
  const [backPreview, setBackPreview] = useState<string | null>(null);
  const [portraitPreview, setPortraitPreview] = useState<string | null>(null);
  const [frontAssetId, setFrontAssetId] = useState<number | null>(null);
  const [backAssetId, setBackAssetId] = useState<number | null>(null);
  const [portraitAssetId, setPortraitAssetId] = useState<number | null>(null);

  // Step 3: Form data
  const [formData, setFormData] = useState<CccdData>({
    cccdNumber: '',
    fullName: '',
    dob: '',
    gender: 'Nam',
    hometown: '',
    address: '',
  });

  // Step 4: Face match webcam
  const [faceStage, setFaceStage] = useState<'CAPTURE' | 'PREVIEW'>('CAPTURE');
  const [capturedSelfie, setCapturedSelfie] = useState<string | null>(null);
  const [capturedSamples, setCapturedSamples] = useState<string[]>([]);
  const [isManualCapture, setIsManualCapture] = useState<boolean>(false);
  const [matchDetails, setMatchDetails] = useState<{
    distance: number;
    confidence: number;
    isMatch?: boolean;
    message?: string;
    thresholdUsed?: number;
  } | null>(null);

  // Complaint modal state
  const { submitComplaint } = useManagementStore();
  const [showComplaintModal, setShowComplaintModal] = useState<boolean>(false);
  const [complaintTitle, setComplaintTitle] = useState<string>('');
  const [complaintContent, setComplaintContent] = useState<string>('');
  const [complaintSuccess, setComplaintSuccess] = useState<string | null>(null);

  // Seller Store Registration State (for eKYC-verified users)
  const [sellerSettings, setSellerSettings] = useState<SellerSettings | null>(null);
  const [userProfile, setUserProfile] = useState<UserProfile | null>(null);
  const [addresses, setAddresses] = useState<UserAddress[]>([]);
  const [storeName, setStoreName] = useState<string>('');
  const [contactPhone, setContactPhone] = useState<string>('');
  const [pickupAddressId, setPickupAddressId] = useState<number | ''>('');
  const [returnAddressId, setReturnAddressId] = useState<number | ''>('');
  const [sameAddress, setSameAddress] = useState<boolean>(true);
  const [termsAccepted, setTermsAccepted] = useState<boolean>(false);
  const [isSubmittingSeller, setIsSubmittingSeller] = useState<boolean>(false);
  const [sellerFormError, setSellerFormError] = useState<string | null>(null);
  const [sellerFormSuccess, setSellerFormSuccess] = useState<string | null>(null);

  // Bank Info state if not linked yet
  const [newBankName, setNewBankName] = useState<string>('');
  const [newBankAccountNumber, setNewBankAccountNumber] = useState<string>('');
  const [newBankAccountHolder, setNewBankAccountHolder] = useState<string>('');

  // Quick Address Creation state
  const [showQuickAddAddress, setShowQuickAddAddress] = useState<boolean>(false);
  const [newRecipientName, setNewRecipientName] = useState<string>('');
  const [newRecipientPhone, setNewRecipientPhone] = useState<string>('');
  const [newProvince, setNewProvince] = useState<string>('');
  const [newDistrict, setNewDistrict] = useState<string>('');
  const [newWard, setNewWard] = useState<string>('');
  const [newDetailAddress, setNewDetailAddress] = useState<string>('');
  const [isCreatingAddress, setIsCreatingAddress] = useState<boolean>(false);

  const loadSellerRegistrationData = useCallback(async () => {
    try {
      const [sett, addrs, prof] = await Promise.all([
        sellerSettingsApi.getSettings().catch(() => null),
        profileApi.getAddresses().catch(() => []),
        profileApi.getProfile().catch(() => null),
      ]);
      if (prof) {
        setUserProfile(prof);
      }
      if (sett) {
        setSellerSettings(sett);
        setStoreName(sett.storeName || (user?.fullName ? `${user.fullName}'s Shop` : ''));
        setContactPhone(sett.hotlinePhone || user?.phoneNumber || '');
        if (sett.pickupAddressId) setPickupAddressId(sett.pickupAddressId);
        else if (addrs.length > 0) setPickupAddressId(addrs[0].id);
        if (sett.returnAddressId) setReturnAddressId(sett.returnAddressId);
        else if (addrs.length > 0) setReturnAddressId(addrs[0].id);
      } else {
        setStoreName(user?.fullName ? `${user.fullName}'s Shop` : '');
        setContactPhone(user?.phoneNumber || '');
        if (addrs.length > 0) {
          setPickupAddressId(addrs[0].id);
          setReturnAddressId(addrs[0].id);
        }
      }
      setAddresses(addrs);
    } catch (err) {
      console.warn('Cannot load seller settings or addresses:', err);
    }
  }, [user]);

  const loadStatus = useCallback(async () => {
    if (!statusLoadedRef.current) {
      setIsLoading(true);
    }
    setError(null);
    try {
      const data = await ekycApi.getStatus();
      statusLoadedRef.current = true;
      setStatus(data);
      if (data.hasProfile || data.hasVerifiedProfile) {
        setShowFullEkyc(true);
      }
      if (!isSeller && (data.state === 'PENDING' || data.state === 'VERIFIED')) {
        setCurrentStep(5);
        if (data.state === 'VERIFIED' && !hasReloadedOnVerifiedRef.current) {
          hasReloadedOnVerifiedRef.current = true;
          void reloadCurrentUser();
        }
      } else if (data.submittedData) {
        setFormData(data.submittedData);
        if (data.frontAssetId) setFrontAssetId(data.frontAssetId);
        if (data.backAssetId) setBackAssetId(data.backAssetId);
      }
    } catch (err: unknown) {
      // In mock/test environments, ignore backend connect error unless explicitly triggered
      console.warn('Cannot load eKYC status:', err);
    } finally {
      setIsLoading(false);
    }
  }, [isSeller, reloadCurrentUser]);

  const userId = user?.userId;

  useEffect(() => {
    if (!userId) {
      navigate('/login');
      return;
    }
    const sp = new URLSearchParams(location.search);
    if (sp.get('step') === '2') {
      setCurrentStep(2);
    }
    if (sp.get('reverify') === 'true' && sp.get('step') !== '2') {
      setShowReverifyQuickAuth(true);
    }
    if (showFullEkyc || user?.sellerApprovalRequired || user?.verifiedSeller) {
      void loadStatus();
    }
    void loadSellerRegistrationData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId, showFullEkyc, location.search, navigate]);

  const handleOpenComplaint = () => {
    setComplaintTitle(`Khiếu nại kết quả xét duyệt eKYC (Hồ sơ #${status?.profileId || ''})`);
    setComplaintContent('');
    setComplaintSuccess(null);
    setShowComplaintModal(true);
  };

  const handleSubmitComplaint = (e: React.FormEvent) => {
    e.preventDefault();
    if (!complaintContent.trim() || !user) return;
    const complaint = submitComplaint({
      title: complaintTitle || 'Khiếu nại kết quả xét duyệt eKYC',
      description: complaintContent.trim(),
      complainantName: user.fullName || user.email,
      complainantEmail: user.email,
      complainantRole: 'SELLER',
      type: 'SYSTEM_ERROR',
      urgency: 'HIGH',
    });
    setShowComplaintModal(false);
    setComplaintSuccess(`Khiếu nại #${complaint.id} của bạn đã được gửi thành công đến Ban Quản Trị.`);
  };

  const handleCloseReverifyQuickAuth = useCallback(() => {
    setShowReverifyQuickAuth(false);
    if (location.search.includes('reverify')) {
      navigate(isSeller ? '/seller/verification' : '/profile', { replace: true });
    }
  }, [isSeller, location.search, navigate]);

  const handleNeedEkycReverify = useCallback(() => {
    setShowReverifyQuickAuth(false);
    setCurrentStep(2);
  }, []);

  const handleReverifyQuickAuthSuccess = useCallback(async (quickAuthResultId: string) => {
    setReverifyQuickAuthResultId(quickAuthResultId);
    setShowReverifyQuickAuth(false);
    setIsLoading(true);
    setError(null);
    try {
      const res = await ekycApi.startOrResume(quickAuthResultId);
      setStatus((prev) => prev ? {
        ...prev,
        state: 'IN_PROGRESS',
        profileId: res.profileId,
        revisionNo: res.revisionNo,
        rejectionReason: undefined,
      } : {
        hasProfile: true,
        canSubmit: true,
        isSeller: false,
        nonmatchCount: 0,
        state: 'IN_PROGRESS',
        profileId: res.profileId,
        revisionNo: res.revisionNo,
      });
      setFrontFile(null);
      setBackFile(null);
      setPortraitFile(null);
      setFrontPreview(null);
      setBackPreview(null);
      setPortraitPreview(null);
      setFrontAssetId(null);
      setBackAssetId(null);
      setPortraitAssetId(null);
      setFormData({
        cccdNumber: '',
        fullName: '',
        dob: '',
        gender: 'Nam',
        hometown: '',
        address: '',
      });
      setCapturedSelfie(null);
      setCapturedSamples([]);
      setMatchDetails(null);
      setFaceStage('CAPTURE');
      setCurrentStep(2);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Không thể khởi tạo phiên xác thực lại.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  const handleStartReverification = async () => {
    if (status?.state === 'VERIFIED' || user?.verifiedSeller) {
      setShowReverifyQuickAuth(true);
      return;
    }
    setIsLoading(true);
    setError(null);
    setReverifyQuickAuthResultId(null); // Clear previous result
    try {
      const res = await ekycApi.startOrResume();
      if (res.state === 'QUICK_AUTH_REQUIRED') {
        setShowReverifyQuickAuth(true);
        return;
      }
      setStatus((prev) => prev ? {
        ...prev,
        state: 'IN_PROGRESS',
        profileId: res.profileId,
        revisionNo: res.revisionNo,
        rejectionReason: undefined,
      } : null);
      setFrontFile(null);
      setBackFile(null);
      setPortraitFile(null);
      setFrontPreview(null);
      setBackPreview(null);
      setPortraitPreview(null);
      setFrontAssetId(null);
      setBackAssetId(null);
      setPortraitAssetId(null);
      setFormData({
        cccdNumber: '',
        fullName: '',
        dob: '',
        gender: 'Nam',
        hometown: '',
        address: '',
      });
      setCapturedSelfie(null);
      setCapturedSamples([]);
      setMatchDetails(null);
      setFaceStage('CAPTURE');
      setCurrentStep(2);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Không thể khởi tạo phiên xác thực lại.');
    } finally {
      setIsLoading(false);
    }
  };

  if (!user) {
    navigate('/login');
    return null;
  }

  // MVP Activation Handler
  const handleActivate = async () => {
    if (isLoading || isSeller) return;

    setIsLoading(true);
    setError(null);
    setSuccessMessage(null);

    try {
      const result = await verificationApi.activateMvpSeller();
      await reloadCurrentUser();
      setSuccessMessage(
        `Kích hoạt quyền Người bán thành công! (Mã hồ sơ: #${result.verificationId}, Phương thức: ${result.verificationMethod})`
      );
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Kích hoạt quyền Người bán thất bại.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleCreateQuickAddress = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newRecipientName.trim() || !newRecipientPhone.trim() || !newProvince.trim() || !newDistrict.trim() || !newWard.trim() || !newDetailAddress.trim()) {
      setSellerFormError('Vui lòng điền đầy đủ các thông tin địa chỉ kho lấy hàng.');
      return;
    }
    setIsCreatingAddress(true);
    setSellerFormError(null);
    try {
      const created = await profileApi.createAddress({
        recipientName: newRecipientName.trim(),
        phoneNumber: newRecipientPhone.trim(),
        province: newProvince.trim(),
        district: newDistrict.trim(),
        ward: newWard.trim(),
        detailAddress: newDetailAddress.trim(),
        isDefault: true,
      });
      setAddresses((prev) => [created, ...prev]);
      setPickupAddressId(created.id);
      if (sameAddress) setReturnAddressId(created.id);
      setShowQuickAddAddress(false);
      setNewRecipientName('');
      setNewRecipientPhone('');
      setNewProvince('');
      setNewDistrict('');
      setNewWard('');
      setNewDetailAddress('');
    } catch (err: unknown) {
      setSellerFormError(err instanceof Error ? err.message : 'Tạo địa chỉ kho thất bại.');
    } finally {
      setIsCreatingAddress(false);
    }
  };

  const handleSubmitSellerRegistration = async (e: React.FormEvent) => {
    e.preventDefault();
    setSellerFormError(null);
    if (!storeName.trim() || storeName.trim().length < 3) {
      setSellerFormError('Tên gian hàng tối thiểu 3 ký tự.');
      return;
    }
    const phoneRegex = /^(0|\+84)[35789][0-9]{8}$/;
    if (!contactPhone.trim() || !phoneRegex.test(contactPhone.trim())) {
      setSellerFormError('Số điện thoại hotline không đúng định dạng di động Việt Nam (gồm 10 số, ví dụ 0901234567).');
      return;
    }
    if (!pickupAddressId) {
      setSellerFormError('Vui lòng chọn hoặc thêm địa chỉ kho lấy hàng GHN.');
      return;
    }
    if (!termsAccepted) {
      setSellerFormError('Vui lòng xác nhận đồng ý với Quy chế hoạt động và Chính sách ký quỹ của O.G Shop.');
      return;
    }

    setIsSubmittingSeller(true);
    try {
      // 1. If bank info was provided for the first time, save bank account
      const hasBank = Boolean(sellerSettings?.bankAccountNumberMasked || userProfile?.bankAccountNumberMasked || userProfile?.bankName);
      if (!hasBank && newBankName.trim() && newBankAccountNumber.trim() && newBankAccountHolder.trim()) {
        await profileApi.updateBankAccount({
          bankName: newBankName.trim(),
          bankAccountNumber: newBankAccountNumber.trim(),
          bankAccountHolder: newBankAccountHolder.trim().toUpperCase(),
        }).catch(() => null);
      }

      // 2. Save seller settings
      const finalReturnId = sameAddress ? pickupAddressId : (returnAddressId || pickupAddressId);
      await sellerSettingsApi.updateSettings({
        storeName: storeName.trim(),
        pickupAddressId: Number(pickupAddressId),
        returnAddressId: Number(finalReturnId),
      });

      // 3. Activate SELLER role on backend
      await verificationApi.activateMvpSeller();

      // 4. Reload user session
      await reloadCurrentUser();

      setSellerFormSuccess('Đăng ký gian hàng Người bán thành công!');
      setSuccessMessage('🎉 Chúc mừng! Quyền Người bán của bạn đã được kích hoạt thành công. Đang chuyển vào Kênh Người Bán...');
      setTimeout(() => {
        navigate('/seller/products');
      }, 1500);
    } catch (err: unknown) {
      setSellerFormError(err instanceof Error ? err.message : 'Kích hoạt quyền Người bán thất bại.');
    } finally {
      setIsSubmittingSeller(false);
    }
  };

  // eKYC Handlers
  const handleStartEkyc = async () => {
    if (status?.state === 'VERIFIED' || user?.verifiedSeller) {
      setShowReverifyQuickAuth(true);
      return;
    }
    setIsLoading(true);
    setError(null);
    try {
      const res = await ekycApi.startOrResume();
      if (res.state === 'QUICK_AUTH_REQUIRED') {
        setShowReverifyQuickAuth(true);
        return;
      }
      setCurrentStep(2);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Không thể bắt đầu phiên eKYC.');
    } finally {
      setIsLoading(false);
    }
  };

  const invalidateOcrResult = () => {
    setFrontAssetId(null);
    setBackAssetId(null);
    setPortraitAssetId(null);
    setFormData({
      cccdNumber: '',
      fullName: '',
      dob: '',
      gender: 'Nam',
      hometown: '',
      address: '',
    });
    setCapturedSelfie(null);
    setCapturedSamples([]);
    setMatchDetails(null);
    setFaceStage('CAPTURE');
    setCurrentStep(2);
    setSuccessMessage(null);
  };

  const handleFrontFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      invalidateOcrResult();
      setFrontFile(file);
      try {
        setFrontPreview(URL.createObjectURL(file));
      } catch {
        setFrontPreview('blob:front-preview');
      }
      setError(null);
    }
  };

  const handleBackFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      invalidateOcrResult();
      setBackFile(file);
      try {
        setBackPreview(URL.createObjectURL(file));
      } catch {
        setBackPreview('blob:back-preview');
      }
      setError(null);
    }
  };

  const handlePortraitFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      invalidateOcrResult();
      setPortraitFile(file);
      try {
        setPortraitPreview(URL.createObjectURL(file));
      } catch {
        setPortraitPreview('blob:portrait-preview');
      }
      setError(null);
    }
  };

  const handleScanOcr = async () => {
    if (!frontFile || !backFile || !portraitFile) {
      setError('Vui lòng tải lên đầy đủ cả 3 ảnh bắt buộc: Mặt trước CCCD, Mặt sau CCCD và Ảnh thẻ chân dung 3x4 / 4x6.');
      return;
    }

    invalidateOcrResult();
    setIsLoading(true);
    setError(null);

    try {
      const res = await ekycApi.uploadCccd(frontFile, backFile, portraitFile);
      setFrontAssetId(res.frontAssetId);
      setBackAssetId(res.backAssetId ?? null);
      setPortraitAssetId(res.portraitAssetId ?? null);
      setFormData(res.ocrData);
      setCurrentStep(3);
    } catch (err: unknown) {
      invalidateOcrResult();
      setError(err instanceof Error ? err.message : 'Bóc tách thông tin thẻ CCCD thất bại. Vui lòng thử lại với ảnh rõ nét hơn.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleProceedToStep4 = () => {
    setCurrentStep(4);
    setFaceStage('CAPTURE');
    setCapturedSelfie(null);
    setCapturedSamples([]);
    setIsManualCapture(false);
    setMatchDetails(null);
    setError(null);
    setSuccessMessage(null);
  };

  const handleScanSuccess = (
    validPhoto: string,
    details: { distance: number; confidence: number; isMatch: boolean; message?: string; thresholdUsed?: number; isManualCapture?: boolean }
  ) => {
    setCapturedSelfie(validPhoto);
    setIsManualCapture(Boolean(details.isManualCapture));
    setCapturedSamples((prev) => (prev.includes(validPhoto) ? prev : [...prev, validPhoto].slice(-5)));
    setMatchDetails({
      distance: details.distance,
      confidence: details.confidence,
      isMatch: true,
      message: details.message || (details.isManualCapture
        ? 'Đã chụp ảnh khuôn mặt thành công để gửi KTV xét duyệt thủ công.'
        : 'Xác thực khuôn mặt thành công!'),
      thresholdUsed: details.thresholdUsed || 0.50,
    });
    setFaceStage('PREVIEW');
    setSuccessMessage(
      details.isManualCapture
        ? 'Ảnh khuôn mặt đã được chụp thành công. Bạn hãy kiểm tra lại ảnh và gửi hồ sơ để Kỹ thuật viên (KTV) xét duyệt thủ công.'
        : 'Khuôn mặt đã được quét và chụp tự động thành công! Bạn có thể kiểm tra ảnh và gửi hồ sơ cho KTV duyệt.'
    );
  };

  const handleRetakeFace = () => {
    setCapturedSelfie(null);
    setCapturedSamples([]);
    setIsManualCapture(false);
    setMatchDetails(null);
    setError(null);
    setSuccessMessage(null);
    setFaceStage('CAPTURE');
  };

  const handleSubmitProfile = async () => {
    if (!frontAssetId) {
      setError('Thiếu ảnh mặt trước CCCD. Vui lòng quay lại bước 2 để tải lên.');
      return;
    }
    if (!capturedSelfie) {
      setError('Chưa có ảnh chân dung sinh trắc học. Vui lòng chụp ảnh khuôn mặt trước.');
      return;
    }
    if (matchDetails && !matchDetails.isMatch) {
      setError('Khuôn mặt chưa đạt yêu cầu. Vui lòng chụp lại ảnh hợp lệ trước khi gửi duyệt.');
      return;
    }
    if (!frontAssetId || !backAssetId || !portraitAssetId) {
      setError('Thiếu thông tin ảnh định danh (Mặt trước CCCD, Mặt sau CCCD hoặc Ảnh chân dung). Vui lòng tải lại ảnh hợp lệ.');
      return;
    }

    // Lấy quickAuthResultId nếu đang làm lại eKYC (đã có hồ sơ VERIFIED)
    const needsQuickAuth = status?.state === 'VERIFIED' || user?.verifiedSeller;
    const quickAuthResultId = needsQuickAuth ? (reverifyQuickAuthResultId || undefined) : undefined;

    setIsLoading(true);
    setError(null);

    try {
      const res = await ekycApi.submitVerification(
        formData,
        capturedSelfie,
        frontAssetId,
        backAssetId,
        quickAuthResultId,
        capturedSamples.filter((sample) => sample !== capturedSelfie),
        portraitAssetId,
        isManualCapture
      );

      setStatus((prev) => ({
        hasProfile: true,
        canSubmit: false,
        isSeller: false,
        nonmatchCount: 0,
        ...prev,
        state: 'PENDING',
        profileId: res.profileId,
        matchDistance: res.distance,
        confidenceScore: res.confidenceScore,
        submittedData: formData,
      }));
      setMatchDetails({
        distance: res.distance,
        confidence: res.confidenceScore,
        isMatch: true,
        message: res.message,
      });
      setCurrentStep(5);
      setSuccessMessage('Hồ sơ eKYC của bạn đã được gửi thành công. Đang chờ Kỹ thuật viên (KTV) phê duyệt.');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Nộp hồ sơ định danh thất bại.');
    } finally {
      setIsLoading(false);
    }
  };

  const isVerifiedEkyc = Boolean(
    user?.verifiedSeller ||
    status?.hasVerifiedProfile ||
    status?.state === 'VERIFIED' ||
    sellerSettings?.verifiedSeller
  );

  const isReverifyMode = location.search.includes('reverify');
  const isReverifying = isReverifyMode || currentStep > 1 || status?.state === 'IN_PROGRESS';

  // A. Trường hợp tài khoản đã có quyền SELLER chính thức (chỉ khi ở bước 1 và không trong luồng xác thực lại)
  if (isSeller && !isReverifying && currentStep === 1) {
    return (
      <div style={{ maxWidth: '960px', margin: '30px auto', padding: '0 16px' }}>
        <Card
          title="Kênh Người Bán (Đã Kích Hoạt)"
          subtitle="Tài khoản của bạn đã được xác minh danh tính và có toàn quyền đăng bán đồ cũ trên O.G Shop"
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <Alert type="success">
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <span style={{ fontSize: '1.4rem' }}>✓</span>
                <div>
                  <strong>Đã kích hoạt quyền Người bán</strong>
                  <p style={{ margin: '4px 0 0', fontSize: '0.9rem' }}>
                    Tài khoản của bạn hiện đã có quyền <strong>SELLER</strong> từ hệ thống Backend. Bạn có thể sử dụng đầy đủ các tính năng đăng tin và quản lý sản phẩm ở bước tiếp theo.
                  </p>
                </div>
              </div>
            </Alert>

            <div
              style={{
                backgroundColor: 'var(--og-color-surface-subtle)',
                borderRadius: '12px',
                padding: '20px',
                border: '1px solid var(--og-color-border)',
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                gap: '16px',
              }}
            >
              <div>
                <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', display: 'block' }}>Tên gian hàng</span>
                <strong style={{ fontSize: '0.95rem' }}>{sellerSettings?.storeName || `${user.fullName}'s Shop`}</strong>
              </div>
              <div>
                <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', display: 'block' }}>Hotline liên hệ</span>
                <strong style={{ fontSize: '0.95rem' }}>{sellerSettings?.hotlinePhone || user.phoneNumber || 'Chưa thiết lập'}</strong>
              </div>
              <div>
                <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', display: 'block' }}>Ngân hàng giải ngân</span>
                <strong style={{ fontSize: '0.95rem' }}>{sellerSettings?.bankName || userProfile?.bankName || 'Đã liên kết'} ({sellerSettings?.bankAccountNumberMasked || userProfile?.bankAccountNumberMasked || '******'})</strong>
              </div>
              <div>
                <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', display: 'block' }}>Trạng thái định danh</span>
                <Badge variant="seller">✓✓ Đã xác thực eKYC</Badge>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', marginTop: '10px' }}>
              <Button variant="primary" onClick={() => navigate('/seller/products')}>
                Quản lý sản phẩm & Đăng tin mới
              </Button>
              <Button variant="outline" onClick={() => navigate('/seller/settings')}>
                Cài đặt gian hàng
              </Button>
              <Button variant="gold" onClick={handleStartReverification} isLoading={isLoading}>
                Xác thực lại hồ sơ eKYC / Đổi CCCD
              </Button>
              <Button variant="outline" onClick={() => navigate('/profile')}>
                Về trang Hồ sơ cá nhân
              </Button>
            </div>
          </div>
        </Card>

        <QuickFaceAuthModal
          isOpen={showReverifyQuickAuth}
          onClose={handleCloseReverifyQuickAuth}
          onSuccess={handleReverifyQuickAuthSuccess}
          onNeedEkyc={handleNeedEkycReverify}
          title="Xác thực khuôn mặt đối chiếu hồ sơ cũ"
          description="Vui lòng quét khuôn mặt để xác thực danh tính chủ sở hữu trước khi nộp hồ sơ CCCD mới."
        />
      </div>
    );
  }

  // B. Trường hợp đã qua eKYC nhưng chưa đăng ký thông tin Người bán (User test 1)
  if (isVerifiedEkyc && !isSeller && !isReverifyMode && currentStep === 1) {
    const hasLinkedBank = Boolean(sellerSettings?.bankAccountNumberMasked || userProfile?.bankAccountNumberMasked || userProfile?.bankName);

    return (
      <div style={{ maxWidth: '960px', margin: '30px auto', padding: '0 16px' }}>
        <Card
          title="Đăng ký mở gian hàng Người bán"
          subtitle="Tài khoản của bạn đã hoàn tất xác thực eKYC. Thiết lập thông tin gian hàng và địa chỉ kho để bắt đầu bán đồ cũ ngay lập tức."
        >
          {sellerFormSuccess && <Alert type="success">{sellerFormSuccess}</Alert>}
          {sellerFormError && <Alert type="danger">{sellerFormError}</Alert>}

          <div style={{ display: 'flex', flexDirection: 'column', gap: '22px' }}>
            {/* Banner đã xác thực eKYC thành công */}
            <div
              style={{
                backgroundColor: 'rgba(16, 185, 129, 0.08)',
                border: '1px solid rgba(16, 185, 129, 0.25)',
                borderRadius: '12px',
                padding: '16px 20px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                flexWrap: 'wrap',
                gap: '12px',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <span style={{ fontSize: '1.6rem' }}>🛡️</span>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <strong style={{ fontSize: '0.98rem', color: 'var(--og-color-success)' }}>
                      Danh tính eKYC đã được xác thực thành công
                    </strong>
                    <Badge variant="seller">✓✓ eKYC</Badge>
                  </div>
                  <p style={{ margin: '4px 0 0', fontSize: '0.86rem', color: 'var(--og-color-text-secondary)', lineHeight: 1.5 }}>
                    Hồ sơ CCCD gắn chip & sinh trắc học khuôn mặt của bạn đã hợp lệ. Quyền Người bán sẽ được kích hoạt tức thì ngay khi bạn lưu thông tin gian hàng.
                  </p>
                </div>
              </div>
              <Button
                variant="outline"
                size="sm"
                type="button"
                onClick={handleStartReverification}
                isLoading={isLoading}
              >
                🔄 Đổi CCCD khác
              </Button>
            </div>

            {/* Form Thiết lập thông tin gian hàng */}
            <form onSubmit={handleSubmitSellerRegistration} style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '16px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.88rem', fontWeight: 600, marginBottom: '6px' }}>
                    Tên gian hàng / Biệt hiệu Shop *
                  </label>
                  <Input
                    value={storeName}
                    onChange={(e) => setStoreName(e.target.value)}
                    placeholder="Ví dụ: Tiệm Đồ Cũ Vintage, Laptop 2nd Hand..."
                    required
                  />
                  <small style={{ color: 'var(--og-color-text-secondary)', display: 'block', marginTop: '4px', fontSize: '0.8rem' }}>
                    Tên hiển thị công khai trên các món đồ bạn đăng bán.
                  </small>
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: '0.88rem', fontWeight: 600, marginBottom: '6px' }}>
                    Hotline liên hệ gian hàng (SĐT) *
                  </label>
                  <Input
                    value={contactPhone}
                    onChange={(e) => setContactPhone(e.target.value)}
                    placeholder="Ví dụ: 0901234567"
                    required
                  />
                  <small style={{ color: 'var(--og-color-text-secondary)', display: 'block', marginTop: '4px', fontSize: '0.8rem' }}>
                    Dùng để liên hệ giao hàng và nhận mã khi shipper GHN đến lấy hàng.
                  </small>
                </div>
              </div>

              {/* Phần Địa chỉ kho lấy hàng */}
              <div style={{ borderTop: '1px solid var(--og-color-border)', paddingTop: '16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                  <label style={{ fontSize: '0.92rem', fontWeight: 600 }}>
                    📍 Địa chỉ kho lấy hàng (GHN đến nhận hàng) *
                  </label>
                  <Button
                    variant="outline"
                    size="sm"
                    type="button"
                    onClick={() => setShowQuickAddAddress(!showQuickAddAddress)}
                  >
                    {showQuickAddAddress ? 'Đóng tạo địa chỉ' : '+ Thêm địa chỉ mới'}
                  </Button>
                </div>

                {/* Form tạo nhanh địa chỉ nếu chưa có hoặc người dùng bấm thêm */}
                {showQuickAddAddress || addresses.length === 0 ? (
                  <div
                    style={{
                      backgroundColor: 'var(--og-color-surface-subtle)',
                      border: '1px dashed var(--og-color-border)',
                      borderRadius: '10px',
                      padding: '16px',
                      marginBottom: '16px',
                    }}
                  >
                    <p style={{ margin: '0 0 12px', fontSize: '0.86rem', fontWeight: 600 }}>
                      {addresses.length === 0 ? 'Bạn chưa có địa chỉ nào trong Sổ địa chỉ. Vui lòng thiết lập địa chỉ kho lấy hàng:' : 'Nhập thông tin địa chỉ kho mới:'}
                    </p>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '12px', marginBottom: '12px' }}>
                      <Input
                        label="Họ tên người gửi *"
                        value={newRecipientName}
                        onChange={(e) => setNewRecipientName(e.target.value)}
                        placeholder="Ví dụ: Nguyễn Văn A"
                      />
                      <Input
                        label="SĐT người gửi *"
                        value={newRecipientPhone}
                        onChange={(e) => setNewRecipientPhone(e.target.value)}
                        placeholder="Ví dụ: 0901234567"
                      />
                      <Input
                        label="Tỉnh / Thành phố *"
                        value={newProvince}
                        onChange={(e) => setNewProvince(e.target.value)}
                        placeholder="Ví dụ: TP. Hồ Chí Minh"
                      />
                      <Input
                        label="Quận / Huyện *"
                        value={newDistrict}
                        onChange={(e) => setNewDistrict(e.target.value)}
                        placeholder="Ví dụ: Quận 1"
                      />
                      <Input
                        label="Phường / Xã *"
                        value={newWard}
                        onChange={(e) => setNewWard(e.target.value)}
                        placeholder="Ví dụ: Phường Bến Nghé"
                      />
                      <Input
                        label="Địa chỉ chi tiết (Số nhà, tên đường) *"
                        value={newDetailAddress}
                        onChange={(e) => setNewDetailAddress(e.target.value)}
                        placeholder="Ví dụ: 123 Lê Lợi"
                      />
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
                      {addresses.length > 0 && (
                        <Button variant="outline" size="sm" type="button" onClick={() => setShowQuickAddAddress(false)}>
                          Hủy
                        </Button>
                      )}
                      <Button variant="primary" size="sm" type="button" onClick={handleCreateQuickAddress} isLoading={isCreatingAddress}>
                        Lưu địa chỉ kho này
                      </Button>
                    </div>
                  </div>
                ) : null}

                {addresses.length > 0 && (
                  <div>
                    <select
                      value={pickupAddressId}
                      onChange={(e) => {
                        const val = Number(e.target.value);
                        setPickupAddressId(val);
                        if (sameAddress) setReturnAddressId(val);
                      }}
                      style={{
                        width: '100%',
                        padding: '10px 14px',
                        borderRadius: '8px',
                        border: '1px solid var(--og-color-border)',
                        backgroundColor: 'var(--og-color-surface)',
                        color: 'var(--og-color-text-primary)',
                        fontSize: '0.92rem',
                      }}
                    >
                      {addresses.map((addr) => (
                        <option key={addr.id} value={addr.id}>
                          {addr.recipientName} - {addr.phoneNumber} ({addr.detailAddress}, {addr.ward}, {addr.district}, {addr.province}) {addr.isDefault ? '[Mặc định]' : ''}
                        </option>
                      ))}
                    </select>
                  </div>
                )}
              </div>

              {/* Phần Địa chỉ kho trả hàng */}
              <div>
                <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.9rem', cursor: 'pointer', marginBottom: '8px' }}>
                  <input
                    type="checkbox"
                    checked={sameAddress}
                    onChange={(e) => {
                      setSameAddress(e.target.checked);
                      if (e.target.checked && pickupAddressId) {
                        setReturnAddressId(pickupAddressId);
                      }
                    }}
                    style={{ width: '16px', height: '16px', accentColor: 'var(--og-color-primary)' }}
                  />
                  <span>Sử dụng cùng địa chỉ kho lấy hàng làm địa chỉ trả hàng khi giao thất bại</span>
                </label>

                {!sameAddress && addresses.length > 0 && (
                  <div style={{ marginTop: '8px' }}>
                    <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '6px' }}>
                      Chọn địa chỉ trả hàng:
                    </label>
                    <select
                      value={returnAddressId}
                      onChange={(e) => setReturnAddressId(Number(e.target.value))}
                      style={{
                        width: '100%',
                        padding: '10px 14px',
                        borderRadius: '8px',
                        border: '1px solid var(--og-color-border)',
                        backgroundColor: 'var(--og-color-surface)',
                        color: 'var(--og-color-text-primary)',
                        fontSize: '0.92rem',
                      }}
                    >
                      {addresses.map((addr) => (
                        <option key={addr.id} value={addr.id}>
                          {addr.recipientName} - {addr.phoneNumber} ({addr.detailAddress}, {addr.ward}, {addr.district}, {addr.province})
                        </option>
                      ))}
                    </select>
                  </div>
                )}
              </div>

              {/* Tài khoản ngân hàng nhận tiền giải ngân Escrow */}
              <div style={{ borderTop: '1px solid var(--og-color-border)', paddingTop: '16px' }}>
                <h4 style={{ margin: '0 0 10px', fontSize: '0.94rem' }}>
                  🏦 Tài khoản ngân hàng nhận tiền bán hàng (Escrow)
                </h4>
                {hasLinkedBank ? (
                  <div
                    style={{
                      backgroundColor: 'var(--og-color-surface-subtle)',
                      borderRadius: '10px',
                      padding: '14px 16px',
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      border: '1px solid var(--og-color-border)',
                    }}
                  >
                    <div>
                      <strong style={{ fontSize: '0.94rem', color: 'var(--og-color-text-primary)' }}>
                        {sellerSettings?.bankName || userProfile?.bankName || 'Ngân hàng đã liên kết'}
                      </strong>
                      <span style={{ margin: '0 8px', color: 'var(--og-color-text-muted)' }}>&bull;</span>
                      <span style={{ fontSize: '0.92rem', letterSpacing: '0.5px' }}>
                        STK: {sellerSettings?.bankAccountNumberMasked || userProfile?.bankAccountNumberMasked || '******'}
                      </span>
                      <span style={{ margin: '0 8px', color: 'var(--og-color-text-muted)' }}>&bull;</span>
                      <span style={{ fontSize: '0.88rem', color: 'var(--og-color-text-secondary)' }}>
                        Chủ TK: {sellerSettings?.bankAccountHolder || userProfile?.bankAccountHolder || user?.fullName}
                      </span>
                    </div>
                    <Badge variant="buyer">Đã liên kết</Badge>
                  </div>
                ) : (
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '12px' }}>
                    <Input
                      label="Tên ngân hàng *"
                      value={newBankName}
                      onChange={(e) => setNewBankName(e.target.value)}
                      placeholder="Ví dụ: MB Bank, Vietcombank..."
                      required
                    />
                    <Input
                      label="Số tài khoản ngân hàng *"
                      value={newBankAccountNumber}
                      onChange={(e) => setNewBankAccountNumber(e.target.value)}
                      placeholder="Nhập số tài khoản"
                      required
                    />
                    <Input
                      label="Tên chủ tài khoản (Viết hoa) *"
                      value={newBankAccountHolder}
                      onChange={(e) => setNewBankAccountHolder(e.target.value)}
                      placeholder="Ví dụ: NGUYEN VAN A"
                      required
                    />
                  </div>
                )}
              </div>

              {/* Giải thích cơ chế 100% Escrow & Checkbox Cam kết */}
              <div
                style={{
                  backgroundColor: 'rgba(245, 158, 11, 0.06)',
                  border: '1px solid rgba(245, 158, 11, 0.25)',
                  borderRadius: '12px',
                  padding: '16px 18px',
                }}
              >
                <h4 style={{ margin: '0 0 8px', fontSize: '0.95rem', color: '#b45309', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>⚖️</span> Cơ chế Ký quỹ 100% Escrow & Quy định dành cho Người bán
                </h4>
                <ul style={{ margin: 0, paddingLeft: '20px', fontSize: '0.85rem', color: 'var(--og-color-text-secondary)', lineHeight: 1.6, display: 'flex', flexDirection: 'column', gap: '4px' }}>
                  <li><strong>Bảo vệ dòng tiền:</strong> Người mua thanh toán &rarr; Tiền được sàn giữ an toàn tại tài khoản trung gian &rarr; Bạn giao hàng qua GHN &rarr; Người mua nhận hàng, kiểm tra đồ cũ &rarr; Tiền giải ngân 100% về tài khoản ngân hàng của bạn.</li>
                  <li><strong>Mô tả minh bạch:</strong> Bạn cam kết chụp ảnh thật và mô tả trung thực tình trạng ngoại quan, chức năng và phụ kiện đi kèm của món đồ cũ.</li>
                  <li><strong>Chống gian lận:</strong> Nghiêm cấm bán hàng giả, hàng nhái hoặc lừa đảo chuyển khoản ngoài sàn.</li>
                </ul>
                <div style={{ marginTop: '12px', paddingTop: '10px', borderTop: '1px dashed rgba(245, 158, 11, 0.3)' }}>
                  <label style={{ display: 'flex', alignItems: 'flex-start', gap: '10px', fontSize: '0.88rem', cursor: 'pointer', color: 'var(--og-color-text-primary)' }}>
                    <input
                      type="checkbox"
                      checked={termsAccepted}
                      onChange={(e) => setTermsAccepted(e.target.checked)}
                      style={{ marginTop: '3px', width: '16px', height: '16px', accentColor: 'var(--og-color-primary)' }}
                      required
                    />
                    <span>
                      Tôi đã đọc, hiểu rõ và cam kết tuân thủ <strong>Quy chế hoạt động</strong> và <strong>Chính sách ký quỹ 100% Escrow</strong> của O.G Shop.
                    </span>
                  </label>
                </div>
              </div>

              {/* Nút hành động */}
              <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', paddingTop: '6px' }}>
                <Button
                  variant="gold"
                  type="submit"
                  isLoading={isSubmittingSeller}
                  disabled={isSubmittingSeller}
                  style={{ padding: '12px 24px', fontSize: '1rem', fontWeight: 700 }}
                >
                  Hoàn tất đăng ký & Kích hoạt quyền Người bán
                </Button>
                <Button
                  variant="outline"
                  type="button"
                  onClick={() => navigate('/profile')}
                  disabled={isSubmittingSeller}
                >
                  Về trang Hồ sơ cá nhân
                </Button>
              </div>
            </form>
          </div>
        </Card>

        {/* Quick Face Auth Modal for Re-verification Guard */}
        <QuickFaceAuthModal
          isOpen={showReverifyQuickAuth}
          onClose={handleCloseReverifyQuickAuth}
          onSuccess={handleReverifyQuickAuthSuccess}
          onNeedEkyc={handleNeedEkycReverify}
          title="Xác thực khuôn mặt đối chiếu hồ sơ cũ"
          description="Vui lòng quét khuôn mặt để xác thực danh tính chủ sở hữu trước khi nộp hồ sơ CCCD mới."
        />
      </div>
    );
  }

  // C. Chế độ Full eKYC hoặc người dùng cần phê duyệt bắt buộc
  if (isVerifiedEkyc || showFullEkyc || (user.sellerApprovalRequired && !isSeller)) {
    const isEkycRoute = location.pathname.includes('/ekyc');
    const pageTitle = isEkycRoute || !user.sellerApprovalRequired
      ? "Xác thực danh tính (eKYC)"
      : "Xác minh hồ sơ Người bán";
    const pageSubtitle = isEkycRoute || !user.sellerApprovalRequired
      ? "Phục vụ bảo vệ tài khoản, mua hàng giá trị cao và mở gian hàng bán đồ cũ khi có nhu cầu."
      : "Quyền bán hàng cần được phê duyệt sau khi xác minh danh tính.";

    return (
      <div style={{ maxWidth: '960px', margin: '30px auto', padding: '0 16px' }}>
        <Card
          title={pageTitle}
          subtitle={pageSubtitle}
        >
          {location.state?.registrationCoins === 3000 && (
            <Alert type="success">Đăng ký thành công. Bạn đã nhận 3.000 xu.</Alert>
          )}

          <Alert type="info">
            Nếu bạn đã chọn Người bán khi đăng ký, thông tin cửa hàng, địa chỉ và ngân hàng đã được lưu.
            Tài khoản hiện có quyền Người mua. Quyền Người bán sẽ được cấp sau eKYC và kiểm duyệt.
          </Alert>

          <p style={{ color: 'var(--og-color-text-secondary)', marginBottom: '20px' }}>
            Bước nộp eKYC và theo dõi phê duyệt đang được hoàn thiện. Bạn có thể mua hàng trong thời gian này.
          </p>

          {/* MÀN HÌNH HỒ SƠ BỊ TỪ CHỐI (REJECTED) */}
          {status?.state === 'REJECTED' ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
              {/* Thẻ Alert Danger hiển thị lý do từ chối & thời gian kiểm duyệt */}
              <Alert type="danger">
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '8px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <strong style={{ fontSize: '1.05rem' }}>Hồ sơ xác thực danh tính (eKYC) chưa được phê duyệt</strong>
                    </div>
                    <Badge variant="danger">Bị từ chối</Badge>
                  </div>
                  <div style={{ backgroundColor: 'rgba(239, 68, 68, 0.08)', padding: '12px 14px', borderRadius: '8px', borderLeft: '4px solid #ef4444' }}>
                    <p style={{ margin: 0, fontWeight: 600, color: '#b91c1c' }}>
                      Lý do từ chối từ Kỹ thuật viên (KTV):
                    </p>
                    <p style={{ margin: '4px 0 0', color: 'var(--og-color-text-primary)', fontSize: '0.92rem', lineHeight: 1.5 }}>
                      {status.rejectionReason || 'Thông tin giấy tờ hoặc hình ảnh đối chiếu chưa đạt yêu cầu kiểm duyệt.'}
                    </p>
                  </div>
                  <p style={{ margin: 0, fontSize: '0.82rem', color: 'var(--og-color-text-secondary)' }}>
                    Thời gian kiểm duyệt: {status.verifiedAt ? new Date(status.verifiedAt).toLocaleString('vi-VN') : (status.submittedAt ? new Date(status.submittedAt).toLocaleString('vi-VN') : 'Gần đây')} &bull; Phiên bản hồ sơ: #{status.revisionNo || 1}
                  </p>
                </div>
              </Alert>

              {complaintSuccess && <Alert type="success">{complaintSuccess}</Alert>}

              {/* 1. Xem thông tin cá nhân đã bóc tách */}
              <div style={{ backgroundColor: 'var(--og-color-surface-subtle)', borderRadius: '12px', padding: '18px 20px', border: '1px solid var(--og-color-border)' }}>
                <h4 style={{ margin: '0 0 14px', fontSize: '1rem', color: 'var(--og-color-text-primary)', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>📋</span> Thông tin cá nhân đã ghi nhận
                </h4>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '14px' }}>
                  <div>
                    <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-muted)', display: 'block' }}>Số CCCD</span>
                    <strong style={{ fontSize: '0.95rem' }}>{status.submittedData?.cccdNumber || formData.cccdNumber || '---'}</strong>
                  </div>
                  <div>
                    <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-muted)', display: 'block' }}>Họ và tên</span>
                    <strong style={{ fontSize: '0.95rem' }}>{status.submittedData?.fullName || formData.fullName || '---'}</strong>
                  </div>
                  <div>
                    <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-muted)', display: 'block' }}>Ngày sinh</span>
                    <strong style={{ fontSize: '0.95rem' }}>{status.submittedData?.dob || formData.dob || '---'}</strong>
                  </div>
                  <div>
                    <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-muted)', display: 'block' }}>Giới tính</span>
                    <strong style={{ fontSize: '0.95rem' }}>{status.submittedData?.gender || formData.gender || '---'}</strong>
                  </div>
                  <div>
                    <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-muted)', display: 'block' }}>Quê quán</span>
                    <strong style={{ fontSize: '0.95rem' }}>{status.submittedData?.hometown || formData.hometown || '---'}</strong>
                  </div>
                  <div>
                    <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-muted)', display: 'block' }}>Địa chỉ thường trú</span>
                    <strong style={{ fontSize: '0.95rem' }}>{status.submittedData?.address || formData.address || '---'}</strong>
                  </div>
                </div>
              </div>

              {/* 2. Lưới 4 ảnh đã nộp qua SecureAssetImage */}
              <div>
                <h4 style={{ margin: '0 0 14px', fontSize: '1rem', color: 'var(--og-color-text-primary)', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>🖼️</span> Hình ảnh hồ sơ đã gửi đối chiếu
                </h4>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '16px' }}>
                  {/* Ảnh mặt trước */}
                  <div style={{ border: '1px solid var(--og-color-border)', borderRadius: '10px', padding: '12px', textAlign: 'center', backgroundColor: 'var(--og-color-surface)' }}>
                    <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--og-color-text-secondary)', display: 'block', marginBottom: '8px' }}>
                      CCCD Mặt trước
                    </span>
                    <SecureAssetImage
                      assetId={status.frontAssetId || frontAssetId}
                      alt="CCCD Mặt trước"
                      style={{ width: '100%', height: '140px', objectFit: 'contain', borderRadius: '6px', backgroundColor: '#0f172a' }}
                      fallbackText="Chưa có ảnh mặt trước"
                    />
                  </div>

                  {/* Ảnh mặt sau */}
                  <div style={{ border: '1px solid var(--og-color-border)', borderRadius: '10px', padding: '12px', textAlign: 'center', backgroundColor: 'var(--og-color-surface)' }}>
                    <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--og-color-text-secondary)', display: 'block', marginBottom: '8px' }}>
                      CCCD Mặt sau (Mã MRZ)
                    </span>
                    <SecureAssetImage
                      assetId={status.backAssetId || backAssetId}
                      alt="CCCD Mặt sau"
                      style={{ width: '100%', height: '140px', objectFit: 'contain', borderRadius: '6px', backgroundColor: '#0f172a' }}
                      fallbackText="Chưa có ảnh mặt sau"
                    />
                  </div>

                  {/* Ảnh thẻ chân dung 3x4 */}
                  <div style={{ border: '1px solid var(--og-color-border)', borderRadius: '10px', padding: '12px', textAlign: 'center', backgroundColor: 'var(--og-color-surface)' }}>
                    <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--og-color-text-secondary)', display: 'block', marginBottom: '8px' }}>
                      Ảnh thẻ chân dung (3x4)
                    </span>
                    <SecureAssetImage
                      assetId={status.portraitAssetId || portraitAssetId}
                      alt="Ảnh thẻ chân dung"
                      style={{ width: '100%', height: '140px', objectFit: 'contain', borderRadius: '6px', backgroundColor: '#0f172a' }}
                      fallbackText="Chưa có ảnh thẻ"
                    />
                  </div>

                  {/* Ảnh webcam */}
                  <div style={{ border: '1px solid var(--og-color-border)', borderRadius: '10px', padding: '12px', textAlign: 'center', backgroundColor: 'var(--og-color-surface)' }}>
                    <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--og-color-text-secondary)', display: 'block', marginBottom: '8px' }}>
                      Chân dung Webcam
                    </span>
                    <SecureAssetImage
                      assetId={status.liveAssetId}
                      alt="Chân dung đối chiếu"
                      style={{ width: '100%', height: '140px', objectFit: 'contain', borderRadius: '6px', backgroundColor: '#0f172a' }}
                      fallbackText="Chưa có ảnh chân dung"
                    />
                  </div>
                </div>
              </div>

              {/* 3. Các nút hành động */}
              <div style={{ display: 'flex', gap: '12px', justifyContent: 'center', flexWrap: 'wrap', paddingTop: '12px', borderTop: '1px solid var(--og-color-border)' }}>
                <Button variant="outline" type="button" onClick={() => navigate(-1)}>
                  ⬅️ Quay lại
                </Button>
                <Button variant="outline" type="button" onClick={handleOpenComplaint}>
                  Khiếu nại kết quả
                </Button>
                <Button variant="primary" type="button" onClick={handleStartReverification} isLoading={isLoading}>
                  Xác thực lại hồ sơ mới
                </Button>
              </div>

              {/* Modal Khiếu nại */}
              {showComplaintModal && (
                <div
                  style={{
                    position: 'fixed',
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    backgroundColor: 'rgba(0, 0, 0, 0.55)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    zIndex: 1000,
                    padding: '16px',
                  }}
                  role="dialog"
                  aria-modal="true"
                  aria-label="Khiếu nại kết quả eKYC"
                >
                  <div
                    style={{
                      backgroundColor: 'var(--og-color-surface)',
                      borderRadius: '12px',
                      maxWidth: '520px',
                      width: '100%',
                      padding: '24px',
                      boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.2)',
                    }}
                  >
                    <h3 style={{ margin: '0 0 8px', fontSize: '1.15rem' }}>Gửi khiếu nại về kết quả eKYC</h3>
                    <p style={{ margin: '0 0 16px', fontSize: '0.86rem', color: 'var(--og-color-text-secondary)' }}>
                      Nếu bạn cho rằng hồ sơ của mình bị từ chối không chính xác, vui lòng gửi phản ánh đến Ban Quản Trị để được hỗ trợ kiểm tra lại thủ công.
                    </p>
                    <form onSubmit={handleSubmitComplaint} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                      <div>
                        <label style={{ display: 'block', fontSize: '0.84rem', fontWeight: 600, marginBottom: '6px' }}>
                          Tiêu đề khiếu nại *
                        </label>
                        <Input
                          value={complaintTitle}
                          onChange={(e) => setComplaintTitle(e.target.value)}
                          required
                          placeholder="Nhập tiêu đề khiếu nại..."
                        />
                      </div>
                      <div>
                        <label style={{ display: 'block', fontSize: '0.84rem', fontWeight: 600, marginBottom: '6px' }}>
                          Nội dung / Lý do khiếu nại *
                        </label>
                        <textarea
                          rows={4}
                          value={complaintContent}
                          onChange={(e) => setComplaintContent(e.target.value)}
                          required
                          placeholder="Mô tả chi tiết lý do bạn cho rằng hồ sơ đã hợp lệ..."
                          style={{
                            width: '100%',
                            padding: '10px 12px',
                            borderRadius: '8px',
                            border: '1px solid var(--og-color-border)',
                            backgroundColor: 'var(--og-color-surface)',
                            color: 'var(--og-color-text-primary)',
                            fontSize: '0.9rem',
                            fontFamily: 'inherit',
                            resize: 'vertical',
                          }}
                        />
                      </div>
                      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px' }}>
                        <Button variant="outline" type="button" onClick={() => setShowComplaintModal(false)}>
                          Hủy bỏ
                        </Button>
                        <Button variant="primary" type="submit">
                          Gửi khiếu nại
                        </Button>
                      </div>
                    </form>
                  </div>
                </div>
              )}
            </div>
          ) : (
            <>
              {/* Stepper Header */}
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '28px', borderBottom: '1px solid var(--og-color-border)', paddingBottom: '16px' }}>
            {[
              { num: 1, label: 'Bắt đầu' },
              { num: 2, label: 'Tải CCCD' },
              { num: 3, label: 'Xác nhận OCR' },
              { num: 4, label: 'Khuôn mặt' },
              { num: 5, label: 'Chờ duyệt' },
            ].map((s) => (
              <div
                key={s.num}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                  fontWeight: currentStep === s.num ? 700 : 500,
                  color: currentStep === s.num ? 'var(--og-color-primary)' : currentStep > s.num ? 'var(--og-color-success)' : 'var(--og-color-text-muted)',
                }}
              >
                <span
                  style={{
                    width: '26px',
                    height: '26px',
                    borderRadius: '50%',
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '13px',
                    backgroundColor: currentStep === s.num ? 'var(--og-color-primary)' : currentStep > s.num ? 'var(--og-color-success)' : 'var(--og-color-surface-subtle)',
                    color: currentStep >= s.num ? '#fff' : 'inherit',
                  }}
                >
                  {currentStep > s.num ? '✓' : s.num}
                </span>
                <span>{s.label}</span>
              </div>
            ))}
          </div>

          {error && <Alert type="danger">{error}</Alert>}
          {successMessage && <Alert type="success">{successMessage}</Alert>}

          {/* BƯỚC 1: Bắt đầu & Cơ chế chính sách eKYC */}
          {currentStep === 1 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              {/* Card 1: Cơ chế Ký quỹ 100% Escrow */}
              <div
                style={{
                  backgroundColor: 'rgba(245, 158, 11, 0.06)',
                  border: '1px solid rgba(245, 158, 11, 0.25)',
                  borderRadius: '12px',
                  padding: '16px 18px',
                }}
              >
                <h4 style={{ margin: '0 0 10px', fontSize: '0.98rem', color: '#b45309', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>🛡️</span> 1. Cơ chế Ký quỹ 100% Escrow bảo vệ người bán đồ cũ
                </h4>
                <p style={{ margin: '0 0 8px', fontSize: '0.88rem', color: 'var(--og-color-text-primary)', lineHeight: 1.6 }}>
                  Sàn <strong>O.G Shop</strong> hoạt động trên mô hình <strong>100% Escrow Protection</strong> (Ký quỹ an toàn tuyệt đối). Khi người mua đặt món đồ cũ của bạn:
                </p>
                <ul style={{ margin: 0, paddingLeft: '20px', fontSize: '0.85rem', color: 'var(--og-color-text-secondary)', lineHeight: 1.6, display: 'flex', flexDirection: 'column', gap: '4px' }}>
                  <li>Số tiền thanh toán sẽ được hệ thống tạm giữ an toàn tại tài khoản trung gian của sàn O.G Shop.</li>
                  <li>Bạn đóng gói và bàn giao hàng cho shipper Giao Hàng Nhanh (GHN) đến lấy tận nơi.</li>
                  <li>Khi người mua nhận hàng, kiểm tra đồ cũ đúng như mô tả, hệ thống sẽ tự động giải ngân 100% tiền hàng về tài khoản ngân hàng của bạn.</li>
                  <li>Loại bỏ hoàn toàn rủi ro bị bùng đơn, lừa đảo biên lai chuyển khoản giả.</li>
                </ul>
              </div>

              {/* Card 2: Chính sách Bắt buộc Định danh eKYC */}
              <div
                style={{
                  backgroundColor: 'var(--og-color-surface-subtle)',
                  border: '1px solid var(--og-color-border)',
                  borderRadius: '12px',
                  padding: '16px 18px',
                }}
              >
                <h4 style={{ margin: '0 0 10px', fontSize: '0.98rem', color: 'var(--og-color-text-primary)', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>🪪</span> 2. Vì sao bắt buộc xác thực CCCD gắn chip & Khuôn mặt sinh trắc học?
                </h4>
                <p style={{ margin: '0 0 8px', fontSize: '0.88rem', color: 'var(--og-color-text-secondary)', lineHeight: 1.6 }}>
                  Đồ cũ là mặt hàng đã qua sử dụng, đòi hỏi sự tin cậy cao về nguồn gốc và tình trạng thực tế:
                </p>
                <ul style={{ margin: 0, paddingLeft: '20px', fontSize: '0.85rem', color: 'var(--og-color-text-secondary)', lineHeight: 1.6, display: 'flex', flexDirection: 'column', gap: '4px' }}>
                  <li><strong>Chống gian lận và hàng không rõ nguồn gốc:</strong> Mỗi chủ gian hàng được gắn chặt với danh tính công dân thực tế qua thẻ CCCD gắn chip, chịu trách nhiệm pháp lý với món đồ rao bán.</li>
                  <li><strong>Đối soát sinh trắc học AI:</strong> Mô hình ArcFace đối chiếu ảnh CCCD với khuôn mặt trực tiếp qua webcam, ngăn chặn việc sử dụng giấy tờ giả hoặc danh tính người khác.</li>
                  <li><strong>Quyền lợi người bán uy tín:</strong> Gian hàng đạt chuẩn eKYC được gắn huy hiệu <code>✓✓ Verified Seller</code>, tăng 300% tỷ lệ tin tưởng và chốt đơn từ người mua.</li>
                </ul>
              </div>

              {/* Card 3: Yêu cầu chuẩn bị */}
              <div
                style={{
                  border: '1px dashed var(--og-color-border)',
                  borderRadius: '12px',
                  padding: '16px 18px',
                  backgroundColor: 'var(--og-color-surface)',
                }}
              >
                <p style={{ color: 'var(--og-color-text-secondary)', lineHeight: 1.6, margin: '0 0 8px' }}>
                  Để tuân thủ quy định giao dịch an toàn và nâng cao uy tín cho gian hàng của bạn trên <strong>O.G Shop</strong>, vui lòng chuẩn bị:
                </p>
                <ul style={{ paddingLeft: '20px', color: 'var(--og-color-text-secondary)', lineHeight: 1.8, margin: 0 }}>
                  <li>Thẻ <strong>Căn cước công dân (CCCD)</strong> gắn chip bản gốc, còn hiệu lực (chụp rõ cả mặt trước và mặt sau).</li>
                  <li><strong>Ảnh thẻ chân dung (3x4 hoặc 4x6)</strong> phông nền sáng, chụp thẳng, rõ nét khuôn mặt để đối chiếu sinh trắc học và chống gian lận.</li>
                  <li>Thiết bị có <strong>Webcam</strong> hoặc Camera hoạt động tốt trong điều kiện đủ sáng.</li>
                  <li>Không đeo kính râm, khẩu trang hoặc mũ che khuất mặt trong suốt quá trình chụp.</li>
                </ul>
              </div>

              <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', marginTop: '6px' }}>
                <Button variant="primary" onClick={handleStartEkyc} isLoading={isLoading}>
                  Bắt đầu xác minh eKYC (Bước 1/5)
                </Button>
                <Button variant="outline" onClick={() => navigate('/profile')}>
                  Về Hồ sơ cá nhân
                </Button>
              </div>
            </div>
          )}

          {/* BƯỚC 2: Tải ảnh CCCD & Ảnh thẻ chân dung */}
          {currentStep === 2 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              <Alert type="info">
                Tải lên đầy đủ <strong>3 ảnh bắt buộc</strong>: <strong>Mặt trước CCCD</strong>, <strong>Mặt sau CCCD</strong> và <strong>Ảnh thẻ chân dung (3x4 hoặc 4x6)</strong>. Hệ thống AI sẽ tự động bóc tách thông tin, đối chiếu khuôn mặt trên CCCD với ảnh thẻ chân dung và kiểm tra đặc điểm bảo an.
              </Alert>

              <div className="og-ekyc-upload-grid">
                {/* 1. Mặt trước CCCD */}
                <div className="og-ekyc-upload-card">
                  <div>
                    <h4 style={{ margin: '0 0 8px', fontSize: '1rem', color: 'var(--og-color-text-primary)' }}>
                      🪪 1. Mặt trước CCCD gắn chip *
                    </h4>
                    <p style={{ fontSize: '0.82rem', color: 'var(--og-color-text-secondary)', marginBottom: '14px', minHeight: '38px' }}>
                      Chụp rõ nét 4 góc, không chói lóa, đủ thông tin và ảnh chân dung
                    </p>

                    {frontPreview ? (
                      <div style={{ marginBottom: '12px' }}>
                        <img
                          src={frontPreview}
                          alt="Mặt trước CCCD"
                          style={{
                            width: '100%',
                            maxHeight: '160px',
                            objectFit: 'contain',
                            borderRadius: '8px',
                            backgroundColor: '#0f172a',
                            boxShadow: '0 4px 12px rgba(0,0,0,0.08)',
                          }}
                        />
                      </div>
                    ) : (
                      <div
                        style={{
                          padding: '30px 12px',
                          color: 'var(--og-color-text-muted)',
                          backgroundColor: 'var(--og-color-surface)',
                          borderRadius: '8px',
                          border: '1px dashed var(--og-color-border)',
                          marginBottom: '12px',
                          minHeight: '160px',
                          display: 'flex',
                          flexDirection: 'column',
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}
                      >
                        <div style={{ fontSize: '0.88rem', fontWeight: 500 }}>Chọn ảnh mặt trước thẻ CCCD</div>
                      </div>
                    )}
                  </div>

                  <div>
                    <input
                      id="frontCccdInput"
                      type="file"
                      accept="image/*"
                      onChange={handleFrontFileChange}
                      style={{ display: 'none' }}
                    />
                    <Button
                      variant="outline"
                      type="button"
                      size="sm"
                      onClick={() => document.getElementById('frontCccdInput')?.click()}
                    >
                      {frontFile ? 'Đổi ảnh mặt trước' : 'Tải ảnh mặt trước'}
                    </Button>
                  </div>
                </div>

                {/* 2. Mặt sau CCCD (MRZ & Chip) */}
                <div className="og-ekyc-upload-card">
                  <div>
                    <h4 style={{ margin: '0 0 8px', fontSize: '1rem', color: 'var(--og-color-text-primary)' }}>
                      2. Mặt sau CCCD gắn chip *
                    </h4>
                    <p style={{ fontSize: '0.82rem', color: 'var(--og-color-text-secondary)', marginBottom: '14px', minHeight: '38px' }}>
                      Chụp rõ nét mặt sau chứa chip điện tử, con dấu, ô vân tay và dải MRZ
                    </p>

                    {backPreview ? (
                      <div style={{ marginBottom: '12px' }}>
                        <img
                          src={backPreview}
                          alt="Mặt sau CCCD"
                          style={{
                            width: '100%',
                            maxHeight: '160px',
                            objectFit: 'contain',
                            borderRadius: '8px',
                            backgroundColor: '#0f172a',
                            boxShadow: '0 4px 12px rgba(0,0,0,0.08)',
                          }}
                        />
                      </div>
                    ) : (
                      <div
                        style={{
                          padding: '30px 12px',
                          color: 'var(--og-color-text-muted)',
                          backgroundColor: 'var(--og-color-surface)',
                          borderRadius: '8px',
                          border: '1px dashed var(--og-color-border)',
                          marginBottom: '12px',
                          minHeight: '160px',
                          display: 'flex',
                          flexDirection: 'column',
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}
                      >
                        <div style={{ fontSize: '0.88rem', fontWeight: 500 }}>Chọn ảnh mặt sau chứa chip & mã MRZ</div>
                      </div>
                    )}
                  </div>

                  <div>
                    <input
                      id="backCccdInput"
                      type="file"
                      accept="image/*"
                      onChange={handleBackFileChange}
                      style={{ display: 'none' }}
                    />
                    <Button
                      variant="outline"
                      type="button"
                      size="sm"
                      onClick={() => document.getElementById('backCccdInput')?.click()}
                    >
                      {backFile ? 'Đổi ảnh mặt sau' : 'Tải ảnh mặt sau'}
                    </Button>
                  </div>
                </div>

                {/* 3. Ảnh thẻ chân dung 3x4 / 4x6 */}
                <div className="og-ekyc-upload-card">
                  <div>
                    <h4 style={{ margin: '0 0 8px', fontSize: '1rem', color: 'var(--og-color-text-primary)' }}>
                      3. Ảnh thẻ chân dung 3x4 / 4x6 *
                    </h4>
                    <p style={{ fontSize: '0.82rem', color: 'var(--og-color-text-secondary)', marginBottom: '14px', minHeight: '38px' }}>
                      Ảnh thẻ chụp chân dung rõ nét, nhìn thẳng, không bị chói để đối chiếu khuôn mặt
                    </p>

                    {portraitPreview ? (
                      <div style={{ marginBottom: '12px' }}>
                        <img
                          src={portraitPreview}
                          alt="Ảnh thẻ chân dung"
                          style={{
                            width: '100%',
                            maxHeight: '160px',
                            objectFit: 'contain',
                            borderRadius: '8px',
                            backgroundColor: '#0f172a',
                            boxShadow: '0 4px 12px rgba(0,0,0,0.08)',
                          }}
                        />
                      </div>
                    ) : (
                      <div
                        style={{
                          padding: '30px 12px',
                          color: 'var(--og-color-text-muted)',
                          backgroundColor: 'var(--og-color-surface)',
                          borderRadius: '8px',
                          border: '1px dashed var(--og-color-border)',
                          marginBottom: '12px',
                          minHeight: '160px',
                          display: 'flex',
                          flexDirection: 'column',
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}
                      >
                        <div style={{ fontSize: '0.88rem', fontWeight: 500 }}>Chọn ảnh thẻ chân dung 3x4 / 4x6</div>
                      </div>
                    )}
                  </div>

                  <div>
                    <input
                      id="portraitInput"
                      type="file"
                      accept="image/*"
                      onChange={handlePortraitFileChange}
                      style={{ display: 'none' }}
                    />
                    <Button
                      variant="outline"
                      type="button"
                      size="sm"
                      onClick={() => document.getElementById('portraitInput')?.click()}
                    >
                      {portraitFile ? 'Đổi ảnh thẻ chân dung' : 'Tải ảnh thẻ chân dung'}
                    </Button>
                  </div>
                </div>
              </div>

              <div style={{ display: 'flex', gap: '12px', justifyContent: 'center', marginTop: '6px' }}>
                <Button
                  variant="primary"
                  onClick={handleScanOcr}
                  isLoading={isLoading}
                  disabled={!frontFile || !backFile || !portraitFile}
                >
                  Bóc tách OCR & Đối chiếu tiếp tục (Bước 3/5)
                </Button>
                <Button
                  variant="outline"
                  onClick={() => {
                    if (isSeller) {
                      navigate('/seller/verification');
                    } else if (isReverifyMode) {
                      navigate('/profile');
                    } else {
                      setCurrentStep(1);
                    }
                  }}
                >
                  Quay lại
                </Button>
              </div>
            </div>
          )}

          {/* BƯỚC 3: Đối chiếu thông tin */}
          {currentStep === 3 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              {formData.mrzVerified && (
                <Alert type="success">
                  ✓ <strong>Đối chiếu mã MRZ mặt sau thành công:</strong> Hệ thống AI đã phân tích chính xác 3 dòng mã vạch ký tự mặt sau và xác nhận trùng khớp 100% với thông tin mặt trước (Số CCCD, Họ tên, Ngày sinh, Giới tính).
                </Alert>
              )}

              {formData.aiStandardized ? (
                <Alert type="success">
                  ✓ Thông tin thẻ CCCD đã được AI Agent tự động chuẩn hóa chính tả địa danh hành chính và viết hoa Họ Tên. Vui lòng kiểm tra và chỉnh sửa nếu cần trước khi tiếp tục.
                </Alert>
              ) : (
                <Alert type="info">
                  Hệ thống AI đã tự động bóc tách thông tin từ thẻ CCCD. Vui lòng kiểm tra và chỉnh sửa nếu có sai sót trước khi tiếp tục.
                </Alert>
              )}

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                <Input
                  label="Số CCCD (12 số)"
                  value={formData.cccdNumber}
                  onChange={(e) => setFormData({ ...formData, cccdNumber: e.target.value })}
                  required
                />
                <Input
                  label="Họ và tên"
                  value={formData.fullName}
                  onChange={(e) => setFormData({ ...formData, fullName: e.target.value })}
                  required
                />
                <Input
                  label="Ngày sinh"
                  value={formData.dob}
                  onChange={(e) => setFormData({ ...formData, dob: e.target.value })}
                  required
                />
                <div>
                  <label style={{ display: 'block', marginBottom: '6px', fontSize: '0.9rem', fontWeight: 500 }}>Giới tính</label>
                  <select
                    value={formData.gender}
                    onChange={(e) => setFormData({ ...formData, gender: e.target.value })}
                    style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', border: '1px solid var(--og-color-border)' }}
                  >
                    <option value="Nam">Nam</option>
                    <option value="Nữ">Nữ</option>
                  </select>
                </div>
              </div>

              <Input
                label="Quê quán"
                value={formData.hometown}
                onChange={(e) => setFormData({ ...formData, hometown: e.target.value })}
              />
              <Input
                label="Nơi thường trú (Địa chỉ)"
                value={formData.address}
                onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                required
              />

              <div style={{ display: 'flex', gap: '12px', marginTop: '12px' }}>
                <Button
                  variant="primary"
                  onClick={handleProceedToStep4}
                  disabled={!formData.cccdNumber || !formData.fullName || !formData.dob}
                >
                  Xác nhận & Chuyển sang quét khuôn mặt
                </Button>
                <Button variant="outline" onClick={() => setCurrentStep(2)}>
                  Quay lại tải CCCD
                </Button>
              </div>
            </div>
          )}

          {/* BƯỚC 4: Face Match Camera */}
          {currentStep === 4 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              {/* PHÂN ĐOẠN 1: CĂN CHỈNH & QUÉT LIÊN TỤC (CAPTURE) */}
              {faceStage === 'CAPTURE' && (
                <FaceScanner
                  frontAssetId={frontAssetId}
                  portraitAssetId={portraitAssetId}
                  onScanSuccess={handleScanSuccess}
                  onBack={() => setCurrentStep(3)}
                />
              )}

              {/* PHÂN ĐOẠN 2: XEM ẢNH & KẾT QUẢ AI (PREVIEW) */}
              {faceStage === 'PREVIEW' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                  {matchDetails?.isMatch ? (
                    <Alert type="success">
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <span style={{ fontSize: '1.2rem' }}>🎉</span>
                          <strong style={{ fontSize: '1rem' }}>
                            {isManualCapture
                              ? 'Ảnh khuôn mặt đã chụp thành công để KTV xét duyệt!'
                              : 'Xác thực khuôn mặt thành công!'}
                          </strong>
                        </div>
                        <p style={{ margin: '4px 0 0', fontSize: '0.88rem', opacity: 0.9 }}>
                          {isManualCapture
                            ? 'Bạn đã chọn chụp ảnh thủ công sau khi quét. Hồ sơ sẽ được chuyển tới Kỹ thuật viên (KTV) đối chiếu xét duyệt trực quan.'
                            : 'Khuôn mặt chụp trực tiếp hoàn toàn trùng khớp với hồ sơ định danh. Bạn có thể gửi hồ sơ để Kỹ thuật viên (KTV) phê duyệt, hoặc chụp lại nếu muốn đổi ảnh khác.'}
                        </p>
                      </div>
                    </Alert>
                  ) : (
                    <Alert type="danger">
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <span style={{ fontSize: '1.2rem' }}>❌</span>
                          <strong style={{ fontSize: '1rem' }}>Khuôn mặt không khớp hoặc không hợp lệ</strong>
                        </div>
                        <p style={{ margin: '4px 0 0', fontSize: '0.88rem', opacity: 0.9 }}>
                          {matchDetails?.message || 'Khuôn mặt chụp được không khớp với hồ sơ định danh hoặc chất lượng ảnh không đạt tiêu chuẩn.'} Vui lòng nhấn nút <strong>&quot;Chụp lại ảnh khuôn mặt&quot;</strong> bên dưới để thực hiện lại.
                        </p>
                      </div>
                    </Alert>
                  )}

                  {/* Hiển thị 4 ảnh so sánh đối chiếu */}
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
                      gap: '16px',
                      maxWidth: '840px',
                      margin: '0 auto',
                      width: '100%',
                    }}
                  >
                    {frontPreview && (
                      <div
                        style={{
                          border: '1px solid var(--og-color-border)',
                          borderRadius: '12px',
                          padding: '14px',
                          textAlign: 'center',
                          backgroundColor: 'var(--og-color-surface)',
                        }}
                      >
                        <h4 style={{ margin: '0 0 10px', fontSize: '0.9rem', color: 'var(--og-color-text-secondary)' }}>
                          Ảnh CCCD Mặt trước
                        </h4>
                        <img
                          src={frontPreview}
                          alt="Ảnh mặt trước CCCD"
                          style={{
                            width: '100%',
                            height: '160px',
                            objectFit: 'contain',
                            borderRadius: '8px',
                            backgroundColor: '#0f172a',
                          }}
                        />
                        <div style={{ marginTop: '10px' }}>
                          <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-muted)' }}>
                            Nguồn đối chiếu
                          </span>
                        </div>
                      </div>
                    )}

                    {backPreview && (
                      <div
                        style={{
                          border: '1px solid var(--og-color-border)',
                          borderRadius: '12px',
                          padding: '14px',
                          textAlign: 'center',
                          backgroundColor: 'var(--og-color-surface)',
                        }}
                      >
                        <h4 style={{ margin: '0 0 10px', fontSize: '0.9rem', color: 'var(--og-color-text-secondary)' }}>
                          Ảnh CCCD Mặt sau
                        </h4>
                        <img
                          src={backPreview}
                          alt="Ảnh mặt sau CCCD"
                          style={{
                            width: '100%',
                            height: '160px',
                            objectFit: 'contain',
                            borderRadius: '8px',
                            backgroundColor: '#0f172a',
                          }}
                        />
                        <div style={{ marginTop: '10px' }}>
                          <span style={{ fontSize: '0.8rem', color: formData.mrzVerified ? '#16a34a' : 'var(--og-color-text-muted)', fontWeight: formData.mrzVerified ? 600 : 400 }}>
                            {formData.mrzVerified ? '✓ Đã chứng thực MRZ' : 'Mặt sau thẻ CCCD'}
                          </span>
                        </div>
                      </div>
                    )}

                    {portraitPreview && (
                      <div
                        style={{
                          border: '1px solid var(--og-color-border)',
                          borderRadius: '12px',
                          padding: '14px',
                          textAlign: 'center',
                          backgroundColor: 'var(--og-color-surface)',
                        }}
                      >
                        <h4 style={{ margin: '0 0 10px', fontSize: '0.9rem', color: 'var(--og-color-text-secondary)' }}>
                          Ảnh thẻ chân dung (3x4)
                        </h4>
                        <img
                          src={portraitPreview}
                          alt="Ảnh thẻ chân dung"
                          style={{
                            width: '100%',
                            height: '160px',
                            objectFit: 'contain',
                            borderRadius: '8px',
                            backgroundColor: '#0f172a',
                          }}
                        />
                        <div style={{ marginTop: '10px' }}>
                          <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-muted)' }}>
                            Mẫu tham chiếu AI
                          </span>
                        </div>
                      </div>
                    )}

                    <div
                      style={{
                        border: '1px solid var(--og-color-border)',
                        borderRadius: '12px',
                        padding: '14px',
                        textAlign: 'center',
                        backgroundColor: 'var(--og-color-surface)',
                      }}
                    >
                      <h4 style={{ margin: '0 0 10px', fontSize: '0.9rem', color: 'var(--og-color-text-secondary)' }}>
                        Ảnh khuôn mặt trực tiếp
                      </h4>
                      {capturedSelfie ? (
                        <img
                          src={capturedSelfie}
                          alt="Chân dung selfie"
                          style={{
                            width: '100%',
                            height: '160px',
                            objectFit: 'cover',
                            borderRadius: '8px',
                            backgroundColor: '#0f172a',
                          }}
                        />
                      ) : (
                        <div
                          style={{
                            height: '160px',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            backgroundColor: 'var(--og-color-surface-subtle)',
                            color: 'var(--og-color-text-muted)',
                            borderRadius: '8px',
                          }}
                        >
                          Chưa có ảnh
                        </div>
                      )}
                      <div style={{ marginTop: '10px' }}>
                        {matchDetails?.isMatch ? (
                          <Badge variant="seller">Đạt chuẩn sinh trắc học</Badge>
                        ) : (
                          <Badge variant="warning">Chưa đạt chuẩn</Badge>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Nút hành động */}
                  <div style={{ display: 'flex', gap: '12px', justifyContent: 'center', flexWrap: 'wrap', marginTop: '8px' }}>
                    {matchDetails?.isMatch ? (
                      <>
                        <Button
                          variant="primary"
                          onClick={handleSubmitProfile}
                          isLoading={isLoading}
                          disabled={isLoading}
                        >
                          📤 Nộp hồ sơ eKYC để KTV duyệt
                        </Button>
                        <Button
                          variant="outline"
                          onClick={handleRetakeFace}
                          disabled={isLoading}
                        >
                          🔄 Quét lại khuôn mặt
                        </Button>
                        <Button
                          variant="outline"
                          onClick={() => setCurrentStep(3)}
                          disabled={isLoading}
                        >
                          Quay lại bước 3
                        </Button>
                      </>
                    ) : (
                      <>
                        <Button
                          variant="primary"
                          onClick={handleRetakeFace}
                        >
                          🔄 Chụp lại ảnh khuôn mặt
                        </Button>
                        <Button
                          variant="outline"
                          onClick={() => setCurrentStep(3)}
                        >
                          Quay lại bước 3
                        </Button>
                        <Button
                          variant="outline"
                          onClick={() => setCurrentStep(2)}
                        >
                          Quay lại tải CCCD
                        </Button>
                      </>
                    )}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* BƯỚC 5: Trạng thái chờ duyệt / Hoàn tất */}
          {currentStep === 5 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div
                style={{
                  backgroundColor: 'var(--og-color-surface-subtle)',
                  padding: '24px',
                  borderRadius: 'var(--og-radius-md)',
                  textAlign: 'center',
                }}
              >
                <div style={{ display: 'inline-flex', marginBottom: '12px' }}>
                  {status?.state === 'VERIFIED' || isSeller ? (
                    <Badge variant="seller">Đã xác minh thành công</Badge>
                  ) : (
                    <Badge variant="warning">Hồ sơ đang chờ KTV phê duyệt (PENDING)</Badge>
                  )}
                </div>

                <h3 style={{ margin: '0 0 8px' }}>
                  {status?.state === 'VERIFIED' || isSeller
                    ? 'Bạn đã là Người bán chính thức!'
                    : 'Hồ sơ đã được gửi đến ban kiểm duyệt KTV'}
                </h3>
                <p style={{ color: 'var(--og-color-text-secondary)', maxWidth: '540px', margin: '0 auto 16px', lineHeight: 1.6 }}>
                  {status?.state === 'VERIFIED' || isSeller
                    ? 'Tài khoản của bạn đã có đầy đủ quyền SELLER. Bạn có thể bắt đầu đăng bán sản phẩm ngay bây giờ.'
                    : 'Kỹ thuật viên O.G Shop sẽ đối chiếu các trường thông tin và sinh trắc học khuôn mặt của bạn trong vòng tối đa 24 giờ làm việc.'}
                </p>

                <div style={{ display: 'flex', justifyContent: 'center', gap: '12px', flexWrap: 'wrap' }}>
                  {status?.state === 'VERIFIED' || isSeller ? (
                    <>
                      <Button variant="primary" onClick={() => navigate('/seller/dashboard')}>
                        Vào Kênh Người Bán
                      </Button>
                      <Button
                        variant="gold"
                        onClick={handleStartReverification}
                        isLoading={isLoading}
                      >
                        Xác thực lại hồ sơ mới / Đổi CCCD
                      </Button>
                    </>
                  ) : null}
                  <Button variant={status?.state === 'VERIFIED' || isSeller ? "outline" : "primary"} onClick={() => navigate('/profile')}>
                    Về trang Hồ sơ cá nhân
                  </Button>
                </div>
              </div>
            </div>
          )}
          </>
        )}
        </Card>

        {/* Quick Face Auth Modal for Re-verification Guard */}
        <QuickFaceAuthModal
          isOpen={showReverifyQuickAuth}
          onClose={handleCloseReverifyQuickAuth}
          onSuccess={handleReverifyQuickAuthSuccess}
          onNeedEkyc={handleNeedEkycReverify}
          title="Xác thực khuôn mặt đối chiếu hồ sơ cũ"
          description="Vui lòng quét khuôn mặt để xác thực danh tính chủ sở hữu trước khi nộp hồ sơ CCCD mới."
        />
      </div>
    );
  }

  // 2. Chế độ MVP Default Flow (cho SellerVerificationPage MVP tests)
  return (
    <div style={{ maxWidth: '680px', margin: '30px auto' }}>
      <Card
        title="Kích hoạt tài khoản bán hàng (MVP)"
        subtitle="Thiết lập quyền Người bán cho tài khoản trên sàn giao dịch O.G Shop"
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Cảnh báo rõ ràng về chế độ MVP */}
          <Alert type="info">
            <strong>Chế độ MVP:</strong> Bước xác minh eKYC thật đang tạm thời được bỏ qua. Đây không phải xác minh danh tính production.
          </Alert>

          {error && <Alert type="danger">{error}</Alert>}
          {successMessage && <Alert type="success">{successMessage}</Alert>}

          {isSeller ? (
            <div
              style={{
                backgroundColor: 'var(--og-color-surface-subtle)',
                padding: '20px',
                borderRadius: 'var(--og-radius-md)',
                border: '1px solid var(--og-color-border)',
                display: 'flex',
                flexDirection: 'column',
                gap: '12px',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Badge variant="seller">Đã kích hoạt quyền Người bán</Badge>
              </div>
              <p style={{ margin: 0, color: 'var(--og-color-text-secondary)', fontSize: '0.95rem' }}>
                Tài khoản của bạn hiện đã có quyền <strong>SELLER</strong> từ hệ thống Backend. Bạn có thể sử dụng đầy đủ các tính năng đăng tin và quản lý sản phẩm ở bước tiếp theo.
              </p>
              <div style={{ marginTop: '8px' }}>
                <Button variant="primary" onClick={() => navigate('/profile')}>
                  Về trang Hồ sơ cá nhân
                </Button>
              </div>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <p style={{ margin: 0, color: 'var(--og-color-text-secondary)', lineHeight: 1.6 }}>
                Khi nhấn kích hoạt, Backend sẽ cấp role <strong>SELLER</strong> cho tài khoản của bạn thông qua chế độ <code>MVP_BYPASS</code>. Quyền Người bán sẽ cho phép bạn đăng tin bán đồ cũ, quản lý đơn hàng và thương lượng ở task Catalog tiếp theo.
              </p>

              <div style={{ display: 'flex', gap: '12px', marginTop: '12px' }}>
                <Button
                  variant="gold"
                  onClick={handleActivate}
                  isLoading={isLoading}
                  disabled={isLoading}
                >
                  Kích hoạt quyền Người bán
                </Button>
                <Button
                  variant="outline"
                  onClick={() => setShowFullEkyc(true)}
                  disabled={isLoading}
                >
                  Thực hiện eKYC thực tế (5 bước AI)
                </Button>
                <Button
                  variant="outline"
                  onClick={() => navigate('/profile')}
                  disabled={isLoading}
                >
                  Quay lại Hồ sơ
                </Button>
              </div>
            </div>
          )}
        </div>
      </Card>
    </div>
  );
};

export const IdentityVerificationPage = SellerVerificationPage;
