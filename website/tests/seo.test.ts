import { beforeEach, describe, expect, it, vi } from 'vitest';

const { sample } = vi.hoisted(() => ({
  sample: {
    id: 'vector',
    name: 'vector',
    title: 'Vector Sample',
    description: 'Vector search with Oracle AI Database.',
    path: 'vector',
    readmePath: 'vector/README.md',
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
    urlPath: '/samples/vector/',
    canonicalUrl: 'https://anders-swanson.github.io/oracle-database-code-samples/samples/vector/',
    metaTitle: 'Vector Sample | Oracle AI Database Code Samples',
    metaDescription: 'Vector search with Oracle AI Database.',
    ogImageUrl: 'https://anders-swanson.github.io/oracle-database-code-samples/social-card.svg'
  }
}));

vi.mock('../src/lib/catalog', () => ({
  findSampleById: (id: string) => (id === sample.id ? sample : undefined)
}));

import { applyCurrentRouteMetadata } from '../src/lib/seo';

describe('SEO metadata sync', () => {
  beforeEach(() => {
    document.head.innerHTML = '';
    document.title = '';
  });

  it('applies catalog metadata', () => {
    applyCurrentRouteMetadata({
      name: 'catalog',
      params: {}
    } as never);

    expect(document.title).toBe('Oracle AI Database Code Samples');
    expect(document.head.querySelector('meta[name="description"]')?.getAttribute('content')).toContain('Browse runnable');
    expect(document.head.querySelector('link[rel="canonical"]')?.getAttribute('href')).toBe(
      'https://anders-swanson.github.io/oracle-database-code-samples/'
    );
  });

  it('applies sample metadata and structured data', () => {
    applyCurrentRouteMetadata({
      name: 'sample-detail',
      params: {
        id: sample.id
      }
    } as never);

    expect(document.title).toBe(sample.metaTitle);
    expect(document.head.querySelector('meta[property="og:type"]')?.getAttribute('content')).toBe('article');
    expect(document.head.querySelector('meta[property="og:url"]')?.getAttribute('content')).toBe(sample.canonicalUrl);
    expect(document.head.querySelector('#app-structured-data')?.textContent).toContain('SoftwareSourceCode');
  });

  it('applies feature map metadata', () => {
    applyCurrentRouteMetadata({
      name: 'feature-map',
      params: {}
    } as never);

    expect(document.title).toBe('Feature Map | Oracle AI Database Code Samples');
    expect(document.head.querySelector('meta[property="og:url"]')?.getAttribute('content')).toBe(
      'https://anders-swanson.github.io/oracle-database-code-samples/feature-map/'
    );
    expect(document.head.querySelector('#app-structured-data')?.textContent).toContain('CollectionPage');
  });
});
