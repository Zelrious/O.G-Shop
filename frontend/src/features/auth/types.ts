export type UserRole = 'BUYER' | 'SELLER' | 'ADMIN' | 'KTV';

export interface UserPrincipal {
  userId: number;
  email: string;
  fullName: string;
  phoneNumber?: string;
  avatarUrl?: string;
  roles: UserRole[];
  createdAt: string;
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
  phoneNumber: string;
  password: string;
  confirmPassword?: string;
}

export interface AuthResponse {
  user: UserPrincipal;
  accessToken: string;
  expiresIn: number;
}

export interface AuthContextType {
  user: UserPrincipal | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (credentials: LoginCredentials) => Promise<UserPrincipal>;
  register: (payload: RegisterPayload) => Promise<void>;
  logout: () => Promise<void>;
  reloadCurrentUser: () => Promise<void>;
}
