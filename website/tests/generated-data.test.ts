import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import zlib from 'node:zlib';
import { describe, expect, it } from 'vitest';
import catalogIndex from '../src/data/catalog-index.json';
import featurePages from '../src/data/feature-pages.json';
import languagePages from '../src/data/language-pages.json';
import patternMappings from '../src/data/patternMappings.json';
import { decodeCatalogIndex } from '../src/lib/catalog';
import type { PackedCatalogIndex, SampleDetail, SampleRecord, SampleSummary } from '../src/types';

const typedCatalogIndex = catalogIndex as PackedCatalogIndex;
const currentDirectory = path.dirname(fileURLToPath(import.meta.url));
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

  it('writes feature and language landing page data for indexed topic pages', () => {
    const indexIds = new Set(typedCatalogIndex.i.map(([id]) => id));
    const vectorPage = featurePages.find((page) => page.slug === 'vector-search');
    const javaPage = languagePages.find((page) => page.slug === 'java');

    expect(featurePages.length).toBeGreaterThan(0);
    expect(languagePages.length).toBeGreaterThan(0);
    expect(vectorPage?.canonicalUrl).toBe(
      'https://anders-swanson.github.io/oracle-database-code-samples/features/vector-search/'
    );
    expect(vectorPage?.metaTitle).toContain('Oracle AI Database Vector Search Samples');
    expect(javaPage?.canonicalUrl).toBe('https://anders-swanson.github.io/oracle-database-code-samples/languages/java/');

    for (const page of [...featurePages, ...languagePages]) {
      expect(page.sampleIds.length).toBeGreaterThan(0);
      expect(Date.parse(page.updatedAt)).not.toBeNaN();
      expect(page.sampleIds.every((sampleId) => indexIds.has(sampleId))).toBe(true);
    }
  });

  it('keeps pattern mappings aligned with the packed catalog index', () => {
    const indexIds = new Set(typedCatalogIndex.i.map(([id]) => id));
    const missingIds = patternMappings.patterns.flatMap((pattern) =>
      pattern.sampleIds.filter((sampleId) => !indexIds.has(sampleId)).map((sampleId) => `${pattern.id}:${sampleId}`)
    );

    expect(missingIds).toEqual([]);
  });
});
