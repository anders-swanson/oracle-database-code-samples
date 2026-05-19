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

<style scoped>
.hero {
  display: grid;
  grid-template-columns: 1.6fr 1fr;
  gap: 1.5rem;
  padding: 2rem 0 1.5rem;
}

.hero__copy {
  padding: clamp(1.5rem, 3vw, 2.75rem);
}

.hero__copy h1 {
  font-size: clamp(2.4rem, 6vw, 4.9rem);
}

.hero__copy p {
  font-size: 1.02rem;
}

.hero__stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}

.catalog-layout {
  display: grid;
  grid-template-columns: minmax(290px, 340px) minmax(0, 1fr);
  gap: 1.3rem;
}

.catalog-sidebar,
.catalog-results,
.control-panel {
  min-width: 0;
}

.catalog-sidebar {
  align-self: start;
  position: sticky;
  top: 1.25rem;
}

.control-panel {
  position: static;
  max-height: none;
  padding: 1.35rem;
  overflow: visible;
}

.control-panel__topline {
  align-items: center;
}

.control-panel h2,
.catalog-results__header h2 {
  margin: 0;
  font-size: 1.1rem;
}

.catalog-results__header {
  align-items: end;
  margin-bottom: 1.25rem;
  padding: 0 0.15rem;
}

.search-field {
  display: grid;
  gap: 0.55rem;
  margin: 1rem 0;
  color: var(--text-muted);
}

.search-field input,
.search-field select {
  width: 100%;
  padding: 0.92rem 1rem;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.04);
  color: var(--text);
  transition: transform var(--transition-fast), border-color var(--transition-fast), background var(--transition-fast), box-shadow var(--transition-fast);
}

.search-field input:focus,
.search-field select:focus {
  outline: none;
  border-color: rgba(89, 212, 255, 0.62);
  box-shadow: 0 0 0 4px rgba(89, 212, 255, 0.16);
}

@media (max-width: 1080px) {
  .hero,
  .catalog-layout {
    grid-template-columns: 1fr;
  }

  .control-panel,
  .catalog-sidebar {
    position: static;
  }

  .control-panel {
    max-height: none;
    overflow: visible;
  }
}

@media (max-width: 720px) {
  .hero__stats {
    grid-template-columns: 1fr 1fr;
  }

}
</style>
