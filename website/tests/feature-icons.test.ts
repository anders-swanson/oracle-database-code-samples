import { describe, expect, it } from 'vitest';
import { getFeatureDetail } from '../src/data/featureDetails';
import { getFeatureIcon } from '../src/data/featureIcons';
import { buildSubfeatureGraph, samples } from '../src/lib/catalog';

describe('feature icon mapping', () => {
  it('maps close feature names to icons from the Oracle icon deck', () => {
    expect(getFeatureIcon('Spatial')).toEqual({
      iconPath: '/feature-icons/spatial.svg',
      sourceLabel: 'Spatial'
    });
    expect(getFeatureIcon('SQL')).toEqual({
      iconPath: '/feature-icons/plsql.svg',
      sourceLabel: 'Database Badge SQL'
    });
    expect(getFeatureIcon('SQLcl')).toEqual({
      iconPath: '/feature-icons/sqlcl.svg',
      sourceLabel: 'SQL-Developer-Command-Line'
    });
    expect(getFeatureIcon('Property Graph')).toEqual({
      iconPath: '/feature-icons/graph.svg',
      sourceLabel: 'Graph'
    });
    expect(getFeatureIcon('SQL GraphQL')).toEqual({
      iconPath: '/feature-icons/graphql.svg',
      sourceLabel: 'API'
    });
  });

  it('covers every default feature-map node with an icon', () => {
    const graph = buildSubfeatureGraph(samples);
    const missingIcons = graph.nodes.filter((node) => !getFeatureIcon(node.name)).map((node) => node.name);

    expect(missingIcons).toEqual([]);
  });

  it('covers every default feature-map node with hover details', () => {
    const graph = buildSubfeatureGraph(samples);
    const missingDetails = graph.nodes
      .filter((node) => !getFeatureDetail(node.name) || !node.description || !node.useWhen)
      .map((node) => node.name);

    expect(missingDetails).toEqual([]);
  });

  it('leaves unmapped tags without an icon', () => {
    expect(getFeatureIcon('not-a-feature')).toBeUndefined();
    expect(getFeatureDetail('not-a-feature')).toBeUndefined();
  });
});
