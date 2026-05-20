import { getFeatureIcon } from './featureIcons';

interface PatternTheme {
  accent: string;
  accentRgb: string;
  accent2: string;
  accent2Rgb: string;
  panelRgb: string;
}

export interface PatternVisual {
  iconPath?: string;
  sourceLabel?: string;
  styleVars: Record<string, string>;
}

const patternThemes: Record<string, PatternTheme> = {
  'AI Agents': {
    accent: '#d2a1ff',
    accentRgb: '210, 161, 255',
    accent2: '#59d4ff',
    accent2Rgb: '89, 212, 255',
    panelRgb: '37, 24, 58'
  },
  'Duality Views': {
    accent: '#5de0a7',
    accentRgb: '93, 224, 167',
    accent2: '#ffb066',
    accent2Rgb: '255, 176, 102',
    panelRgb: '17, 48, 39'
  },
  JSON: {
    accent: '#ffcf66',
    accentRgb: '255, 207, 102',
    accent2: '#5de0a7',
    accent2Rgb: '93, 224, 167',
    panelRgb: '53, 43, 18'
  },
  Kafka: {
    accent: '#ff8aa0',
    accentRgb: '255, 138, 160',
    accent2: '#ffb066',
    accent2Rgb: '255, 176, 102',
    panelRgb: '57, 26, 35'
  },
  Observability: {
    accent: '#9fe870',
    accentRgb: '159, 232, 112',
    accent2: '#59d4ff',
    accent2Rgb: '89, 212, 255',
    panelRgb: '31, 52, 28'
  },
  ORDS: {
    accent: '#75d7ff',
    accentRgb: '117, 215, 255',
    accent2: '#d2a1ff',
    accent2Rgb: '210, 161, 255',
    panelRgb: '20, 42, 59'
  },
  'Property Graph': {
    accent: '#b9f28b',
    accentRgb: '185, 242, 139',
    accent2: '#ff8aa0',
    accent2Rgb: '255, 138, 160',
    panelRgb: '35, 51, 28'
  },
  Security: {
    accent: '#ffb066',
    accentRgb: '255, 176, 102',
    accent2: '#ff8aa0',
    accent2Rgb: '255, 138, 160',
    panelRgb: '55, 36, 20'
  },
  Spatial: {
    accent: '#6ee7d8',
    accentRgb: '110, 231, 216',
    accent2: '#9fe870',
    accent2Rgb: '159, 232, 112',
    panelRgb: '17, 51, 51'
  },
  Spring: {
    accent: '#7ce36f',
    accentRgb: '124, 227, 111',
    accent2: '#59d4ff',
    accent2Rgb: '89, 212, 255',
    panelRgb: '22, 52, 29'
  },
  'SQL GraphQL': {
    accent: '#d2a1ff',
    accentRgb: '210, 161, 255',
    accent2: '#ff8aa0',
    accent2Rgb: '255, 138, 160',
    panelRgb: '45, 31, 58'
  },
  Testcontainers: {
    accent: '#59d4ff',
    accentRgb: '89, 212, 255',
    accent2: '#5de0a7',
    accent2Rgb: '93, 224, 167',
    panelRgb: '18, 43, 57'
  },
  TxEventQ: {
    accent: '#ffb066',
    accentRgb: '255, 176, 102',
    accent2: '#59d4ff',
    accent2Rgb: '89, 212, 255',
    panelRgb: '54, 37, 22'
  },
  'Vector Search': {
    accent: '#59d4ff',
    accentRgb: '89, 212, 255',
    accent2: '#d2a1ff',
    accent2Rgb: '210, 161, 255',
    panelRgb: '22, 35, 60'
  }
};

const fallbackThemes: PatternTheme[] = [
  patternThemes.Testcontainers,
  patternThemes.JSON,
  patternThemes.Security,
  patternThemes['AI Agents'],
  patternThemes.Spatial
];

function fallbackThemeFor(topic: string) {
  const index =
    Array.from(topic).reduce((total, character) => total + character.charCodeAt(0), 0) % fallbackThemes.length;

  return fallbackThemes[index];
}

export function getPatternVisual(topic: string): PatternVisual {
  const theme = patternThemes[topic] ?? fallbackThemeFor(topic);
  const icon = getFeatureIcon(topic);

  return {
    iconPath: icon?.iconPath,
    sourceLabel: icon?.sourceLabel,
    styleVars: {
      '--pattern-accent': theme.accent,
      '--pattern-accent-rgb': theme.accentRgb,
      '--pattern-accent-2': theme.accent2,
      '--pattern-accent-2-rgb': theme.accent2Rgb,
      '--pattern-panel-rgb': theme.panelRgb
    }
  };
}
