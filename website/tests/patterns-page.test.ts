import { mount, RouterLinkStub } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

vi.mock('vue-router', () => ({
  useRoute: () => ({
    name: 'patterns'
  })
}));

import PatternsPage from '../src/pages/PatternsPage.vue';

function mountPatternsPage() {
  return mount(PatternsPage, {
    global: {
      stubs: {
        RouterLink: RouterLinkStub
      }
    }
  });
}

describe('PatternsPage', () => {
  it('renders the pattern node list and default Event Streaming inspector', () => {
    const wrapper = mountPatternsPage();

    expect(wrapper.text()).toContain('Pattern Atlas');
    expect(wrapper.text()).toContain('Event Streaming');
    expect(wrapper.text()).toContain('React to change');
    expect(wrapper.text()).toContain('TxEventQ');
    expect(wrapper.find('.patterns-node-list').exists()).toBe(true);
    expect(wrapper.findAll('.patterns-node').length).toBeGreaterThan(0);
    expect(wrapper.findAll('.patterns-legend button')).toHaveLength(0);
    expect(wrapper.find('.patterns-inspector').text()).toContain('Move business events');
    expect(wrapper.find('.patterns-inspector').text()).toContain('Oracle AI Database Transactional Event Queues Examples');
    expect(wrapper.find('.patterns-inspector').text()).toContain(
      'Spring Boot application that ingests news events, stores them in Oracle AI Database, and supports vector...'
    );
    expect(wrapper.find('.patterns-inspector').text()).not.toContain('news-event-streaming');

    const links = wrapper.findAllComponents(RouterLinkStub);
    expect(
      links.some(
        (link) =>
          JSON.stringify(link.props('to')) ===
          JSON.stringify({ name: 'sample-detail', params: { id: 'txeventq-examples' } })
      )
    ).toBe(true);
  });

  it('updates the inspector when a pattern node is selected', async () => {
    const wrapper = mountPatternsPage();

    const nodes = wrapper.findAll('.patterns-node');
    const aiAgentsNode = nodes.find((node) => node.text().includes('AI Agents'));

    expect(aiAgentsNode).toBeTruthy();
    await aiAgentsNode?.trigger('click');

    const inspector = wrapper.find('.patterns-inspector');
    expect(inspector.text()).toContain('AI Agents');
    expect(inspector.text()).toContain('SQLcl MCP Agent');
    expect(inspector.text()).toContain('LangChain4j Agent Memory');
  });
});
