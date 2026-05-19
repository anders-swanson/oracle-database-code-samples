import type { RouteLocationNormalizedLoaded } from 'vue-router';
import type { FeatureLandingPage, LanguageLandingPage, SampleRecord } from '../types';
import { findFeaturePageBySlug, findLanguagePageBySlug, findSampleById, samplesForIds } from './catalog';
import { getRouteSampleDetail, hydrateSample } from './sampleDetails';
import {
  DEFAULT_DESCRIPTION,
  DEFAULT_OG_IMAGE_ALT,
  DEFAULT_OG_IMAGE_URL,
  SITE_NAME,
  SITE_URL,
  SOCIAL_CARD_HEIGHT,
  SOCIAL_CARD_WIDTH
} from './site';

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

function buildBreadcrumbStructuredData(items: { name: string; item: string }[]) {
  return {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: items.map((item, index) => ({
      '@type': 'ListItem',
      position: index + 1,
      name: item.name,
      item: item.item
    }))
  };
}

function buildCollectionStructuredData(
  name: string,
  description: string,
  canonicalUrl: string,
  sampleIds: string[],
  about: string
) {
  const pageSamples = samplesForIds(sampleIds);

  return {
    '@context': 'https://schema.org',
    '@type': 'CollectionPage',
    name,
    url: canonicalUrl,
    description,
    about,
    mainEntity: {
      '@type': 'ItemList',
      itemListElement: pageSamples.map((sample, index) => ({
        '@type': 'ListItem',
        position: index + 1,
        name: sample.title,
        url: `${SITE_URL}samples/${sample.id}/`
      }))
    }
  };
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
    'Explore a visual topic map of Oracle AI Database code samples, with topics sized by the number of related samples.';

  return {
    title: `Topic Map | ${SITE_NAME}`,
    description,
    canonicalUrl,
    ogType: 'website',
    ogImageUrl: DEFAULT_OG_IMAGE_URL,
    ogImageAlt: DEFAULT_OG_IMAGE_ALT,
    structuredData: [
      {
        '@context': 'https://schema.org',
        '@type': 'CollectionPage',
        name: 'Oracle AI Database Topic Map',
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
        ...buildBreadcrumbStructuredData([
          { name: 'Code Samples', item: SITE_URL },
          { name: sample.title, item: sample.canonicalUrl }
        ])
      }
    ]
  };
}

function buildFeatureMetadata(featurePage: FeatureLandingPage): PageMetadata {
  return {
    title: featurePage.metaTitle,
    description: featurePage.metaDescription,
    canonicalUrl: featurePage.canonicalUrl,
    ogType: 'website',
    ogImageUrl: DEFAULT_OG_IMAGE_URL,
    ogImageAlt: DEFAULT_OG_IMAGE_ALT,
    structuredData: [
      buildCollectionStructuredData(
        featurePage.title,
        featurePage.description,
        featurePage.canonicalUrl,
        featurePage.sampleIds,
        featurePage.name
      ),
      buildBreadcrumbStructuredData([
        { name: 'Code Samples', item: SITE_URL },
        { name: 'Features', item: `${SITE_URL}feature-map/` },
        { name: featurePage.name, item: featurePage.canonicalUrl }
      ])
    ]
  };
}

function buildLanguageMetadata(languagePage: LanguageLandingPage): PageMetadata {
  return {
    title: languagePage.metaTitle,
    description: languagePage.metaDescription,
    canonicalUrl: languagePage.canonicalUrl,
    ogType: 'website',
    ogImageUrl: DEFAULT_OG_IMAGE_URL,
    ogImageAlt: DEFAULT_OG_IMAGE_ALT,
    structuredData: [
      buildCollectionStructuredData(
        languagePage.title,
        languagePage.description,
        languagePage.canonicalUrl,
        languagePage.sampleIds,
        languagePage.name
      ),
      buildBreadcrumbStructuredData([
        { name: 'Code Samples', item: SITE_URL },
        { name: 'Languages', item: SITE_URL },
        { name: languagePage.name, item: languagePage.canonicalUrl }
      ])
    ]
  };
}

function buildNotFoundMetadata(label = 'Sample'): PageMetadata {
  return {
    title: `${label} Not Found | ${SITE_NAME}`,
    description: `The requested Oracle AI Database ${label.toLowerCase()} page could not be found.`,
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
    const sampleId = String(route.params.id);
    const sample = hydrateSample(sampleId, getRouteSampleDetail(route));
    if (!sample && findSampleById(sampleId)) {
      return buildNotFoundMetadata();
    }
    return sample ? buildSampleMetadata(sample) : buildNotFoundMetadata();
  }
  if (route.name === 'feature-map') {
    return buildSubfeatureMapMetadata();
  }
  if (route.name === 'patterns') {
    return buildPatternsMetadata();
  }
  if (route.name === 'feature-detail') {
    const featurePage = findFeaturePageBySlug(String(route.params.slug));
    return featurePage ? buildFeatureMetadata(featurePage) : buildNotFoundMetadata('Feature');
  }
  if (route.name === 'language-detail') {
    const languagePage = findLanguagePageBySlug(String(route.params.slug));
    return languagePage ? buildLanguageMetadata(languagePage) : buildNotFoundMetadata('Language');
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
      { property: 'og:image:type', content: 'image/png' },
      { property: 'og:image:width', content: String(SOCIAL_CARD_WIDTH) },
      { property: 'og:image:height', content: String(SOCIAL_CARD_HEIGHT) },
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
