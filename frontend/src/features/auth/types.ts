export type UserRole = 'BUYER' | 'SELLER' | 'ADMIN' | 'KTV';

export interface UserPrincipal {
  userId: number;
  email: string;
  fullName: string;
  phoneNumber?: string;
  avatarUrl?: string;
  roles: UserRole[];
  createdAt: string;
  sellerApprovalRequired?: boolean;
}

export interface TokenPair {
  accessToken: string;
  expiresIn: number;
}

export interface LoginCredentials {
  email: string;
  password: string;
}

export interface RegisterPayload {
  fullName: string;
  email: string;
  phoneNumber?: string;
  password: string;
  confirmPassword?: string;
  accountType: 'BUYER' | 'SELLER';
  seller?: SellerRegistration;
  googleTicket?: string;
}

export interface RegistrationAddress {
  province: string;
  district: string;
  ward: string;
  detailAddress: string;
}

export interface SellerRegistration {
  storeName: string;
  contactPhone: string;
  bankName: string;
  accountNumber: string;
  accountHolder: string;
  pickupAddress: RegistrationAddress;
  returnAddress: RegistrationAddress;
}

export interface RegistrationChallenge {
  status: 'OTP_REQUIRED';
  challengeId: string;
  email: string;
  expiresAt: string;
  resendAt: string;
  attemptsRemaining: number;
}

export interface RegistrationCompletion extends AuthResponse {
  nextStep: 'PROFILE' | 'SELLER_VERIFICATION';
  awardedCoins: number;
}

export interface AuthChallenge extends Omit<RegistrationChallenge, 'status'> {
  status: 'OTP_REQUIRED' | 'RESET_READY';
  purpose: 'LOGIN_EMAIL_2FA' | 'PASSWORD_RESET' | 'GOOGLE_LINK';
}
export interface GoogleRegistration {
  status: 'REGISTRATION_REQUIRED';
  registrationTicket: string;
  email: string;
  fullName: string;
}
export type LoginResponse = AuthResponse | AuthChallenge;
export type GoogleResponse = LoginResponse | GoogleRegistration;

export interface AuthResponse {
  user: UserPrincipal;
  accessToken: string;
  expiresIn: number;
}

export interface AuthContextType {
  user: UserPrincipal | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (credentials: LoginCredentials) => Promise<UserPrincipal | AuthChallenge>;
  register: (payload: RegisterPayload) => Promise<RegistrationChallenge>;
  verifyRegistration: (challengeId: string, code: string) => Promise<RegistrationCompletion>;
  logout: () => Promise<void>;
  reloadCurrentUser: () => Promise<void>;
}
