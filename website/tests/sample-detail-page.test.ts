import { mount, RouterLinkStub } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import type { SampleRecord } from '../src/types';

const { sample } = vi.hoisted(() => ({
  sample: {
    id: 'database-per-service-example--sample',
    title: 'Database Per Service Sample',
    description: 'Example sample.',
    path: 'database-per-service-example/sample',
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
    sourceUpdatedAt: '2026-05-01T00:00:00.000Z',
    featured: false,
    canonicalUrl:
      'https://anders-swanson.github.io/oracle-database-code-samples/samples/database-per-service-example--sample/',
    metaTitle: 'Database Per Service Sample | Oracle AI Database Code Samples',
    metaDescription: 'Example sample.',
    ogImageUrl:
      'https://anders-swanson.github.io/oracle-database-code-samples/sample-cards/database-per-service-example--sample.png'
  } satisfies SampleRecord
}));

vi.mock('vue-router', () => ({
  useRoute: () => ({
    params: {
      id: sample.id
    },
    meta: {
      state: {
        sampleDetail: sample
      }
    }
  })
}));

vi.mock('../src/lib/catalog', () => ({
  samples: [sample],
  findPatternMappingByTopic: (name: string) =>
    name === 'Security'
      ? {
          id: 'spring-microservice-config',
          title: 'Spring Boot',
          sampleIds: [sample.id]
        }
      : undefined,
  findLanguagePageByName: (name: string) =>
    name === 'Java'
      ? {
          slug: 'java',
          name: 'Java'
        }
      : undefined,
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
    expect(wrapper.text()).toContain('What this sample demonstrates');
    expect(wrapper.findComponent(RouterLinkStub).exists()).toBe(true);
  });
});
