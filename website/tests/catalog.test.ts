import { describe, expect, it } from 'vitest';
import { buildSubfeatureGraph, filterSamples, routeQueryToFilters, serializeFilters } from '../src/lib/catalog';
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
    featured: true,
    urlPath: '/samples/vector/',
    canonicalUrl: 'https://anders-swanson.github.io/oracle-database-code-samples/samples/vector/',
    metaTitle: 'Vector Sample | Oracle AI Database Code Samples',
    metaDescription: 'Vector search with JDBC',
    ogImageUrl: 'https://anders-swanson.github.io/oracle-database-code-samples/social-card.svg'
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
    featured: false,
    urlPath: '/samples/graph/',
    canonicalUrl: 'https://anders-swanson.github.io/oracle-database-code-samples/samples/graph/',
    metaTitle: 'Graph Sample | Oracle AI Database Code Samples',
    metaDescription: 'Property graph over JDBC',
    ogImageUrl: 'https://anders-swanson.github.io/oracle-database-code-samples/social-card.svg'
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

  it('builds a feature graph from non-generic tags', () => {
    const graph = buildSubfeatureGraph(
      [
        {
          ...samples[0],
          tags: ['Vector', 'Java']
        },
        {
          ...samples[1],
          tags: ['Graph', 'Java']
        },
        {
          ...samples[0],
          id: 'json',
          name: 'json',
          title: 'JSON Sample',
          description: 'JSON features',
          path: 'json',
          readmePath: 'json/README.md',
          githubReadmeUrl: 'https://example.com/readme-json',
          githubCodeUrl: 'https://example.com/code-json',
          tags: ['JSON', 'Java']
        }
      ],
      10
    );

    expect(graph.centerLabel).toBe('Oracle AI Database');
    expect(graph.centerSubtitle).toBe('Converged Database');
    expect(graph.totalSamples).toBe(3);
    expect(graph.width).toBeGreaterThan(1000);
    expect(graph.height).toBeGreaterThan(1000);
    expect(graph.nodes.map((node) => `${node.name}:${node.count}`)).toEqual(['Graph:1', 'JSON:1', 'Vector:1']);
    expect(graph.nodes.every((node) => node.x > 0 && node.x < graph.width && node.y > 0 && node.y < graph.height)).toBe(
      true
    );
    expect(graph.nodes.every((node) => node.width >= 168 && node.height === 96)).toBe(true);
  });
});
