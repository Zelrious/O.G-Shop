import React, { useEffect, useState } from 'react';
import { authApi } from './authApi';
import { LoginCredentials, RegisterPayload, UserPrincipal } from './types';
import { AuthContext } from './context';

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserPrincipal | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let active = true;
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

  const login = async (credentials: LoginCredentials) => {
    setIsLoading(true);
    try {
      setUser((await authApi.login(credentials)).user);
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
    await authApi.logout();
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
