import { describe, expect, it, vi } from 'vitest';

const { patternPage, languagePage, sample } = vi.hoisted(() => ({
  patternPage: {
    id: 'semantic-search-rag',
    intentId: 'build-ai-experiences',
    title: 'Semantic Search / RAG',
    summary: 'Store embeddings and search records by semantic similarity.',
    useWhen: 'Use when users search by meaning or AI answers need grounded records.',
    features: ['Vector Search', 'JSON'],
    topics: ['AI', 'Vector Search', 'Oracle Text'],
    sampleIds: ['vector'],
    samples: [] as never[]
  },
  languagePage: {
    slug: 'java',
    name: 'Java',
    title: 'Java Samples for Oracle AI Database',
    description: 'Java samples for Oracle AI Database. Browse 3 runnable samples with linked source code.',
    useWhen: 'Use when JVM applications need real Oracle AI Database examples instead of pseudocode.',
    sampleIds: ['vector'],
    relatedPatternIds: ['semantic-search-rag'],
    canonicalUrl: 'https://anders-swanson.github.io/oracle-database-code-samples/languages/java/',
    metaTitle: 'Java Samples for Oracle AI Database | Oracle AI Database Code Samples',
    metaDescription: 'Java samples for Oracle AI Database.',
    updatedAt: '2026-05-01T00:00:00.000Z'
  },
  sample: {
    id: 'vector',
    title: 'Vector Sample',
    description: 'Vector search with Oracle AI Database.',
    path: 'vector',
    githubReadmeUrl: 'https://example.com/readme',
    githubCodeUrl: 'https://example.com/code',
    tags: ['Vector', 'Java'],
    features: ['Vector Search'],
    language: 'Java',
    parentCollection: 'Standalone',
    blogPost: '',
    readmeExcerpt: 'Vector sample excerpt.',
    highlights: [],
    sourceUpdatedAt: '2026-05-01T00:00:00.000Z',
    featured: true,
    canonicalUrl: 'https://anders-swanson.github.io/oracle-database-code-samples/samples/vector/',
    metaTitle: 'Vector Sample | Oracle AI Database Code Samples',
    metaDescription: 'Vector search with Oracle AI Database.',
    ogImageUrl: 'https://anders-swanson.github.io/oracle-database-code-samples/sample-cards/vector.png'
  }
}));

vi.mock('../src/lib/catalog', () => ({
  findPatternMappingBySlug: (slug: string) => (slug === patternPage.id ? patternPage : undefined),
  findLanguagePageBySlug: (slug: string) => (slug === languagePage.slug ? languagePage : undefined),
  findSampleById: (id: string) => (id === sample.id ? sample : undefined),
  samplesForIds: (sampleIds: string[]) => sampleIds.includes(sample.id) ? [sample] : []
}));

import { buildRouteHead, resolveRouteMetadata } from '../src/lib/seo';

describe('SEO metadata sync', () => {
  function headFor(route: { name: string; params?: Record<string, string> }) {
    return buildRouteHead(
      resolveRouteMetadata({
        params: {},
        meta: route.name === 'sample-detail' ? { state: { sampleDetail: sample } } : {},
        ...route
      } as never)
    );
  }

  function findMeta(
    head: ReturnType<typeof buildRouteHead>,
    key: 'name' | 'property',
    value: string
  ) {
    return head.meta.find((entry) => entry[key] === value);
  }

  it('applies catalog metadata', () => {
    const head = headFor({ name: 'catalog' });

    expect(head.title).toBe('Oracle AI Database Code Samples');
    expect(findMeta(head, 'name', 'description')?.content).toContain('Browse runnable');
    expect(head.link[0].href).toBe('https://anders-swanson.github.io/oracle-database-code-samples/');
  });

  it('applies sample metadata and structured data', () => {
    const head = headFor({
      name: 'sample-detail',
      params: {
        id: sample.id
      }
    });

    expect(head.title).toBe(sample.metaTitle);
    expect(findMeta(head, 'property', 'og:type')?.content).toBe('article');
    expect(findMeta(head, 'property', 'og:url')?.content).toBe(sample.canonicalUrl);
    expect(findMeta(head, 'property', 'og:image')?.content).toBe(sample.ogImageUrl);
    expect(findMeta(head, 'property', 'og:image:type')?.content).toBe('image/png');
    expect(findMeta(head, 'property', 'og:image:width')?.content).toBe('1200');
    expect(findMeta(head, 'property', 'og:image:height')?.content).toBe('630');
    expect(head.script[0]?.textContent).toContain('SoftwareSourceCode');
  });

  it('applies pattern detail metadata and structured data', () => {
    const head = headFor({
      name: 'pattern-detail',
      params: {
        slug: patternPage.id
      }
    });

    expect(head.title).toBe('Semantic Search / RAG Pattern | Oracle AI Database Code Samples');
    expect(findMeta(head, 'property', 'og:type')?.content).toBe('website');
    expect(findMeta(head, 'property', 'og:url')?.content).toBe(
      'https://anders-swanson.github.io/oracle-database-code-samples/patterns/semantic-search-rag/'
    );
    expect(findMeta(head, 'name', 'description')?.content).toContain('Store embeddings');
    expect(head.script[0]?.textContent).toContain('CollectionPage');
    expect(head.script[0]?.textContent).toContain('BreadcrumbList');
  });

  it('applies language landing page metadata and structured data', () => {
    const head = headFor({
      name: 'language-detail',
      params: {
        slug: languagePage.slug
      }
    });

    expect(head.title).toBe(languagePage.metaTitle);
    expect(findMeta(head, 'property', 'og:url')?.content).toBe(languagePage.canonicalUrl);
    expect(findMeta(head, 'name', 'description')?.content).toContain('Java samples');
    expect(head.script[0]?.textContent).toContain('CollectionPage');
  });

  it('applies topic map metadata', () => {
    const head = headFor({ name: 'feature-map' });

    expect(head.title).toBe('Topic Map | Oracle AI Database Code Samples');
    expect(findMeta(head, 'property', 'og:url')?.content).toBe(
      'https://anders-swanson.github.io/oracle-database-code-samples/feature-map/'
    );
    expect(head.script[0]?.textContent).toContain('CollectionPage');
  });

  it('applies patterns metadata', () => {
    const head = headFor({ name: 'patterns' });

    expect(head.title).toBe('Patterns | Oracle AI Database Code Samples');
    expect(findMeta(head, 'property', 'og:url')?.content).toBe(
      'https://anders-swanson.github.io/oracle-database-code-samples/patterns/'
    );
    expect(findMeta(head, 'name', 'description')?.content).toContain('software engineering patterns');
    expect(head.script[0]?.textContent).toContain('CollectionPage');
  });
});
