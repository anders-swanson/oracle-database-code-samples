<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import AppShell from '../components/AppShell.vue';
import SampleCard from '../components/SampleCard.vue';
import { findFeaturePageBySlug, samplesForIds } from '../lib/catalog';

const route = useRoute();
const featurePage = computed(() => findFeaturePageBySlug(String(route.params.slug ?? '')));
const featureSamples = computed(() => (featurePage.value ? samplesForIds(featurePage.value.sampleIds) : []));
const relatedFeatures = computed(() =>
  featurePage.value
    ? featurePage.value.relatedFeatureSlugs.map((slug) => findFeaturePageBySlug(slug)).filter(Boolean)
    : []
);
</script>

<template>
  <AppShell compact>
    <div v-if="featurePage" class="landing-page">
      <section class="detail-hero landing-hero">
        <div class="detail-hero__frame">
          <nav class="detail-breadcrumbs" aria-label="Breadcrumb">
            <RouterLink to="/">Catalog</RouterLink>
            <span>/</span>
            <span>Features</span>
            <span>/</span>
            <span>{{ featurePage.name }}</span>
          </nav>
          <div class="detail-hero__topline">
            <span>Feature Samples</span>
            <span>{{ featureSamples.length }} runnable examples</span>
          </div>
          <h1>{{ featurePage.title }}</h1>
          <p>{{ featurePage.description }}</p>
          <div class="detail-hero__actions">
            <RouterLink
              class="button button--primary"
              :to="{ name: 'catalog', query: { q: featurePage.name }, hash: '#catalog-results' }"
            >
              Search Catalog
            </RouterLink>
            <RouterLink class="button button--ghost" :to="{ name: 'feature-map' }">
              Topic Map
            </RouterLink>
          </div>
        </div>
      </section>

      <section class="landing-layout">
        <article class="detail-panel">
          <div class="detail-panel__header">
            <span class="catalog-results__eyebrow">When to use this feature</span>
          </div>
          <p class="detail-panel__excerpt">{{ featurePage.useWhen }}</p>
          <div class="detail-panel__block">
            <h2>What these samples show</h2>
            <p>
              These Oracle AI Database samples link the feature intent to runnable source code, README
              context, and related implementation paths in the catalog.
            </p>
          </div>
        </article>

        <aside v-if="relatedFeatures.length > 0" class="detail-panel">
          <div class="detail-panel__header">
            <span class="catalog-results__eyebrow">Related features</span>
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
          <span class="catalog-results__eyebrow">Feature sample set</span>
        </div>
        <div class="sample-grid sample-grid--compact">
          <SampleCard v-for="sample in featureSamples" :key="sample.id" :sample="sample" />
        </div>
      </section>
    </div>

    <section v-else class="empty-state empty-state--full">
      <h1>Feature not found</h1>
      <p>The requested feature page does not exist in the generated catalog.</p>
      <RouterLink class="button button--primary" to="/">Return to catalog</RouterLink>
    </section>
  </AppShell>
</template>
