import { mount, RouterLinkStub } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

vi.mock('vue-router', () => ({
  useRoute: () => ({
    name: 'feature-map'
  })
}));

vi.mock('../src/lib/catalog', () => ({
  samples: [],
  buildSubfeatureGraph: () => ({
    centerLabel: 'Oracle AI Database',
    centerSubtitle: 'Converged Database',
    totalSamples: 12,
    totalTags: 4,
    hiddenTags: 0,
    width: 2400,
    height: 1800,
    centerX: 1200,
    centerY: 900,
    orbitRadii: [300, 520, 760],
    nodes: [
      {
        name: 'Vector Search',
        count: 7,
        iconPath: '/feature-icons/vector-search.png',
        iconSourceLabel: 'Vector Search',
        description: 'Store embeddings and search records by semantic similarity.',
        useWhen: 'Use when users search by meaning or AI answers need grounded records.',
        x: 640,
        y: 480,
        ring: 0,
        size: 10.2,
        width: 192,
        height: 158
      },
      {
        name: 'JSON',
        count: 4,
        iconPath: '/feature-icons/json.svg',
        iconSourceLabel: 'Database Badge {}',
        description: 'Document-shaped data and SQL/JSON querying inside Oracle AI Database.',
        useWhen: 'Use when records need flexible structure without leaving SQL, indexes, constraints, and transactions.',
        x: 1540,
        y: 780,
        ring: 1,
        size: 9,
        width: 168,
        height: 158
      }
    ]
  })
}));

import SubfeatureMapPage from '../src/pages/SubfeatureMapPage.vue';

describe('SubfeatureMapPage', () => {
  it('renders graph nodes, hover details, and catalog links', async () => {
    const wrapper = mount(SubfeatureMapPage, {
      global: {
        stubs: {
          RouterLink: RouterLinkStub
        }
      }
    });

    expect(wrapper.text()).toContain('Oracle AI Database');
    expect(wrapper.text()).toContain('Converged Database');
    expect(wrapper.text()).toContain('Vector Search');
    expect(wrapper.text()).toContain('7');
    expect(wrapper.find('img[src="/feature-icons/vector-search.png"]').exists()).toBe(true);
    expect(wrapper.find('.tag-map-viewport').exists()).toBe(true);
    expect(wrapper.find('button.tag-map-panel__button').text()).toContain('Recenter Map');

    await wrapper.find('.tag-map-node').trigger('focus');
    expect(wrapper.find('.tag-map-tooltip').attributes('role')).toBe('tooltip');
    expect(wrapper.find('.tag-map-tooltip').text()).toContain('Vector Search');
    expect(wrapper.find('.tag-map-tooltip').text()).toContain('Store embeddings and search records by semantic similarity.');
    expect(wrapper.find('.tag-map-tooltip').text()).toContain(
      'Use when users search by meaning or AI answers need grounded records.'
    );

    const links = wrapper.findAllComponents(RouterLinkStub);
    expect(links.some((link) => link.props('to') === '/')).toBe(true);
    expect(
      links.some(
        (link) =>
          JSON.stringify(link.props('to')) === JSON.stringify({ name: 'catalog', query: { tags: 'Vector Search' } })
      )
    ).toBe(true);
  });
});
