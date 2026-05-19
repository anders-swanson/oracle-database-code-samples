<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import AppShell from '../components/AppShell.vue';
import SampleCard from '../components/SampleCard.vue';
import { findFeaturePageBySlug, findLanguagePageBySlug, samplesForIds } from '../lib/catalog';

const route = useRoute();
const languagePage = computed(() => findLanguagePageBySlug(String(route.params.slug ?? '')));
const languageSamples = computed(() => (languagePage.value ? samplesForIds(languagePage.value.sampleIds) : []));
const relatedFeatures = computed(() =>
  languagePage.value
    ? languagePage.value.relatedFeatureSlugs.map((slug) => findFeaturePageBySlug(slug)).filter(Boolean)
    : []
);
</script>

<template>
  <AppShell compact>
    <div v-if="languagePage" class="landing-page">
      <section class="detail-hero landing-hero">
        <div class="detail-hero__frame">
          <nav class="detail-breadcrumbs" aria-label="Breadcrumb">
            <RouterLink to="/">Catalog</RouterLink>
            <span>/</span>
            <span>Languages</span>
            <span>/</span>
            <span>{{ languagePage.name }}</span>
          </nav>
          <div class="detail-hero__topline">
            <span>Language Samples</span>
            <span>{{ languageSamples.length }} runnable examples</span>
          </div>
          <h1>{{ languagePage.title }}</h1>
          <p>{{ languagePage.description }}</p>
          <div class="detail-hero__actions">
            <RouterLink
              class="button button--primary"
              :to="{ name: 'catalog', query: { q: languagePage.name }, hash: '#catalog-results' }"
            >
              Search Catalog
            </RouterLink>
          </div>
        </div>
      </section>

      <section class="landing-layout">
        <article class="detail-panel">
          <div class="detail-panel__header">
            <span class="catalog-results__eyebrow">When to use this path</span>
          </div>
          <p class="detail-panel__excerpt">{{ languagePage.useWhen }}</p>
        </article>

        <aside v-if="relatedFeatures.length > 0" class="detail-panel">
          <div class="detail-panel__header">
            <span class="catalog-results__eyebrow">Common features</span>
          </div>
          <div class="landing-link-list">
            <RouterLink
              v-for="feature in relatedFeatures"
              :key="feature.slug"
              :to="{ name: 'feature-detail', params: { slug: feature.slug } }"
            >
              <strong>{{ feature.name }}</strong>
              <span>{{ feature.sampleIds.length }} samples</span>
            </RouterLink>
          </div>
        </aside>
      </section>

      <section class="related-section">
        <div class="detail-panel__header">
          <span class="catalog-results__eyebrow">Language sample set</span>
        </div>
        <div class="sample-grid sample-grid--compact">
          <SampleCard v-for="sample in languageSamples" :key="sample.id" :sample="sample" />
        </div>
      </section>
    </div>

    <section v-else class="empty-state empty-state--full">
      <h1>Language not found</h1>
      <p>The requested language page does not exist in the generated catalog.</p>
      <RouterLink class="button button--primary" to="/">Return to catalog</RouterLink>
    </section>
  </AppShell>
</template>
