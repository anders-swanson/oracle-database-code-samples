import { describe, expect, it } from 'vitest';
import type { RouteLocationNormalizedLoaded } from 'vue-router';
import { routes, scrollBehavior } from '../src/router';

function route(path: string, hash = '') {
  return {
    path,
    hash
  } as RouteLocationNormalizedLoaded;
}

describe('router scroll behavior', () => {
  it('registers generated landing page route patterns', () => {
    expect(routes.some((entry) => entry.name === 'pattern-detail' && entry.path === '/patterns/:slug/')).toBe(true);
    expect(routes.some((entry) => entry.path === '/features/:slug/')).toBe(false);
    expect(routes.some((entry) => entry.name === 'language-detail' && entry.path === '/languages/:slug/')).toBe(true);
  });

  it('scrolls feature-map filter links to the catalog results anchor', () => {
    expect(scrollBehavior(route('/', '#catalog-results'), route('/feature-map/'), null)).toEqual({
      el: '#catalog-results'
    });
  });

  it('preserves scroll for query-only catalog changes', () => {
    expect(scrollBehavior(route('/'), route('/'), null)).toBe(false);
  });
});
