export interface SampleRecord {
  id: string;
  name: string;
  title: string;
  description: string;
  path: string;
  readmePath: string;
  githubReadmeUrl: string;
  githubCodeUrl: string;
  tags: string[];
  features: string[];
  language: string;
  parentCollection: string;
  blogPost: string;
  readmeExcerpt: string;
  highlights: string[];
  featured: boolean;
  urlPath: string;
  canonicalUrl: string;
  metaTitle: string;
  metaDescription: string;
  ogImageUrl: string;
}

export interface CatalogFilters {
  query: string;
  features: string[];
  languages: string[];
  tags: string[];
  sort: 'featured' | 'name';
}

export interface FilterOption {
  value: string;
  count: number;
}

export interface FeatureSummary {
  name: string;
  theme: string;
  count: number;
  description: string;
}

export interface SubfeatureGraphNode {
  name: string;
  count: number;
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
