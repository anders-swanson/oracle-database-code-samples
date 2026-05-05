import { ViteSSG } from 'vite-ssg';
import App from './App.vue';
import { routes, scrollBehavior } from './router';
import './styles.css';

export const createApp = ViteSSG(App, {
  base: import.meta.env.BASE_URL,
  routes,
  scrollBehavior
});
