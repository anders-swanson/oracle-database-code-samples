import fs from 'node:fs';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

const siteUrl = 'https://anders-swanson.github.io/oracle-database-code-samples/';
const distRoot = new URL('./dist/', import.meta.url);
const catalogIndexPath = new URL('./src/data/catalog-index.json', import.meta.url);

interface PackedCatalogIndex {
  i: [string, string, string, number[], number, number, 0 | 1][];
}

function readCatalogIndex() {
  return JSON.parse(fs.readFileSync(catalogIndexPath, 'utf8')) as PackedCatalogIndex;
}

function buildSamplePath(id: string) {
  return `/samples/${id}/`;
}

function buildCanonicalUrl(pathname: string) {
  const normalized = pathname.startsWith('/') ? pathname.slice(1) : pathname;
  return new URL(normalized, siteUrl).toString();
}

function buildRoutes() {
  return ['/', '/patterns/', '/feature-map/', ...readCatalogIndex().i.map(([id]) => buildSamplePath(id))];
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
  const buildDate = new Date().toISOString();
  const urls = [
    siteUrl,
    `${siteUrl}patterns/`,
    `${siteUrl}feature-map/`,
    ...readCatalogIndex().i.map(([id]) => buildCanonicalUrl(buildSamplePath(id)))
  ];
  const sitemapBody = urls
    .map((url) => `  <url><loc>${escapeXml(url)}</loc><lastmod>${buildDate}</lastmod></url>`)
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
      return buildRoutes();
    },
    onFinished() {
      writeCrawlerFiles();
    }
  },
  test: {
    environment: 'jsdom'
  }
}));
