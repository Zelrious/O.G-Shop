import React from 'react';
import { Outlet, Navigate } from 'react-router-dom';
import { useAuth } from '../../features/auth';
import { AppHeader } from './AppHeader';
import { MobileBottomNavigation } from './MobileBottomNavigation';

export const AppLayout: React.FC = () => {
  const { user, isAuthenticated, isLoading } = useAuth();

  // Admin and KTV roles do not operate in buyer marketplace interface
  if (!isLoading && isAuthenticated && user) {
    if (user.roles.includes('ADMIN')) {
      return <Navigate to="/admin" replace />;
    }
    if (user.roles.includes('KTV')) {
      return <Navigate to="/ktv" replace />;
    }
  }

  return (
    <div className="og-app-shell-root">
      <AppHeader />
      <main className="og-main-viewport">
        <Outlet />
      </main>
      <MobileBottomNavigation />
    </div>
  );
};
