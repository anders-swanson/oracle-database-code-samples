import rawCatalogIndex from '../data/catalog-index.json';
import { getFeatureDetail } from '../data/featureDetails';
import { getFeatureIcon } from '../data/featureIcons';
import rawLanguagePages from '../data/language-pages.json';
import rawPatternMappings from '../data/patternMappings.json';
import type {
  CatalogFilters,
  FilterOption,
  LanguageLandingPage,
  PackedCatalogIndex,
  PatternIntent,
  PatternMapping,
  PatternMappingData,
  ResolvedPatternMapping,
  SampleSummary,
  SubfeatureGraph
} from '../types';

const REPO_TREE_BASE = 'https://github.com/anders-swanson/oracle-database-code-samples/tree/main';

const catalogIndex = rawCatalogIndex as PackedCatalogIndex;
const languagePageData = rawLanguagePages as LanguageLandingPage[];
const patternMappingData = rawPatternMappings as PatternMappingData;

export const patternIntents = patternMappingData.intents as PatternIntent[];
export const patternMappings = patternMappingData.patterns as PatternMapping[];
export const languagePages = languagePageData;

export const samples = decodeCatalogIndex(catalogIndex);
const sampleById = new Map(samples.map((sample) => [sample.id, sample]));
const languagePageBySlug = new Map(languagePages.map((page) => [page.slug, page]));
const languagePageByName = new Map(languagePages.map((page) => [page.name, page]));

export const defaultFilters: CatalogFilters = {
  query: '',
  tags: [],
  sort: 'featured'
};

const subfeatureGraphExcludedTags = new Set(['Java', 'Go', 'NodeJS', 'python', 'TypeScript', 'docker', 'oraclefree']);
const subfeatureGraphRingCapacity = [6, 8, 10, 12];
const subfeatureGraphOrbitRadii = [300, 520, 760, 980];
const subfeatureGraphWorldWidth = 2400;
const subfeatureGraphWorldHeight = 1800;
const subfeatureGraphCenterX = subfeatureGraphWorldWidth / 2;
const subfeatureGraphCenterY = subfeatureGraphWorldHeight / 2;
const subfeatureGraphCenterRadius = 188;

export function sampleIdToPath(id: string) {
  return id.replace(/--/g, '/');
}

export function buildSamplePath(id: string) {
  return `/samples/${id}/`;
}

export function buildGithubCodeUrl(id: string) {
  return `${REPO_TREE_BASE}/${sampleIdToPath(id)}`;
}

export function decodeCatalogIndex(index: PackedCatalogIndex): SampleSummary[] {
  return index.i.map(([id, title, description, tagIds, languageId, parentId, featuredFlag]) => ({
    id,
    title,
    description,
    path: sampleIdToPath(id),
    githubCodeUrl: buildGithubCodeUrl(id),
    tags: tagIds.map((tagId) => index.t[tagId]),
    language: index.l[languageId],
    parentCollection: index.p[parentId],
    featured: featuredFlag === 1
  }));
}

function intersects(
  left: { x: number; y: number; width: number; height: number },
  right: { x: number; y: number; width: number; height: number }
) {
  return !(
    left.x + left.width / 2 <= right.x - right.width / 2 ||
    left.x - left.width / 2 >= right.x + right.width / 2 ||
    left.y + left.height / 2 <= right.y - right.height / 2 ||
    left.y - left.height / 2 >= right.y + right.height / 2
  );
}

function optionList(values: string[]) {
  const counts = new Map<string, number>();

  for (const value of values) {
    counts.set(value, (counts.get(value) ?? 0) + 1);
  }

  return Array.from(counts.entries())
    .map(([value, count]) => ({ value, count }))
    .sort((left, right) => left.value.localeCompare(right.value));
}

export function getFilterOptions(items: SampleSummary[]) {
  return {
    tags: optionList(items.flatMap((sample) => sample.tags))
  };
}

function matchesEvery(selected: string[], actualValues: string[]) {
  return selected.length === 0 || selected.every((value) => actualValues.includes(value));
}

export function filterSamples(items: SampleSummary[], filters: CatalogFilters) {
  const query = filters.query.trim().toLowerCase();

  const filtered = items.filter((sample) => {
    const searchHaystack = [
      sample.title,
      sample.description,
      sample.path,
      sample.language,
      sample.parentCollection,
      ...sample.tags
    ]
      .join(' ')
      .toLowerCase();

    const queryMatch = query.length === 0 || searchHaystack.includes(query);

    return queryMatch && matchesEvery(filters.tags, sample.tags);
  });

  return filtered.sort((left, right) => {
    if (filters.sort === 'name') {
      return left.title.localeCompare(right.title);
    }
    if (left.featured !== right.featured) {
      return left.featured ? -1 : 1;
    }
    return left.title.localeCompare(right.title);
  });
}

export function findSampleById(id: string) {
  return sampleById.get(id);
}

export function findLanguagePageBySlug(slug: string) {
  return languagePageBySlug.get(slug);
}

export function findLanguagePageByName(name: string) {
  return languagePageByName.get(name);
}

export function samplesForIds(sampleIds: string[], items: SampleSummary[] = samples) {
  const sampleLookup = items === samples ? sampleById : new Map(items.map((sample) => [sample.id, sample]));
  return sampleIds
    .map((sampleId) => sampleLookup.get(sampleId))
    .filter((sample): sample is SampleSummary => Boolean(sample));
}

export function resolvePatternMappings(items: SampleSummary[] = samples): ResolvedPatternMapping[] {
  const sampleLookup = items === samples ? sampleById : new Map(items.map((sample) => [sample.id, sample]));

  return patternMappings.map((pattern) => ({
    ...pattern,
    samples: pattern.sampleIds
      .map((sampleId) => sampleLookup.get(sampleId))
      .filter((sample): sample is SampleSummary => Boolean(sample))
  }));
}

export const resolvedPatternMappings = resolvePatternMappings();

const patternMappingById = new Map(resolvedPatternMappings.map((pattern) => [pattern.id, pattern]));

function patternMappingsForItems(items: SampleSummary[]) {
  return items === samples ? resolvedPatternMappings : resolvePatternMappings(items);
}

function normalizeTopic(value: string) {
  return value.trim().toLowerCase();
}

function uniqueValues(values: string[]) {
  return Array.from(new Set(values));
}

function patternTopicValues(pattern: PatternMapping) {
  return uniqueValues([pattern.title, ...pattern.features, ...pattern.topics]);
}

function patternTitleAndFeatureValues(pattern: PatternMapping) {
  return uniqueValues([pattern.title, ...pattern.features]);
}

export function findPatternMappingBySlug(slug: string, items: SampleSummary[] = samples) {
  return items === samples
    ? patternMappingById.get(slug)
    : resolvePatternMappings(items).find((pattern) => pattern.id === slug);
}

export function patternMappingsForIds(patternIds: string[], items: SampleSummary[] = samples) {
  const patternLookup =
    items === samples
      ? patternMappingById
      : new Map(resolvePatternMappings(items).map((pattern) => [pattern.id, pattern]));

  return patternIds
    .map((patternId) => patternLookup.get(patternId))
    .filter((pattern): pattern is ResolvedPatternMapping => Boolean(pattern));
}

export function findPatternMappingByTopic(topic: string, items: SampleSummary[] = samples) {
  const normalizedTopic = normalizeTopic(topic);
  const resolvedPatterns = patternMappingsForItems(items);
  const explicitTopicMatch = resolvedPatterns.find((pattern) =>
    pattern.topics.some((patternTopic) => normalizeTopic(patternTopic) === normalizedTopic)
  );

  if (explicitTopicMatch) {
    return explicitTopicMatch;
  }

  const directPatternMatch = resolvedPatterns.find((pattern) =>
    patternTitleAndFeatureValues(pattern).some((patternTopic) => normalizeTopic(patternTopic) === normalizedTopic)
  );

  if (directPatternMatch) {
    return directPatternMatch;
  }

  const topicSampleIds = new Set(
    items
      .filter((sample) => sample.tags.some((tag) => normalizeTopic(tag) === normalizedTopic))
      .map((sample) => sample.id)
  );

  if (topicSampleIds.size === 0) {
    return undefined;
  }

  return resolvedPatterns
    .map((pattern) => ({
      pattern,
      overlap: pattern.sampleIds.filter((sampleId) => topicSampleIds.has(sampleId)).length
    }))
    .filter((entry) => entry.overlap > 0)
    .sort(
      (left, right) =>
        right.overlap - left.overlap ||
        left.pattern.sampleIds.length - right.pattern.sampleIds.length ||
        left.pattern.title.localeCompare(right.pattern.title)
    )[0]?.pattern;
}

export function findRelatedPatternMappings(pattern: PatternMapping, limit = 6, items: SampleSummary[] = samples) {
  const patternTopics = new Set(patternTopicValues(pattern).map(normalizeTopic));
  const patternSamples = new Set(pattern.sampleIds);

  return patternMappingsForItems(items)
    .filter((candidate) => candidate.id !== pattern.id)
    .map((candidate) => {
      const sharedTopics = patternTopicValues(candidate).filter((topic) => patternTopics.has(normalizeTopic(topic)))
        .length;
      const sharedSamples = candidate.sampleIds.filter((sampleId) => patternSamples.has(sampleId)).length;
      const sameIntent = candidate.intentId === pattern.intentId ? 1 : 0;

      return {
        pattern: candidate,
        score: sameIntent * 4 + sharedTopics * 2 + sharedSamples
      };
    })
    .filter((entry) => entry.score > 0)
    .sort((left, right) => right.score - left.score || left.pattern.title.localeCompare(right.pattern.title))
    .slice(0, limit)
    .map((entry) => entry.pattern);
}

export function findRelatedSamples(target: SampleSummary, items: SampleSummary[], limit = 4) {
  return items
    .filter((candidate) => candidate.id !== target.id)
    .map((candidate) => ({
      sample: candidate,
      score:
        candidate.tags.filter((tag) => target.tags.includes(tag)).length * 2 +
        Number(candidate.parentCollection === target.parentCollection) +
        Number(candidate.language === target.language)
    }))
    .filter((entry) => entry.score > 0)
    .sort((left, right) => right.score - left.score || left.sample.title.localeCompare(right.sample.title))
    .slice(0, limit)
    .map((entry) => entry.sample);
}

export function getStats(items: SampleSummary[]) {
  const featuredCount = items.filter((sample) => sample.featured).length;
  const languageCount = new Set(items.map((sample) => sample.language)).size;

  return {
    total: items.length,
    featured: featuredCount,
    languages: languageCount,
    features: catalogIndex.f
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
    tags: parseQueryList(query.tags),
    sort
  };
}

export function topFilterOptions(options: FilterOption[], limit: number) {
  return [...options]
    .sort((left, right) => right.count - left.count || left.value.localeCompare(right.value))
    .slice(0, limit);
}

export function buildSubfeatureGraph(items: SampleSummary[], limit = Number.POSITIVE_INFINITY): SubfeatureGraph {
  const allTags = optionList(
    items.flatMap((sample) => sample.tags.filter((tag) => !subfeatureGraphExcludedTags.has(tag)))
  ).sort((left, right) => right.count - left.count || left.value.localeCompare(right.value));

  const visibleTags = allTags.slice(0, limit);
  const maxCount = visibleTags[0]?.count ?? 1;
  const placedNodes: { x: number; y: number; width: number; height: number }[] = [
    {
      x: subfeatureGraphCenterX,
      y: subfeatureGraphCenterY,
      width: subfeatureGraphCenterRadius * 2,
      height: subfeatureGraphCenterRadius * 2
    }
  ];
  const nodes = visibleTags.map((tag, index) => {
    let ring = 0;
    let nodesBeforeRing = 0;

    for (let currentRing = 0; currentRing < subfeatureGraphRingCapacity.length; currentRing += 1) {
      const nextTotal = nodesBeforeRing + subfeatureGraphRingCapacity[currentRing];
      if (index < nextTotal) {
        ring = currentRing;
        break;
      }
      nodesBeforeRing = nextTotal;
      ring = currentRing + 1;
    }

    const ringCount = Math.min(Math.max(visibleTags.length - nodesBeforeRing, 0), subfeatureGraphRingCapacity[ring] ?? 12);
    const ringIndex = index - nodesBeforeRing;
    const angleOffset = ring * 0.28;
    const baseAngle = -Math.PI / 2 + angleOffset + (Math.PI * 2 * ringIndex) / Math.max(ringCount, 1);
    const baseRadius = subfeatureGraphOrbitRadii[ring] ?? subfeatureGraphOrbitRadii[subfeatureGraphOrbitRadii.length - 1];
    const width = Math.max(168, Math.min(250, 118 + tag.value.length * 8));
    const height = Math.max(158, Math.min(184, Math.round(width * 0.82)));
    const icon = getFeatureIcon(tag.value);
    const detail = getFeatureDetail(tag.value);
    const padding = 34;
    let placed = {
      name: tag.value,
      count: tag.count,
      iconPath: icon?.iconPath,
      iconSourceLabel: icon?.sourceLabel,
      description: detail?.description,
      useWhen: detail?.useWhen,
      x: subfeatureGraphCenterX + Math.cos(baseAngle) * baseRadius,
      y: subfeatureGraphCenterY + Math.sin(baseAngle) * baseRadius,
      ring,
      size: Number((8 + (tag.count / maxCount) * 4).toFixed(2)),
      width,
      height
    };

    for (let attempt = 0; attempt < 72; attempt += 1) {
      const angle = baseAngle + attempt * 0.23;
      const radius = baseRadius + Math.floor(attempt / 8) * 54;
      const candidate = {
        ...placed,
        x: Math.max(
          width / 2 + padding,
          Math.min(subfeatureGraphWorldWidth - width / 2 - padding, subfeatureGraphCenterX + Math.cos(angle) * radius)
        ),
        y: Math.max(
          height / 2 + padding,
          Math.min(subfeatureGraphWorldHeight - height / 2 - padding, subfeatureGraphCenterY + Math.sin(angle) * radius)
        )
      };

      const hasCollision = placedNodes.some((node) =>
        intersects(
          {
            x: candidate.x,
            y: candidate.y,
            width: candidate.width + padding,
            height: candidate.height + padding
          },
          {
            x: node.x,
            y: node.y,
            width: node.width + padding,
            height: node.height + padding
          }
        )
      );

      if (!hasCollision) {
        placed = candidate;
        break;
      }
    }

    placedNodes.push({
      x: placed.x,
      y: placed.y,
      width: placed.width,
      height: placed.height
    });

    return placed;
  });

  return {
    centerLabel: 'Oracle AI Database',
    centerSubtitle: 'Converged Database',
    totalSamples: items.length,
    totalTags: allTags.length,
    hiddenTags: Math.max(allTags.length - visibleTags.length, 0),
    width: subfeatureGraphWorldWidth,
    height: subfeatureGraphWorldHeight,
    centerX: subfeatureGraphCenterX,
    centerY: subfeatureGraphCenterY,
    orbitRadii: subfeatureGraphOrbitRadii,
    nodes
  };
}
