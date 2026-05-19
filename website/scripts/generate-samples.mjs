import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  deriveSampleRecord,
  parseReadmeFile
} from './sample-catalog-utils.mjs';
import { buildPatternMappings } from './pattern-mapping-utils.mjs';
import {
  buildCanonicalUrl,
  buildFeaturePath,
  buildLanguagePath,
  DEFAULT_DESCRIPTION,
  SAMPLE_SOCIAL_CARD_DIRECTORY,
  SITE_NAME,
  trimDescription
} from './seo-utils.mjs';
import { writeDefaultSocialCard, writeSampleSocialCards } from './social-card-utils.mjs';

const currentDir = path.dirname(fileURLToPath(import.meta.url));
const websiteRoot = path.resolve(currentDir, '..');
const repoRoot = path.resolve(websiteRoot, '..');
const catalogIndexPath = path.join(websiteRoot, 'src', 'data', 'catalog-index.json');
const sampleDetailsDirectory = path.join(websiteRoot, 'src', 'data', 'sample-details');
const featureDetailsPath = path.join(websiteRoot, 'src', 'data', 'featureDetails.json');
const featurePagesPath = path.join(websiteRoot, 'src', 'data', 'feature-pages.json');
const languagePagesPath = path.join(websiteRoot, 'src', 'data', 'language-pages.json');
const patternDefinitionsPath = path.join(websiteRoot, 'src', 'data', 'patternDefinitions.json');
const patternMappingsPath = path.join(websiteRoot, 'src', 'data', 'patternMappings.json');
const languagePageMinimumSampleCount = 3;
const skipDirectories = new Set([
  '.git',
  'node_modules',
  'website'
]);

const languageCopy = {
  Go: {
    description: 'Go samples for connecting services, tests, and database-backed workflows to Oracle AI Database.',
    useWhen: 'Use when Go services need runnable database access patterns, local containers, or integration-test setup.'
  },
  Java: {
    description: 'Java samples for JDBC, Spring Boot, messaging, search, JSON, graph, spatial, and test workflows on Oracle AI Database.',
    useWhen: 'Use when JVM applications need real Oracle AI Database examples instead of pseudocode.'
  },
  Python: {
    description: 'Python samples for database connectivity, full-text search, LangChain, MCP agents, and Testcontainers with Oracle AI Database.',
    useWhen: 'Use when Python tools or AI workflows need runnable database-backed examples.'
  },
  Script: {
    description: 'Script-oriented samples for local Oracle AI Database setup and command-line workflows.',
    useWhen: 'Use when setup, orchestration, or one-command local environments are the main concern.'
  },
  SQL: {
    description: 'SQL samples for exploring Oracle AI Database features directly from database scripts.',
    useWhen: 'Use when the core behavior is best understood through SQL statements and database-native APIs.'
  },
  TypeScript: {
    description: 'TypeScript samples for Node.js applications, eventing, and test workflows backed by Oracle AI Database.',
    useWhen: 'Use when JavaScript or TypeScript services need runnable database-backed examples.'
  }
};

function walkDirectory(directory, readmes = []) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    if (skipDirectories.has(entry.name)) {
      continue;
    }

    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      walkDirectory(fullPath, readmes);
      continue;
    }

    if (entry.isFile() && entry.name === 'README.md') {
      readmes.push(fullPath);
    }
  }

  return readmes;
}

function validateReversibleSampleIds(samples) {
  const invalidPaths = samples
    .map((sample) => sample.path)
    .filter((samplePath) => samplePath.split('/').some((segment) => segment.includes('--')));

  if (invalidPaths.length > 0) {
    throw new Error(`Sample paths cannot contain "--" because ids must be reversible: ${invalidPaths.join(', ')}`);
  }
}

function buildIndexDictionary(samples, key) {
  return Array.from(new Set(samples.map((sample) => sample[key]))).sort((left, right) => left.localeCompare(right));
}

function buildTagDictionary(samples) {
  return Array.from(new Set(samples.flatMap((sample) => sample.tags))).sort((left, right) => left.localeCompare(right));
}

function buildCatalogIndex(samples) {
  const tags = buildTagDictionary(samples);
  const languages = buildIndexDictionary(samples, 'language');
  const parents = buildIndexDictionary(samples, 'parentCollection');
  const tagIds = new Map(tags.map((tag, index) => [tag, index]));
  const languageIds = new Map(languages.map((language, index) => [language, index]));
  const parentIds = new Map(parents.map((parent, index) => [parent, index]));
  const featureCount = new Set(samples.flatMap((sample) => sample.features)).size;

  return {
    t: tags,
    l: languages,
    p: parents,
    f: featureCount,
    i: samples
      .map((sample) => [
        sample.id,
        sample.title,
        sample.description,
        sample.tags.map((tag) => tagIds.get(tag)),
        languageIds.get(sample.language),
        parentIds.get(sample.parentCollection),
        sample.featured ? 1 : 0
      ])
      .sort((left, right) => left[1].localeCompare(right[1]))
  };
}

function slugify(value) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

function maxUpdatedAt(samples) {
  return samples
    .map((sample) => sample.sourceUpdatedAt)
    .sort()
    .at(-1) ?? '1970-01-01T00:00:00.000Z';
}

function buildRelatedFeatureSlugs(featureName, samples, featureSlugs, limit = 6) {
  const counts = new Map();

  for (const sample of samples) {
    if (!sample.features.includes(featureName)) {
      continue;
    }

    for (const feature of sample.features) {
      if (feature === featureName || !featureSlugs.has(feature)) {
        continue;
      }
      counts.set(feature, (counts.get(feature) ?? 0) + 1);
    }
  }

  return Array.from(counts.entries())
    .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]))
    .slice(0, limit)
    .map(([feature]) => featureSlugs.get(feature));
}

function buildFeaturePages(samples, featureDetails) {
  const featureNames = Array.from(new Set(samples.flatMap((sample) => sample.features)))
    .filter((feature) => feature !== 'Oracle AI Database')
    .sort((left, right) => left.localeCompare(right));
  const featureSlugs = new Map(featureNames.map((feature) => [feature, slugify(feature)]));

  return featureNames
    .map((feature) => {
      const featureSamples = samples
        .filter((sample) => sample.features.includes(feature))
        .sort((left, right) => left.title.localeCompare(right.title));
      const detail = featureDetails[feature] ?? {
        description: `Runnable samples for ${feature} in Oracle AI Database.`,
        useWhen: `Use when applications need ${feature} behavior backed by Oracle AI Database.`
      };
      const slug = featureSlugs.get(feature);
      const sampleWord = featureSamples.length === 1 ? 'sample' : 'samples';
      const title = `Oracle AI Database ${feature} Samples`;
      const description = `${detail.description} Browse ${featureSamples.length} runnable ${sampleWord} with linked source code.`;

      return {
        slug,
        name: feature,
        title,
        description,
        useWhen: detail.useWhen,
        sampleIds: featureSamples.map((sample) => sample.id),
        relatedFeatureSlugs: buildRelatedFeatureSlugs(feature, samples, featureSlugs),
        canonicalUrl: buildCanonicalUrl(buildFeaturePath(slug)),
        metaTitle: `${title} | ${SITE_NAME}`,
        metaDescription: trimDescription(description || DEFAULT_DESCRIPTION),
        updatedAt: maxUpdatedAt(featureSamples)
      };
    })
    .sort((left, right) => right.sampleIds.length - left.sampleIds.length || left.name.localeCompare(right.name));
}

function buildLanguagePages(samples, featurePages) {
  const featureByName = new Map(featurePages.map((feature) => [feature.name, feature]));
  const languageNames = Array.from(new Set(samples.map((sample) => sample.language))).sort((left, right) =>
    left.localeCompare(right)
  );

  return languageNames
    .map((language) => {
      const languageSamples = samples
        .filter((sample) => sample.language === language)
        .sort((left, right) => left.title.localeCompare(right.title));

      if (languageSamples.length < languagePageMinimumSampleCount) {
        return null;
      }

      const copy = languageCopy[language] ?? {
        description: `${language} samples for Oracle AI Database.`,
        useWhen: `Use when ${language} applications need runnable Oracle AI Database examples.`
      };
      const featureCounts = new Map();
      for (const sample of languageSamples) {
        for (const feature of sample.features) {
          if (featureByName.has(feature)) {
            featureCounts.set(feature, (featureCounts.get(feature) ?? 0) + 1);
          }
        }
      }
      const relatedFeatureSlugs = Array.from(featureCounts.entries())
        .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]))
        .slice(0, 8)
        .map(([feature]) => featureByName.get(feature).slug);
      const slug = slugify(language);
      const title = `${language} Samples for Oracle AI Database`;
      const description = `${copy.description} Browse ${languageSamples.length} runnable samples with linked source code.`;

      return {
        slug,
        name: language,
        title,
        description,
        useWhen: copy.useWhen,
        sampleIds: languageSamples.map((sample) => sample.id),
        relatedFeatureSlugs,
        canonicalUrl: buildCanonicalUrl(buildLanguagePath(slug)),
        metaTitle: `${title} | ${SITE_NAME}`,
        metaDescription: trimDescription(description || DEFAULT_DESCRIPTION),
        updatedAt: maxUpdatedAt(languageSamples)
      };
    })
    .filter(Boolean)
    .sort((left, right) => right.sampleIds.length - left.sampleIds.length || left.name.localeCompare(right.name));
}

function buildSampleDetail(sample) {
  return {
    id: sample.id,
    githubReadmeUrl: sample.githubReadmeUrl,
    blogPost: sample.blogPost,
    readmeExcerpt: sample.readmeExcerpt,
    highlights: sample.highlights,
    features: sample.features,
    sourceUpdatedAt: sample.sourceUpdatedAt,
    metaTitle: sample.metaTitle,
    metaDescription: sample.metaDescription,
    canonicalUrl: sample.canonicalUrl,
    ogImageUrl: sample.ogImageUrl
  };
}

function writeSampleDetails(samples) {
  fs.rmSync(sampleDetailsDirectory, { recursive: true, force: true });
  fs.mkdirSync(sampleDetailsDirectory, { recursive: true });

  for (const sample of samples) {
    fs.writeFileSync(
      path.join(sampleDetailsDirectory, `${sample.id}.json`),
      `${JSON.stringify(buildSampleDetail(sample), null, 2)}\n`
    );
  }
}

function validateGeneratedOutputs(samples, catalogIndex, patternMappings, featurePages, languagePages) {
  const sampleIds = new Set(samples.map((sample) => sample.id));
  const indexIds = new Set(catalogIndex.i.map((item) => item[0]));
  const missingIndexIds = samples.map((sample) => sample.id).filter((id) => !indexIds.has(id));
  const extraIndexIds = catalogIndex.i.map((item) => item[0]).filter((id) => !sampleIds.has(id));
  const missingPatternIds = patternMappings.patterns
    .flatMap((pattern) => pattern.sampleIds.map((sampleId) => ({ patternId: pattern.id, sampleId })))
    .filter(({ sampleId }) => !indexIds.has(sampleId))
    .map(({ patternId, sampleId }) => `${patternId}:${sampleId}`);

  if (missingIndexIds.length > 0 || extraIndexIds.length > 0) {
    throw new Error(
      `Catalog index sample ids are out of sync. Missing: ${missingIndexIds.join(', ') || 'none'}. Extra: ${
        extraIndexIds.join(', ') || 'none'
      }.`
    );
  }

  if (missingPatternIds.length > 0) {
    throw new Error(`Pattern mappings reference samples missing from the catalog index: ${missingPatternIds.join(', ')}`);
  }

  const missingFeaturePageIds = featurePages
    .flatMap((page) => page.sampleIds.map((sampleId) => ({ page: page.slug, sampleId })))
    .filter(({ sampleId }) => !indexIds.has(sampleId))
    .map(({ page, sampleId }) => `${page}:${sampleId}`);
  const missingLanguagePageIds = languagePages
    .flatMap((page) => page.sampleIds.map((sampleId) => ({ page: page.slug, sampleId })))
    .filter(({ sampleId }) => !indexIds.has(sampleId))
    .map(({ page, sampleId }) => `${page}:${sampleId}`);

  if (missingFeaturePageIds.length > 0 || missingLanguagePageIds.length > 0) {
    throw new Error(
      `Landing pages reference samples missing from the catalog index. Feature pages: ${
        missingFeaturePageIds.join(', ') || 'none'
      }. Language pages: ${missingLanguagePageIds.join(', ') || 'none'}.`
    );
  }
}

const readmeFiles = walkDirectory(repoRoot);
const samples = readmeFiles
  .map((fullPath) => parseReadmeFile(repoRoot, fullPath))
  .filter(Boolean)
  .map((parsed) => deriveSampleRecord(parsed))
  .sort((left, right) => {
    if (left.featured !== right.featured) {
      return left.featured ? -1 : 1;
    }

    return left.title.localeCompare(right.title);
  });

validateReversibleSampleIds(samples);

const patternDefinitions = JSON.parse(fs.readFileSync(patternDefinitionsPath, 'utf8'));
const featureDetails = JSON.parse(fs.readFileSync(featureDetailsPath, 'utf8'));
const patternMappings = buildPatternMappings(samples, patternDefinitions);
const catalogIndex = buildCatalogIndex(samples);
const featurePages = buildFeaturePages(samples, featureDetails);
const languagePages = buildLanguagePages(samples, featurePages);
validateGeneratedOutputs(samples, catalogIndex, patternMappings, featurePages, languagePages);
fs.writeFileSync(catalogIndexPath, `${JSON.stringify(catalogIndex)}\n`);
writeSampleDetails(samples);
fs.writeFileSync(featurePagesPath, `${JSON.stringify(featurePages, null, 2)}\n`);
fs.writeFileSync(languagePagesPath, `${JSON.stringify(languagePages, null, 2)}\n`);
writeDefaultSocialCard({ websiteRoot });
writeSampleSocialCards(samples, { websiteRoot });
fs.writeFileSync(patternMappingsPath, `${JSON.stringify(patternMappings, null, 2)}\n`);

console.log(`Generated packed catalog index into ${path.relative(repoRoot, catalogIndexPath)}`);
console.log(`Generated ${samples.length} sample detail files into ${path.relative(repoRoot, sampleDetailsDirectory)}`);
console.log(`Generated ${featurePages.length} feature landing pages into ${path.relative(repoRoot, featurePagesPath)}`);
console.log(`Generated ${languagePages.length} language landing pages into ${path.relative(repoRoot, languagePagesPath)}`);
console.log(
  `Generated ${samples.length} sample social cards into ${path.relative(
    repoRoot,
    path.join(websiteRoot, 'public', SAMPLE_SOCIAL_CARD_DIRECTORY)
  )}`
);
console.log(
  `Generated ${patternMappings.patterns.length} pattern mappings into ${path.relative(repoRoot, patternMappingsPath)}`
);
