# O.G Shop UI guidance

## Provenance and use

This guidance distills the two Mercari-oriented design-system files supplied by the project owner on 2026-09-18. They are reference material, not approved O.G Shop branding and not a complete screen design.

Reuse the design-system discipline and marketplace interaction principles. Do not copy the Mercari name, logo, text, screenshots, trademarked visual identity, or page composition. Treat exact colors from the source as candidates that require approval before becoming O.G Shop tokens.

## Product intent

O.G Shop should make second-hand transactions feel clear and trustworthy. A user must be able to understand the item condition, seller identity or verification state, price and offer state, transaction progress, and the next safe action without guessing.

Prefer a compact marketplace layout that remains calm and readable. Important status and risk information must use both text and visual treatment; color alone is insufficient.

## Token foundation

Define semantic tokens rather than scattering raw values through components:

- Typography: start with a system sans-serif stack. Candidate body size is 15px with approximately 21px line height; use a small, consistent scale around 14, 15, 16, and 20px and extend it only when a screen hierarchy requires it.
- Spacing: use a small scale such as 4, 8, 12, 16, 24, 36, 40, and 64px.
- Radius: keep controls and cards consistent, using a small pair such as 4px and 8px.
- Motion: use short transitions around 250ms, respect `prefers-reduced-motion`, and never make motion necessary to understand state.
- Candidate source palette: base `#000000`, muted surface `#222222`, strong surface `#303030`, primary text `#e5e5e5`, secondary text `#9c9c9c`, link `#30b2ff`, and accent `#da3e50`. This palette came from the Mercari reference. It must be evaluated for O.G Shop identity and contrast in the approval report rather than copied automatically.
- Add semantic roles for background, surface, border, text, muted text, primary action, link, success, warning, danger, focus, and disabled states. Component code consumes these roles, not brand-specific raw values.

## Required component behavior

For each interactive component, document and implement its anatomy, variants, size, keyboard behavior, pointer/touch behavior, and relevant states. At minimum consider:

- default, hover, focus-visible, active, disabled, loading, and error;
- long labels, long Vietnamese currency and address values, missing media, and overflow;
- empty collections, zero search results, slow data, failed data, and retry;
- role or ownership restrictions without leaking hidden actions;
- mobile touch targets and predictable tab order.

Product cards should expose image fallback, price, item condition, seller trust signal when available, and availability state. Forms must have persistent labels, field-level errors, a form-level failure message when needed, and submission feedback. Transaction screens must make the current state and next permitted action explicit.

## Accessibility baseline

- Target WCAG 2.2 AA.
- Use semantic landmarks, headings, lists, buttons, links, labels, and status regions.
- Support keyboard-only operation and visible `:focus-visible` treatment.
- Maintain AA contrast for text, controls, focus indicators, and status information.
- Give images useful alternative text or mark decorative images appropriately.
- Announce asynchronous validation, loading completion, and failures when the information affects the next action.

## Responsive baseline

Design mobile-first, then verify tablet and desktop composition. Breakpoints should follow content pressure rather than device names. Navigation, filters, product grids, chat, checkout summaries, and admin tables need explicit small-screen behavior. Avoid horizontal scrolling except for a deliberate data-view pattern with an accessible alternative.

## Trust-specific UI checks

- Verification badges must describe what was verified; avoid implying legal or production KYC when the project uses a mock.
- Payment, shipping, and identity-provider screens must identify simulated or sandbox behavior when relevant.
- Destructive or irreversible-looking actions require a clear consequence and confirmation pattern even when the backend is mocked.
- Complaint evidence and private conversation data must not appear in public views or reusable demo fixtures containing personal data.
- Never derive authorization from whether a button is hidden; the interface only reflects server-enforced permissions.

## Design review questions

Before approval, answer:

1. Which UC and role justify each screen and primary action?
2. Which content is verified repository fact, approved requirement, mock data, or proposal?
3. Can a keyboard and screen-reader user complete the intended UI interaction?
4. What happens with no data, invalid data, slow data, failure, and insufficient permission?
5. Does the visual treatment communicate trust without overstating verification or payment protection?
6. Which tokens or components are reusable, and which are intentionally feature-specific?

