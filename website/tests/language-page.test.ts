import { mount, RouterLinkStub } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

vi.mock('vue-router', () => ({
  useRoute: () => ({
    name: 'language-detail',
    params: {
      slug: 'java'
    }
  })
}));

import LanguagePage from '../src/pages/LanguagePage.vue';

describe('LanguagePage', () => {
  it('renders crawlable language copy and pattern cross-links', () => {
    const wrapper = mount(LanguagePage, {
      global: {
        stubs: {
          RouterLink: RouterLinkStub
        }
      }
    });

    expect(wrapper.text()).toContain('Java Samples for Oracle AI Database');
    expect(wrapper.text()).toContain('JVM applications need real Oracle AI Database examples');
    expect(wrapper.findAll('.sample-card').length).toBeGreaterThan(0);
    expect(
      wrapper.findAllComponents(RouterLinkStub).some((link) => JSON.stringify(link.props('to')).includes('pattern-detail'))
    ).toBe(true);
  });
});
