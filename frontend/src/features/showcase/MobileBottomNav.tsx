import { useDemo, useI18n } from '../../shared/context';

export function MobileBottomNav() {
  const { role, activeScreenId, setActiveScreenId, cart } = useDemo();
  const { language } = useI18n();

  if (role === 'BUYER') {
    return (
      <nav className="og-mobile-bottom-nav" aria-label="Buyer Mobile Navigation">
        <button
          type="button"
          className={`og-bottom-tab ${activeScreenId === 'SCR-BUYER-HOME' ? 'og-bottom-tab--active' : ''}`}
          onClick={() => setActiveScreenId('SCR-BUYER-HOME')}
        >
          <span style={{ fontSize: '18px' }}>🏠</span>
          <span>{language === 'vi' ? 'Trang Chủ' : 'Home'}</span>
        </button>

        <button
          type="button"
          className={`og-bottom-tab ${activeScreenId === 'SCR-BUYER-SEARCH' || activeScreenId === 'SCR-BUYER-SEMANTIC' ? 'og-bottom-tab--active' : ''}`}
          onClick={() => setActiveScreenId('SCR-BUYER-SEARCH')}
        >
          <span style={{ fontSize: '18px' }}>🔍</span>
          <span>{language === 'vi' ? 'Khám Phá' : 'Search'}</span>
        </button>

        <button
          type="button"
          className={`og-bottom-tab ${activeScreenId === 'SCR-CHAT-PRODUCT' || activeScreenId === 'SCR-CHAT-OFFER' ? 'og-bottom-tab--active' : ''}`}
          onClick={() => setActiveScreenId('SCR-CHAT-PRODUCT')}
        >
          <span style={{ fontSize: '18px' }}>💬</span>
          <span>{language === 'vi' ? 'Tin Nhắn' : 'Chat'}</span>
          <span className="og-bottom-tab__badge">1</span>
        </button>

        <button
          type="button"
          className={`og-bottom-tab ${activeScreenId === 'SCR-BUYER-CART' || activeScreenId === 'SCR-BUYER-CHECKOUT' ? 'og-bottom-tab--active' : ''}`}
          onClick={() => setActiveScreenId('SCR-BUYER-CART')}
        >
          <span style={{ fontSize: '18px' }}>🛒</span>
          <span>{language === 'vi' ? 'Giỏ Hàng' : 'Cart'}</span>
          {cart.length > 0 && <span className="og-bottom-tab__badge">{cart.length}</span>}
        </button>

        <button
          type="button"
          className={`og-bottom-tab ${activeScreenId === 'SCR-USER-01' || activeScreenId === 'SCR-REWARDS-WALLET' ? 'og-bottom-tab--active' : ''}`}
          onClick={() => setActiveScreenId('SCR-USER-01')}
        >
          <span style={{ fontSize: '18px' }}>👤</span>
          <span>{language === 'vi' ? 'Tài Khoản' : 'Account'}</span>
        </button>
      </nav>
    );
  }

  if (role === 'SELLER') {
    return (
      <nav className="og-mobile-bottom-nav" aria-label="Seller Mobile Navigation">
        <button
          type="button"
          className={`og-bottom-tab ${activeScreenId === 'SCR-SELLER-LISTINGS' ? 'og-bottom-tab--active' : ''}`}
          onClick={() => setActiveScreenId('SCR-SELLER-LISTINGS')}
        >
          <span style={{ fontSize: '18px' }}>📦</span>
          <span>{language === 'vi' ? 'Tin Đăng' : 'Listings'}</span>
        </button>

        <button
          type="button"
          className={`og-bottom-tab ${activeScreenId === 'SCR-SELLER-ORDERS' ? 'og-bottom-tab--active' : ''}`}
          onClick={() => setActiveScreenId('SCR-SELLER-ORDERS')}
        >
          <span style={{ fontSize: '18px' }}>📋</span>
          <span>{language === 'vi' ? 'Đơn Bán' : 'Orders'}</span>
          <span className="og-bottom-tab__badge">2</span>
        </button>

        <button
          type="button"
          className={`og-bottom-tab ${activeScreenId === 'SCR-SELLER-CREATE' || activeScreenId === 'SCR-SELLER-AI-CHECK' ? 'og-bottom-tab--active' : ''}`}
          onClick={() => setActiveScreenId('SCR-SELLER-CREATE')}
          style={{ transform: 'translateY(-6px)' }}
        >
          <span style={{ fontSize: '24px', background: 'var(--og-color-accent-gold)', color: '#fff', borderRadius: '50%', width: '38px', height: '38px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>+</span>
          <span>{language === 'vi' ? 'Đăng Bán' : 'Sell'}</span>
        </button>

        <button
          type="button"
          className={`og-bottom-tab ${activeScreenId === 'SCR-REWARDS-WALLET' ? 'og-bottom-tab--active' : ''}`}
          onClick={() => setActiveScreenId('SCR-REWARDS-WALLET')}
        >
          <span style={{ fontSize: '18px' }}>💰</span>
          <span>{language === 'vi' ? 'Ví Tiền' : 'Wallet'}</span>
        </button>

        <button
          type="button"
          className={`og-bottom-tab ${activeScreenId === 'SCR-SELLER-BANK' || activeScreenId === 'SCR-KYC-01' ? 'og-bottom-tab--active' : ''}`}
          onClick={() => setActiveScreenId('SCR-SELLER-BANK')}
        >
          <span style={{ fontSize: '18px' }}>⚙️</span>
          <span>{language === 'vi' ? 'Cài Đặt' : 'Settings'}</span>
        </button>
      </nav>
    );
  }

  // ADMIN Role
  return (
    <nav className="og-mobile-bottom-nav" aria-label="Admin Mobile Navigation">
      <button
        type="button"
        className={`og-bottom-tab ${activeScreenId === 'SCR-ADMIN-STATS' ? 'og-bottom-tab--active' : ''}`}
        onClick={() => setActiveScreenId('SCR-ADMIN-STATS')}
      >
        <span style={{ fontSize: '18px' }}>📊</span>
        <span>{language === 'vi' ? 'Thống Kê' : 'Metrics'}</span>
      </button>

      <button
        type="button"
        className={`og-bottom-tab ${activeScreenId === 'SCR-ADMIN-DISPUTE-ROOM' || activeScreenId === 'SCR-ADMIN-DISPUTE-DECIDE' ? 'og-bottom-tab--active' : ''}`}
        onClick={() => setActiveScreenId('SCR-ADMIN-DISPUTE-ROOM')}
      >
        <span style={{ fontSize: '18px' }}>⚖️</span>
        <span>{language === 'vi' ? 'Tranh Chấp' : 'Disputes'}</span>
        <span className="og-bottom-tab__badge">6</span>
      </button>

      <button
        type="button"
        className={`og-bottom-tab ${activeScreenId === 'SCR-ADMIN-MODERATION' ? 'og-bottom-tab--active' : ''}`}
        onClick={() => setActiveScreenId('SCR-ADMIN-MODERATION')}
      >
        <span style={{ fontSize: '18px' }}>🛡️</span>
        <span>{language === 'vi' ? 'Duyệt Tin' : 'Moderate'}</span>
        <span className="og-bottom-tab__badge">8</span>
      </button>

      <button
        type="button"
        className={`og-bottom-tab ${activeScreenId === 'SCR-ADMIN-ESCROW' ? 'og-bottom-tab--active' : ''}`}
        onClick={() => setActiveScreenId('SCR-ADMIN-ESCROW')}
      >
        <span style={{ fontSize: '18px' }}>🏦</span>
        <span>{language === 'vi' ? 'Escrow' : 'Escrow'}</span>
      </button>

      <button
        type="button"
        className={`og-bottom-tab ${activeScreenId === 'SCR-ADMIN-USERS' || activeScreenId === 'SCR-ADMIN-PENALTY' ? 'og-bottom-tab--active' : ''}`}
        onClick={() => setActiveScreenId('SCR-ADMIN-USERS')}
      >
        <span style={{ fontSize: '18px' }}>👥</span>
        <span>{language === 'vi' ? 'Người Dùng' : 'Users'}</span>
      </button>
    </nav>
  );
}
