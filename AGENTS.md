# Repository Guidelines

- This repo contains simple, developer friendly code samples for Oracle AI Database.
- Always use "Oracle AI Database" instead of "Oracle Database". 
- Generate any diagrams at 800x600 resolution.

## Project Structure & Module Organization
- `pom.xml` is the root pom for a multi-module Maven build. subdirectories with a pom.xml are child modules of this build.
- All module README.md files should have a front matter with name, description, and tags fields
- When linking to any code within the repo, always use the main blob URL: https://github.com/anders-swanson/oracle-database-code-samples/blob/main
  - any link to a specific file comes after that. For example, this AGENTS.md file links to https://github.com/anders-swanson/oracle-database-code-samples/blob/main/AGENTS.md

## Coding Style & Naming Conventions
- Abide by the principles of DRY and KISS. Readability is paramount.
- Keep the coding style of sibling modules.
- Java uses `com.example` package prefixes.
- If the files in the working directory have changed since the last pass, re-read to capture relevant information

## Testing Guidelines
- Primary framework is JUnit 5 with `@Testcontainers`; write deterministic integration tests that provision Oracle AI Database Free containers and clean up via lifecycle hooks. The `testcontainers` module container idiomatic @Testcontainers tests for Java. New modules should follow these guidelines.
- Tests should be simple, running the main sample and doing any verifications.
- Name new tests `<Feature>Test` and colocate fixtures in `src/test/resources`. For TypeScript, follow Vitest's `*.test.ts` pattern; for Go, use `_test.go` files.
