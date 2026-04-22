import { mount, RouterLinkStub } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import SampleCard from '../src/components/SampleCard.vue';

describe('SampleCard', () => {
  it('renders the code link and detail link', () => {
    const wrapper = mount(SampleCard, {
      props: {
        sample: {
          id: 'mcp-agent',
          name: 'mcp-agent',
          title: 'MCP Agent',
          description: 'Natural-language SQL agent sample.',
          path: 'mcp-agent',
          readmePath: 'mcp-agent/README.md',
          githubReadmeUrl: 'https://example.com/readme',
          githubCodeUrl: 'https://example.com/code',
          tags: ['AI', 'MCP'],
          features: ['AI Agents'],
          language: 'Java',
          parentCollection: 'Standalone',
          blogPost: '',
          readmeExcerpt: 'Learn MCP.',
          highlights: [],
          featured: true,
          urlPath: '/samples/mcp-agent/',
          canonicalUrl: 'https://anders-swanson.github.io/oracle-database-code-samples/samples/mcp-agent/',
          metaTitle: 'MCP Agent | Oracle AI Database Code Samples',
          metaDescription: 'Natural-language SQL agent sample.',
          ogImageUrl: 'https://anders-swanson.github.io/oracle-database-code-samples/social-card.svg'
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
