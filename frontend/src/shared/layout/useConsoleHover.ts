import { useEffect, useRef, useState } from 'react';

export function useConsoleHover() {
  const [isHovered, setIsHovered] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const clear = () => { if (timer.current) { clearTimeout(timer.current); timer.current = null; } };
  useEffect(() => () => { if (timer.current) clearTimeout(timer.current); }, []);
  return { isHovered, onMouseEnter: () => { clear(); setIsHovered(true); }, onMouseLeave: () => { clear(); timer.current = setTimeout(() => { setIsHovered(false); timer.current = null; }, 320); } };
}
