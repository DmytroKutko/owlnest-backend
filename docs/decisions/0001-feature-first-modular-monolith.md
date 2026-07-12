# ADR-0001: Feature-First Modular Monolith

**Status:** Accepted

**Date:** 2026-07-12

## Context

OwlNest Backend will grow across identity, profiles, social relationships, publishing, feeds, notifications, and messaging. A global package structure such as `controller`, `service`, and `repository` would mix unrelated features and make ownership unclear. Microservices or physical build modules would add deployment, transaction, configuration, and dependency-management complexity before the product requires it.

## Decision

Build one deployable Spring Boot application and one Gradle project as a modular monolith. Represent each business capability as a top-level feature package under `dev.dkutko.owlnest`, for example `post`, `socialgraph`, or `messaging`.

Each feature owns its API, application use cases, domain rules, and infrastructure. Other features may use only an explicit application-facing API or published events; they must not access another feature's repositories or internal persistence types.

“Multimodule” in this project means these logical feature modules. It does not currently mean Gradle subprojects.

## Consequences

- Features remain easy to find, understand, test, and change independently.
- The application keeps simple local startup, database transactions, and deployment.
- Module boundaries rely initially on package visibility, tests, and review.
- Spring Modulith verification can be added later without changing the feature-first structure.
- Physical Gradle modules should be reconsidered only for concrete needs such as independent dependency sets, stricter compile-time isolation, build performance, or separate team ownership.
