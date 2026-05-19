<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import AppShell from '../components/AppShell.vue';
import InlineMarkdown from '../components/InlineMarkdown.vue';
import SampleCard from '../components/SampleCard.vue';
import {
  findFeaturePageByName,
  findLanguagePageByName,
  findRelatedSamples,
  findSampleById,
  samples
} from '../lib/catalog';
import { getFeatureDetail } from '../data/featureDetails';
import { getRouteSampleDetail, hydrateSample } from '../lib/sampleDetails';

const route = useRoute();
const sampleId = computed(() => String(route.params.id));
const summary = computed(() => findSampleById(sampleId.value));
const sample = computed(() => hydrateSample(sampleId.value, getRouteSampleDetail(route)));
const related = computed(() => (summary.value ? findRelatedSamples(summary.value, samples) : []));
const languagePage = computed(() => (sample.value ? findLanguagePageByName(sample.value.language) : undefined));
const featureContext = computed(() =>
  sample.value
    ? sample.value.features.map((feature) => ({
        name: feature,
        detail: getFeatureDetail(feature),
        page: findFeaturePageByName(feature)
      }))
    : []
);
</script>

<template>
  <AppShell compact>
    <div v-if="sample" class="detail-page">
      <section class="detail-hero">
        <div class="detail-hero__frame">
          <nav class="detail-breadcrumbs" aria-label="Breadcrumb">
            <RouterLink to="/">Catalog</RouterLink>
            <span>/</span>
            <span>{{ sample.title }}</span>
          </nav>
          <div class="detail-hero__topline">
            <span>{{ sample.language }}</span>
            <span>{{ sample.parentCollection }}</span>
          </div>
          <h1>{{ sample.title }}</h1>
          <p>{{ sample.description }}</p>

          <div class="detail-hero__actions">
            <a class="button button--primary" :href="sample.githubCodeUrl" target="_blank" rel="noreferrer">
              View Code
            </a>
            <a class="button button--ghost" :href="sample.githubReadmeUrl" target="_blank" rel="noreferrer">
              View README
            </a>
            <a
              v-if="sample.blogPost"
              class="button button--ghost"
              :href="sample.blogPost"
              target="_blank"
              rel="noreferrer"
            >
              Blog Post
            </a>
          </div>

          <div class="detail-hero__feature-list">
            <template v-for="feature in featureContext" :key="feature.name">
              <RouterLink
                v-if="feature.page"
                class="sample-card__feature"
                :to="{ name: 'feature-detail', params: { slug: feature.page.slug } }"
              >
                {{ feature.name }}
              </RouterLink>
            </template>
            <span v-for="tag in sample.tags" :key="tag" class="sample-card__tag">#{{ tag }}</span>
          </div>
        </div>
      </section>

      <section class="detail-layout">
        <article class="detail-panel">
          <div class="detail-panel__header">
            <span class="catalog-results__eyebrow">What this sample helps you learn</span>
          </div>
          <p class="detail-panel__excerpt">{{ sample.readmeExcerpt }}</p>
          <div v-if="featureContext.length > 0" class="detail-panel__block">
            <h2>What this sample demonstrates</h2>
            <div class="feature-context-list">
              <section v-for="feature in featureContext" :key="feature.name">
                <h3>
                  <RouterLink
                    v-if="feature.page"
                    :to="{ name: 'feature-detail', params: { slug: feature.page.slug } }"
                  >
                    {{ feature.name }}
                  </RouterLink>
                  <span v-else>{{ feature.name }}</span>
                </h3>
                <p>{{ feature.detail?.description ?? `Runnable ${feature.name} behavior on Oracle AI Database.` }}</p>
                <p>{{ feature.detail?.useWhen ?? `Use when ${feature.name} needs to be tested against real database behavior.` }}</p>
              </section>
            </div>
          </div>
          <div v-if="sample.highlights.length > 0" class="detail-panel__block">
            <h3>Highlights</h3>
            <ul>
              <li v-for="highlight in sample.highlights" :key="highlight">
                <InlineMarkdown :text="highlight" :base-url="sample.githubReadmeUrl" />
              </li>
            </ul>
          </div>
        </article>

        <aside class="detail-sidebar">
          <div class="detail-panel">
            <div class="detail-panel__header">
              <span class="catalog-results__eyebrow">Context</span>
            </div>
            <dl class="metadata-list">
              <div>
                <dt>Repo path</dt>
                <dd><code>{{ sample.path }}</code></dd>
              </div>
              <div>
                <dt>Collection</dt>
                <dd>{{ sample.parentCollection }}</dd>
              </div>
              <div>
                <dt>Language</dt>
                <dd>
                  <RouterLink
                    v-if="languagePage"
                    class="metadata-list__link"
                    :to="{ name: 'language-detail', params: { slug: languagePage.slug } }"
                  >
                    {{ sample.language }}
                  </RouterLink>
                  <span v-else>{{ sample.language }}</span>
                </dd>
              </div>
              <div>
                <dt>Tags</dt>
                <dd>{{ sample.tags.length > 0 ? sample.tags.join(', ') : 'No tags declared' }}</dd>
              </div>
            </dl>
          </div>
        </aside>
      </section>

      <section v-if="related.length > 0" class="related-section">
        <div class="detail-panel__header">
          <span class="catalog-results__eyebrow">Keep Exploring</span>
        </div>
        <div class="sample-grid sample-grid--compact">
          <SampleCard v-for="entry in related" :key="entry.id" :sample="entry" />
        </div>
      </section>
    </div>

    <section v-else class="empty-state empty-state--full">
      <h1>Sample not found</h1>
      <p>The requested sample id does not exist in the generated catalog.</p>
      <RouterLink class="button button--primary" to="/">Return to catalog</RouterLink>
    </section>
  </AppShell>
</template>

<style scoped>
.detail-page {
  display: grid;
  gap: 1.4rem;
}

.detail-hero__feature-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(280px, 0.9fr);
  gap: 1.3rem;
}

.metadata-list {
  display: grid;
  gap: 1rem;
  margin: 0;
}

.metadata-list div {
  display: grid;
  gap: 0.35rem;
}

.metadata-list dt {
  color: var(--text-muted);
}

.metadata-list dd {
  margin: 0;
}

.metadata-list__link,
.feature-context-list a {
  color: var(--accent);
}

.metadata-list__link:hover,
.feature-context-list a:hover {
  color: #bfefff;
}

.feature-context-list {
  display: grid;
  gap: 1rem;
}

.feature-context-list section {
  display: grid;
  gap: 0.45rem;
  padding: 1rem;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.03);
}

.feature-context-list h3,
.feature-context-list p {
  margin: 0;
}

@media (max-width: 1080px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }
}
</style>
