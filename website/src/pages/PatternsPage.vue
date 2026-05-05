<script setup lang="ts">
import { computed, ref } from 'vue';
import AppShell from '../components/AppShell.vue';
import { patternIntents, resolvePatternMappings } from '../lib/catalog';

const patterns = resolvePatternMappings();
const selectedId = ref('event-streaming');
const sampleDescriptionMaxLength = 110;

const selectedPattern = computed(() => patterns.find((pattern) => pattern.id === selectedId.value) ?? patterns[0]);

const activeIntent = computed(
  () => patternIntents.find((intent) => intent.id === selectedPattern.value.intentId) ?? patternIntents[0]
);
const patternsWithIntent = computed(() =>
  patterns.map((pattern) => ({
    pattern,
    intent: patternIntents.find((intent) => intent.id === pattern.intentId) ?? patternIntents[0]
  }))
);

function selectPattern(id: string) {
  selectedId.value = id;
}

function isIntentActive(intentId: string) {
  return activeIntent.value.id === intentId;
}

function nodeClasses(patternId: string) {
  return {
    'is-selected': selectedPattern.value.id === patternId
  };
}

function truncateDescription(description: string) {
  if (description.length <= sampleDescriptionMaxLength) {
    return description;
  }

  const preview = description.slice(0, sampleDescriptionMaxLength - 3).trimEnd();
  const wordBoundary = preview.lastIndexOf(' ');
  const truncated = wordBoundary > 0 ? preview.slice(0, wordBoundary) : preview;

  return `${truncated}...`;
}
</script>

<template>
  <AppShell>
    <section class="patterns-hero">
      <div class="patterns-hero__copy">
        <span class="hero__eyebrow">Pattern Atlas</span>
        <h1>Map software patterns to Oracle AI Database samples</h1>
        <p>
          Explore database features by development intent, and then jump to runnable source code.
        </p>
      </div>
      <div class="patterns-legend" aria-label="Intent family legend">
        <span
          v-for="intent in patternIntents"
          :key="intent.id"
          class="patterns-legend__item"
          :class="{ 'is-active': isIntentActive(intent.id) }"
          :style="{ '--line-color': intent.color }"
        >
          <span></span>
          <strong>{{ intent.title }}</strong>
          <em>{{ intent.summary }}</em>
        </span>
      </div>
    </section>

    <section class="patterns-layout">
      <div class="patterns-node-shell">
        <div class="patterns-node-toolbar">
          <span>{{ patterns.length }} engineering patterns</span>
          <span>{{ selectedPattern.samples.length }} linked samples selected</span>
        </div>

        <div class="patterns-node-list" aria-label="Software pattern nodes">
          <button
            v-for="{ pattern, intent } in patternsWithIntent"
            :key="pattern.id"
            type="button"
            class="patterns-node"
            :class="nodeClasses(pattern.id)"
            :style="{ '--node-color': intent.color }"
            @click="selectPattern(pattern.id)"
          >
            <span class="patterns-node__marker"></span>
            <span>
              <strong>{{ pattern.title }}</strong>
              <em>{{ pattern.features.join(' / ') }}</em>
            </span>
            <span class="patterns-node__count">
              {{ pattern.samples.length }} samples
            </span>
          </button>
        </div>
      </div>

      <aside class="patterns-inspector" aria-live="polite">
        <div class="patterns-inspector__topline">
          <span class="catalog-results__eyebrow">Selected Pattern</span>
          <strong>{{ selectedPattern.samples.length }} samples</strong>
        </div>

        <h2>{{ selectedPattern.title }}</h2>
        <p>{{ selectedPattern.summary }}</p>

        <div class="patterns-inspector__block">
          <h3>Use when</h3>
          <p>{{ selectedPattern.useWhen }}</p>
        </div>

        <div class="patterns-inspector__features">
          <span v-for="feature in selectedPattern.features" :key="feature" class="sample-card__feature">
            {{ feature }}
          </span>
        </div>

        <div class="patterns-sample-stop-list">
          <RouterLink
            v-for="sample in selectedPattern.samples"
            :key="sample.id"
            class="patterns-sample-stop"
            :to="{ name: 'sample-detail', params: { id: sample.id } }"
          >
            <span class="patterns-sample-stop__dot"></span>
            <span>
              <strong>{{ sample.title }}</strong>
              <em :title="sample.description">{{ truncateDescription(sample.description) }}</em>
            </span>
          </RouterLink>
        </div>
      </aside>
    </section>
  </AppShell>
</template>
