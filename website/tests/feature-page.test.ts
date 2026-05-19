import { mount, RouterLinkStub } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

vi.mock('vue-router', () => ({
  useRoute: () => ({
    name: 'feature-detail',
    params: {
      slug: 'vector-search'
    }
  })
}));

import FeaturePage from '../src/pages/FeaturePage.vue';

describe('FeaturePage', () => {
  it('renders crawlable feature copy, sample links, and related feature links', () => {
    const wrapper = mount(FeaturePage, {
      global: {
        stubs: {
          RouterLink: RouterLinkStub
        }
      }
    });

    expect(wrapper.text()).toContain('Oracle AI Database Vector Search Samples');
    expect(wrapper.text()).toContain('Store embeddings and search records by semantic similarity');
    expect(wrapper.findAll('.sample-card').length).toBeGreaterThan(0);
    expect(wrapper.find('.landing-link-list').exists()).toBe(true);
    expect(
      wrapper.findAllComponents(RouterLinkStub).some((link) => JSON.stringify(link.props('to')).includes('sample-detail'))
    ).toBe(true);
  });
});
