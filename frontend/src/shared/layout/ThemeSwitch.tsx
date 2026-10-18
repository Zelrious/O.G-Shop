import { useContext } from 'react';
import { ThemeContext } from '../context/themeContext';
import { Icon } from '../components/Icon/Icon';

export function ThemeSwitch() {
  const theme = useContext(ThemeContext);
  if (!theme) return null;
  return <button type="button" className="og-utility-button" onClick={theme.toggleTheme} aria-label={theme.resolvedTheme === 'light' ? 'Chuyển sang chế độ tối' : 'Chuyển sang chế độ sáng'} title={theme.resolvedTheme === 'light' ? 'Chế độ tối' : 'Chế độ sáng'}><Icon name={theme.resolvedTheme === 'light' ? 'moon' : 'sun'} /></button>;
}
