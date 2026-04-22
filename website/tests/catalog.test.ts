import { describe, expect, it } from 'vitest';
import { filterSamples, routeQueryToFilters, serializeFilters } from '../src/lib/catalog';
import type { SampleRecord } from '../src/types';

const samples: SampleRecord[] = [
  {
    id: 'vector',
    name: 'vector',
    title: 'Vector Sample',
    description: 'Vector search with JDBC',
    path: 'vector',
    readmePath: 'vector/README.md',
    githubReadmeUrl: 'https://example.com/readme',
    githubCodeUrl: 'https://example.com/code',
    tags: ['Vector', 'JDBC'],
    features: ['Vector Search'],
    language: 'Java',
    parentCollection: 'Standalone',
    blogPost: '',
    readmeExcerpt: 'Learn vector search.',
    highlights: [],
    featured: true
  },
  {
    id: 'graph',
    name: 'graph',
    title: 'Graph Sample',
    description: 'Property graph over JDBC',
    path: 'graph',
    readmePath: 'graph/README.md',
    githubReadmeUrl: 'https://example.com/readme-graph',
    githubCodeUrl: 'https://example.com/code-graph',
    tags: ['Graph'],
    features: ['Property Graph'],
    language: 'Java',
    parentCollection: 'Standalone',
    blogPost: '',
    readmeExcerpt: 'Learn property graph.',
    highlights: [],
    featured: false
  }
];

describe('catalog filtering', () => {
  it('filters by query and feature', () => {
    const result = filterSamples(samples, {
      query: 'vector',
      features: ['Vector Search'],
      languages: [],
      tags: [],
      sort: 'featured'
    });

    expect(result).toHaveLength(1);
    expect(result[0].id).toBe('vector');
  });

  it('serializes and parses route query filters', () => {
    const serialized = serializeFilters({
      query: 'graph',
      features: ['Property Graph'],
      languages: ['Java'],
      tags: ['Graph'],
      sort: 'name'
    });

    expect(serialized).toEqual({
      q: 'graph',
      tags: 'Graph',
      sort: 'name'
    });

    expect(routeQueryToFilters({
      ...serialized,
      features: 'Property Graph',
      languages: 'Java',
      sort: 'path'
    })).toEqual({
      query: 'graph',
      features: [],
      languages: [],
      tags: ['Graph'],
      sort: 'featured'
    });
  });
});
