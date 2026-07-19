---
name: spring-testing-regression
description: Establish an OwlNest test baseline and run focused, PostgreSQL/Redis integration, contract, security, and regression evidence without hiding failures.
---

# Spring Testing and Regression

Use when implementing or verifying production behavior, migrations, APIs, security, Redis, or regressions.

## Baseline procedure

Before changes, record `git status --short --branch`, user-owned dirty files, and the smallest relevant baseline evidence. Do not run the full suite as a `FAST` baseline unless broad existing behavior is genuinely in doubt. The Gradle wrapper is authoritative. Gradle 9.5.1 requires a Java 17+ launcher and resolves the declared Java 21 toolchain; fix a stale Java 11 environment before attributing that startup failure to code.

Select commands by tier and risk; this is a menu, not a mandatory sequence:

```shell
./gradlew compileJava
./gradlew test
./gradlew build
git diff --check
python3 scripts/validate-agent-system.py
```

No formatter, linter, or static-analysis plugin is configured. Apply IDE Java formatting; do not claim a linter ran. The agent-system validator provides deterministic project and configuration checks but does not replace compilation or tests.

## Test strategy

- JUnit 5 plain unit tests for logic without Spring.
- Focused MVC/security/data slices when they cover the boundary; `@SpringBootTest` only for full integration.
- MockMvc runs the real MVC and security filters without a network port.
- PostgreSQL 18 and Redis 7 Testcontainers use `@ServiceConnection`; never use H2 for database behavior.
- Test behavior names such as `rejectsExpiredToken()` and `*Test`/`*Tests` suffixes.
- Cover acceptance criteria, negative authorization/ownership, validation, ProblemDetail codes, schema constraints, transaction rollback, races/idempotency, Redis TTL/failure, OpenAPI, and regression scope as applicable.

Record exact command, exit code, observed output, and whether a failure is pre-existing or introduced. A test that was not run is `NOT_RUN`, never PASS. Run focused tests during development and the broadest justified suite at most once after the final blocking correction. Share command evidence across reviewers while the code is unchanged. Advisory-only changes do not justify another test run; after a blocking fix, target only invalidated tests and finding IDs. Do not require both fresh API QA and fresh regression QA for the same unchanged evidence.
