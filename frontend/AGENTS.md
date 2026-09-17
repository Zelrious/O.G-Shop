# Frontend-specific instructions

Read the root `AGENTS.md`, `.agents/rules/frontend.md` and affected module documents before proposing Frontend work.

- Organize code by user-facing feature.
- Keep API access behind typed feature/shared clients.
- Do not duplicate server business rules in the UI.
- Handle loading, empty, error, disabled and success states explicitly.
- Use semantic HTML and preserve keyboard access.
- Never render secrets, private evidence URLs or authorization decisions from client-only state.
- Add component or behavior tests for meaningful interactions.
- Do not add packages or change design foundations without prior approval.
