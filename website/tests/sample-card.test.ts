import { mount, RouterLinkStub } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import SampleCard from '../src/components/SampleCard.vue';

describe('SampleCard', () => {
  it('renders the code link and detail link', () => {
    const wrapper = mount(SampleCard, {
      props: {
        sample: {
          id: 'mcp-agent',
          title: 'MCP Agent',
          description: 'Natural-language SQL agent sample.',
          path: 'mcp-agent',
          githubCodeUrl: 'https://example.com/code',
          tags: ['AI', 'MCP'],
          language: 'Java',
          parentCollection: 'Standalone',
          featured: true
        }
      },
      global: {
        stubs: {
          RouterLink: RouterLinkStub
        }
      }
    });

    expect(wrapper.getComponent(RouterLinkStub).props('to')).toEqual({
      name: 'sample-detail',
      params: { id: 'mcp-agent' }
    });
    expect(wrapper.get('a[href="https://example.com/code"]').text()).toContain('View Code');
  });
});
