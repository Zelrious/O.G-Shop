import { useState } from 'react';
import { useDemo, useI18n } from '../../shared/context';
import { ALL_SCREENS, ScreenDefinition } from './screensData';

export function ScreenExplorerDrawer() {
  const { isExplorerOpen, setIsExplorerOpen, activeScreenId, setActiveScreenId, setRole } = useDemo();
  const { language } = useI18n();
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedModule, setSelectedModule] = useState<number | 'ALL'>('ALL');

  if (!isExplorerOpen) return null;

  const filteredScreens = ALL_SCREENS.filter(screen => {
    const matchesModule = selectedModule === 'ALL' || screen.module === selectedModule;
    const q = searchQuery.toLowerCase();
    const matchesQuery =
      screen.code.toLowerCase().includes(q) ||
      screen.name.vi.toLowerCase().includes(q) ||
      screen.name.en.toLowerCase().includes(q) ||
      screen.description.vi.toLowerCase().includes(q);
    return matchesModule && matchesQuery;
  });

  const handleSelectScreen = (screen: ScreenDefinition) => {
    setActiveScreenId(screen.id);
    setRole(screen.role);
    setIsExplorerOpen(false);
  };

  return (
    <div className="og-explorer-overlay" onClick={() => setIsExplorerOpen(false)}>
      <aside
        className="og-explorer-drawer"
        onClick={e => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Screen Explorer"
      >
        {/* Header */}
        <div className="og-explorer-drawer__header">
          <div>
            <h2 style={{ margin: 0, fontSize: '18px', color: 'var(--og-color-primary)', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span>🗺️</span>
              <span>{language === 'vi' ? 'Bản Đồ 44 Màn Hình O.G Shop' : '44 Screens Directory'}</span>
            </h2>
            <p style={{ margin: '4px 0 0', fontSize: '12px', color: 'var(--og-color-text-secondary)' }}>
              {language === 'vi' ? 'Chọn màn hình để xem và tương tác trực tiếp' : 'Click any screen to view and test in viewport'}
            </p>
          </div>
          <button
            type="button"
            className="og-button og-button--ghost og-button--sm"
            onClick={() => setIsExplorerOpen(false)}
            aria-label="Đóng"
            style={{ fontSize: '18px', padding: '4px 8px' }}
          >
            ✕
          </button>
        </div>

        {/* Filter & Search Bar */}
        <div style={{ padding: '12px 24px', borderBottom: '1px solid var(--og-color-border)', background: 'var(--og-color-surface)' }}>
          <input
            type="search"
            className="og-input"
            placeholder={language === 'vi' ? '🔍 Tìm kiếm theo mã (SCR-...) hoặc tên màn hình...' : '🔍 Search by code or screen title...'}
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            style={{ width: '100%', marginBottom: '10px', fontSize: '13px' }}
          />

          <div style={{ display: 'flex', gap: '6px', overflowX: 'auto', paddingBottom: '4px' }}>
            <button
              type="button"
              className={`og-button og-button--sm ${selectedModule === 'ALL' ? 'og-button--primary' : 'og-button--ghost'}`}
              onClick={() => setSelectedModule('ALL')}
              style={{ fontSize: '11px', padding: '3px 8px', whiteSpace: 'nowrap' }}
            >
              {language === 'vi' ? 'Tất cả (44)' : 'All (44)'}
            </button>
            <button
              type="button"
              className={`og-button og-button--sm ${selectedModule === 1 ? 'og-button--primary' : 'og-button--ghost'}`}
              onClick={() => setSelectedModule(1)}
              style={{ fontSize: '11px', padding: '3px 8px', whiteSpace: 'nowrap' }}
            >
              M1: Auth (14)
            </button>
            <button
              type="button"
              className={`og-button og-button--sm ${selectedModule === 2 ? 'og-button--primary' : 'og-button--ghost'}`}
              onClick={() => setSelectedModule(2)}
              style={{ fontSize: '11px', padding: '3px 8px', whiteSpace: 'nowrap' }}
            >
              M2: Giao Dịch (19)
            </button>
            <button
              type="button"
              className={`og-button og-button--sm ${selectedModule === 3 ? 'og-button--primary' : 'og-button--ghost'}`}
              onClick={() => setSelectedModule(3)}
              style={{ fontSize: '11px', padding: '3px 8px', whiteSpace: 'nowrap' }}
            >
              M3: Vận Chuyển (6)
            </button>
            <button
              type="button"
              className={`og-button og-button--sm ${selectedModule === 4 ? 'og-button--primary' : 'og-button--ghost'}`}
              onClick={() => setSelectedModule(4)}
              style={{ fontSize: '11px', padding: '3px 8px', whiteSpace: 'nowrap' }}
            >
              M4: Tranh Chấp (5)
            </button>
          </div>
        </div>

        {/* Screen List */}
        <div className="og-explorer-drawer__body">
          {filteredScreens.length === 0 ? (
            <p style={{ textAlign: 'center', color: 'var(--og-color-text-muted)', padding: '40px 0' }}>
              {language === 'vi' ? 'Không tìm thấy màn hình phù hợp.' : 'No matching screens found.'}
            </p>
          ) : (
            filteredScreens.map(screen => {
              const isActive = activeScreenId === screen.id;
              return (
                <button
                  key={screen.id}
                  type="button"
                  className={`og-screen-item-btn ${isActive ? 'og-screen-item-btn--active' : ''}`}
                  onClick={() => handleSelectScreen(screen)}
                >
                  <div style={{ flex: 1, paddingRight: '8px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                      <span className="og-screen-code">{screen.code}</span>
                      <span
                        style={{
                          fontSize: '10px',
                          fontWeight: 700,
                          padding: '1px 6px',
                          borderRadius: '4px',
                          background: screen.role === 'BUYER' ? '#e8f3ed' : screen.role === 'SELLER' ? '#fbf4e8' : '#f3e8ff',
                          color: screen.role === 'BUYER' ? '#1d4d38' : screen.role === 'SELLER' ? '#8e6216' : '#6b21a8'
                        }}
                      >
                        {screen.role}
                      </span>
                    </div>
                    <div style={{ fontSize: '14px', fontWeight: isActive ? 700 : 600, color: 'var(--og-color-text-primary)' }}>
                      {screen.name[language]}
                    </div>
                    <div style={{ fontSize: '12px', color: 'var(--og-color-text-secondary)', marginTop: '2px', lineHeight: 1.3 }}>
                      {screen.description[language]}
                    </div>
                  </div>
                  <div style={{ fontSize: '18px', color: isActive ? 'var(--og-color-primary)' : 'var(--og-color-text-muted)' }}>
                    {isActive ? '✓' : '→'}
                  </div>
                </button>
              );
            })
          )}
        </div>
      </aside>
    </div>
  );
}
