import { describe, expect, it } from 'vitest';
import type { RouteLocationNormalizedLoaded } from 'vue-router';
import { scrollBehavior } from '../src/router';

function route(path: string, hash = '') {
  return {
    path,
    hash
  } as RouteLocationNormalizedLoaded;
}

describe('router scroll behavior', () => {
  it('scrolls feature-map filter links to the catalog results anchor', () => {
    expect(scrollBehavior(route('/', '#catalog-results'), route('/feature-map/'), null)).toEqual({
      el: '#catalog-results'
    });
  });

  it('preserves scroll for query-only catalog changes', () => {
    expect(scrollBehavior(route('/'), route('/'), null)).toBe(false);
  });
});
