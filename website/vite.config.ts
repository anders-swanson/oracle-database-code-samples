import fs from 'node:fs';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

const distRoot = new URL('./dist/', import.meta.url);
const siteMetadataPath = new URL('./src/data/siteMetadata.json', import.meta.url);
const catalogIndexPath = new URL('./src/data/catalog-index.json', import.meta.url);
const languagePagesPath = new URL('./src/data/language-pages.json', import.meta.url);
const sampleDetailsRoot = new URL('./src/data/sample-details/', import.meta.url);
const featureDetailsPath = new URL('./src/data/featureDetails.json', import.meta.url);
const patternDefinitionsPath = new URL('./src/data/patternDefinitions.json', import.meta.url);
const patternMappingsPath = new URL('./src/data/patternMappings.json', import.meta.url);

interface PackedCatalogIndex {
  i: [string, string, string, number[], number, number, 0 | 1][];
}

interface SiteMetadata {
  siteUrl: string;
}

interface SampleDetail {
  id: string;
  sourceUpdatedAt: string;
}

interface LandingPage {
  slug: string;
  updatedAt: string;
}

interface PatternMappingData {
  patterns: {
    id: string;
    sampleIds: string[];
  }[];
}

interface SitemapRoute {
  pathname: string;
  lastmod: string;
}

function readJson<T>(url: URL) {
  return JSON.parse(fs.readFileSync(url, 'utf8')) as T;
}

const siteMetadata = readJson<SiteMetadata>(siteMetadataPath);

function readCatalogIndex() {
  return readJson<PackedCatalogIndex>(catalogIndexPath);
}

function readLanguagePages() {
  return readJson<LandingPage[]>(languagePagesPath);
}

function readPatternMappings() {
  return readJson<PatternMappingData>(patternMappingsPath);
}

function readSampleDetail(id: string) {
  return readJson<SampleDetail>(new URL(`${id}.json`, sampleDetailsRoot));
}

function buildSamplePath(id: string) {
  return `/samples/${id}/`;
}

function buildPatternPath(slug: string) {
  return `/patterns/${slug}/`;
}

function buildLanguagePath(slug: string) {
  return `/languages/${slug}/`;
}

function buildCanonicalUrl(pathname: string) {
  const normalized = pathname.startsWith('/') ? pathname.slice(1) : pathname;
  return new URL(normalized, siteMetadata.siteUrl).toString();
}

function maxLastmod(values: string[]) {
  return values.sort().at(-1) ?? '1970-01-01T00:00:00.000Z';
}

function fileLastmod(url: URL) {
  return fs.statSync(url).mtime.toISOString();
}

function sampleLastmod(sampleLastmods: Map<string, string>, sampleId: string) {
  const lastmod = sampleLastmods.get(sampleId);

  if (!lastmod) {
    throw new Error(`Pattern route references missing sample detail ${sampleId}`);
  }

  return lastmod;
}

function buildRoutes() {
  const catalog = readCatalogIndex();
  const sampleLastmods = new Map(catalog.i.map(([id]) => [id, readSampleDetail(id).sourceUpdatedAt]));
  const sampleRoutes = Array.from(sampleLastmods.entries()).map(([id, lastmod]) => ({
    pathname: buildSamplePath(id),
    lastmod
  }));
  const patternRoutes = readPatternMappings().patterns.map((pattern) => ({
    pathname: buildPatternPath(pattern.id),
    lastmod: maxLastmod([
      fileLastmod(patternDefinitionsPath),
      ...pattern.sampleIds.map((sampleId) => sampleLastmod(sampleLastmods, sampleId))
    ])
  }));
  const languageRoutes = readLanguagePages().map((page) => ({
    pathname: buildLanguagePath(page.slug),
    lastmod: page.updatedAt
  }));
  const contentLastmod = maxLastmod([
    fileLastmod(featureDetailsPath),
    fileLastmod(patternDefinitionsPath),
    ...sampleRoutes.map((route) => route.lastmod),
    ...patternRoutes.map((route) => route.lastmod),
    ...languageRoutes.map((route) => route.lastmod)
  ]);

  return [
    { pathname: '/', lastmod: contentLastmod },
    { pathname: '/patterns/', lastmod: contentLastmod },
    { pathname: '/feature-map/', lastmod: contentLastmod },
    ...patternRoutes,
    ...languageRoutes,
    ...sampleRoutes
  ] satisfies SitemapRoute[];
}

function escapeXml(value: string) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&apos;');
}

function writeCrawlerFiles() {
  const sitemapBody = buildRoutes()
    .map(
      ({ pathname, lastmod }) =>
        `  <url><loc>${escapeXml(buildCanonicalUrl(pathname))}</loc><lastmod>${escapeXml(lastmod)}</lastmod></url>`
    )
    .join('\n');

  fs.writeFileSync(
    new URL('sitemap.xml', distRoot),
    `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${sitemapBody}\n</urlset>\n`
  );
  fs.writeFileSync(new URL('robots.txt', distRoot), `User-agent: *\nAllow: /\nSitemap: ${siteMetadata.siteUrl}sitemap.xml\n`);
}

export default defineConfig(({ command }) => ({
  base: command === 'serve' ? '/' : '/oracle-database-code-samples/',
  plugins: [vue()],
  ssgOptions: {
    dirStyle: 'nested',
    includedRoutes() {
      return buildRoutes().map((route) => route.pathname);
    },
    onFinished() {
      writeCrawlerFiles();
    }
  },
  test: {
    environment: 'jsdom'
  }
}));
