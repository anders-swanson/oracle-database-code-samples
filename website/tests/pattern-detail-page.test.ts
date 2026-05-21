import { mount, RouterLinkStub } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

vi.mock('vue-router', () => ({
  useRoute: () => ({
    name: 'pattern-detail',
    params: {
      slug: 'semantic-search-rag'
    }
  })
}));

import PatternDetailPage from '../src/pages/PatternDetailPage.vue';
import { resolvedPatternMappings } from '../src/lib/catalog';

describe('PatternDetailPage', () => {
  it('renders crawlable pattern copy, sample links, and related pattern links', () => {
    const wrapper = mount(PatternDetailPage, {
      global: {
        stubs: {
          RouterLink: RouterLinkStub
        }
      }
    });

    expect(wrapper.text()).toContain('Semantic Search / RAG Pattern');
    expect(wrapper.text()).toContain('Combine embeddings, text filters, and SQL access paths');
    expect(wrapper.text()).toContain('Feature coverage');
    expect(wrapper.text()).toContain('Implementation topics');
    expect(wrapper.text()).toContain('Sample breadth');
    expect(wrapper.findAll('.sample-card').length).toBeGreaterThan(0);
    expect(wrapper.find('.pattern-hero__icon').attributes('alt')).toBe('Semantic Search / RAG pattern icon');
    expect(wrapper.find('.pattern-link-list').exists()).toBe(true);
    expect(wrapper.findAll('.pattern-link-card')).toHaveLength(resolvedPatternMappings.length);
    expect(wrapper.find('.pattern-link-card.is-current').attributes('aria-current')).toBe('page');
    expect(wrapper.find('.pattern-link-card.is-current').text()).toContain('Semantic Search / RAG');
    expect(
      wrapper.findAllComponents(RouterLinkStub).some((link) => JSON.stringify(link.props('to')).includes('sample-detail'))
    ).toBe(true);
  });
});
