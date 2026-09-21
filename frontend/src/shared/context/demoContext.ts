import { createContext } from 'react';
import { MockProduct, MockVoucher } from '../data/mockData';

export type UserRole = 'BUYER' | 'SELLER' | 'ADMIN';
export type DeviceMode = 'desktop' | 'mobile';

export interface DemoContextType {
  role: UserRole;
  setRole: (role: UserRole) => void;
  deviceMode: DeviceMode;
  setDeviceMode: (mode: DeviceMode) => void;
  activeScreenId: string;
  setActiveScreenId: (id: string) => void;
  
  // Cart & Commerce
  cart: MockProduct[];
  addToCart: (product: MockProduct) => void;
  removeFromCart: (productId: string) => void;
  activeVoucher: MockVoucher | null;
  applyVoucher: (voucher: MockVoucher | null) => void;

  // Modals & Navigation Drawers
  isExplorerOpen: boolean;
  setIsExplorerOpen: (open: boolean) => void;
  isOfferModalOpen: boolean;
  setIsOfferModalOpen: (open: boolean) => void;
  isVoucherModalOpen: boolean;
  setIsVoucherModalOpen: (open: boolean) => void;
  isCoInspectModalOpen: boolean;
  setIsCoInspectModalOpen: (open: boolean) => void;
  isConditionHelpModalOpen: boolean;
  setIsConditionHelpModalOpen: (open: boolean) => void;

  // Interactive Offer Bargaining State
  offerState: 'pending' | 'accepted' | 'rejected' | 'countered';
  currentOfferPrice: number;
  acceptOffer: () => void;
  rejectOffer: () => void;
  makeCounterOffer: (price: number) => void;

  // Dispute & Escrow Verdict State
  disputeVerdict: string | null;
  setDisputeVerdict: (verdict: string | null) => void;
  
  // KYC State for Seller
  isSellerKycApproved: boolean;
  setIsSellerKycApproved: (approved: boolean) => void;
}

export const DemoContext = createContext<DemoContextType | undefined>(undefined);
