import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth, UserRole } from '../features/auth';

interface ProtectedRouteProps {
  children: React.ReactNode;
  allowedRoles?: UserRole[];
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, allowedRoles }) => {
  const { user, isAuthenticated, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: '80px 0' }}>
        <div className="og-spinner" style={{ width: '32px', height: '32px', borderColor: 'var(--og-color-primary)' }} />
      </div>
    );
  }

  if (!isAuthenticated || !user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (allowedRoles && allowedRoles.length > 0) {
    const hasRole = allowedRoles.some((role) => user.roles.includes(role));
    if (!hasRole) {
      if (user.roles.includes('ADMIN')) {
        return <Navigate to="/admin" replace />;
      }
      if (user.roles.includes('KTV')) {
        return <Navigate to="/ktv" replace />;
      }
      return <Navigate to="/" replace />;
    }
  }

  return <>{children}</>;
};
