import { mount, RouterLinkStub } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

const routeState = vi.hoisted(() => ({
  name: 'catalog' as string | undefined
}));

vi.mock('vue-router', () => ({
  useRoute: () => routeState
}));

import AppShell from '../src/components/AppShell.vue';

function mountAppShell(routeName: string) {
  routeState.name = routeName;

  return mount(AppShell, {
    slots: {
      default: '<section>Page content</section>'
    },
    global: {
      stubs: {
        RouterLink: RouterLinkStub
      }
    }
  });
}

describe('AppShell', () => {
  it('renders the shared top-level navigation', () => {
    const wrapper = mountAppShell('catalog');
    const navLinks = wrapper.findAll('.site-header__nav-link');

    expect(navLinks.map((link) => link.text())).toEqual(['Catalog', 'Patterns', 'Topic Map']);
    expect(wrapper.find('.site-header__github').text()).toContain('Star on GitHub');
    expect(wrapper.text()).toContain('Page content');
  });

  it.each([
    ['catalog', 'Catalog'],
    ['sample-detail', 'Catalog'],
    ['patterns', 'Patterns'],
    ['feature-map', 'Topic Map'],
    ['feature-detail', 'Topic Map']
  ])('marks %s routes under %s', (routeName, activeLabel) => {
    const wrapper = mountAppShell(routeName);

    expect(wrapper.find('.site-header__nav-link.is-active').text()).toBe(activeLabel);
  });
});
