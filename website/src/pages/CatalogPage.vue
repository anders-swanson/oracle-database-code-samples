<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppShell from '../components/AppShell.vue';
import FilterChipGroup from '../components/FilterChipGroup.vue';
import SampleCard from '../components/SampleCard.vue';
import {
  defaultFilters,
  filterSamples,
  getFilterOptions,
  getStats,
  samples,
  serializeFilters,
  routeQueryToFilters
} from '../lib/catalog';
import type { CatalogFilters } from '../types';

const route = useRoute();
const router = useRouter();
const filters = ref<CatalogFilters>({ ...defaultFilters });
const syncingFromRoute = ref(false);
const catalogSection = ref<HTMLElement | null>(null);

function syncFromRoute() {
  syncingFromRoute.value = true;
  filters.value = routeQueryToFilters(route.query as Record<string, unknown>);
  syncingFromRoute.value = false;
}

watch(
  () => route.query,
  () => syncFromRoute(),
  { immediate: true }
);

watch(
  filters,
  (value) => {
    if (syncingFromRoute.value) {
      return;
    }

    const nextQuery = serializeFilters(value);
    const currentQuery = serializeFilters(routeQueryToFilters(route.query as Record<string, unknown>));

    if (JSON.stringify(nextQuery) !== JSON.stringify(currentQuery)) {
      router.replace({ query: nextQuery });
    }
  },
  { deep: true }
);

const stats = getStats(samples);
const options = getFilterOptions(samples);
const visibleSamples = computed(() => filterSamples(samples, filters.value));
const allTags = computed(() => [...options.tags].sort((left, right) => right.count - left.count || left.value.localeCompare(right.value)));

function scrollToCatalogTop() {
  nextTick(() => {
    catalogSection.value?.scrollIntoView({ block: 'start' });
  });
}

function toggle(listName: 'tags', value: string) {
  const current = filters.value[listName];
  filters.value = {
    ...filters.value,
    [listName]: current.includes(value)
      ? current.filter((entry) => entry !== value)
      : [...current, value]
  };
  scrollToCatalogTop();
}

function clearFilters() {
  filters.value = { ...defaultFilters };
  scrollToCatalogTop();
}
</script>

<template>
  <AppShell>
    <section class="hero">
      <div class="hero__copy">
        <span class="hero__eyebrow">browse and learn Oracle developer samples</span>
        <h1>Explore Oracle AI Database with real code samples you can run for free</h1>
        <p>
          Use this repo as a learning system: vector search, JSON, graph, spatial, TxEventQ, ORDS,
          Spring Boot integrations and more, all linked to sample code.
        </p>
      </div>
      <div class="hero__stats">
        <div class="stat-card">
          <strong>{{ stats.total }}</strong>
          <span>Code Samples</span>
        </div>
        <div class="stat-card">
          <strong>{{ stats.features }}</strong>
          <span>Database Features</span>
        </div>
        <div class="stat-card">
          <strong>{{ stats.languages }}</strong>
          <span>Languages</span>
        </div>
        <div class="stat-card">
          <strong>{{ stats.featured }}</strong>
          <span>Featured entries</span>
        </div>
      </div>
    </section>

    <section id="catalog-results" ref="catalogSection" class="catalog-layout">
      <aside class="catalog-sidebar">
        <div class="control-panel">
          <div class="control-panel__topline">
            <h2>Search sample catalog</h2>
            <button type="button" class="button button--ghost" @click="clearFilters">Clear</button>
          </div>

          <label class="search-field">
            <span>Search</span>
            <input v-model="filters.query" type="search" placeholder="vector, graph, spring, txeventq, json..." />
          </label>

          <label class="search-field">
            <span>Sort</span>
            <select v-model="filters.sort">
              <option value="featured">Featured</option>
              <option value="name">Name</option>
            </select>
          </label>

          <FilterChipGroup
            title="Tags"
            :options="allTags"
            :selected="filters.tags"
            @toggle="toggle('tags', $event)"
          />
        </div>
      </aside>

      <section class="catalog-results">
        <div class="catalog-results__header">
          <div>
            <span class="catalog-results__eyebrow">Live Results</span>
            <h2>{{ visibleSamples.length }} samples</h2>
          </div>
        </div>

        <div class="sample-grid">
          <SampleCard v-for="sample in visibleSamples" :key="sample.id" :sample="sample" />
        </div>

        <div v-if="visibleSamples.length === 0" class="empty-state">
          <h3>No samples match the current filters.</h3>
          <p>Clear a few chips or broaden the search term to reopen the catalog.</p>
        </div>
      </section>
    </section>
  </AppShell>
</template>
