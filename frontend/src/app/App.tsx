import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import { AuthProvider } from '../features/auth';
import { Navbar } from './Navbar';
import { ProtectedRoute } from './ProtectedRoute';
import {
  HomePage,
  LoginPage,
  RegisterPage,
  ForgotPasswordPage,
  ProfilePage,
  SellerVerificationPage,
} from '../pages';
import './styles.css';

function NotFoundPage() {
  return (
    <div style={{ textAlign: 'center', padding: '80px 20px' }}>
      <h1 style={{ fontSize: '3rem', margin: '0 0 12px', color: 'var(--og-color-primary)' }}>404</h1>
      <p style={{ color: 'var(--og-color-text-secondary)', marginBottom: '24px' }}>
        Trang bạn tìm kiếm không tồn tại hoặc đã được di chuyển.
      </p>
      <Link to="/" className="og-button og-button--primary">
        Về Trang Chủ
      </Link>
    </div>
  );
}

export function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <div className="og-app-shell">
          <Navbar />
          <main className="og-main-content">
            <Routes>
              <Route path="/" element={<HomePage />} />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route path="/forgot-password" element={<ForgotPasswordPage />} />

              {/* Các route yêu cầu xác thực phiên đăng nhập */}
              <Route
                path="/profile"
                element={
                  <ProtectedRoute>
                    <ProfilePage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/seller-verification"
                element={
                  <ProtectedRoute>
                    <SellerVerificationPage />
                  </ProtectedRoute>
                }
              />

              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </main>
        </div>
      </AuthProvider>
    </BrowserRouter>
  );
}
