import React, { useState, useEffect } from 'react';
import { UserPrincipal, LoginCredentials, RegisterPayload, UserRole } from './types';
import { mockAuthService } from './mockAuthService';
import { AuthContext } from './context';

const STORAGE_SESSION_USER = 'og_shop_session_user';
const STORAGE_REFRESH_TOKEN = 'og_shop_refresh_token';

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserPrincipal | null>(() => {
    try {
      const stored = localStorage.getItem(STORAGE_SESSION_USER);
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  });
  const [, setAccessToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  // Khôi phục phiên làm việc bất đồng bộ khi khởi tạo
  useEffect(() => {
    let active = true;

    const restore = async () => {
      const storedRefreshToken = localStorage.getItem(STORAGE_REFRESH_TOKEN);
      if (!storedRefreshToken) {
        if (active) setIsLoading(false);
        return;
      }
      try {
        const tokens = await mockAuthService.rotateToken(storedRefreshToken);
        if (active) {
          setAccessToken(tokens.accessToken);
          localStorage.setItem(STORAGE_REFRESH_TOKEN, tokens.refreshToken);
        }
      } catch {
        if (active) {
          localStorage.removeItem(STORAGE_SESSION_USER);
          localStorage.removeItem(STORAGE_REFRESH_TOKEN);
          setUser(null);
          setAccessToken(null);
        }
      } finally {
        if (active) {
          setIsLoading(false);
        }
      }
    };

    void restore();

    return () => {
      active = false;
    };
  }, []);

  const login = async (credentials: LoginCredentials) => {
    setIsLoading(true);
    try {
      const res = await mockAuthService.login(credentials);
      setUser(res.user);
      setAccessToken(res.tokens.accessToken);
      localStorage.setItem(STORAGE_SESSION_USER, JSON.stringify(res.user));
      localStorage.setItem(STORAGE_REFRESH_TOKEN, res.tokens.refreshToken);
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (payload: RegisterPayload) => {
    setIsLoading(true);
    try {
      const res = await mockAuthService.register(payload);
      setUser(res.user);
      setAccessToken(res.tokens.accessToken);
      localStorage.setItem(STORAGE_SESSION_USER, JSON.stringify(res.user));
      localStorage.setItem(STORAGE_REFRESH_TOKEN, res.tokens.refreshToken);
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async () => {
    const currentRefreshToken = localStorage.getItem(STORAGE_REFRESH_TOKEN);
    await mockAuthService.logout(currentRefreshToken || undefined);
    localStorage.removeItem(STORAGE_SESSION_USER);
    localStorage.removeItem(STORAGE_REFRESH_TOKEN);
    setUser(null);
    setAccessToken(null);
  };

  const refreshSession = async (): Promise<boolean> => {
    try {
      const storedRefreshToken = localStorage.getItem(STORAGE_REFRESH_TOKEN);
      if (!storedRefreshToken) return false;
      const tokens = await mockAuthService.rotateToken(storedRefreshToken);
      setAccessToken(tokens.accessToken);
      localStorage.setItem(STORAGE_REFRESH_TOKEN, tokens.refreshToken);
      return true;
    } catch {
      await logout();
      return false;
    }
  };

  const updateUserRoles = (newRoles: UserRole[]) => {
    if (user) {
      const updated = { ...user, roles: newRoles };
      setUser(updated);
      localStorage.setItem(STORAGE_SESSION_USER, JSON.stringify(updated));
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: Boolean(user),
        isLoading,
        login,
        register,
        logout,
        refreshSession,
        updateUserRoles,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
