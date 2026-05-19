export const SITE_NAME = 'Oracle AI Database Code Samples';
export const SITE_TITLE = 'Oracle AI Database Samples';
export const SITE_URL = 'https://anders-swanson.github.io/oracle-database-code-samples/';
export const SITE_BASE_PATH = '/oracle-database-code-samples/';
export const SAMPLE_SOCIAL_CARD_DIRECTORY = 'sample-cards';
export const DEFAULT_DESCRIPTION =
  'Browse runnable Oracle AI Database code samples for vector search, JSON, graph, spatial, TxEventQ, ORDS, Spring Boot, Java, Go, Python, and TypeScript.';
export const DEFAULT_OG_IMAGE_URL = `${SITE_URL}social-card.svg`;
export const DEFAULT_OG_IMAGE_ALT =
  'Oracle AI Database Code Samples with a stylized database graphic and feature tags for vector search, JSON, graph, spatial, Spring Boot, and TxEventQ.';

export function trimDescription(value, limit = 160) {
  const normalized = String(value ?? '')
    .replace(/\s+/g, ' ')
    .trim();

  if (normalized.length <= limit) {
    return normalized;
  }

  const shortened = normalized.slice(0, limit - 1);
  const lastSpace = shortened.lastIndexOf(' ');
  return `${(lastSpace > 100 ? shortened.slice(0, lastSpace) : shortened).trim()}…`;
}

export function buildSamplePath(id) {
  return `/samples/${id}/`;
}

export function buildCanonicalUrl(pathname = '/') {
  const normalized = pathname.startsWith('/') ? pathname.slice(1) : pathname;
  return new URL(normalized, SITE_URL).toString();
}

export function buildSampleSocialCardUrl(id) {
  return new URL(`${SAMPLE_SOCIAL_CARD_DIRECTORY}/${id}.svg`, SITE_URL).toString();
}

export function buildSampleMetaTitle(title) {
  return `${title} | ${SITE_NAME}`;
}

export function buildSampleMetaDescription(sample) {
  return trimDescription(sample.description || sample.readmeExcerpt || sample.title || DEFAULT_DESCRIPTION);
}

export function buildCatalogStructuredData() {
  return [
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
  ];
}

export function buildSampleStructuredData(sample) {
  return [
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
  ];
}
