export interface SampleSummary {
  id: string;
  title: string;
  description: string;
  path: string;
  githubCodeUrl: string;
  tags: string[];
  language: string;
  parentCollection: string;
  featured: boolean;
}

export interface SampleDetail {
  id: string;
  githubReadmeUrl: string;
  blogPost: string;
  readmeExcerpt: string;
  highlights: string[];
  features: string[];
  canonicalUrl: string;
  metaTitle: string;
  metaDescription: string;
  ogImageUrl: string;
}

export type SampleRecord = SampleSummary & SampleDetail;

export interface CatalogFilters {
  query: string;
  tags: string[];
  sort: 'featured' | 'name';
}

export interface FilterOption {
  value: string;
  count: number;
}

export interface FeatureDetail {
  description: string;
  useWhen: string;
}

export interface SubfeatureGraphNode {
  name: string;
  count: number;
  iconPath?: string;
  iconSourceLabel?: string;
  description?: string;
  useWhen?: string;
  x: number;
  y: number;
  ring: number;
  size: number;
  width: number;
  height: number;
}

export interface SubfeatureGraph {
  centerLabel: string;
  centerSubtitle: string;
  totalSamples: number;
  totalTags: number;
  hiddenTags: number;
  width: number;
  height: number;
  centerX: number;
  centerY: number;
  orbitRadii: number[];
  nodes: SubfeatureGraphNode[];
}

export interface PatternMapping {
  id: string;
  intentId: string;
  title: string;
  summary: string;
  useWhen: string;
  features: string[];
  sampleIds: string[];
}

export interface ResolvedPatternMapping extends PatternMapping {
  samples: SampleSummary[];
}

export interface PatternIntent {
  id: string;
  title: string;
  summary: string;
  color: string;
}

export interface PatternMappingData {
  intents: PatternIntent[];
  patterns: PatternMapping[];
}

export type PackedCatalogItem = [
  id: string,
  title: string,
  description: string,
  tagIds: number[],
  languageId: number,
  parentId: number,
  featuredFlag: 0 | 1
];

export interface PackedCatalogIndex {
  t: string[];
  l: string[];
  p: string[];
  f: number;
  i: PackedCatalogItem[];
}
