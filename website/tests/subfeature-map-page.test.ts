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
        x: 640,
        y: 480,
        ring: 0,
        size: 10.2,
        width: 192,
        height: 96
      },
      {
        name: 'JSON',
        count: 4,
        x: 1540,
        y: 780,
        ring: 1,
        size: 9,
        width: 168,
        height: 96
      }
    ]
  })
}));

import SubfeatureMapPage from '../src/pages/SubfeatureMapPage.vue';

describe('SubfeatureMapPage', () => {
  it('renders graph nodes and catalog links', () => {
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
    expect(wrapper.find('.tag-map-viewport').exists()).toBe(true);
    expect(wrapper.find('button.tag-map-panel__button').text()).toContain('Recenter Map');

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
