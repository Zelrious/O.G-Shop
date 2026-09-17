# O.G Shop Agent Instructions

## Project identity

- Official name: Old but Gold.
- Short name: O.G Shop.
- Architecture: modular monolith.
- Backend: Java/Spring Boot/Maven.
- Frontend: React/TypeScript/Vite.
- Database: PostgreSQL/Flyway.

## Mandatory session start

Before proposing or changing anything:

1. Read `docs/progress/STATUS.md`.
2. Read `docs/modules/INDEX.md`.
3. Read the documentation of every affected module.
4. Read the active task file when one exists.
5. Inspect the current code, tests and Git diff. Do not rely on remembered state.
6. Read only the relevant rule and workflow files under `.agents/`.

## Approval gate

Before changing code, configuration, architecture, dependencies, database or documentation:

1. Report the proposed change, reason, affected scope, files, expected output, verification plan, advantages and trade-offs.
2. Explain the central code or design so the project owner can learn from it.
3. Wait for explicit approval.
4. Implement only the approved option.
5. If the approved option becomes infeasible or a materially different approach is needed, stop and request new approval.

Read `.agents/rules/approval-and-reporting.md` for the required report format.

## Evidence and accuracy

- Never invent file contents, command output, test results, requirements, APIs or completion state.
- Distinguish verified facts, assumptions and proposals.
- Treat code and tests as the current implementation; treat approved requirements and ADRs as intended behavior. Report conflicts instead of silently choosing one.
- Mark work complete only after relevant checks pass. Report partial or failed work exactly.
- Never claim a Git push, migration, build or test succeeded without observing its result.

## Scope and safety

- Work only inside this repository unless the owner explicitly approves another path.
- Do not import files from outside `D:\Khanh\Đồ án 1`.
- Do not automatically copy nearby project files into this repository.
- Never commit secrets, credentials, real KYC data or private evidence.
- Use mock or sandbox providers for payment, KYC and shipping.
- Preserve history for users, orders, payments, complaints and audit records.

## Architecture boundaries

- Organize Backend by business module, then by `api`, `application`, `domain` and `infrastructure` when those layers are needed.
- Controllers call application services, never repositories.
- A module must not access another module's repository directly.
- API models must not expose persistence entities.
- Frontend code is organized by user-facing feature; shared code must be domain-neutral.
- Flyway migrations are immutable after they have been shared.

## Verification

- Prefer the smallest meaningful test set that proves the change.
- Security-sensitive and transaction-sensitive changes require negative-path tests.
- Checkout, payment callbacks, release, refund and dispute resolution require idempotency/concurrency verification.
- Run formatting, linting, type checking and build checks appropriate to changed files.

## Documentation and handoff

At the start of approved work, create or update `docs/progress/active/<task-id>.md` to `IN_PROGRESS`.

Before ending:

1. Update affected module documents.
2. Record files changed and verification evidence in the task file.
3. Update `docs/progress/STATUS.md` and `BACKLOG.md`.
4. State completed, incomplete, blocked and next actions separately.
5. Provide a before/after report explaining how the code produced the observed result.

## Context efficiency

- Use `rg`/`rg --files` and scoped reads before opening large files.
- Load only the relevant module, rule, workflow and skill.
- Summarize stable facts in module docs rather than rediscovering them every session.
- Keep root instruction files concise; put conditional detail in `.agents/rules`, `.agents/workflows` or skill references.
