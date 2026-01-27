# Repository Guidelines

## Project Structure & Module Organization
- `pom.xml` orchestrates a multi-module Maven build; Java samples live under directories such as `ai-vector-search`, `oracle-database-kafka-apis`, `spring-*`, and `txeventq-*`, each with its own `pom.xml` and `src` tree.
- Cross-language samples sit in sibling folders (`golang`, `python-oracle`, `typescript`, `sql`), while shared Docker and infrastructure assets appear in `oracle-ai-database-docker-compose`, `truecache-free`, and `testcontainers`.
- Keep documentation alongside modules (for example `spring-boot-jms-example/README.md`) and place new samples under a top-level folder with a distinct name.

## Build, Test, and Development Commands
- Run all Java modules with `mvn verify`; target a single module using `mvn -pl <module> -am verify` from the repo root. Tests rely on Testcontainers, so ensure Docker is running.
- Module-specific entry points: `mvn test` inside a module for fast feedback; integration suites often live under `src/test/java` and connect to Oracle Database Free via Testcontainers.
- Other stacks: `npm install && npm test` under `typescript/` (Vitest), and `go test ./...` under `golang/`. Refer to module READMEs for Docker compose or additional setup.

## Coding Style & Naming Conventions
- Java uses Spring Boot 4 defaults: Java 21, 4-space indentation, PascalCase classes, camelCase methods, and package prefixes `com.example` per sample. Favor Spring configuration via `application.yaml` in `src/main/resources`.
- Align SQL scripts in `sql/` and module `src/main/resources` using uppercase keywords and snake_case table names. Keep TypeScript in ES module format with lint-friendly imports, and organize Go code under package-level directories mirroring sample names.

## Testing Guidelines
- Primary framework is JUnit 5 with `@Testcontainers`; write deterministic integration tests that provision Oracle Database Free containers and clean up via lifecycle hooks.
- Name new tests `<Feature>Test` or `<Feature>IT` and colocate fixtures in `src/test/resources`. For TypeScript, follow Vitest's `*.test.ts` pattern; for Go, use `_test.go` files.
- Aim to keep tests self-sufficient: avoid shared databases, prefer module-scoped containers or reusable fixtures in `testcontainers/src/test/java/com/example/reusable`.

## Commit & Pull Request Guidelines
- Follow the repo's terse, capitalized subject line pattern (e.g., "Update dependencies", "Spring Boot 4"); use imperative mood and keep to ~50 characters.
- Mention the primary module(s) touched in either the subject or first sentence. Reference issue IDs or Oracle support tickets where applicable.
- PRs should summarize intent, list major changes, note required environment variables or Docker services, and include screenshots or CLI output when relevant.
