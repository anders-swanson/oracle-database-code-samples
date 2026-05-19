import type { FeatureDetail } from '../types';
import rawFeatureDetails from './featureDetails.json';

const featureDetails = rawFeatureDetails as Record<string, FeatureDetail>;

export function getFeatureDetail(feature: string): FeatureDetail | undefined {
  return featureDetails[feature];
}
