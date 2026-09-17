---
name: api-contract
description: Design or change an O.G Shop HTTP API contract across Spring Boot and React, including DTOs, status and error semantics, authorization, compatibility, client types, and contract tests.
---

# API contract

1. Follow `../../workflows/api-contract-change.md` and read `docs/api/API_CONVENTIONS.md`.
2. Identify consumers, ownership, request/response models, errors, authorization and idempotency.
3. State compatibility and migration impact in the approval report.
4. Update server contract, client types, tests and API docs together.
5. Never expose persistence entities or trust ownership/role supplied by the client.
