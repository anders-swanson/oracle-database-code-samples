import { describe, expect, it } from 'vitest';
import {
  buildGithubCodeUrl,
  buildSamplePath,
  buildSubfeatureGraph,
  findPatternMappingByTopic,
  filterSamples,
  getStats,
  patternMappings,
  resolvePatternMappings,
  routeQueryToFilters,
  sampleIdToPath,
  samples as catalogSamples,
  serializeFilters
} from '../src/lib/catalog';
import type { SampleSummary } from '../src/types';

const samples: SampleSummary[] = [
  {
    id: 'vector',
    title: 'Vector Sample',
    description: 'Vector search with JDBC',
    path: 'vector',
    githubCodeUrl: 'https://example.com/code',
    tags: ['Vector', 'JDBC'],
    language: 'Java',
    parentCollection: 'Standalone',
    featured: true
  },
  {
    id: 'graph',
    title: 'Graph Sample',
    description: 'Property graph over JDBC',
    path: 'graph',
    githubCodeUrl: 'https://example.com/code-graph',
    tags: ['Graph'],
    language: 'Java',
    parentCollection: 'Standalone',
    featured: false
  }
];

describe('catalog filtering', () => {
  it('filters by query', () => {
    const result = filterSamples(samples, {
      query: 'vector',
      tags: [],
      sort: 'featured'
    });

    expect(result).toHaveLength(1);
    expect(result[0].id).toBe('vector');
  });

  it('filters by tag and sorts by displayed title', () => {
    const result = filterSamples(samples, {
      query: '',
      tags: ['JDBC'],
      sort: 'name'
    });

    expect(result.map((sample) => sample.title)).toEqual(['Vector Sample']);
  });

  it('derives paths, urls, and stats from compact catalog assumptions', () => {
    expect(sampleIdToPath('database-per-service-example--courses')).toBe('database-per-service-example/courses');
    expect(buildSamplePath('mcp-agent')).toBe('/samples/mcp-agent/');
    expect(buildGithubCodeUrl('json--jdbc-json-basic')).toBe(
      'https://github.com/anders-swanson/oracle-database-code-samples/tree/main/json/jdbc-json-basic'
    );
    expect(getStats(samples)).toMatchObject({
      total: 2,
      featured: 1,
      languages: 1
    });
  });

  it('serializes and parses route query filters', () => {
    const serialized = serializeFilters({
      query: 'graph',
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
          title: 'JSON Sample',
          description: 'JSON features',
          path: 'json',
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
    expect(graph.nodes.every((node) => node.width >= 168 && node.height >= 158 && node.height <= 184)).toBe(true);
  });

  it('represents every generated non-generic catalog tag in the default feature graph', () => {
    const graph = buildSubfeatureGraph(catalogSamples);

    expect(graph.hiddenTags).toBe(0);
    expect(graph.nodes).toHaveLength(graph.totalTags);
    expect(graph.nodes.map((node) => node.name)).toEqual(expect.arrayContaining(['Spatial', 'SQLcl']));
  });

  it('routes topic-map tags to curated engineering pattern pages', () => {
    expect(findPatternMappingByTopic('Testcontainers')?.id).toBe('local-testing');
    expect(findPatternMappingByTopic('JSON')?.id).toBe('json-documents-duality');
    expect(findPatternMappingByTopic('Duality Views')?.id).toBe('json-documents-duality');
  });

  it('resolves every curated pattern sample id against the generated catalog', () => {
    const catalogIds = new Set(catalogSamples.map((sample) => sample.id));
    const missingIds = patternMappings.flatMap((pattern) =>
      pattern.sampleIds
        .filter((sampleId) => !catalogIds.has(sampleId))
        .map((sampleId) => `${pattern.id}:${sampleId}`)
    );
    const mappedSampleIds = new Set(patternMappings.flatMap((pattern) => pattern.sampleIds));
    const unmappedIds = catalogSamples
      .filter((sample) => !mappedSampleIds.has(sample.id))
      .map((sample) => sample.id)
      .sort();
    const resolvedPatterns = resolvePatternMappings(catalogSamples);

    expect(missingIds).toEqual([]);
    expect(unmappedIds).toEqual([]);
    expect(resolvedPatterns).toHaveLength(patternMappings.length);
    expect(resolvedPatterns.every((pattern) => pattern.samples.length === pattern.sampleIds.length)).toBe(true);
    expect(resolvedPatterns[0].id).toBe('event-streaming');
  });
});
