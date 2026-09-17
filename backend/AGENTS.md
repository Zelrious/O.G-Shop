# Backend-specific instructions

Read the root `AGENTS.md`, `.agents/rules/backend.md` and the affected module document before proposing Backend work.

- Use package-by-module.
- Add only layers that have a real responsibility.
- Controllers call application services and return DTOs.
- Domain code must not depend on Spring MVC or persistence details.
- Repositories belong to their owning module.
- Validate input at the API boundary and enforce authorization in the application layer.
- Use transactions for state-changing use cases.
- Add negative-path tests for ownership, role and state-transition checks.
- Do not add dependencies or change the public API without prior approval.
