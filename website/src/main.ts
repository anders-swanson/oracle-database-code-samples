import { createApp } from 'vue';
import App from './App.vue';
import { applyCurrentRouteMetadata, setupSeoSync } from './lib/seo';
import { router } from './router';
import './styles.css';

setupSeoSync(router);

createApp(App).use(router).mount('#app');

router.isReady().then(() => {
  applyCurrentRouteMetadata(router.currentRoute.value);
});
