import { useDemo, useTheme, useI18n } from '../../shared/context';

interface ShowcaseControlBarProps {
  screenTitle: string;
}

export function ShowcaseControlBar({ screenTitle }: ShowcaseControlBarProps) {
  const {
    role,
    setRole,
    deviceMode,
    setDeviceMode,
    activeScreenId,
    setIsExplorerOpen
  } = useDemo();
  const { resolvedTheme, toggleTheme } = useTheme();
  const { language, toggleLanguage, t } = useI18n();

  return (
    <header className="og-showcase-control-bar" role="banner" aria-label="Demo Controls">
      {/* Current Screen Indicator & Explorer Button */}
      <div className="og-showcase-control-bar__group">
        <button
          type="button"
          className="og-button og-button--primary og-button--sm"
          onClick={() => setIsExplorerOpen(true)}
          title="Mở danh sách 44 màn hình"
        >
          <span aria-hidden="true">🗺️</span>
          <span>{t('nav.showcase')}</span>
        </button>

        <span className="og-showcase-screen-tag" title="Mã màn hình hiện tại">
          <code>{activeScreenId}</code>: {screenTitle}
        </span>
      </div>

      {/* Persona Role Switcher */}
      <div className="og-showcase-control-bar__group" aria-label="Role Switcher">
        <span style={{ fontSize: '12px', fontWeight: 600, color: 'var(--og-color-text-muted)' }}>
          {language === 'vi' ? 'Vai trò:' : 'Role:'}
        </span>
        <button
          type="button"
          className={`og-role-pill ${role === 'BUYER' ? 'og-role-pill--active' : ''}`}
          onClick={() => setRole('BUYER')}
        >
          🛒 {t('role.buyer')}
        </button>
        <button
          type="button"
          className={`og-role-pill og-role-pill--seller ${role === 'SELLER' ? 'og-role-pill--active' : ''}`}
          onClick={() => setRole('SELLER')}
        >
          🏷️ {t('role.seller')}
        </button>
        <button
          type="button"
          className={`og-role-pill og-role-pill--admin ${role === 'ADMIN' ? 'og-role-pill--active' : ''}`}
          onClick={() => setRole('ADMIN')}
        >
          🛡️ {t('role.admin')}
        </button>
      </div>

      {/* Device Viewport, Theme & Language Switchers */}
      <div className="og-showcase-control-bar__group">
        {/* Device Switcher */}
        <div style={{ display: 'inline-flex', border: '1px solid var(--og-color-border)', borderRadius: 'var(--og-radius-md)', overflow: 'hidden' }}>
          <button
            type="button"
            className={`og-button og-button--sm ${deviceMode === 'desktop' ? 'og-button--primary' : 'og-button--ghost'}`}
            style={{ borderRadius: 0, padding: '4px 10px', fontSize: '12px' }}
            onClick={() => setDeviceMode('desktop')}
            title="Xem toàn màn hình Desktop"
          >
            🖥️ Desktop
          </button>
          <button
            type="button"
            className={`og-button og-button--sm ${deviceMode === 'mobile' ? 'og-button--primary' : 'og-button--ghost'}`}
            style={{ borderRadius: 0, padding: '4px 10px', fontSize: '12px' }}
            onClick={() => setDeviceMode('mobile')}
            title="Mô phỏng màn hình điện thoại Mobile"
          >
            📱 Mobile
          </button>
        </div>

        {/* Theme Switcher */}
        <button
          type="button"
          className="og-button og-button--outline og-button--sm"
          onClick={toggleTheme}
          title="Đổi Dark/Light Mode"
          style={{ padding: '4px 10px', fontSize: '12px' }}
        >
          {resolvedTheme === 'dark' ? '🌙 Dark' : '☀️ Light'}
        </button>

        {/* Language Switcher */}
        <button
          type="button"
          className="og-button og-button--outline og-button--sm"
          onClick={toggleLanguage}
          title="Đổi Tiếng Việt / English"
          style={{ padding: '4px 10px', fontSize: '12px', fontWeight: 700 }}
        >
          {language === 'vi' ? '🇻🇳 VI' : '🇬🇧 EN'}
        </button>
      </div>
    </header>
  );
}
