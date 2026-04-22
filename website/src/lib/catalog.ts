import rawSamples from '../data/samples.json';
import { featureDescriptions } from '../data/featureDescriptions';
import type { CatalogFilters, FeatureSummary, FilterOption, SampleRecord } from '../types';

export const samples = rawSamples as SampleRecord[];

export const defaultFilters: CatalogFilters = {
  query: '',
  features: [],
  languages: [],
  tags: [],
  sort: 'featured'
};

const featureThemes: Record<string, string> = {
  'Vector Search': 'vector',
  JSON: 'json',
  'Duality Views': 'duality',
  'Property Graph': 'graph',
  'SQL GraphQL': 'graphql',
  Spatial: 'spatial',
  TxEventQ: 'txeventq',
  'AI Agents': 'agent',
  ORDS: 'ords',
  Testcontainers: 'testcontainers',
  Spring: 'spring',
  Observability: 'observability',
  Kafka: 'kafka',
  Security: 'security',
  'Oracle AI Database': 'default'
};

function optionList(values: string[]) {
  const counts = new Map<string, number>();

  for (const value of values) {
    counts.set(value, (counts.get(value) ?? 0) + 1);
  }

  return Array.from(counts.entries())
    .map(([value, count]) => ({ value, count }))
    .sort((left, right) => left.value.localeCompare(right.value));
}

export function getFilterOptions(items: SampleRecord[]) {
  return {
    features: optionList(items.flatMap((sample) => sample.features)),
    languages: optionList(items.map((sample) => sample.language)),
    tags: optionList(items.flatMap((sample) => sample.tags))
  };
}

function matchesEvery(selected: string[], actualValues: string[]) {
  return selected.length === 0 || selected.every((value) => actualValues.includes(value));
}

export function filterSamples(items: SampleRecord[], filters: CatalogFilters) {
  const query = filters.query.trim().toLowerCase();

  const filtered = items.filter((sample) => {
    const searchHaystack = [
      sample.name,
      sample.title,
      sample.description,
      sample.path,
      sample.language,
      sample.parentCollection,
      ...sample.tags,
      ...sample.features
    ]
      .join(' ')
      .toLowerCase();

    const queryMatch = query.length === 0 || searchHaystack.includes(query);

    return (
      queryMatch &&
      matchesEvery(filters.features, sample.features) &&
      matchesEvery(filters.languages, [sample.language]) &&
      matchesEvery(filters.tags, sample.tags)
    );
  });

  return filtered.sort((left, right) => {
    if (filters.sort === 'name') {
      return left.name.localeCompare(right.name);
    }
    if (left.featured !== right.featured) {
      return left.featured ? -1 : 1;
    }
    return left.name.localeCompare(right.name);
  });
}

export function summarizeFeatures(items: SampleRecord[]): FeatureSummary[] {
  const counts = new Map<string, number>();

  for (const sample of items) {
    for (const feature of sample.features) {
      counts.set(feature, (counts.get(feature) ?? 0) + 1);
    }
  }

  return Array.from(counts.entries())
    .map(([name, count]) => ({
      name,
      count,
      theme: featureThemes[name] ?? 'default',
      description: featureDescriptions[name] ?? featureDescriptions['Oracle AI Database']
    }))
    .sort((left, right) => right.count - left.count);
}

export function findSampleById(id: string) {
  return samples.find((sample) => sample.id === id);
}

export function findRelatedSamples(target: SampleRecord, items: SampleRecord[], limit = 4) {
  return items
    .filter((candidate) => candidate.id !== target.id)
    .map((candidate) => ({
      sample: candidate,
      score:
        candidate.features.filter((feature) => target.features.includes(feature)).length * 3 +
        candidate.tags.filter((tag) => target.tags.includes(tag)).length * 2 +
        Number(candidate.language === target.language)
    }))
    .filter((entry) => entry.score > 0)
    .sort((left, right) => right.score - left.score || left.sample.name.localeCompare(right.sample.name))
    .slice(0, limit)
    .map((entry) => entry.sample);
}

export function getStats(items: SampleRecord[]) {
  const featuredCount = items.filter((sample) => sample.featured).length;
  const languageCount = new Set(items.map((sample) => sample.language)).size;
  const featureCount = new Set(items.flatMap((sample) => sample.features)).size;

  return {
    total: items.length,
    featured: featuredCount,
    languages: languageCount,
    features: featureCount
  };
}

export function parseQueryList(value: unknown) {
  if (typeof value !== 'string' || value.trim().length === 0) {
    return [];
  }

  return value
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean);
}

export function serializeFilters(filters: CatalogFilters) {
  const query: Record<string, string> = {};

  if (filters.query) {
    query.q = filters.query;
  }
  if (filters.tags.length > 0) {
    query.tags = filters.tags.join(',');
  }
  if (filters.sort !== 'featured') {
    query.sort = filters.sort;
  }

  return query;
}

export function routeQueryToFilters(query: Record<string, unknown>): CatalogFilters {
  const sort = query.sort === 'name' ? query.sort : 'featured';

  return {
    query: typeof query.q === 'string' ? query.q : '',
    features: [],
    languages: [],
    tags: parseQueryList(query.tags),
    sort
  };
}

export function topFilterOptions(options: FilterOption[], limit: number) {
  return [...options].sort((left, right) => right.count - left.count || left.value.localeCompare(right.value)).slice(0, limit);
}
