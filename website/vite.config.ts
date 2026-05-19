import fs from 'node:fs';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

const distRoot = new URL('./dist/', import.meta.url);
const siteMetadataPath = new URL('./src/data/siteMetadata.json', import.meta.url);
const catalogIndexPath = new URL('./src/data/catalog-index.json', import.meta.url);
const featurePagesPath = new URL('./src/data/feature-pages.json', import.meta.url);
const languagePagesPath = new URL('./src/data/language-pages.json', import.meta.url);
const sampleDetailsRoot = new URL('./src/data/sample-details/', import.meta.url);
const featureDetailsPath = new URL('./src/data/featureDetails.json', import.meta.url);
const patternDefinitionsPath = new URL('./src/data/patternDefinitions.json', import.meta.url);

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

interface SitemapRoute {
  pathname: string;
  lastmod: string;
}

function readJson<T>(url: URL) {
  return JSON.parse(fs.readFileSync(url, 'utf8')) as T;
}

function readSiteMetadata() {
  return readJson<SiteMetadata>(siteMetadataPath);
}

function readCatalogIndex() {
  return readJson<PackedCatalogIndex>(catalogIndexPath);
}

function readFeaturePages() {
  return readJson<LandingPage[]>(featurePagesPath);
}

function readLanguagePages() {
  return readJson<LandingPage[]>(languagePagesPath);
}

function readSampleDetail(id: string) {
  return readJson<SampleDetail>(new URL(`${id}.json`, sampleDetailsRoot));
}

function buildSamplePath(id: string) {
  return `/samples/${id}/`;
}

function buildFeaturePath(slug: string) {
  return `/features/${slug}/`;
}

function buildLanguagePath(slug: string) {
  return `/languages/${slug}/`;
}

function buildCanonicalUrl(pathname: string) {
  const siteUrl = readSiteMetadata().siteUrl;
  const normalized = pathname.startsWith('/') ? pathname.slice(1) : pathname;
  return new URL(normalized, siteUrl).toString();
}

function maxLastmod(values: string[]) {
  return values.sort().at(-1) ?? '1970-01-01T00:00:00.000Z';
}

function fileLastmod(url: URL) {
  return fs.statSync(url).mtime.toISOString();
}

function buildRoutes() {
  const catalog = readCatalogIndex();
  const sampleRoutes = catalog.i.map(([id]) => ({
    pathname: buildSamplePath(id),
    lastmod: readSampleDetail(id).sourceUpdatedAt
  }));
  const featureRoutes = readFeaturePages().map((page) => ({
    pathname: buildFeaturePath(page.slug),
    lastmod: page.updatedAt
  }));
  const languageRoutes = readLanguagePages().map((page) => ({
    pathname: buildLanguagePath(page.slug),
    lastmod: page.updatedAt
  }));
  const contentLastmod = maxLastmod([
    fileLastmod(featureDetailsPath),
    fileLastmod(patternDefinitionsPath),
    ...sampleRoutes.map((route) => route.lastmod),
    ...featureRoutes.map((route) => route.lastmod),
    ...languageRoutes.map((route) => route.lastmod)
  ]);

  return [
    { pathname: '/', lastmod: contentLastmod },
    { pathname: '/patterns/', lastmod: maxLastmod([contentLastmod, fileLastmod(patternDefinitionsPath)]) },
    { pathname: '/feature-map/', lastmod: contentLastmod },
    ...featureRoutes,
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
  const siteUrl = readSiteMetadata().siteUrl;
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
  fs.writeFileSync(new URL('robots.txt', distRoot), `User-agent: *\nAllow: /\nSitemap: ${siteUrl}sitemap.xml\n`);
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
