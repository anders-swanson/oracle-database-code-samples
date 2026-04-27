import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { JSDOM } from 'jsdom';
import {
  buildCatalogStructuredData,
  buildSampleStructuredData,
  DEFAULT_DESCRIPTION,
  DEFAULT_OG_IMAGE_ALT,
  DEFAULT_OG_IMAGE_URL,
  SITE_BASE_PATH,
  SITE_NAME,
  SITE_TITLE,
  SITE_URL
} from './seo-utils.mjs';

const currentDir = path.dirname(fileURLToPath(import.meta.url));
const websiteRoot = path.resolve(currentDir, '..');
const distRoot = path.join(websiteRoot, 'dist');
const samplesPath = path.join(websiteRoot, 'src', 'data', 'samples.json');
const samples = JSON.parse(fs.readFileSync(samplesPath, 'utf8'));
const template = fs.readFileSync(path.join(distRoot, 'index.html'), 'utf8');
const projectBasePath = SITE_BASE_PATH.endsWith('/') ? SITE_BASE_PATH.slice(0, -1) : SITE_BASE_PATH;
const buildDate = new Date().toISOString();

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function toSitePath(urlPath = '/') {
  return urlPath === '/' ? SITE_BASE_PATH : `${projectBasePath}${urlPath}`;
}

function renderInlineMarkdown(text, baseUrl) {
  const pattern = /\[([^\]]+)\]\(([^)]+)\)/g;
  let cursor = 0;
  let result = '';

  for (const match of text.matchAll(pattern)) {
    const [fullMatch, label, href] = match;
    const matchIndex = match.index ?? 0;
    result += escapeHtml(text.slice(cursor, matchIndex));

    let resolvedHref = '';
    try {
      resolvedHref = new URL(href, baseUrl).toString();
    } catch {
      resolvedHref = '';
    }

    if (resolvedHref) {
      result += `<a class="inline-markdown-link" href="${escapeHtml(resolvedHref)}">${escapeHtml(label)}</a>`;
    } else {
      result += escapeHtml(fullMatch);
    }

    cursor = matchIndex + fullMatch.length;
  }

  result += escapeHtml(text.slice(cursor));
  return result;
}

function renderHeader() {
  return `
    <header class="site-header">
      <a class="site-header__brand" href="${toSitePath('/')}">
        <span class="site-header__eyebrow">Oracle AI Database</span>
        <span class="site-header__title">Code Samples</span>
      </a>
      <nav class="site-header__nav">
        <a href="${toSitePath('/')}">Catalog</a>
        <a href="${toSitePath('/feature-map/')}">Feature Map</a>
        <a href="https://github.com/anders-swanson/oracle-database-code-samples" target="_blank" rel="noreferrer">
          GitHub
        </a>
      </nav>
    </header>
  `;
}

function renderSampleCard(sample) {
  return `
    <article class="sample-card">
      <div class="sample-card__header">
        <div class="sample-card__meta">
          <span class="sample-card__language">${escapeHtml(sample.language)}</span>
        </div>
        <a class="sample-card__title" href="${toSitePath(sample.urlPath)}">${escapeHtml(sample.title)}</a>
        <p class="sample-card__description">${escapeHtml(sample.description)}</p>
      </div>
      <div class="sample-card__tags">
        ${sample.tags.slice(0, 5).map((tag) => `<span class="sample-card__tag">#${escapeHtml(tag)}</span>`).join('')}
      </div>
      <div class="sample-card__footer">
        <code>${escapeHtml(sample.path)}</code>
        <div class="sample-card__actions">
          <a class="button button--ghost" href="${toSitePath(sample.urlPath)}">Read More</a>
          <a class="button button--primary" href="${escapeHtml(sample.githubCodeUrl)}" target="_blank" rel="noreferrer">View Code</a>
        </div>
      </div>
    </article>
  `;
}

function renderCatalogPage() {
  const featuredCount = samples.filter((sample) => sample.featured).length;
  const languageCount = new Set(samples.map((sample) => sample.language)).size;
  const featureCount = new Set(samples.flatMap((sample) => sample.features)).size;
  const topTags = Array.from(
    samples
      .flatMap((sample) => sample.tags)
      .reduce((counts, tag) => counts.set(tag, (counts.get(tag) ?? 0) + 1), new Map())
      .entries()
  )
    .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]))
    .slice(0, 8)
    .map(([tag]) => tag);

  return `
    <div class="app-shell">
      <div class="app-shell__glow app-shell__glow--one"></div>
      <div class="app-shell__glow app-shell__glow--two"></div>
      <div class="app-shell__grid"></div>
      ${renderHeader()}
      <main class="site-main">
        <section class="hero">
          <div class="hero__copy">
            <span class="hero__eyebrow">browse and learn Oracle developer samples</span>
            <h1>Explore Oracle AI Database with real code samples you can run for free</h1>
            <p>
              Use this catalog to find runnable Oracle AI Database examples for vector search, JSON, graph, spatial,
              ORDS, TxEventQ, Spring Boot, Java, Go, Python, and TypeScript.
            </p>
          </div>
          <div class="hero__stats">
            <div class="stat-card"><strong>${samples.length}</strong><span>Code Samples</span></div>
            <div class="stat-card"><strong>${featureCount}</strong><span>Database Features</span></div>
            <div class="stat-card"><strong>${languageCount}</strong><span>Languages</span></div>
            <div class="stat-card"><strong>${featuredCount}</strong><span>Featured entries</span></div>
          </div>
        </section>
        <section class="catalog-layout">
          <aside class="catalog-sidebar">
            <div class="control-panel">
              <div class="control-panel__topline">
                <h2>Browse the full sample catalog</h2>
              </div>
              <p class="detail-panel__excerpt">
                Every sample links back to source code and README documentation in the repository. Use the interactive
                filters after the app loads, or browse the static catalog below.
              </p>
              <div class="detail-panel__block">
                <h3>Popular topics</h3>
                <div class="detail-hero__feature-list">
                  ${topTags.map((tag) => `<span class="sample-card__tag">#${escapeHtml(tag)}</span>`).join('')}
                </div>
              </div>
            </div>
          </aside>
          <section class="catalog-results">
            <div class="catalog-results__header">
              <div>
                <span class="catalog-results__eyebrow">Sample Index</span>
                <h2>${samples.length} samples</h2>
              </div>
              <p>Static HTML is prerendered for search engines; the interactive Vue catalog loads on top of it.</p>
            </div>
            <div class="sample-grid">
              ${samples.map((sample) => renderSampleCard(sample)).join('')}
            </div>
          </section>
        </section>
      </main>
    </div>
  `;
}

function findRelatedSamples(target, limit = 4) {
  return samples
    .filter((candidate) => candidate.id !== target.id)
    .map((candidate) => ({
      sample: candidate,
      score:
        candidate.features.filter((feature) => target.features.includes(feature)).length * 3 +
        candidate.tags.filter((tag) => target.tags.includes(tag)).length * 2 +
        Number(candidate.language === target.language)
    }))
    .filter((entry) => entry.score > 0)
    .sort((left, right) => right.score - left.score || left.sample.name.localeCompare(right.sample.name))
    .slice(0, limit)
    .map((entry) => entry.sample);
}

function renderSamplePage(sample) {
  const related = findRelatedSamples(sample);

  return `
    <div class="app-shell">
      <div class="app-shell__glow app-shell__glow--one"></div>
      <div class="app-shell__glow app-shell__glow--two"></div>
      <div class="app-shell__grid"></div>
      ${renderHeader()}
      <main class="site-main site-main--compact">
        <div class="detail-page">
          <section class="detail-hero">
            <div class="detail-hero__frame">
              <nav class="detail-breadcrumbs" aria-label="Breadcrumb">
                <a href="${toSitePath('/')}">Catalog</a>
                <span>/</span>
                <span>${escapeHtml(sample.title)}</span>
              </nav>
              <div class="detail-hero__topline">
                <span>${escapeHtml(sample.language)}</span>
                <span>${escapeHtml(sample.parentCollection)}</span>
              </div>
              <h1>${escapeHtml(sample.title)}</h1>
              <p>${escapeHtml(sample.description)}</p>

              <div class="detail-hero__actions">
                <a class="button button--primary" href="${escapeHtml(sample.githubCodeUrl)}" target="_blank" rel="noreferrer">View Code</a>
                <a class="button button--ghost" href="${escapeHtml(sample.githubReadmeUrl)}" target="_blank" rel="noreferrer">View README</a>
                ${sample.blogPost ? `<a class="button button--ghost" href="${escapeHtml(sample.blogPost)}" target="_blank" rel="noreferrer">Blog Post</a>` : ''}
              </div>

              <div class="detail-hero__feature-list">
                ${sample.tags.map((tag) => `<span class="sample-card__tag">#${escapeHtml(tag)}</span>`).join('')}
              </div>
            </div>
          </section>

          <section class="detail-layout">
            <article class="detail-panel">
              <div class="detail-panel__header">
                <span class="catalog-results__eyebrow">What this sample helps you learn</span>
              </div>
              <p class="detail-panel__excerpt">${escapeHtml(sample.readmeExcerpt)}</p>
              ${
                sample.highlights.length > 0
                  ? `
                    <div class="detail-panel__block">
                      <h3>Highlights</h3>
                      <ul>
                        ${sample.highlights.map((highlight) => `<li>${renderInlineMarkdown(highlight, sample.githubReadmeUrl)}</li>`).join('')}
                      </ul>
                    </div>
                  `
                  : ''
              }
            </article>

            <aside class="detail-sidebar">
              <div class="detail-panel">
                <div class="detail-panel__header">
                  <span class="catalog-results__eyebrow">Context</span>
                </div>
                <dl class="metadata-list">
                  <div>
                    <dt>Repo path</dt>
                    <dd><code>${escapeHtml(sample.path)}</code></dd>
                  </div>
                  <div>
                    <dt>Collection</dt>
                    <dd>${escapeHtml(sample.parentCollection)}</dd>
                  </div>
                  <div>
                    <dt>Language</dt>
                    <dd>${escapeHtml(sample.language)}</dd>
                  </div>
                  <div>
                    <dt>Tags</dt>
                    <dd>${sample.tags.length > 0 ? escapeHtml(sample.tags.join(', ')) : 'No tags declared'}</dd>
                  </div>
                </dl>
              </div>
            </aside>
          </section>

          ${
            related.length > 0
              ? `
                <section class="related-section">
                  <div class="detail-panel__header">
                    <span class="catalog-results__eyebrow">Keep Exploring</span>
                  </div>
                  <div class="sample-grid sample-grid--compact">
                    ${related.map((entry) => renderSampleCard(entry)).join('')}
                  </div>
                </section>
              `
              : ''
          }
        </div>
      </main>
    </div>
  `;
}

function renderFeatureMapPage() {
  return `
    <div class="app-shell">
      <div class="app-shell__glow app-shell__glow--one"></div>
      <div class="app-shell__glow app-shell__glow--two"></div>
      <div class="app-shell__grid"></div>
      ${renderHeader()}
      <main class="site-main">
        <section class="map-hero">
          <div class="map-hero__copy">
            <span class="hero__eyebrow">Feature Tag Map</span>
            <h1>Explore Oracle AI Database samples like a navigable feature map</h1>
            <p>
              Open the interactive feature map to browse the strongest feature clusters and jump into matching sample
              sets across the catalog.
            </p>
          </div>
        </section>
        <section class="tag-map-panel">
          <div class="tag-map-panel__toolbar">
            <p>The full interactive map loads when JavaScript initializes.</p>
            <a class="button button--ghost tag-map-panel__button" href="${toSitePath('/')}">Browse Full Catalog</a>
          </div>
        </section>
      </main>
    </div>
  `;
}

function upsertMeta(document, attribute, key, content) {
  let element = document.head.querySelector(`meta[${attribute}="${key}"]`);
  if (!element) {
    element = document.createElement('meta');
    element.setAttribute(attribute, key);
    document.head.append(element);
  }

  element.setAttribute('content', content);
}

function upsertLink(document, rel, href) {
  let element = document.head.querySelector(`link[rel="${rel}"]`);
  if (!element) {
    element = document.createElement('link');
    element.setAttribute('rel', rel);
    document.head.append(element);
  }

  element.setAttribute('href', href);
}

function upsertStructuredData(document, structuredData) {
  const existing = document.head.querySelector('#app-structured-data');
  if (existing) {
    existing.remove();
  }

  if (structuredData.length === 0) {
    return;
  }

  const script = document.createElement('script');
  script.id = 'app-structured-data';
  script.type = 'application/ld+json';
  script.textContent = JSON.stringify(structuredData.length === 1 ? structuredData[0] : structuredData);
  document.head.append(script);
}

function renderDocument({ title, description, canonicalUrl, ogType, structuredData, appHtml }) {
  const dom = new JSDOM(template);
  const { document } = dom.window;

  document.title = title;
  upsertMeta(document, 'name', 'description', description);
  upsertMeta(document, 'property', 'og:type', ogType);
  upsertMeta(document, 'property', 'og:title', title);
  upsertMeta(document, 'property', 'og:description', description);
  upsertMeta(document, 'property', 'og:url', canonicalUrl);
  upsertMeta(document, 'property', 'og:image', DEFAULT_OG_IMAGE_URL);
  upsertMeta(document, 'property', 'og:image:alt', DEFAULT_OG_IMAGE_ALT);
  upsertMeta(document, 'name', 'twitter:card', 'summary_large_image');
  upsertMeta(document, 'name', 'twitter:title', title);
  upsertMeta(document, 'name', 'twitter:description', description);
  upsertMeta(document, 'name', 'twitter:image', DEFAULT_OG_IMAGE_URL);
  upsertMeta(document, 'name', 'twitter:image:alt', DEFAULT_OG_IMAGE_ALT);
  upsertLink(document, 'canonical', canonicalUrl);
  upsertStructuredData(document, structuredData);

  const app = document.querySelector('#app');
  if (!app) {
    throw new Error('Missing #app container in Vite build output.');
  }
  app.innerHTML = appHtml;

  return `<!doctype html>\n${document.documentElement.outerHTML}`;
}

function writePage(relativePath, content) {
  const outputPath = path.join(distRoot, relativePath);
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, content);
}

function renderCatalogDocument() {
  return renderDocument({
    title: SITE_NAME,
    description: DEFAULT_DESCRIPTION,
    canonicalUrl: SITE_URL,
    ogType: 'website',
    structuredData: buildCatalogStructuredData(),
    appHtml: renderCatalogPage()
  });
}

function renderSampleDocument(sample) {
  return renderDocument({
    title: sample.metaTitle,
    description: sample.metaDescription,
    canonicalUrl: sample.canonicalUrl,
    ogType: 'article',
    structuredData: buildSampleStructuredData(sample),
    appHtml: renderSamplePage(sample)
  });
}

function renderFeatureMapDocument() {
  const canonicalUrl = `${SITE_URL}feature-map/`;
  const description =
    'Explore a visual tag map of Oracle AI Database code samples, with features sized by the number of related samples.';

  return renderDocument({
    title: `Feature Map | ${SITE_NAME}`,
    description,
    canonicalUrl,
    ogType: 'website',
    structuredData: [
      {
        '@context': 'https://schema.org',
        '@type': 'CollectionPage',
        name: 'Oracle AI Database Feature Map',
        url: canonicalUrl,
        description
      }
    ],
    appHtml: renderFeatureMapPage()
  });
}

function writeSitemap() {
  const urls = [SITE_URL, `${SITE_URL}feature-map/`, ...samples.map((sample) => sample.canonicalUrl)];
  const body = urls
    .map((url) => `  <url><loc>${escapeHtml(url)}</loc><lastmod>${buildDate}</lastmod></url>`)
    .join('\n');
  fs.writeFileSync(
    path.join(distRoot, 'sitemap.xml'),
    `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${body}\n</urlset>\n`
  );
}

function writeRobots() {
  fs.writeFileSync(
    path.join(distRoot, 'robots.txt'),
    `User-agent: *\nAllow: /\nSitemap: ${SITE_URL}sitemap.xml\n`
  );
}

writePage('index.html', renderCatalogDocument());
writePage(path.join('feature-map', 'index.html'), renderFeatureMapDocument());

for (const sample of samples) {
  writePage(path.join(sample.urlPath.replace(/^\//, ''), 'index.html'), renderSampleDocument(sample));
}

writeSitemap();
writeRobots();

console.log(`Prerendered ${samples.length + 1} HTML pages plus sitemap and robots output for ${SITE_TITLE}.`);
