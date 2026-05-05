import type { RouteLocationNormalizedLoaded } from 'vue-router';
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

function buildSubfeatureMapMetadata(): PageMetadata {
  const canonicalUrl = `${SITE_URL}feature-map/`;
  const description =
    'Explore a visual tag map of Oracle AI Database code samples, with features sized by the number of related samples.';

  return {
    title: `Feature Map | ${SITE_NAME}`,
    description,
    canonicalUrl,
    ogType: 'website',
    ogImageUrl: DEFAULT_OG_IMAGE_URL,
    ogImageAlt: DEFAULT_OG_IMAGE_ALT,
    structuredData: [
      {
        '@context': 'https://schema.org',
        '@type': 'CollectionPage',
        name: 'Oracle AI Database Feature Map',
        url: canonicalUrl,
        description
      }
    ]
  };
}

function buildPatternsMetadata(): PageMetadata {
  const canonicalUrl = `${SITE_URL}patterns/`;
  const description =
    'Map common software engineering patterns to Oracle AI Database features and linked code samples.';

  return {
    title: `Patterns | ${SITE_NAME}`,
    description,
    canonicalUrl,
    ogType: 'website',
    ogImageUrl: DEFAULT_OG_IMAGE_URL,
    ogImageAlt: DEFAULT_OG_IMAGE_ALT,
    structuredData: [
      {
        '@context': 'https://schema.org',
        '@type': 'CollectionPage',
        name: 'Oracle AI Database Pattern Atlas',
        url: canonicalUrl,
        description
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

export function resolveRouteMetadata(route: RouteLocationNormalizedLoaded) {
  if (route.name === 'sample-detail') {
    const sample = findSampleById(String(route.params.id));
    return sample ? buildSampleMetadata(sample) : buildNotFoundMetadata();
  }
  if (route.name === 'feature-map') {
    return buildSubfeatureMapMetadata();
  }
  if (route.name === 'patterns') {
    return buildPatternsMetadata();
  }

  return buildCatalogMetadata();
}

export function buildRouteHead(metadata: PageMetadata) {
  return {
    title: metadata.title,
    meta: [
      { name: 'description', content: metadata.description },
      { property: 'og:type', content: metadata.ogType },
      { property: 'og:title', content: metadata.title },
      { property: 'og:description', content: metadata.description },
      { property: 'og:url', content: metadata.canonicalUrl },
      { property: 'og:image', content: metadata.ogImageUrl },
      { property: 'og:image:alt', content: metadata.ogImageAlt },
      { name: 'twitter:card', content: 'summary_large_image' },
      { name: 'twitter:title', content: metadata.title },
      { name: 'twitter:description', content: metadata.description },
      { name: 'twitter:image', content: metadata.ogImageUrl },
      { name: 'twitter:image:alt', content: metadata.ogImageAlt },
      ...(metadata.robots ? [{ name: 'robots', content: metadata.robots }] : [])
    ],
    link: [{ rel: 'canonical', href: metadata.canonicalUrl }],
    script:
      metadata.structuredData.length > 0
        ? [
            {
              id: 'app-structured-data',
              type: 'application/ld+json',
              textContent: JSON.stringify(
                metadata.structuredData.length === 1 ? metadata.structuredData[0] : metadata.structuredData
              )
            }
          ]
        : []
  };
}
