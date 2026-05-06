import { ViteSSG } from 'vite-ssg';
import App from './App.vue';
import { findSampleById } from './lib/catalog';
import { loadSampleDetail } from './lib/sampleDetails';
import { routes, scrollBehavior } from './router';
import './styles.css';

export const createApp = ViteSSG(App, {
  base: import.meta.env.BASE_URL,
  routes,
  scrollBehavior
}, async ({ router, initialState }) => {
  router?.beforeEach(async (to) => {
    if (to.name !== 'sample-detail') {
      return true;
    }

    const sampleId = String(to.params.id);
    if (!findSampleById(sampleId)) {
      return true;
    }

    const existingDetail = initialState.sampleDetail;
    const sampleDetail = existingDetail?.id === sampleId ? existingDetail : await loadSampleDetail(sampleId);

    if (sampleDetail) {
      initialState.sampleDetail = sampleDetail;
      to.meta.state = {
        ...(to.meta.state as Record<string, unknown> | undefined),
        sampleDetail
      };
    }

    return true;
  });
});
