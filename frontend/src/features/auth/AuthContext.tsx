import React, { useEffect, useRef, useState } from 'react';
import { authApi, SESSION_EXPIRED_EVENT } from './authApi';
import { AuthResponse, AuthChallenge, LoginCredentials, RegisterPayload, UserPrincipal } from './types';
import { AuthContext } from './context';

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserPrincipal | null>(null);
  const [expiresAt, setExpiresAt] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const sessionRevision = useRef(0);
  const lastActivityRef = useRef<number>(0);
  const hasInteractedRef = useRef<boolean>(false);

  const acceptSession = (session: AuthResponse) => {
    setUser(session.user);
    setExpiresAt(Date.now() + session.expiresIn * 1000);
  };

  useEffect(() => {
    lastActivityRef.current = Date.now();
    const handleInteraction = () => {
      hasInteractedRef.current = true;
      lastActivityRef.current = Date.now();
    };
    const events = ['mousedown', 'keydown', 'scroll', 'touchstart'] as const;
    events.forEach(evt => window.addEventListener(evt, handleInteraction, { passive: true }));
    return () => {
      events.forEach(evt => window.removeEventListener(evt, handleInteraction));
    };
  }, []);

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

    if (expiresAt === null) {
      return () => {
        window.removeEventListener(SESSION_EXPIRED_EVENT, expire);
      };
    }

    const remainingMs = expiresAt - Date.now();
    // Proactive sliding renewal: trigger when 25% of token lifetime remains (capped at 5m lead time)
    const renewLeadTime = Math.min(300_000, Math.max(1000, remainingMs * 0.25));
    const timeUntilRenew = Math.max(0, remainingMs - renewLeadTime);

    let renewTimer: number | undefined;
    if (timeUntilRenew > 0 && timeUntilRenew < remainingMs) {
      renewTimer = window.setTimeout(() => {
        const now = Date.now();
        const idleWindow = Math.min(30 * 60 * 1000, Math.max(remainingMs, 5000));
        const wasActive = hasInteractedRef.current && (now - lastActivityRef.current < idleWindow);
        if (wasActive) {
          hasInteractedRef.current = false;
          authApi.restoreSession()
            .then(acceptSession)
            .catch(() => {
              // If restore fails in background, continue countdown to hard expiration
            });
        }
      }, timeUntilRenew);
    }

    const expireTimer = window.setTimeout(expire, Math.max(0, remainingMs));

    return () => {
      window.removeEventListener(SESSION_EXPIRED_EVENT, expire);
      if (renewTimer !== undefined) window.clearTimeout(renewTimer);
      if (expireTimer !== undefined) window.clearTimeout(expireTimer);
    };
  }, [expiresAt]);

  useEffect(() => {
    const onFocusOrVisible = () => {
      hasInteractedRef.current = true;
      lastActivityRef.current = Date.now();
      if (user && expiresAt) {
        const remaining = expiresAt - Date.now();
        if (remaining < 300_000) {
          authApi.restoreSession().then(acceptSession).catch(() => {});
        }
      }
    };
    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') onFocusOrVisible();
    };
    window.addEventListener('visibilitychange', onVisibilityChange);
    window.addEventListener('focus', onFocusOrVisible);
    return () => {
      window.removeEventListener('visibilitychange', onVisibilityChange);
      window.removeEventListener('focus', onFocusOrVisible);
    };
  }, [user, expiresAt]);

  const login = async (credentials: LoginCredentials): Promise<UserPrincipal | AuthChallenge> => {
    sessionRevision.current++;
    setIsLoading(true);
    hasInteractedRef.current = true;
    lastActivityRef.current = Date.now();
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
    hasInteractedRef.current = true;
    lastActivityRef.current = Date.now();
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
