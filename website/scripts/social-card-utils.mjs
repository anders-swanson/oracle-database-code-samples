import fs from 'node:fs';
import path from 'node:path';
import { SAMPLE_SOCIAL_CARD_DIRECTORY, SITE_NAME, trimDescription } from './seo-utils.mjs';

const CARD_WIDTH = 1200;
const CARD_HEIGHT = 630;
const FONT_FAMILY = 'Avenir Next, Segoe UI, Helvetica Neue, Arial, sans-serif';

function escapeXml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&apos;');
}

function normalizeOracleName(value) {
  return String(value ?? '').replace(/\bOracle Database\b/g, 'Oracle AI Database');
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function readAttribute(attributes, name) {
  const match = attributes.match(new RegExp(`${name}=["']([^"']+)["']`, 'i'));
  return match ? match[1] : '';
}

function readNumberAttribute(attributes, name, fallback) {
  const rawValue = readAttribute(attributes, name);
  const parsed = Number.parseFloat(rawValue);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function wrapText(value, maxCharacters, maxLines) {
  const words = normalizeOracleName(value).replace(/\s+/g, ' ').trim().split(' ').filter(Boolean);
  const lines = [];
  let currentLine = '';

  for (const word of words) {
    const nextLine = currentLine ? `${currentLine} ${word}` : word;
    if (nextLine.length <= maxCharacters) {
      currentLine = nextLine;
      continue;
    }

    if (currentLine) {
      lines.push(currentLine);
    }
    currentLine = word;

    if (lines.length === maxLines) {
      break;
    }
  }

  if (lines.length < maxLines && currentLine) {
    lines.push(currentLine);
  }

  if (lines.length === maxLines && words.join(' ').length > lines.join(' ').length) {
    lines[lines.length - 1] = trimDescription(lines[lines.length - 1], Math.max(24, maxCharacters - 1));
  }

  return lines;
}

function renderTextLines(lines, { x, y, fill, fontSize, fontWeight = 500, lineHeight }) {
  return lines
    .map(
      (line, index) =>
        `<text x="${x}" y="${y + index * lineHeight}" fill="${fill}" font-family="${FONT_FAMILY}" font-size="${fontSize}" font-weight="${fontWeight}">${escapeXml(line)}</text>`
    )
    .join('\n');
}

function selectCardIcons(sample, featureIconDefinitions) {
  const candidates = [...sample.features, sample.language, ...sample.tags, 'Oracle AI Database'];
  const selected = [];
  const seenFiles = new Set();

  for (const name of candidates) {
    const definition = featureIconDefinitions[name];
    if (!definition || seenFiles.has(definition.file)) {
      continue;
    }

    selected.push({
      name,
      ...definition
    });
    seenFiles.add(definition.file);

    if (selected.length === 3) {
      break;
    }
  }

  return selected;
}

function findIconPath(publicDirectory, icon) {
  const iconPath = path.join(publicDirectory, 'feature-icons', icon.file);
  return fs.existsSync(iconPath) ? iconPath : '';
}

function renderSvgIcon(filePath, x, y, size) {
  const source = fs.readFileSync(filePath, 'utf8');
  const match = source.match(/<svg\b([^>]*)>([\s\S]*?)<\/svg>\s*$/i);
  if (!match) {
    return '';
  }

  const attributes = match[1];
  const width = readNumberAttribute(attributes, 'width', 100);
  const height = readNumberAttribute(attributes, 'height', 100);
  const viewBox = readAttribute(attributes, 'viewBox') || `0 0 ${width} ${height}`;

  return `<svg x="${x}" y="${y}" width="${size}" height="${size}" viewBox="${escapeXml(viewBox)}" preserveAspectRatio="xMidYMid meet">${match[2].trim()}</svg>`;
}

function renderPngIcon(filePath, x, y, size) {
  const data = fs.readFileSync(filePath).toString('base64');
  return `<image x="${x}" y="${y}" width="${size}" height="${size}" href="data:image/png;base64,${data}" preserveAspectRatio="xMidYMid meet" />`;
}

function renderIconAsset(icon, publicDirectory, x, y, size) {
  const iconPath = findIconPath(publicDirectory, icon);
  if (!iconPath) {
    return '';
  }

  if (icon.file.endsWith('.svg')) {
    return renderSvgIcon(iconPath, x, y, size);
  }

  if (icon.file.endsWith('.png')) {
    return renderPngIcon(iconPath, x, y, size);
  }

  return '';
}

function renderIconTiles(icons, publicDirectory) {
  const availableIcons = icons.filter((icon) => findIconPath(publicDirectory, icon));
  const slots = [
    { x: 816, y: 116 },
    { x: 972, y: 236 },
    { x: 816, y: 356 }
  ];

  return availableIcons
    .map((icon, index) => {
      const slot = slots[index];
      const iconMarkup = renderIconAsset(icon, publicDirectory, slot.x + 32, slot.y + 24, 92);
      const labelLines = wrapText(icon.name, 16, 2);

      return `
        <g>
          <rect x="${slot.x}" y="${slot.y}" width="144" height="144" rx="28" fill="#f7f4ee" stroke="#ffffff" stroke-opacity="0.58" />
          <rect x="${slot.x}" y="${slot.y}" width="144" height="144" rx="28" fill="url(#tileGlow)" />
          ${iconMarkup}
          ${renderTextLines(labelLines, {
            x: slot.x + 72,
            y: slot.y + 115,
            fill: '#2f2925',
            fontSize: 16,
            fontWeight: 700,
            lineHeight: 18
          }).replaceAll('<text ', '<text text-anchor="middle" ')}
        </g>`;
    })
    .join('\n');
}

function renderFeatureChips(features) {
  const chipLabels = features.slice(0, 3);
  let currentX = 0;

  return chipLabels
    .map((feature, index) => {
      const width = Math.max(120, Math.min(230, feature.length * 12 + 38));
      const stroke = index === 0 ? '#59d4ff' : '#ffb066';
      const markup = `
        <g transform="translate(${currentX} 0)">
          <rect x="0" y="0" width="${width}" height="42" rx="21" fill="#0d2036" stroke="${stroke}" stroke-opacity="0.42" />
          <text x="${width / 2}" y="27" text-anchor="middle" fill="#f4f7ff" font-family="${FONT_FAMILY}" font-size="18" font-weight="700">${escapeXml(normalizeOracleName(feature))}</text>
        </g>`;
      currentX += width + 16;
      return markup;
    })
    .join('\n');
}

export function renderSampleSocialCard(sample, { featureIconDefinitions, publicDirectory }) {
  const titleLines = wrapText(sample.title, 25, 2);
  const descriptionLines = wrapText(trimDescription(sample.metaDescription || sample.description, 118), 54, 2);
  const icons = selectCardIcons(sample, featureIconDefinitions);
  const features = sample.features.length > 0 ? sample.features : ['Oracle AI Database'];
  const title = normalizeOracleName(sample.title);
  const pathLabel = normalizeOracleName(sample.path);

  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${CARD_WIDTH} ${CARD_HEIGHT}" role="img" aria-labelledby="title desc">
  <title id="title">${escapeXml(`${title} | ${SITE_NAME}`)}</title>
  <desc id="desc">${escapeXml(`A sample social preview card for ${title}.`)}</desc>
  <defs>
    <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#091422" />
      <stop offset="58%" stop-color="#07111d" />
      <stop offset="100%" stop-color="#061019" />
    </linearGradient>
    <radialGradient id="glowCyan" cx="16%" cy="10%" r="80%">
      <stop offset="0%" stop-color="#59d4ff" stop-opacity="0.33" />
      <stop offset="100%" stop-color="#59d4ff" stop-opacity="0" />
    </radialGradient>
    <radialGradient id="glowWarm" cx="96%" cy="16%" r="80%">
      <stop offset="0%" stop-color="#ffb066" stop-opacity="0.24" />
      <stop offset="100%" stop-color="#ffb066" stop-opacity="0" />
    </radialGradient>
    <linearGradient id="panel" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#13253d" stop-opacity="0.97" />
      <stop offset="100%" stop-color="#091525" stop-opacity="0.96" />
    </linearGradient>
    <linearGradient id="tileGlow" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#ffffff" stop-opacity="0.45" />
      <stop offset="100%" stop-color="#59d4ff" stop-opacity="0.14" />
    </linearGradient>
  </defs>

  <rect width="${CARD_WIDTH}" height="${CARD_HEIGHT}" fill="url(#bg)" />
  <rect width="${CARD_WIDTH}" height="${CARD_HEIGHT}" fill="url(#glowCyan)" />
  <rect width="${CARD_WIDTH}" height="${CARD_HEIGHT}" fill="url(#glowWarm)" />
  <g opacity="0.13">
    <path d="M0 86H1200M0 172H1200M0 258H1200M0 344H1200M0 430H1200M0 516H1200" stroke="#d7e6ff" />
    <path d="M110 0V630M220 0V630M330 0V630M440 0V630M550 0V630M660 0V630M770 0V630M880 0V630M990 0V630M1100 0V630" stroke="#d7e6ff" />
  </g>

  <rect x="70" y="70" width="1060" height="490" rx="34" fill="url(#panel)" stroke="#a0c5ff" stroke-opacity="0.22" />
  <rect x="70" y="70" width="1060" height="490" rx="34" fill="none" stroke="#ffffff" stroke-opacity="0.05" />

  <g transform="translate(126 126)">
    <text x="0" y="0" fill="#59d4ff" font-family="${FONT_FAMILY}" font-size="19" font-weight="800">ORACLE AI DATABASE CODE SAMPLE</text>
    ${renderTextLines(titleLines, {
      x: 0,
      y: 90,
      fill: '#f4f7ff',
      fontSize: 58,
      fontWeight: 800,
      lineHeight: 66
    })}
    ${renderTextLines(descriptionLines, {
      x: 0,
      y: 244,
      fill: '#a9b8cf',
      fontSize: 25,
      fontWeight: 500,
      lineHeight: 35
    })}
    <g transform="translate(0 344)">
      ${renderFeatureChips(features)}
    </g>
    <text x="0" y="430" fill="#73859f" font-family="${FONT_FAMILY}" font-size="18" font-weight="700">${escapeXml(pathLabel)}</text>
  </g>

  ${renderIconTiles(icons, publicDirectory)}
</svg>
`;
}

export function writeSampleSocialCards(samples, { websiteRoot }) {
  const publicDirectory = path.join(websiteRoot, 'public');
  const outputDirectory = path.join(publicDirectory, SAMPLE_SOCIAL_CARD_DIRECTORY);
  const featureIconDefinitions = readJson(path.join(websiteRoot, 'src', 'data', 'featureIconDefinitions.json'));

  fs.rmSync(outputDirectory, { recursive: true, force: true });
  fs.mkdirSync(outputDirectory, { recursive: true });

  for (const sample of samples) {
    fs.writeFileSync(
      path.join(outputDirectory, `${sample.id}.svg`),
      renderSampleSocialCard(sample, {
        featureIconDefinitions,
        publicDirectory
      })
    );
  }
}
