import fs from 'node:fs';
import path from 'node:path';
import {
  buildCanonicalUrl,
  buildSampleMetaDescription,
  buildSampleMetaTitle,
  buildSamplePath,
  DEFAULT_OG_IMAGE_URL
} from './seo-utils.mjs';

const REPO_BLOB_BASE = 'https://github.com/anders-swanson/oracle-database-code-samples/blob/main';
const REPO_TREE_BASE = 'https://github.com/anders-swanson/oracle-database-code-samples/tree/main';

const FEATURE_RULES = [
  {
    label: 'Vector Search',
    theme: 'vector',
    matches: ['vector', 'embedding', 'semantic', 'retrieval']
  },
  {
    label: 'JSON',
    theme: 'json',
    matches: ['json', 'oson', 'document']
  },
  {
    label: 'Duality Views',
    theme: 'duality',
    matches: ['duality', 'dualityviews']
  },
  {
    label: 'Property Graph',
    theme: 'graph',
    matches: ['property graph', 'property-graph', 'pgql']
  },
  {
    label: 'SQL GraphQL',
    theme: 'graphql',
    matches: ['graphql']
  },
  {
    label: 'Spatial',
    theme: 'spatial',
    matches: ['spatial', 'geometry', 'sdo']
  },
  {
    label: 'TxEventQ',
    theme: 'txeventq',
    matches: ['txeventq', 'transactional event queue', 'event streaming', 'messaging', 'jms', 'okafka']
  },
  {
    label: 'AI Agents',
    theme: 'agent',
    matches: ['agent', 'mcp', 'langchain', 'langchain4j']
  },
  {
    label: 'ORDS',
    theme: 'ords',
    matches: ['ords', 'mongodb api']
  },
  {
    label: 'Testcontainers',
    theme: 'testcontainers',
    matches: ['testcontainers']
  },
  {
    label: 'Spring',
    theme: 'spring',
    matches: ['spring']
  },
  {
    label: 'Observability',
    theme: 'observability',
    matches: ['tracing', 'opentelemetry', 'observability', 'client info']
  },
  {
    label: 'Kafka',
    theme: 'kafka',
    matches: ['kafka']
  },
  {
    label: 'Security',
    theme: 'security',
    matches: ['vault', 'security', 'oci']
  }
];

const FEATURE_ORDER = FEATURE_RULES.map((feature) => feature.label);

const FEATURED_PATHS = new Set([
  'ai-vector-search',
  'jdbc-hybrid-search',
  'langchain4j-agent-memory',
  'mcp-agent',
  'jdbc-property-graph',
  'jdbc-spatial-example',
  'txeventq-examples',
  'news-event-streaming',
  'json/jpa-duality-views',
  'oracle-ai-database-docker-compose',
  'testcontainers'
]);

const COLLECTION_PATHS = new Set([
  'database-per-service-example',
  'golang',
  'json',
  'migrate-kafka-to-oracle',
  'python-oracle',
  'spring-cloud-config',
  'sql',
  'typescript'
]);

function normalizeText(value) {
  return value.toLowerCase().replace(/[`*_]/g, '');
}

function unique(values) {
  return Array.from(new Set(values));
}

function parseFrontMatter(source) {
  const match = source.match(/^---\n([\s\S]*?)\n---\n?/);
  if (!match) {
    return null;
  }

  const frontMatterBlock = match[1];
  const metadata = {
    tags: []
  };
  let currentKey = '';

  for (const rawLine of frontMatterBlock.split('\n')) {
    const line = rawLine.trimEnd();
    if (!line) {
      continue;
    }

    const keyMatch = line.match(/^([a-z_]+):(?:\s*(.*))?$/i);
    if (keyMatch) {
      currentKey = keyMatch[1];
      const value = keyMatch[2] ?? '';
      if (currentKey === 'tags') {
        metadata.tags = [];
      } else {
        metadata[currentKey] = stripQuotes(value.trim());
      }
      continue;
    }

    if (currentKey === 'tags') {
      const tagMatch = line.match(/^\s*-\s+(.+)$/);
      if (tagMatch) {
        metadata.tags.push(stripQuotes(tagMatch[1].trim()));
      }
    }
  }

  return {
    metadata,
    body: source.slice(match[0].length)
  };
}

function stripQuotes(value) {
  return value.replace(/^"(.*)"$/, '$1').trim();
}

function extractTitle(body, fallback) {
  const line = body.split('\n').find((entry) => entry.startsWith('# '));
  return line ? line.replace(/^# /, '').trim() : fallback;
}

function extractExcerpt(body) {
  const sanitized = body
    .replace(/```[\s\S]*?```/g, '')
    .split('\n')
    .map((line) => line.trim());

  const paragraphs = [];
  let currentParagraph = [];

  for (const line of sanitized) {
    if (!line || line.startsWith('#') || line.startsWith('- ') || line.startsWith('* ')) {
      if (currentParagraph.length > 0) {
        paragraphs.push(currentParagraph.join(' '));
        currentParagraph = [];
      }
      continue;
    }

    currentParagraph.push(line);
  }

  if (currentParagraph.length > 0) {
    paragraphs.push(currentParagraph.join(' '));
  }

  return paragraphs[0] ?? '';
}

function extractHighlights(body) {
  const lines = body.split('\n').map((line) => line.trim());
  const highlights = [];

  for (const line of lines) {
    if (line.startsWith('## ')) {
      if (highlights.length > 0) {
        break;
      }
      continue;
    }

    const bulletMatch = line.match(/^[-*] (.+)$/);
    if (bulletMatch) {
      highlights.push(bulletMatch[1].replace(/`/g, ''));
      if (highlights.length === 4) {
        break;
      }
    }
  }

  return highlights;
}

function detectLanguage(relativeDirectory, metadata, content) {
  const haystack = `${relativeDirectory} ${metadata.tags.join(' ')} ${content}`.toLowerCase();

  if (relativeDirectory.startsWith('golang/') || relativeDirectory === 'golang' || haystack.includes(' go ')) {
    return 'Go';
  }
  if (relativeDirectory.startsWith('python-oracle/') || relativeDirectory === 'python-oracle' || haystack.includes('python')) {
    return 'Python';
  }
  if (relativeDirectory.startsWith('typescript/') || relativeDirectory === 'typescript' || haystack.includes('typescript') || haystack.includes('nodejs')) {
    return 'TypeScript';
  }
  if (relativeDirectory === 'sql' || relativeDirectory.startsWith('sql/')) {
    return 'SQL';
  }
  if (haystack.includes('spring') || haystack.includes('java') || relativeDirectory.includes('jdbc') || relativeDirectory.includes('jms')) {
    return 'Java';
  }

  return 'Script';
}

function cleanTags(tags, language) {
  return tags.filter((tag) => {
    if (tag === 'Database') {
      return false;
    }
    if (language === 'Java' && tag === 'JDBC') {
      return false;
    }
    return true;
  });
}

function cleanFeatures(features) {
  return features.filter((feature) => feature !== 'Database Provisioning');
}

function detectFeatures(relativeDirectory, description, tags) {
  const haystack = normalizeText(`${relativeDirectory} ${description} ${tags.join(' ')}`);

  const matched = FEATURE_RULES
    .filter((feature) => feature.matches.some((match) => haystack.includes(match)))
    .map((feature) => feature.label);

  if (matched.length === 0) {
    return ['Oracle AI Database'];
  }

  return matched.sort((left, right) => FEATURE_ORDER.indexOf(left) - FEATURE_ORDER.indexOf(right));
}

function detectParentCollection(relativeDirectory) {
  const parts = relativeDirectory.split('/');
  if (parts.length === 1) {
    return 'Standalone';
  }

  const topLevel = parts[0];
  if (COLLECTION_PATHS.has(topLevel)) {
    return topLevel;
  }

  return topLevel;
}

function buildId(relativeDirectory) {
  return relativeDirectory.replace(/\//g, '--');
}

export function parseReadmeFile(repoRoot, fullPath) {
  const source = fs.readFileSync(fullPath, 'utf8');
  const parsed = parseFrontMatter(source);
  if (!parsed) {
    return null;
  }

  const relativeReadmePath = path.relative(repoRoot, fullPath).replace(/\\/g, '/');
  const relativeDirectory = path.posix.dirname(relativeReadmePath);
  const { metadata, body } = parsed;

  return {
    relativeReadmePath,
    relativeDirectory,
    metadata,
    body,
    title: extractTitle(body, metadata.name || relativeDirectory),
    excerpt: extractExcerpt(body),
    highlights: extractHighlights(body)
  };
}

export function parseReadmeSource(relativeReadmePath, source) {
  const parsed = parseFrontMatter(source);
  if (!parsed) {
    return null;
  }

  const relativeDirectory = path.posix.dirname(relativeReadmePath);
  const { metadata, body } = parsed;

  return {
    relativeReadmePath,
    relativeDirectory,
    metadata,
    body,
    title: extractTitle(body, metadata.name || relativeDirectory),
    excerpt: extractExcerpt(body),
    highlights: extractHighlights(body)
  };
}

export function deriveSampleRecord(parsed) {
  const {
    relativeDirectory,
    relativeReadmePath,
    metadata,
    body,
    title,
    excerpt,
    highlights
  } = parsed;

  const baseTags = unique(
    (metadata.tags || [])
      .map((tag) => tag.trim())
      .filter(Boolean)
      .sort((left, right) => left.localeCompare(right))
  );
  const description = metadata.description?.trim() || excerpt || title;
  const language = detectLanguage(relativeDirectory, metadata, `${description} ${body}`);
  const tags = cleanTags(baseTags, language);
  const features = cleanFeatures(detectFeatures(relativeDirectory, description, tags));
  const urlPath = buildSamplePath(buildId(relativeDirectory));
  const metaDescription = buildSampleMetaDescription({
    title,
    description,
    readmeExcerpt: excerpt
  });

  return {
    id: buildId(relativeDirectory),
    name: metadata.name?.trim() || relativeDirectory,
    title,
    description,
    path: relativeDirectory,
    readmePath: relativeReadmePath,
    githubReadmeUrl: `${REPO_BLOB_BASE}/${relativeReadmePath}`,
    githubCodeUrl: `${REPO_TREE_BASE}/${relativeDirectory}`,
    tags,
    features,
    language,
    parentCollection: detectParentCollection(relativeDirectory),
    blogPost: metadata.blog_post?.trim() || '',
    readmeExcerpt: excerpt || description,
    highlights,
    featured: FEATURED_PATHS.has(relativeDirectory),
    urlPath,
    canonicalUrl: buildCanonicalUrl(urlPath),
    metaTitle: buildSampleMetaTitle(title),
    metaDescription,
    ogImageUrl: DEFAULT_OG_IMAGE_URL
  };
}
