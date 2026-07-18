# Project Context

## Product Vision

OwlNest Backend supports a Flutter social-media application. The initial product includes:

- registration, login, and user profiles;
- posts with optional ordered image/video references, likes, reposts, bookmarks, and a planned comments slice;
- friendships and one-way follows;
- a chronological feed;
- later, persisted text messaging with live WebSocket delivery;
- later, Firebase Cloud Messaging push notifications.

The post contract can already persist untrusted absolute HTTPS image/video references for Flutter rendering. OwlNest does not upload, fetch, proxy, or inspect those URLs yet; a later media-storage slice may add user avatars and managed post media backed by Cloudflare R2. Recommendation algorithms, microservices, and message brokers remain outside the initial scope unless a concrete requirement justifies them.

## Architectural Preference

Use a modular monolith with a feature-first structure. “Module” currently means a logical business module represented by a root feature package inside one Spring Boot application and one Gradle project. Do not organize the repository as global `controller`, `service`, and `repository` layers. Do not create separate Gradle subprojects until physical build isolation provides a concrete benefit.

## Contributor Learning Context

The primary contributor is learning Spring Boot and backend development after 7+ years of native Android development with Java/Kotlin and roughly 3 years of Flutter development. Explanations may assume strong client-development and general programming knowledge, but must make backend, database, security, and Spring lifecycle concepts explicit.

## Collaboration Rules

1. Agree on the architecture and feature boundary before implementing a non-trivial slice.
2. Deliver one small, end-to-end feature at a time; avoid speculative infrastructure.
3. Explain why the chosen approach fits this project and compare meaningful alternatives.
4. For each newly introduced Spring or Jakarta annotation, explain:
   - what it declares;
   - which framework component processes it;
   - when it takes effect in startup or request/transaction lifecycle;
   - where it should and should not be used;
   - important pitfalls and testing implications.
5. Keep documentation synchronized with implementation and clearly label unapproved proposals.
6. Before implementing a feature, document the planned files, dependency use, alternatives, tradeoffs, new annotations, and verification strategy so the work can be reproduced manually.
