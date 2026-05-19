import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  deriveSampleRecord,
  parseReadmeFile
} from './sample-catalog-utils.mjs';
import { buildPatternMappings } from './pattern-mapping-utils.mjs';
import { SAMPLE_SOCIAL_CARD_DIRECTORY } from './seo-utils.mjs';
import { writeSampleSocialCards } from './social-card-utils.mjs';

const currentDir = path.dirname(fileURLToPath(import.meta.url));
const websiteRoot = path.resolve(currentDir, '..');
const repoRoot = path.resolve(websiteRoot, '..');
const catalogIndexPath = path.join(websiteRoot, 'src', 'data', 'catalog-index.json');
const sampleDetailsDirectory = path.join(websiteRoot, 'src', 'data', 'sample-details');
const patternDefinitionsPath = path.join(websiteRoot, 'src', 'data', 'patternDefinitions.json');
const patternMappingsPath = path.join(websiteRoot, 'src', 'data', 'patternMappings.json');
const skipDirectories = new Set([
  '.git',
  'node_modules',
  'website'
]);

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

function buildSampleDetail(sample) {
  return {
    id: sample.id,
    githubReadmeUrl: sample.githubReadmeUrl,
    blogPost: sample.blogPost,
    readmeExcerpt: sample.readmeExcerpt,
    highlights: sample.highlights,
    features: sample.features,
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

function validateGeneratedOutputs(samples, catalogIndex, patternMappings) {
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
const patternMappings = buildPatternMappings(samples, patternDefinitions);
const catalogIndex = buildCatalogIndex(samples);
validateGeneratedOutputs(samples, catalogIndex, patternMappings);
fs.writeFileSync(catalogIndexPath, `${JSON.stringify(catalogIndex)}\n`);
writeSampleDetails(samples);
writeSampleSocialCards(samples, { websiteRoot });
fs.writeFileSync(patternMappingsPath, `${JSON.stringify(patternMappings, null, 2)}\n`);

console.log(`Generated packed catalog index into ${path.relative(repoRoot, catalogIndexPath)}`);
console.log(`Generated ${samples.length} sample detail files into ${path.relative(repoRoot, sampleDetailsDirectory)}`);
console.log(
  `Generated ${samples.length} sample social cards into ${path.relative(
    repoRoot,
    path.join(websiteRoot, 'public', SAMPLE_SOCIAL_CARD_DIRECTORY)
  )}`
);
console.log(
  `Generated ${patternMappings.patterns.length} pattern mappings into ${path.relative(repoRoot, patternMappingsPath)}`
);
