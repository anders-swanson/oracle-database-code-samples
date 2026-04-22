import { mount, RouterLinkStub } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import type { SampleRecord } from '../src/types';

const { sample } = vi.hoisted(() => ({
  sample: {
    id: 'database-per-service-example--sample',
    name: 'database-per-service-example/sample',
    title: 'Database Per Service Sample',
    description: 'Example sample.',
    path: 'database-per-service-example/sample',
    readmePath: 'database-per-service-example/sample/README.md',
    githubReadmeUrl:
      'https://github.com/anders-swanson/oracle-database-code-samples/blob/main/database-per-service-example/sample/README.md',
    githubCodeUrl:
      'https://github.com/anders-swanson/oracle-database-code-samples/tree/main/database-per-service-example/sample',
    tags: ['Java'],
    features: ['Security'],
    language: 'Java',
    parentCollection: 'database-per-service-example',
    blogPost: '',
    readmeExcerpt: 'Example sample.',
    highlights: ['The provisioning script at [create-pdbs.sql](./src/test/resources/create-pdbs.sql)'],
    featured: false,
    urlPath: '/samples/database-per-service-example--sample/',
    canonicalUrl:
      'https://anders-swanson.github.io/oracle-database-code-samples/samples/database-per-service-example--sample/',
    metaTitle: 'Database Per Service Sample | Oracle AI Database Code Samples',
    metaDescription: 'Example sample.',
    ogImageUrl: 'https://anders-swanson.github.io/oracle-database-code-samples/social-card.svg'
  } satisfies SampleRecord
}));

vi.mock('vue-router', () => ({
  useRoute: () => ({
    params: {
      id: sample.id
    }
  })
}));

vi.mock('../src/lib/catalog', () => ({
  samples: [sample],
  findSampleById: () => sample,
  findRelatedSamples: () => []
}));

import SampleDetailPage from '../src/pages/SampleDetailPage.vue';

describe('SampleDetailPage', () => {
  it('renders markdown links inside highlights as hyperlinks', () => {
    const wrapper = mount(SampleDetailPage, {
      global: {
        stubs: {
          RouterLink: RouterLinkStub
        }
      }
    });

    const link = wrapper.get(
      '.detail-panel__block a[href="https://github.com/anders-swanson/oracle-database-code-samples/blob/main/database-per-service-example/sample/src/test/resources/create-pdbs.sql"]'
    );

    expect(link.text()).toBe('create-pdbs.sql');
  });
});
