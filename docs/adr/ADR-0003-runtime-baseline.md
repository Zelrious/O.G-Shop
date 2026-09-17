# ADR-0003: Runtime Baseline

- Status: Accepted
- Date: 2026-09-17

## Decision

- Compile Backend for Java 21 using Spring Boot 3.5.x.
- Use Maven Wrapper.
- Use React 19, TypeScript, Vite and npm for Frontend.
- Use PostgreSQL and Flyway for persistent data.

## Rationale

Java 21 and Spring Boot 3.5 provide a mature teaching and library ecosystem while remaining compatible with the installed JDK. npm is available on the development machine and requires no additional package manager.

## Trade-offs

This does not adopt the newest Spring Boot major version. npm can use more disk and time than pnpm on large dependency graphs.
