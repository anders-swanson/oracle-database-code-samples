import type { RouteRecordRaw, RouterScrollBehavior } from 'vue-router';
import CatalogPage from './pages/CatalogPage.vue';
import LanguagePage from './pages/LanguagePage.vue';
import PatternDetailPage from './pages/PatternDetailPage.vue';
import PatternsPage from './pages/PatternsPage.vue';
import SampleDetailPage from './pages/SampleDetailPage.vue';
import SubfeatureMapPage from './pages/SubfeatureMapPage.vue';

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'catalog',
    component: CatalogPage
  },
  {
    path: '/feature-map/',
    name: 'feature-map',
    component: SubfeatureMapPage
  },
  {
    path: '/patterns/',
    name: 'patterns',
    component: PatternsPage
  },
  {
    path: '/patterns/:slug/',
    name: 'pattern-detail',
    component: PatternDetailPage
  },
  {
    path: '/languages/:slug/',
    name: 'language-detail',
    component: LanguagePage
  },
  {
    path: '/subfeature-map/',
    redirect: '/feature-map/'
  },
  {
    path: '/samples/:id/',
    name: 'sample-detail',
    component: SampleDetailPage
  }
];

export const scrollBehavior: RouterScrollBehavior = (to, from, savedPosition) => {
  if (savedPosition) {
    return savedPosition;
  }
  if (to.hash) {
    return {
      el: to.hash
    };
  }
  if (to.path === from.path) {
    return false;
  }
  return { top: 0 };
};
