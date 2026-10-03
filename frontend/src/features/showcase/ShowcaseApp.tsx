import { useDemo, useI18n } from '../../shared/context';
import { ShowcaseControlBar } from './ShowcaseControlBar';
import { ScreenExplorerDrawer } from './ScreenExplorerDrawer';
import { ALL_SCREENS } from './screensData';
import { DeviceViewport } from './DeviceViewport';
import { Module1AccountScreens } from './screens/Module1AccountScreens';
import { Module2CommerceScreens } from './screens/Module2CommerceScreens';
import { Module3ShippingScreens } from './screens/Module3ShippingScreens';
import { Module4DisputeScreens } from './screens/Module4DisputeScreens';

export function ShowcaseApp() {
  const { activeScreenId, setIsExplorerOpen } = useDemo();
  const { language } = useI18n();

  const currentScreen = ALL_SCREENS.find(s => s.id === activeScreenId) || ALL_SCREENS[0];

  const renderActiveScreen = () => {
    switch (currentScreen.module) {
      case 1:
        return <Module1AccountScreens screenId={activeScreenId} />;
      case 2:
        return <Module2CommerceScreens screenId={activeScreenId} />;
      case 3:
        return <Module3ShippingScreens screenId={activeScreenId} />;
      case 4:
        return <Module4DisputeScreens screenId={activeScreenId} />;
      default:
        return <Module2CommerceScreens screenId={activeScreenId} />;
    }
  };

  return (
    <div className="og-showcase-root" style={{ minHeight: '100vh', background: 'var(--og-color-bg)' }}>
      {/* Top sticky control bar */}
      <ShowcaseControlBar screenTitle={currentScreen.name[language]} />

      {/* Viewport (Full Desktop or Simulated Mobile Frame) */}
      <DeviceViewport>
        {renderActiveScreen()}
      </DeviceViewport>

      {/* Floating Launcher to open 44 screens drawer */}
      <button
        type="button"
        className="og-floating-explorer-btn"
        onClick={() => setIsExplorerOpen(true)}
        aria-label="Mở danh sách 44 màn hình"
      >
        <span style={{ fontSize: '18px' }}>🗺️</span>
        <span>{language === 'vi' ? '44 Màn Hình' : '44 Screens'}</span>
      </button>

      {/* Drawer */}
      <ScreenExplorerDrawer />
    </div>
  );
}
