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
