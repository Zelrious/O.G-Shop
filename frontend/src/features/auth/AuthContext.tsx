import React, { useEffect, useRef, useState } from 'react';
import { authApi, SESSION_EXPIRED_EVENT } from './authApi';
import { AuthResponse, AuthChallenge, LoginCredentials, RegisterPayload, UserPrincipal } from './types';
import { AuthContext } from './context';

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserPrincipal | null>(null);
  const [expiresAt, setExpiresAt] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const sessionRevision = useRef(0);

  const acceptSession = (session: AuthResponse) => {
    setUser(session.user);
    setExpiresAt(Date.now() + session.expiresIn * 1000);
  };

  useEffect(() => {
    let active = true;
    const revision = sessionRevision.current;
    try { localStorage.removeItem('og_dev_user'); } catch { /* Storage may be unavailable. */ }
    authApi.restoreSession()
      .then((session) => {
        if (active && sessionRevision.current === revision) {
          setUser(session.user);
          setExpiresAt(Date.now() + session.expiresIn * 1000);
        }
      })
      .catch(error => {
        if (error && typeof error === 'object' && 'code' in error && error.code === 'SESSION_CHANGED') return;
        if (active && sessionRevision.current === revision) {
          authApi.clearAccessToken();
          setUser(null);
          setExpiresAt(null);
        }
      })
      .finally(() => {
        if (active && sessionRevision.current === revision) setIsLoading(false);
      });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    const expire = () => {
      sessionRevision.current++;
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

  const login = async (credentials: LoginCredentials): Promise<UserPrincipal | AuthChallenge> => {
    sessionRevision.current++;
    setIsLoading(true);
    try {
      const session = await authApi.login(credentials);
      if (!('accessToken' in session)) {
        setUser(null);
        setExpiresAt(null);
        return session;
      }
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

  const register = (payload: RegisterPayload) => authApi.register(payload);

  const verifyRegistration = async (challengeId: string, code: string) => {
    sessionRevision.current++;
    setIsLoading(true);
    try {
      const completed = await authApi.verifyRegistration(challengeId, code);
      acceptSession(completed);
      return completed;
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async () => {
    sessionRevision.current++;
    try { await authApi.logout(); } catch { /* Local logout completes if the server is unavailable. */ }
    authApi.clearAccessToken();
    try { localStorage.removeItem('og_dev_user'); } catch { /* Storage may be unavailable. */ }
    setUser(null);
    setExpiresAt(null);
  };

  const reloadCurrentUser = async () => {
    sessionRevision.current++;
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
      verifyRegistration,
      logout,
      reloadCurrentUser,
    }}>
      {children}
    </AuthContext.Provider>
  );
};
