import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import zlib from 'node:zlib';
import { describe, expect, it } from 'vitest';
import catalogIndex from '../src/data/catalog-index.json';
import languagePages from '../src/data/language-pages.json';
import patternMappings from '../src/data/patternMappings.json';
import { decodeCatalogIndex } from '../src/lib/catalog';
import type { PackedCatalogIndex, SampleDetail, SampleRecord, SampleSummary } from '../src/types';

const typedCatalogIndex = catalogIndex as PackedCatalogIndex;
const currentDirectory = path.dirname(fileURLToPath(import.meta.url));
const dataDirectory = path.resolve(currentDirectory, '../src/data');
const detailDirectory = path.resolve(currentDirectory, '../src/data/sample-details');
const socialCardDirectory = path.resolve(currentDirectory, '../public/sample-cards');
const summaries = decodeCatalogIndex(typedCatalogIndex);

function gzipSize(value: unknown) {
  return zlib.gzipSync(JSON.stringify(value)).length;
}

function readSampleDetail(id: string) {
  return JSON.parse(fs.readFileSync(path.join(detailDirectory, `${id}.json`), 'utf8')) as SampleDetail;
}

function readPngDimensions(filePath: string) {
  const data = fs.readFileSync(filePath);
  return {
    signature: data.subarray(0, 8).toString('hex'),
    width: data.readUInt32BE(16),
    height: data.readUInt32BE(20)
  };
}

function reconstructFullSample(summary: SampleSummary): SampleRecord {
  return {
    ...summary,
    ...readSampleDetail(summary.id)
  };
}

describe('generated runtime data', () => {
  it('keeps the packed catalog index substantially smaller than full sample data', () => {
    const fullSize = gzipSize(summaries.map((summary) => reconstructFullSample(summary)));
    const packedSize = gzipSize(typedCatalogIndex);

    expect(packedSize).toBeLessThanOrEqual(fullSize * 0.25);
  });

  it('writes a valid packed catalog entry for every generated sample', () => {
    const detailIds = new Set(
      fs.readdirSync(detailDirectory)
        .filter((fileName) => fileName.endsWith('.json'))
        .map((fileName) => fileName.replace(/\.json$/, ''))
    );
    const indexIds = new Set(typedCatalogIndex.i.map(([id]) => id));

    expect(indexIds).toEqual(detailIds);
    expect(typedCatalogIndex.i.every((item) => item.length === 7)).toBe(true);
    expect(
      typedCatalogIndex.i.every(([, , , tagIds, languageId, parentId, featuredFlag]) =>
        tagIds.every((tagId) => typeof typedCatalogIndex.t[tagId] === 'string') &&
        typeof typedCatalogIndex.l[languageId] === 'string' &&
        typeof typedCatalogIndex.p[parentId] === 'string' &&
        (featuredFlag === 0 || featuredFlag === 1)
      )
    ).toBe(true);
  });

  it('writes one detail file per generated sample', () => {
    for (const summary of summaries) {
      const detail = readSampleDetail(summary.id);

      expect(detail.id).toBe(summary.id);
      expect(detail.readmeExcerpt.length).toBeGreaterThan(0);
      expect(Array.isArray(detail.features)).toBe(true);
      expect(Date.parse(detail.sourceUpdatedAt)).not.toBeNaN();
      expect(detail.ogImageUrl).toBe(
        `https://anders-swanson.github.io/oracle-database-code-samples/sample-cards/${summary.id}.png`
      );
      const cardPath = path.join(socialCardDirectory, `${summary.id}.png`);
      expect(fs.existsSync(cardPath)).toBe(true);
      expect(readPngDimensions(cardPath)).toEqual({
        signature: '89504e470d0a1a0a',
        width: 1200,
        height: 630
      });
    }
  });

  it('writes language landing page data and curated pattern slugs for indexed topic pages', () => {
    const indexIds = new Set(typedCatalogIndex.i.map(([id]) => id));
    const semanticSearchPattern = patternMappings.patterns.find((pattern) => pattern.id === 'semantic-search-rag');
    const localTestingPattern = patternMappings.patterns.find((pattern) => pattern.id === 'local-testing');
    const jsonPattern = patternMappings.patterns.find((pattern) => pattern.id === 'json-documents-duality');
    const javaPage = languagePages.find((page) => page.slug === 'java');

    expect(languagePages.length).toBeGreaterThan(0);
    expect(semanticSearchPattern?.topics).toContain('Vector Search');
    expect(localTestingPattern?.topics).toContain('Testcontainers');
    expect(jsonPattern?.topics).toEqual(expect.arrayContaining(['JSON', 'Duality Views']));
    expect(javaPage?.canonicalUrl).toBe('https://anders-swanson.github.io/oracle-database-code-samples/languages/java/');
    expect(javaPage?.relatedPatternIds.length).toBeGreaterThan(0);

    for (const page of languagePages) {
      expect(page.sampleIds.length).toBeGreaterThan(0);
      expect(Date.parse(page.updatedAt)).not.toBeNaN();
      expect(page.sampleIds.every((sampleId) => indexIds.has(sampleId))).toBe(true);
    }
  });

  it('does not write the obsolete generated feature-page artifact', () => {
    expect(fs.existsSync(path.join(dataDirectory, 'feature-pages.json'))).toBe(false);
  });

  it('keeps pattern mappings aligned with the packed catalog index', () => {
    const indexIds = new Set(typedCatalogIndex.i.map(([id]) => id));
    const missingIds = patternMappings.patterns.flatMap((pattern) =>
      pattern.sampleIds.filter((sampleId) => !indexIds.has(sampleId)).map((sampleId) => `${pattern.id}:${sampleId}`)
    );

    expect(missingIds).toEqual([]);
  });
});
