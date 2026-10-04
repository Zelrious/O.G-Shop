import { ReactNode } from 'react';
import { useDemo } from '../../shared/context';
import { MobileBottomNav } from './MobileBottomNav';

interface DeviceViewportProps {
  children: ReactNode;
}

export function DeviceViewport({ children }: DeviceViewportProps) {
  const { deviceMode } = useDemo();

  if (deviceMode === 'mobile') {
    return (
      <div className="og-device-viewport-wrapper">
        <div className="og-device-frame-phone" role="region" aria-label="Simulated Mobile Device">
          {/* Top Notch & Camera */}
          <div className="og-phone-notch">
            <div className="og-phone-camera-dot" />
          </div>

          {/* Screen Content Viewport */}
          <div className="og-phone-screen-content">
            {children}
          </div>

          {/* Mobile Bottom Navigation Bar */}
          <MobileBottomNav />

          {/* Home Bar Indicator */}
          <div className="og-phone-home-indicator" />
        </div>
      </div>
    );
  }

  // Desktop Mode: Full width with desktop responsiveness
  return (
    <div className="og-desktop-viewport-wrapper" style={{ minHeight: 'calc(100vh - 120px)', padding: '24px 0 80px' }}>
      <div className="og-container">
        {children}
      </div>
    </div>
  );
}
