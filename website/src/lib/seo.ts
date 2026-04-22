import type { Router, RouteLocationNormalizedLoaded } from 'vue-router';
import type { SampleRecord } from '../types';
import { findSampleById } from './catalog';

const SITE_NAME = 'Oracle AI Database Code Samples';
const SITE_URL = 'https://anders-swanson.github.io/oracle-database-code-samples/';
const DEFAULT_DESCRIPTION =
  'Browse runnable Oracle AI Database code samples for vector search, JSON, graph, spatial, TxEventQ, ORDS, Spring Boot, Java, Go, Python, and TypeScript.';
const DEFAULT_OG_IMAGE_URL = `${SITE_URL}social-card.svg`;
const DEFAULT_OG_IMAGE_ALT =
  'Oracle AI Database Code Samples with a stylized database graphic and feature tags for vector search, JSON, graph, spatial, Spring Boot, and TxEventQ.';

interface PageMetadata {
  title: string;
  description: string;
  canonicalUrl: string;
  ogType: 'website' | 'article';
  ogImageUrl: string;
  ogImageAlt: string;
  robots?: string;
  structuredData: object[];
}

function buildCatalogMetadata(): PageMetadata {
  return {
    title: SITE_NAME,
    description: DEFAULT_DESCRIPTION,
    canonicalUrl: SITE_URL,
    ogType: 'website',
    ogImageUrl: DEFAULT_OG_IMAGE_URL,
    ogImageAlt: DEFAULT_OG_IMAGE_ALT,
    structuredData: [
      {
        '@context': 'https://schema.org',
        '@type': 'WebSite',
        name: SITE_NAME,
        url: SITE_URL,
        description: DEFAULT_DESCRIPTION
      },
      {
        '@context': 'https://schema.org',
        '@type': 'CollectionPage',
        name: SITE_NAME,
        url: SITE_URL,
        description: DEFAULT_DESCRIPTION
      }
    ]
  };
}

function buildSampleMetadata(sample: SampleRecord): PageMetadata {
  return {
    title: sample.metaTitle,
    description: sample.metaDescription,
    canonicalUrl: sample.canonicalUrl,
    ogType: 'article',
    ogImageUrl: sample.ogImageUrl,
    ogImageAlt: DEFAULT_OG_IMAGE_ALT,
    structuredData: [
      {
        '@context': 'https://schema.org',
        '@type': 'SoftwareSourceCode',
        name: sample.title,
        description: sample.metaDescription,
        url: sample.canonicalUrl,
        codeRepository: sample.githubCodeUrl,
        programmingLanguage: sample.language,
        keywords: sample.tags.join(', '),
        about: sample.features
      },
      {
        '@context': 'https://schema.org',
        '@type': 'BreadcrumbList',
        itemListElement: [
          {
            '@type': 'ListItem',
            position: 1,
            name: 'Code Samples',
            item: SITE_URL
          },
          {
            '@type': 'ListItem',
            position: 2,
            name: sample.title,
            item: sample.canonicalUrl
          }
        ]
      }
    ]
  };
}

function buildNotFoundMetadata(): PageMetadata {
  return {
    title: `Sample Not Found | ${SITE_NAME}`,
    description: 'The requested Oracle AI Database sample page could not be found.',
    canonicalUrl: SITE_URL,
    ogType: 'website',
    ogImageUrl: DEFAULT_OG_IMAGE_URL,
    ogImageAlt: DEFAULT_OG_IMAGE_ALT,
    robots: 'noindex',
    structuredData: []
  };
}

function resolveRouteMetadata(route: RouteLocationNormalizedLoaded) {
  if (route.name === 'sample-detail') {
    const sample = findSampleById(String(route.params.id));
    return sample ? buildSampleMetadata(sample) : buildNotFoundMetadata();
  }

  return buildCatalogMetadata();
}

function upsertMeta(attribute: 'name' | 'property', key: string, content: string) {
  let element = document.head.querySelector(`meta[${attribute}="${key}"]`);
  if (!element) {
    element = document.createElement('meta');
    element.setAttribute(attribute, key);
    document.head.append(element);
  }

  element.setAttribute('content', content);
}

function upsertLink(rel: string, href: string) {
  let element = document.head.querySelector(`link[rel="${rel}"]`);
  if (!element) {
    element = document.createElement('link');
    element.setAttribute('rel', rel);
    document.head.append(element);
  }

  element.setAttribute('href', href);
}

function upsertStructuredData(structuredData: object[]) {
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

function syncDocumentMetadata(metadata: PageMetadata) {
  document.title = metadata.title;
  upsertMeta('name', 'description', metadata.description);
  upsertMeta('property', 'og:type', metadata.ogType);
  upsertMeta('property', 'og:title', metadata.title);
  upsertMeta('property', 'og:description', metadata.description);
  upsertMeta('property', 'og:url', metadata.canonicalUrl);
  upsertMeta('property', 'og:image', metadata.ogImageUrl);
  upsertMeta('property', 'og:image:alt', metadata.ogImageAlt);
  upsertMeta('name', 'twitter:card', 'summary_large_image');
  upsertMeta('name', 'twitter:title', metadata.title);
  upsertMeta('name', 'twitter:description', metadata.description);
  upsertMeta('name', 'twitter:image', metadata.ogImageUrl);
  upsertMeta('name', 'twitter:image:alt', metadata.ogImageAlt);
  upsertLink('canonical', metadata.canonicalUrl);
  upsertStructuredData(metadata.structuredData);

  const robotsMeta = document.head.querySelector('meta[name="robots"]');
  if (metadata.robots) {
    if (robotsMeta) {
      robotsMeta.setAttribute('content', metadata.robots);
    } else {
      const element = document.createElement('meta');
      element.setAttribute('name', 'robots');
      element.setAttribute('content', metadata.robots);
      document.head.append(element);
    }
  } else {
    robotsMeta?.remove();
  }
}

export function applyCurrentRouteMetadata(route: RouteLocationNormalizedLoaded) {
  syncDocumentMetadata(resolveRouteMetadata(route));
}

export function setupSeoSync(router: Router) {
  router.afterEach((to) => {
    applyCurrentRouteMetadata(to);
  });
}
