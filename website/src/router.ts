import { createRouter, createWebHistory } from 'vue-router';
import CatalogPage from './pages/CatalogPage.vue';
import SampleDetailPage from './pages/SampleDetailPage.vue';

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'catalog',
      component: CatalogPage
    },
    {
      path: '/samples/:id/',
      name: 'sample-detail',
      component: SampleDetailPage
    }
  ],
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition;
    }
    if (to.path === from.path) {
      return false;
    }
    return { top: 0 };
  }
});
