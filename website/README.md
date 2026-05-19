# Website

This directory contains the Vue 3 + Vite website for browsing Oracle AI Database code samples.

The website is deployed to https://anders-swanson.github.io/oracle-database-code-samples/

## Sample Discovery

The website build automatically picks up new samples when they include a `README.md` file with Markdown front matter.

- `npm run build` runs `npm run generate` before the Vite build.
- The generator scans the repository for `README.md` files, excluding `.git`, `node_modules`, and `website`.
- A sample is included only when its `README.md` begins with front matter in the `--- ... ---` format.

At a minimum, sample README files should include:

```md
---
name: "Sample Name"
description: "Short summary of the sample."
tags:
  - Java
  - Vector Search
---
```

If a new sample directory has a `README.md` with valid front matter, the next website build will include it in the generated catalog.

## Post-Deploy Search Indexing Checklist

After deploying the generated site:

- Submit `https://anders-swanson.github.io/oracle-database-code-samples/sitemap.xml` in Google Search Console.
- Use URL Inspection for the homepage, `/features/vector-search/`, `/features/json/`, `/features/txeventq/`, `/languages/java/`, and a representative sample page.
- Request indexing for the highest-priority feature and sample URLs after confirming the live page is crawlable.
- Track impressions and queries for feature-intent searches such as `Oracle AI Database vector search sample`, `Oracle AI Database JSON duality views sample`, `Oracle AI Database TxEventQ sample`, and `Oracle AI Database Testcontainers`.
