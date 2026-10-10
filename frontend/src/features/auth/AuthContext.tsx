import React, { useEffect, useState } from 'react';
import { authApi, SESSION_EXPIRED_EVENT } from './authApi';
import { AuthResponse, LoginCredentials, RegisterPayload, UserPrincipal } from './types';
import { AuthContext } from './context';

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserPrincipal | null>(null);
  const [expiresAt, setExpiresAt] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const acceptSession = (session: AuthResponse) => {
    setUser(session.user);
    setExpiresAt(Date.now() + session.expiresIn * 1000);
  };

  useEffect(() => {
    let active = true;
    try { localStorage.removeItem('og_dev_user'); } catch { /* Storage may be unavailable. */ }
    authApi.restoreSession()
      .then((session) => {
        if (active) {
          setUser(session.user);
          setExpiresAt(Date.now() + session.expiresIn * 1000);
        }
      })
      .catch(() => {
        authApi.clearAccessToken();
        if (active) {
          setUser(null);
          setExpiresAt(null);
        }
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    const expire = () => {
      authApi.clearAccessToken();
      setUser(null);
      setExpiresAt(null);
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, expire);
    const timer = expiresAt === null ? undefined : window.setTimeout(expire, Math.max(0, expiresAt - Date.now()));
    return () => {
      window.removeEventListener(SESSION_EXPIRED_EVENT, expire);
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [expiresAt]);

  const login = async (credentials: LoginCredentials): Promise<UserPrincipal> => {
    setIsLoading(true);
    try {
      const session = await authApi.login(credentials);
      acceptSession(session);
      return session.user;
    } catch (error) {
      authApi.clearAccessToken();
      setUser(null);
      setExpiresAt(null);
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (payload: RegisterPayload) => {
    setIsLoading(true);
    try {
      acceptSession(await authApi.register(payload));
    } catch (error) {
      authApi.clearAccessToken();
      setUser(null);
      setExpiresAt(null);
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async () => {
    try { await authApi.logout(); } catch { /* Local logout completes if the server is unavailable. */ }
    authApi.clearAccessToken();
    try { localStorage.removeItem('og_dev_user'); } catch { /* Storage may be unavailable. */ }
    setUser(null);
    setExpiresAt(null);
  };

  const reloadCurrentUser = async () => {
    try {
      // Restore current roles within the original deadline, without extending it.
      acceptSession(await authApi.restoreSession());
    } catch (error) {
      authApi.clearAccessToken();
      setUser(null);
      setExpiresAt(null);
      throw error;
    }
  };

  return (
    <AuthContext.Provider value={{
      user,
      isAuthenticated: Boolean(user),
      isLoading,
      login,
      register,
      logout,
      reloadCurrentUser,
    }}>
      {children}
    </AuthContext.Provider>
  );
};
