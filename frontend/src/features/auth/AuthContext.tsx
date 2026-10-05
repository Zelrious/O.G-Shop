import React, { useEffect, useState } from 'react';
import { authApi } from './authApi';
import { LoginCredentials, RegisterPayload, UserPrincipal } from './types';
import { AuthContext } from './context';

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserPrincipal | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let active = true;
    // Retire the old demo cache. Browser storage never establishes a session.
    try { localStorage.removeItem('og_dev_user'); } catch { /* Storage may be unavailable. */ }
    authApi.refresh()
      .then((session) => {
        if (active) setUser(session.user);
      })
      .catch(() => {
        authApi.clearAccessToken();
        if (active) setUser(null);
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const login = async (credentials: LoginCredentials): Promise<UserPrincipal> => {
    setIsLoading(true);
    try {
      const resp = await authApi.login(credentials);
      setUser(resp.user);
      return resp.user;
    } catch (error) {
      authApi.clearAccessToken();
      setUser(null);
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (payload: RegisterPayload) => {
    setIsLoading(true);
    try {
      setUser((await authApi.register(payload)).user);
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async () => {
    try {
      await authApi.logout();
    } catch {
      // Local logout still completes when the server is unavailable.
    }
    authApi.clearAccessToken();
    try { localStorage.removeItem('og_dev_user'); } catch { /* Storage may be unavailable. */ }
    setUser(null);
  };

  const refreshSession = async (): Promise<boolean> => {
    try {
      setUser((await authApi.refresh()).user);
      return true;
    } catch {
      authApi.clearAccessToken();
      setUser(null);
      return false;
    }
  };

  const reloadCurrentUser = async () => {
    setUser(await authApi.currentUser());
  };

  return (
    <AuthContext.Provider value={{
      user,
      isAuthenticated: Boolean(user),
      isLoading,
      login,
      register,
      logout,
      refreshSession,
      reloadCurrentUser,
    }}>
      {children}
    </AuthContext.Provider>
  );
};
