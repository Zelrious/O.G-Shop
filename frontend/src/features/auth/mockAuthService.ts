import { UserPrincipal, TokenPair, LoginCredentials, RegisterPayload, AuthResponse } from './types';
import { hashPassword } from './hashUtils';

const STORAGE_USERS_KEY = 'og_shop_mock_users';
const STORAGE_ACTIVE_REFRESH_TOKENS = 'og_shop_active_refresh_tokens';

interface StoredUser extends UserPrincipal {
  passwordHash: string;
}

// Khởi tạo người dùng mặc định cho môi trường demo
async function getInitialUsers(): Promise<StoredUser[]> {
  const existing = localStorage.getItem(STORAGE_USERS_KEY);
  if (existing) {
    try {
      return JSON.parse(existing);
    } catch {
      // Fallback nếu parse lỗi
    }
  }

  const defaultBuyerPasswordHash = await hashPassword('Password123@');
  const initialUsers: StoredUser[] = [
    {
      userId: 1,
      email: 'buyer@ogshop.vn',
      fullName: 'Nguyễn Văn Mua',
      phoneNumber: '0901234567',
      passwordHash: defaultBuyerPasswordHash,
      roles: ['BUYER'],
      createdAt: new Date().toISOString(),
    },
    {
      userId: 2,
      email: 'seller@ogshop.vn',
      fullName: 'Trần Thị Bán',
      phoneNumber: '0912345678',
      passwordHash: defaultBuyerPasswordHash,
      roles: ['BUYER', 'SELLER'],
      createdAt: new Date().toISOString(),
    },
  ];

  localStorage.setItem(STORAGE_USERS_KEY, JSON.stringify(initialUsers));
  return initialUsers;
}

function getActiveRefreshTokens(): Record<string, number> {
  const data = localStorage.getItem(STORAGE_ACTIVE_REFRESH_TOKENS);
  return data ? JSON.parse(data) : {};
}

function saveActiveRefreshTokens(tokens: Record<string, number>) {
  localStorage.setItem(STORAGE_ACTIVE_REFRESH_TOKENS, JSON.stringify(tokens));
}

// Tạo chuỗi token ngẫu nhiên mô phỏng JWT
function generateMockJwt(userId: number, email: string): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = btoa(
    JSON.stringify({
      sub: email,
      uid: userId,
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 900, // 15 phút
    })
  );
  const signature = btoa(`sig_${Date.now()}_${Math.random()}`);
  return `${header}.${payload}.${signature}`;
}

function generateMockRefreshToken(): string {
  return `rt_${Date.now()}_${Math.random().toString(36).substring(2, 15)}`;
}

export const mockAuthService = {
  async login(credentials: LoginCredentials): Promise<AuthResponse> {
    await new Promise((r) => setTimeout(r, 600)); // Giả lập độ trễ mạng
    const users = await getInitialUsers();
    const target = users.find((u) => u.email.toLowerCase() === credentials.email.toLowerCase());

    if (!target) {
      throw new Error('Email hoặc mật khẩu không chính xác.');
    }

    const inputHash = await hashPassword(credentials.password);
    if (target.passwordHash !== inputHash) {
      throw new Error('Email hoặc mật khẩu không chính xác.');
    }

    // Sinh cặp token
    const accessToken = generateMockJwt(target.userId, target.email);
    const refreshToken = generateMockRefreshToken();

    // Lưu trữ Refresh token còn hiệu lực
    const tokens = getActiveRefreshTokens();
    tokens[refreshToken] = target.userId;
    saveActiveRefreshTokens(tokens);

    const safeUser: UserPrincipal = {
      userId: target.userId,
      email: target.email,
      fullName: target.fullName,
      phoneNumber: target.phoneNumber,
      roles: target.roles,
      createdAt: target.createdAt,
    };
    return {
      user: safeUser,
      tokens: {
        accessToken,
        refreshToken,
        expiresIn: 900,
      },
    };
  },

  async register(payload: RegisterPayload): Promise<AuthResponse> {
    await new Promise((r) => setTimeout(r, 800));
    const users = await getInitialUsers();

    if (users.some((u) => u.email.toLowerCase() === payload.email.toLowerCase())) {
      throw new Error('Email này đã được sử dụng. Vui lòng chọn email khác.');
    }

    const passwordHash = await hashPassword(payload.password);
    const newUser: StoredUser = {
      userId: users.length + 1,
      email: payload.email.toLowerCase(),
      fullName: payload.fullName,
      phoneNumber: payload.phoneNumber,
      passwordHash,
      roles: ['BUYER'], // Mặc định là BUYER theo quy ước O.G Shop
      createdAt: new Date().toISOString(),
    };

    users.push(newUser);
    localStorage.setItem(STORAGE_USERS_KEY, JSON.stringify(users));

    const accessToken = generateMockJwt(newUser.userId, newUser.email);
    const refreshToken = generateMockRefreshToken();
    const activeTokens = getActiveRefreshTokens();
    activeTokens[refreshToken] = newUser.userId;
    saveActiveRefreshTokens(activeTokens);

    const safeUser: UserPrincipal = {
      userId: newUser.userId,
      email: newUser.email,
      fullName: newUser.fullName,
      phoneNumber: newUser.phoneNumber,
      roles: newUser.roles,
      createdAt: newUser.createdAt,
    };
    return {
      user: safeUser,
      tokens: {
        accessToken,
        refreshToken,
        expiresIn: 900,
      },
    };
  },

  // Triển khai Refresh Token Rotation chuẩn Spring Security
  async rotateToken(oldRefreshToken: string): Promise<TokenPair> {
    await new Promise((r) => setTimeout(r, 300));
    const activeTokens = getActiveRefreshTokens();
    const userId = activeTokens[oldRefreshToken];

    if (!userId) {
      throw new Error('Refresh token không hợp lệ hoặc đã bị thu hồi.');
    }

    // 1. Hủy bỏ refresh token cũ ngay lập tức (Single-use)
    delete activeTokens[oldRefreshToken];

    // 2. Cấp refresh token mới
    const users = await getInitialUsers();
    const user = users.find((u) => u.userId === userId);
    if (!user) {
      saveActiveRefreshTokens(activeTokens);
      throw new Error('Người dùng không tồn tại.');
    }

    const newAccessToken = generateMockJwt(user.userId, user.email);
    const newRefreshToken = generateMockRefreshToken();
    activeTokens[newRefreshToken] = user.userId;
    saveActiveRefreshTokens(activeTokens);

    return {
      accessToken: newAccessToken,
      refreshToken: newRefreshToken,
      expiresIn: 900,
    };
  },

  async logout(refreshToken?: string): Promise<void> {
    if (refreshToken) {
      const activeTokens = getActiveRefreshTokens();
      delete activeTokens[refreshToken];
      saveActiveRefreshTokens(activeTokens);
    }
  },

  async requestPasswordReset(email: string): Promise<boolean> {
    await new Promise((r) => setTimeout(r, 500));
    const users = await getInitialUsers();
    const target = users.find((u) => u.email.toLowerCase() === email.toLowerCase());
    if (!target) {
      // Vì lý do bảo mật, vẫn trả về true để không lộ user tồn tại hay không
      return true;
    }
    return true;
  },

  async resetPassword(email: string, otp: string, newPassword: string): Promise<void> {
    await new Promise((r) => setTimeout(r, 600));
    if (otp !== '123456') {
      throw new Error('Mã xác thực OTP không chính xác hoặc đã hết hạn (Mã demo: 123456).');
    }
    const users = await getInitialUsers();
    const target = users.find((u) => u.email.toLowerCase() === email.toLowerCase());
    if (!target) {
      throw new Error('Không tìm thấy tài khoản cần đặt lại mật khẩu.');
    }

    target.passwordHash = await hashPassword(newPassword);
    localStorage.setItem(STORAGE_USERS_KEY, JSON.stringify(users));
  },
};
