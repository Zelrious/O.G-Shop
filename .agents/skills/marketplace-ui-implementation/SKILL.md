---
name: marketplace-ui-implementation
description: Design, implement, or review O.G Shop marketplace screens and reusable UI. Use for page layouts, design tokens, responsive behavior, component states, accessibility, and mock-backed frontend flows.
---

# Marketplace UI implementation

1. Read `../../../AGENTS.md`, `../../../docs/progress/STATUS.md`, `../../../docs/modules/INDEX.md`, the affected module documents, and `../frontend-feature-change/SKILL.md`.
2. Read [O.G Shop UI guidance](references/og-shop-ui-guidance.md) before proposing visual direction or editing a screen.
3. Map each proposed screen and action to an existing UC. Label anything not supported by repository requirements as `ASSUMPTION` or `PROPOSAL`; do not implement it as accepted behavior.
4. Inventory every route, role, permission, data dependency, and UI state: default, hover, focus-visible, active, disabled, loading, empty, error, unauthorized, and success where applicable.
5. Present the approval report required by `../../../.agents/rules/approval-and-reporting.md`. Include the screen batch, responsive plan, candidate visual tokens, files, mock/API boundary, accessibility checks, trade-offs, and expected output. Wait for approval before editing.
6. Keep page composition in `src/pages`, domain interaction in the owning feature, and domain-neutral primitives/tokens in `src/shared`. Use typed fixtures or a mock adapter when an approved API does not exist; never invent an API contract.
7. Build reusable primitives before repeating markup. Use semantic HTML, visible keyboard focus, descriptive labels, sufficient contrast, and touch targets suitable for mobile.
8. Preserve all meaningful states at mobile, tablet, and desktop widths. Handle long Vietnamese text, missing images, zero results, slow data, and permission failures without layout breakage.
9. Verify with the smallest meaningful set of frontend checks: lint, typecheck, focused tests, and build. Add interaction/accessibility tests only for behavior that can regress.
10. Report observed before/after behavior and limitations. Do not claim a workflow works when only its visual mock exists.

