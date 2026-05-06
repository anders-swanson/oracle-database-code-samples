import type { RouteLocationNormalizedLoaded } from 'vue-router';
import { findSampleById } from './catalog';
import type { SampleDetail, SampleRecord } from '../types';

interface SampleDetailRouteState {
  sampleDetail?: SampleDetail;
}

const detailLoaders = import.meta.glob('../data/sample-details/*.json', {
  import: 'default'
}) as Record<string, () => Promise<SampleDetail>>;

function detailModulePath(id: string) {
  return `../data/sample-details/${id}.json`;
}

export async function loadSampleDetail(id: string) {
  const loader = detailLoaders[detailModulePath(id)];

  if (!loader) {
    return undefined;
  }

  return loader();
}

export function getRouteSampleDetail(route: RouteLocationNormalizedLoaded) {
  const state = route.meta?.state as SampleDetailRouteState | undefined;
  const detail = state?.sampleDetail;

  if (!detail || detail.id !== String(route.params.id)) {
    return undefined;
  }

  return detail;
}

export function hydrateSample(id: string, detail: SampleDetail | undefined): SampleRecord | undefined {
  const summary = findSampleById(id);

  if (!summary || !detail) {
    return undefined;
  }

  return {
    ...summary,
    ...detail
  };
}
