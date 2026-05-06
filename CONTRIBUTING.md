# Contributing

This repository contains simple, developer-friendly samples for Oracle AI Database. A new module must easy to run, easy to test, and discoverable by the website catalog.

## Repo structure

- The root [`pom.xml`](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/pom.xml) is a multi-module Maven build for all Java samples.
- Most Java samples are standalone Maven modules at the repository root, such as `jdbc-hybrid-search`, `jdbc-json-oracle-text`, and `testcontainers`.
- Some sample groups are collection modules. For example, `database-per-service-example`, `json`, and `migrate-kafka-to-oracle` contain multiple, related child sample modules.
- Samples in other languages live in language-specific directories, like `python-oracle`, `golang`, `typescript`, and `sql`.
- The website lives in [`website/`](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/website/README.md). It builds a searchable catalog from sample `README.md` front matter.

## Contribute a new module

1. Create a new directory for the sample.

   Use a short, descriptive, lowercase directory name such as `jdbc-example-feature` or `spring-boot-example-feature`.

2. If you're adding a Java module, add it as a child module of the root [`pom.xml`](./pom.xml).

3. Include a descriptive README.md file that provides the following information:

#### README.md 

Every sample must have a `README.md` with front-matter, which is used by the website generator to render samples.

Front-matter must use this shape. The `blog_post` field may backlink to an external blog post, if one exists:

```md
---
name: your-new-module
description: Short, concrete summary of what the sample demonstrates with Oracle AI Database.
tags:
  - Java
  - JDBC
  - JSON
blog_post: ""
---
```

The README should also include:

- A clear `#` title.
- A short explanation of the Oracle AI Database feature.
- Prerequisites.
- Commands to run the sample and tests.
- Links to source files using the main blob URL format, for example `https://github.com/anders-swanson/oracle-database-code-samples/blob/main/your-new-module/src/main/java/com/example/YourSample.java`.

See [./database-per-service-example/README.md](./database-per-service-example/README.md) for an example.

## Generate & publish website data

After adding a new sample + README.md, run `npm ci && npm run build` from the website directory to regenerate the sample data file. On a push to main, the website will be published to https://anders-swanson.github.io/oracle-database-code-samples.

## Style

- Use "Oracle AI Database" in user-facing text.
- Keep samples DRY, simple, and readable.
- Follow the style of sibling modules before adding new abstractions.
- Use `com.example` package prefixes for Java.
- Any diagrams should be rendered at `800x600` resolution.
- Backlink to code in this repository using `https://github.com/anders-swanson/oracle-database-code-samples/blob/main` + the file path.
