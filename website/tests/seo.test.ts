import { describe, expect, it, vi } from 'vitest';

const { sample } = vi.hoisted(() => ({
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
    featured: true,
    canonicalUrl: 'https://anders-swanson.github.io/oracle-database-code-samples/samples/vector/',
    metaTitle: 'Vector Sample | Oracle AI Database Code Samples',
    metaDescription: 'Vector search with Oracle AI Database.',
    ogImageUrl: 'https://anders-swanson.github.io/oracle-database-code-samples/sample-cards/vector.svg'
  }
}));

vi.mock('../src/lib/catalog', () => ({
  findSampleById: (id: string) => (id === sample.id ? sample : undefined)
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
    expect(head.script[0]?.textContent).toContain('SoftwareSourceCode');
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
