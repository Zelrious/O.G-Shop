import type { ReactNode } from 'react';
export function PageHeader({ title, description, actions, eyebrow }: { title: string; description: string; actions?: ReactNode; eyebrow?: string }) {
  return <header className="og-page-header"><div>{eyebrow && <span className="og-eyebrow">{eyebrow}</span>}<h1>{title}</h1><p>{description}</p></div>{actions && <div className="og-page-header__actions">{actions}</div>}</header>;
}
