import type { CSSProperties } from 'react';

const paths = {
  home: 'm3 10 9-7 9 7v10H3z M9 20v-7h6v7',
  search: 'M21 21l-5-5 M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
  message: 'M21 11a9 9 0 0 1-9 9H4l-2 2V11a9 9 0 0 1 19 0 M7 9h10 M7 13h7',
  bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9 M10 21h4',
  cart: 'M2 3h3l3 12h11l3-9H6 M10 20h.01 M18 20h.01',
  package: 'm3 7 9-5 9 5v10l-9 5-9-5z M3 7l9 5 9-5 M12 12v10 M7 4l10 5',
  user: 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M4 21v-2a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v2',
  plus: 'M12 5v14 M5 12h14',
  close: 'm6 6 12 12 M6 18 18 6',
  minus: 'M5 12h14',
  expand: 'M8 3H3v5 M16 3h5v5 M3 16v5h5 M21 16v5h-5',
  sun: 'M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M12 2v2 M12 20v2 M2 12h2 M20 12h2 M5 5l1.5 1.5 M17.5 17.5 19 19 M5 19l1.5-1.5 M17.5 6.5 19 5',
  moon: 'M21 13A9 9 0 0 1 11 3a9 9 0 1 0 10 10',
  shield: 'm12 22-6-4a9 9 0 0 1-3-7V5l9-3 9 3v6a9 9 0 0 1-3 7z m-4-10 3 3 5-5',
  back: 'm14 5-7 7 7 7 M7 12h14',
  arrow: 'M3 12h18 m-7-7 7 7-7 7',
  refresh: 'M21 3v6h-6 M3 21v-6h6 M5 7a8 8 0 0 1 13-2l3 4 M3 15l3 4a8 8 0 0 0 13-2',
  image: 'M3 3h18v18H3z m0 14 5-5 4 4 3-3 6 6 M9 7h.01',
  send: 'm22 2-7 20-4-9-9-4z M22 2 11 13',
  lock: 'M5 10h14v11H5z M8 10V6a4 4 0 0 1 8 0v4 M12 14v3',
  tag: 'M3 3h9l9 9-9 9-9-9z M7 7h.01',
  clock: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0 M12 7v5l3 2',
  credit: 'M2 4h20v16H2z M2 9h20 M6 15h4',
  check: 'M21 11v1a9 9 0 1 1-5-8 m-8 8 4 4 10-10',
  alert: 'm12 3 10 18H2z M12 9v5 M12 17h.01',
  sliders: 'M4 3v4 M4 11v10 M12 3v10 M12 17v4 M20 3v4 M20 11v10 M1 7h6 M9 13h6 M17 7h6',
  grid: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  book: 'M12 5c-3-2-6-2-10-1v16c4-1 7-1 10 1 3-2 6-2 10-1V4c-4-1-7-1-10 1z M12 5v16',
  truck: 'M2 5h12v12H2z M14 9h4l4 5v3h-8 M8 18a2 2 0 1 1-4 0 2 2 0 0 1 4 0 M20 18a2 2 0 1 1-4 0 2 2 0 0 1 4 0',
  store: 'M3 9l1-5h16l1 5 M3 9a3 3 0 0 0 6 0 3 3 0 0 0 6 0 3 3 0 0 0 6 0 M4 12v9h16v-9 M9 21v-7h6v7',
  file: 'M14 2H4v20h16V8z M14 2v6h6 M8 13h8 M8 17h6',
  eye: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12 M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
  phone: 'm4 3 4 1 1 5-3 2a14 14 0 0 0 7 7l2-3 5 1 1 4a2 2 0 0 1-2 2A19 19 0 0 1 2 5a2 2 0 0 1 2-2',
  mail: 'M2 4h20v16H2z m0 0 10 8 10-8',
  pin: 'M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 0 1 16 0 M15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
  star: 'm12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9z',
  info: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0 M12 10v7 M12 7h.01',
} as const;

export type IconName = keyof typeof paths;

export function Icon({ name, size = 20, className, style }: { name: IconName; size?: number; className?: string; style?: CSSProperties }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false" className={className} style={style}><path d={paths[name]} /></svg>;
}
