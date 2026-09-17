---
name: backend-module-change
description: Plan, implement, or review Java and Spring Boot changes inside one O.G Shop backend module while preserving module boundaries, transactions, authorization, and API separation.
---

# Backend module change

1. Read `../../rules/backend.md`, security/testing rules and the affected module document.
2. Identify the use case owner and cross-module contracts before editing.
3. Apply the approval report in `../../rules/approval-and-reporting.md`.
4. Keep HTTP mapping in `api`, orchestration in `application`, rules in `domain`, and adapters in `infrastructure` as needed.
5. Run focused tests plus `mvnw.cmd verify`; update module/progress docs with evidence.
