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
