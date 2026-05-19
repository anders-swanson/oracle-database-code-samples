import featureIconDefinitionsJson from './featureIconDefinitions.json';

export interface FeatureIcon {
  iconPath: string;
  sourceLabel: string;
}

interface FeatureIconDefinition {
  file: string;
  sourceLabel: string;
}

const featureIconDefinitions = featureIconDefinitionsJson as Record<string, FeatureIconDefinition>;

function buildIconPath(file: string) {
  const baseUrl = import.meta.env.BASE_URL.endsWith('/') ? import.meta.env.BASE_URL : `${import.meta.env.BASE_URL}/`;
  return `${baseUrl}feature-icons/${file}`;
}

export function getFeatureIcon(feature: string): FeatureIcon | undefined {
  const definition = featureIconDefinitions[feature];

  if (!definition) {
    return undefined;
  }

  return {
    iconPath: buildIconPath(definition.file),
    sourceLabel: definition.sourceLabel
  };
}
