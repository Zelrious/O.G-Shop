import React from 'react';
import { Outlet } from 'react-router-dom';
import { AppHeader } from './AppHeader';
import { MobileBottomNavigation } from './MobileBottomNavigation';

export const AppLayout: React.FC = () => {
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
