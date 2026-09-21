import { useState, ReactNode } from 'react';
import { MOCK_PRODUCTS, MOCK_VOUCHERS, MockProduct, MockVoucher } from '../data/mockData';
import { UserRole, DeviceMode, DemoContext } from './demoContext';

export function DemoProvider({ children }: { children: ReactNode }) {
  const [role, setRole] = useState<UserRole>('BUYER');
  const [deviceMode, setDeviceMode] = useState<DeviceMode>('desktop');
  const [activeScreenId, setActiveScreenId] = useState<string>('SCR-BUYER-HOME');

  const [cart, setCart] = useState<MockProduct[]>([MOCK_PRODUCTS[0]]);
  const [activeVoucher, setActiveVoucher] = useState<MockVoucher | null>(MOCK_VOUCHERS[0]);

  const [isExplorerOpen, setIsExplorerOpen] = useState(false);
  const [isOfferModalOpen, setIsOfferModalOpen] = useState(false);
  const [isVoucherModalOpen, setIsVoucherModalOpen] = useState(false);
  const [isCoInspectModalOpen, setIsCoInspectModalOpen] = useState(false);
  const [isConditionHelpModalOpen, setIsConditionHelpModalOpen] = useState(false);

  const [offerState, setOfferState] = useState<'pending' | 'accepted' | 'rejected' | 'countered'>('pending');
  const [currentOfferPrice, setCurrentOfferPrice] = useState(23800000);

  const [disputeVerdict, setDisputeVerdict] = useState<string | null>(null);
  const [isSellerKycApproved, setIsSellerKycApproved] = useState(true);

  const addToCart = (product: MockProduct) => {
    if (!cart.some(item => item.id === product.id)) {
      setCart([...cart, product]);
    }
  };

  const removeFromCart = (productId: string) => {
    setCart(cart.filter(item => item.id !== productId));
  };

  const applyVoucher = (voucher: MockVoucher | null) => {
    setActiveVoucher(voucher);
  };

  const acceptOffer = () => {
    setOfferState('accepted');
  };

  const rejectOffer = () => {
    setOfferState('rejected');
  };

  const makeCounterOffer = (price: number) => {
    setCurrentOfferPrice(price);
    setOfferState('countered');
  };

  return (
    <DemoContext.Provider
      value={{
        role,
        setRole,
        deviceMode,
        setDeviceMode,
        activeScreenId,
        setActiveScreenId,
        cart,
        addToCart,
        removeFromCart,
        activeVoucher,
        applyVoucher,
        isExplorerOpen,
        setIsExplorerOpen,
        isOfferModalOpen,
        setIsOfferModalOpen,
        isVoucherModalOpen,
        setIsVoucherModalOpen,
        isCoInspectModalOpen,
        setIsCoInspectModalOpen,
        isConditionHelpModalOpen,
        setIsConditionHelpModalOpen,
        offerState,
        currentOfferPrice,
        acceptOffer,
        rejectOffer,
        makeCounterOffer,
        disputeVerdict,
        setDisputeVerdict,
        isSellerKycApproved,
        setIsSellerKycApproved,
      }}
    >
      {children}
    </DemoContext.Provider>
  );
}
