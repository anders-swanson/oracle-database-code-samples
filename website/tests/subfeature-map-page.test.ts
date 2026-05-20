import { mount, RouterLinkStub } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('vue-router', () => ({
  useRoute: () => ({
    name: 'feature-map'
  })
}));

vi.mock('../src/lib/catalog', () => ({
  samples: [],
  findPatternMappingByTopic: (name: string) =>
    name === 'Vector Search'
      ? {
          id: 'semantic-search-rag',
          title: 'Semantic Search / RAG'
        }
      : name === 'JSON'
        ? {
            id: 'json-documents-duality',
            title: 'JSON Documents and Duality'
          }
        : name === 'Testcontainers'
          ? {
              id: 'local-testing',
              title: 'Local Testing'
            }
          : undefined,
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
  let requestFullscreenMock: ReturnType<typeof vi.fn>;
  let exitFullscreenMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    requestFullscreenMock = vi.fn().mockResolvedValue(undefined);
    exitFullscreenMock = vi.fn().mockResolvedValue(undefined);

    Object.defineProperty(HTMLElement.prototype, 'requestFullscreen', {
      configurable: true,
      value: requestFullscreenMock
    });
    Object.defineProperty(document, 'exitFullscreen', {
      configurable: true,
      value: exitFullscreenMock
    });
  });

  afterEach(() => {
    delete (HTMLElement.prototype as Partial<HTMLElement>).requestFullscreen;
    delete (document as Partial<Document>).exitFullscreen;
  });

  function mountFeatureMap() {
    return mount(SubfeatureMapPage, {
      global: {
        stubs: {
          RouterLink: RouterLinkStub
        }
      }
    });
  }

  function worldStyle(wrapper: ReturnType<typeof mountFeatureMap>) {
    return wrapper.find('.tag-map-world').attributes('style');
  }

  function dispatchWheel(target: Element, deltaY: number) {
    target.dispatchEvent(
      new WheelEvent('wheel', {
        bubbles: true,
        cancelable: true,
        clientX: 120,
        clientY: 160,
        deltaY
      })
    );
  }

  it('renders graph nodes, hover details, and catalog links', async () => {
    const wrapper = mountFeatureMap();
    await wrapper.vm.$nextTick();

    expect(wrapper.text()).toContain('Oracle AI Database');
    expect(wrapper.text()).toContain('Converged Database');
    expect(wrapper.text()).toContain('Vector Search');
    expect(wrapper.text()).toContain('7');
    expect(wrapper.find('img[src="/feature-icons/vector-search.png"]').exists()).toBe(true);
    expect(wrapper.find('.tag-map-window').exists()).toBe(true);
    expect(wrapper.find('.tag-map-viewport').exists()).toBe(true);
    expect(wrapper.find('button.tag-map-panel__button').exists()).toBe(false);
    expect(wrapper.find('button[aria-label="View map fullscreen"]').exists()).toBe(true);
    expect(wrapper.find('button[aria-label="Zoom in"]').exists()).toBe(true);
    expect(wrapper.find('button[aria-label="Zoom out"]').exists()).toBe(true);
    expect(wrapper.find('button[aria-label="Recenter map"]').exists()).toBe(true);

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
          JSON.stringify(link.props('to')) ===
          JSON.stringify({ name: 'pattern-detail', params: { slug: 'semantic-search-rag' } })
      )
    ).toBe(true);
  });

  it('zooms the map window with bounded icon controls', async () => {
    const wrapper = mountFeatureMap();
    const zoomInButton = () => wrapper.find('button[aria-label="Zoom in"]');
    const zoomOutButton = () => wrapper.find('button[aria-label="Zoom out"]');

    expect(worldStyle(wrapper)).toContain('width: 2400px');
    expect(zoomInButton().attributes('disabled')).toBeUndefined();
    expect(zoomOutButton().attributes('disabled')).toBeUndefined();

    await zoomInButton().trigger('click');
    await wrapper.vm.$nextTick();
    await wrapper.vm.$nextTick();

    expect(worldStyle(wrapper)).toContain('width: 2760px');

    await zoomInButton().trigger('click');
    await zoomInButton().trigger('click');
    await zoomInButton().trigger('click');
    await wrapper.vm.$nextTick();

    expect(worldStyle(wrapper)).toContain('width: 3840px');
    expect(zoomInButton().attributes('disabled')).toBeDefined();

    await zoomOutButton().trigger('click');
    await zoomOutButton().trigger('click');
    await zoomOutButton().trigger('click');
    await zoomOutButton().trigger('click');
    await zoomOutButton().trigger('click');
    await zoomOutButton().trigger('click');
    await zoomOutButton().trigger('click');
    await wrapper.vm.$nextTick();

    expect(worldStyle(wrapper)).toContain('width: 1560px');
    expect(zoomOutButton().attributes('disabled')).toBeDefined();
  });

  it('zooms with mouse wheel direction and bounds', async () => {
    const wrapper = mountFeatureMap();
    const viewport = wrapper.find('.tag-map-viewport').element;

    dispatchWheel(viewport, -100);
    await wrapper.vm.$nextTick();
    await wrapper.vm.$nextTick();

    expect(worldStyle(wrapper)).toContain('width: 2760px');

    dispatchWheel(viewport, 100);
    await wrapper.vm.$nextTick();

    expect(worldStyle(wrapper)).toContain('width: 2400px');

    dispatchWheel(viewport, 100);
    dispatchWheel(viewport, 100);
    dispatchWheel(viewport, 100);
    await wrapper.vm.$nextTick();

    expect(worldStyle(wrapper)).toContain('width: 1560px');
    expect(wrapper.find('button[aria-label="Zoom out"]').attributes('disabled')).toBeDefined();
  });

  it('zooms with double-click on the map viewport', async () => {
    const wrapper = mountFeatureMap();

    await wrapper.find('.tag-map-viewport').trigger('dblclick', { clientX: 120, clientY: 160 });
    await wrapper.vm.$nextTick();
    await wrapper.vm.$nextTick();

    expect(worldStyle(wrapper)).toContain('width: 2760px');
  });

  it('ignores wheel and double-click zoom from map links and controls', async () => {
    const wrapper = mountFeatureMap();

    dispatchWheel(wrapper.find('.tag-map-node').element, -100);
    await wrapper.find('.tag-map-node').trigger('dblclick', { clientX: 120, clientY: 160 });
    dispatchWheel(wrapper.find('button[aria-label="Zoom in"]').element, -100);
    await wrapper.vm.$nextTick();

    expect(worldStyle(wrapper)).toContain('width: 2400px');
  });

  it('requests fullscreen for the map window control', async () => {
    const wrapper = mountFeatureMap();
    await wrapper.vm.$nextTick();

    await wrapper.find('button[aria-label="View map fullscreen"]').trigger('click');

    expect(requestFullscreenMock).toHaveBeenCalledTimes(1);
  });
});
