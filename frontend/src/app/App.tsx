import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import { AuthProvider } from '../features/auth';
import { ThemeProvider, I18nProvider, DemoProvider } from '../shared/context';
import { ShowcaseApp } from '../features/showcase/ShowcaseApp';
import { ProtectedRoute } from './ProtectedRoute';
import {
  AppLayout,
  GuestLayout,
  SellerLayoutShell,
  AdminLayoutShell,
} from '../shared/layout';
import {
  HomePage,
  LoginPage,
  RegisterPage,
  ForgotPasswordPage,
  ProfilePage,
  SellerVerificationPage,
  MarketplacePage,
  ProductDetailPage,
  SellerListingsPage,
  CreateListingPage,
  EditListingPage,
  CheckoutPage,
  PaymentPage,
  VnPayReturnPage,
  BuyerOrdersPage,
  BuyerOrderDetailPage,
  SellerOrdersPage,
  SellerOrderDetailPage,
  ModerationPage,
} from '../pages';
import { SellerDashboardPage } from '../pages/seller/SellerDashboardPage';
import { AdminDashboardPage } from '../pages/admin/AdminDashboardPage';
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
      <ThemeProvider>
        <I18nProvider>
          <DemoProvider>
            <AuthProvider>
              <Routes>
                {/* 1. Guest / Auth Layout Shell */}
                <Route element={<GuestLayout />}>
                  <Route path="/login" element={<LoginPage />} />
                  <Route path="/register" element={<RegisterPage />} />
                  <Route path="/forgot-password" element={<ForgotPasswordPage />} />
                </Route>

                {/* 2. Main Public & Buyer Marketplace Layout Shell */}
                <Route element={<AppLayout />}>
                  <Route path="/" element={<HomePage />} />
                  <Route path="/marketplace" element={<MarketplacePage />} />
                  <Route path="/products/:productId" element={<ProductDetailPage />} />
                  <Route
                    path="/checkout"
                    element={
                      <ProtectedRoute>
                        <CheckoutPage />
                      </ProtectedRoute>
                    }
                  />
                  <Route
                    path="/checkout/payment"
                    element={
                      <ProtectedRoute>
                        <PaymentPage />
                      </ProtectedRoute>
                    }
                  />
                  <Route
                    path="/payment/vnpay-return"
                    element={
                      <ProtectedRoute>
                        <VnPayReturnPage />
                      </ProtectedRoute>
                    }
                  />

                  {/* Buyer Orders Management (OR-01, OR-02) */}
                  <Route
                    path="/orders"
                    element={
                      <ProtectedRoute>
                        <BuyerOrdersPage />
                      </ProtectedRoute>
                    }
                  />
                  <Route
                    path="/orders/:orderId"
                    element={
                      <ProtectedRoute>
                        <BuyerOrderDetailPage />
                      </ProtectedRoute>
                    }
                  />

                  {/* Profile & Identity Verification */}
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

                  {/* Showcase prototype retains access */}
                  <Route path="/showcase" element={<ShowcaseApp />} />
                  <Route path="/demo" element={<ShowcaseApp />} />
                </Route>

                {/* 3. Seller Center Layout Shell */}
                <Route
                  element={
                    <ProtectedRoute>
                      <SellerLayoutShell />
                    </ProtectedRoute>
                  }
                >
                  <Route path="/seller" element={<SellerDashboardPage />} />
                  <Route path="/seller/dashboard" element={<SellerDashboardPage />} />
                  <Route path="/seller/products" element={<SellerListingsPage />} />
                  <Route path="/seller/listings" element={<SellerListingsPage />} />
                  <Route path="/seller/products/new" element={<CreateListingPage />} />
                  <Route path="/seller/listings/new" element={<CreateListingPage />} />
                  <Route path="/seller/products/:productId/edit" element={<EditListingPage />} />
                  <Route path="/seller/listings/:productId/edit" element={<EditListingPage />} />
                  <Route path="/seller/orders" element={<SellerOrdersPage />} />
                  <Route path="/seller/orders/:orderId" element={<SellerOrderDetailPage />} />
                </Route>

                {/* 4. Admin Console & Dispute Arbitration Layout Shell */}
                <Route
                  element={
                    <ProtectedRoute>
                      <AdminLayoutShell />
                    </ProtectedRoute>
                  }
                >
                  <Route path="/admin" element={<AdminDashboardPage />} />
                  <Route path="/admin/users" element={<AdminDashboardPage />} />
                  <Route path="/admin/kyc" element={<AdminDashboardPage />} />
                  <Route path="/admin/moderation" element={<ModerationPage />} />
                  <Route path="/moderation" element={<ModerationPage />} />
                  <Route path="/admin/transactions" element={<AdminDashboardPage />} />
                  <Route path="/admin/complaints" element={<AdminDashboardPage />} />
                  <Route path="/admin/announcements" element={<AdminDashboardPage />} />
                </Route>

                {/* 404 Fallback */}
                <Route path="*" element={<NotFoundPage />} />
              </Routes>
            </AuthProvider>
          </DemoProvider>
        </I18nProvider>
      </ThemeProvider>
    </BrowserRouter>
  );
}
