# ADR-0001: Use a Modular Monolith

- Status: Accepted
- Date: 2026-09-17

## Context

Core flows require consistent changes across Order, Payment, Shipment and Complaint. The project is a course assignment with limited operational capacity.

## Decision

Use one Spring Boot deployment separated into explicit business modules.

## Consequences

- Simpler transaction and local development model.
- Module boundaries must be enforced in code review and tests.
- Independent scaling and deployment are deferred.
