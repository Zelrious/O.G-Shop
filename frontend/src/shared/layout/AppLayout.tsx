import React from 'react';
import { Outlet, Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../features/auth';
import { AppHeader } from './AppHeader';
import { AppFooter } from './AppFooter';
import { MobileBottomNavigation } from './MobileBottomNavigation';
import { AccountAccessBanner } from '../../features/account-management/AccountAccessBanner';

export const AppLayout: React.FC = () => {
  const { user, isAuthenticated, isLoading } = useAuth();
  const isInbox = useLocation().pathname === '/messages';

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
    <div className={'og-app-shell-root' + (isInbox ? ' og-app-shell-root--inbox' : '')}>
      <AppHeader />
      <AccountAccessBanner />
      <main className="og-main-viewport">
        <Outlet />
      </main>
      {!isInbox && <AppFooter />}
      <MobileBottomNavigation />
    </div>
  );
};
