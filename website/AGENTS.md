# Website Guidelines

This directory contains the Vue 3 + Vite website for browsing Oracle AI Database code samples.

## Structure
- `src/pages/` contains route-level views such as the catalog and sample detail pages.
- `src/components/` contains reusable UI components.
- `src/lib/catalog.ts` contains filter, summary, related-sample, and query-string logic.
- `src/data/samples.json` is generated data. Do not hand-edit it unless a task explicitly requires that.
- `scripts/generate-samples.mjs` and `scripts/sample-catalog-utils.mjs` are the source of truth for catalog generation.
- `tests/` contains Vitest coverage for catalog logic and core UI rendering.

## Commands
- Run `npm test` for the website test suite.
- Run `npm run generate` after changing sample-catalog generation rules.
- Run `npm run build` to verify the generated catalog and production bundle together.

## Catalog Data Rules
- Sample records use `features`, `language`, and `tags`.
- Keep `tags` as README-derived metadata, but strip redundant values during generation when required by current conventions.
- Language normalization is handled in `sample-catalog-utils.mjs`. The fallback label is `Script`.
- Feature filters come from generated `features`. If a feature should disappear from the website, remove it from generation or normalize it out before writing `samples.json`.

## UI Expectations
- Preserve the current catalog flow: hero, then the filter/results layout. Do not reintroduce the removed feature spotlight section unless explicitly requested.
- Filter changes should not force the page back to the top. Query-only updates on the catalog route must preserve scroll position.
- On the sample detail page, action buttons should stay grouped on the left and visually separated from the feature chips above or below them.

## Editing Notes
- Prefer fixing data issues in the generator instead of patching generated JSON by hand.
- Avoid editing `dist/` or `node_modules/`.
- Keep styles in `src/styles.css` unless there is already a clear local component style pattern to follow.
