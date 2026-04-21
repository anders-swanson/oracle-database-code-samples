import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  deriveSampleRecord,
  parseReadmeFile
} from './sample-catalog-utils.mjs';

const currentDir = path.dirname(fileURLToPath(import.meta.url));
const websiteRoot = path.resolve(currentDir, '..');
const repoRoot = path.resolve(websiteRoot, '..');
const outputPath = path.join(websiteRoot, 'src', 'data', 'samples.json');
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

const readmeFiles = walkDirectory(repoRoot);
const samples = readmeFiles
  .map((fullPath) => parseReadmeFile(repoRoot, fullPath))
  .filter(Boolean)
  .map((parsed) => deriveSampleRecord(parsed))
  .sort((left, right) => {
    if (left.featured !== right.featured) {
      return left.featured ? -1 : 1;
    }

    return left.name.localeCompare(right.name);
  });

fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, `${JSON.stringify(samples, null, 2)}\n`);

console.log(`Generated ${samples.length} samples into ${path.relative(repoRoot, outputPath)}`);
