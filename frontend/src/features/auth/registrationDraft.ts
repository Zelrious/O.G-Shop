import type { RegistrationAddress, SellerRegistration } from './types';

export const emptyAddress = (): RegistrationAddress => ({ province: '', district: '', ward: '', detailAddress: '' });
export const emptySeller = (): SellerRegistration => ({ storeName: '', contactPhone: '', bankName: '', accountNumber: '', accountHolder: '', pickupAddress: emptyAddress(), returnAddress: emptyAddress() });
