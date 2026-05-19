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

<style scoped>
.patterns-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 0.82fr);
  gap: 1.4rem;
  align-items: stretch;
  padding: 1.35rem 0 1rem;
}

.patterns-hero__copy {
  padding: clamp(1.35rem, 2.6vw, 2.3rem);
}

.patterns-hero__copy h1 {
  max-width: 18ch;
  font-size: clamp(2.35rem, 5vw, 4.2rem);
}

.patterns-legend {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-content: center;
  gap: 0.55rem;
  padding: 0.85rem;
}

.patterns-legend__item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 0.65rem;
  min-height: 4.4rem;
  padding: 0.75rem;
  border: 1px solid rgba(160, 197, 255, 0.18);
  border-radius: 0.55rem;
  background: rgba(255, 255, 255, 0.03);
  color: var(--text-muted);
  text-align: left;
}

.patterns-legend__item span {
  grid-row: span 2;
  width: 1.65rem;
  height: 0.32rem;
  border-radius: 999px;
  background: var(--line-color);
  box-shadow: 0 0 18px color-mix(in srgb, var(--line-color) 42%, transparent);
}

.patterns-legend__item strong,
.patterns-legend__item em {
  min-width: 0;
  overflow-wrap: anywhere;
}

.patterns-legend__item strong {
  color: var(--text);
  line-height: 1.2;
}

.patterns-legend__item em {
  font-size: 0.8rem;
  font-style: normal;
  line-height: 1.3;
}

.patterns-legend__item.is-active {
  border-color: color-mix(in srgb, var(--line-color) 62%, transparent);
  background: color-mix(in srgb, var(--line-color) 16%, rgba(255, 255, 255, 0.04));
  color: var(--text);
}

.patterns-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(320px, 0.68fr);
  gap: 1.3rem;
  align-items: start;
}

.patterns-node-shell {
  min-width: 0;
  padding: 1rem;
}

.patterns-node-toolbar,
.patterns-inspector__topline {
  align-items: center;
  color: var(--text-muted);
  font-size: 0.92rem;
}

.patterns-node-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;
  margin-top: 0.9rem;
}

.patterns-node {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.75rem;
  min-height: 5rem;
  padding: 0.9rem;
  border: 1px solid rgba(160, 197, 255, 0.14);
  border-radius: 0.65rem;
  background: rgba(255, 255, 255, 0.035);
  color: var(--text);
  text-align: left;
  transition: border-color var(--transition-fast), background var(--transition-fast), transform var(--transition-fast);
}

.patterns-node:hover {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--node-color, #59d4ff) 46%, rgba(244, 247, 255, 0.16));
  background: color-mix(in srgb, var(--node-color, #59d4ff) 12%, rgba(255, 255, 255, 0.04));
}

.patterns-node.is-selected {
  border-color: color-mix(in srgb, var(--node-color, #59d4ff) 72%, rgba(244, 247, 255, 0.2));
  background: color-mix(in srgb, var(--node-color, #59d4ff) 18%, rgba(255, 255, 255, 0.05));
}

.patterns-node__marker {
  width: 0.95rem;
  height: 0.95rem;
  border: 3px solid rgba(244, 247, 255, 0.9);
  border-radius: 50%;
  background: var(--node-color, #59d4ff);
  box-shadow: 0 0 16px color-mix(in srgb, var(--node-color, #59d4ff) 44%, transparent);
}

.patterns-node__count {
  justify-self: end;
  color: var(--text-muted);
  font-size: 0.8rem;
  white-space: nowrap;
}

.patterns-inspector {
  position: sticky;
  top: 1.25rem;
  display: grid;
  gap: 1rem;
  padding: 1.35rem;
}

.patterns-inspector__topline strong {
  color: var(--accent);
}

.patterns-inspector h2 {
  margin: 0;
  font-size: clamp(1.8rem, 3vw, 2.55rem);
  line-height: 1;
}

.patterns-inspector p {
  margin: 0;
}

.patterns-inspector__block {
  padding-block: 0.95rem;
  border-block: 1px solid rgba(160, 197, 255, 0.16);
}

.patterns-inspector__block h3 {
  margin: 0 0 0.55rem;
  color: var(--accent-warm);
  font-size: 0.9rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.patterns-inspector__features {
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem;
}

.patterns-sample-stop-list {
  display: grid;
  gap: 0.55rem;
}

.patterns-sample-stop {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 0.75rem;
  padding: 0.8rem 0;
  border-bottom: 1px solid rgba(160, 197, 255, 0.12);
  transition: transform var(--transition-fast), color var(--transition-fast);
}

.patterns-sample-stop:hover {
  transform: translateX(0.2rem);
  color: var(--accent);
}

.patterns-node:focus-visible,
.patterns-sample-stop:focus-visible,
.patterns-legend__item:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.patterns-sample-stop__dot {
  width: 0.9rem;
  height: 0.9rem;
  border: 3px solid rgba(244, 247, 255, 0.9);
  border-radius: 50%;
  background: #081423;
  box-shadow: 0 0 16px rgba(89, 212, 255, 0.22);
}

.patterns-sample-stop strong,
.patterns-node strong {
  display: block;
  line-height: 1.25;
}

.patterns-sample-stop em,
.patterns-node em {
  display: block;
  margin-top: 0.2rem;
  color: var(--text-muted);
  font-size: 0.82rem;
  font-style: normal;
  overflow-wrap: anywhere;
}

@media (max-width: 1080px) {
  .patterns-layout {
    grid-template-columns: 1fr;
  }

  .patterns-inspector {
    position: static;
  }

  .patterns-hero__copy h1 {
    max-width: 100%;
    font-size: clamp(2.15rem, 7vw, 3.55rem);
    line-height: 0.98;
  }

  .patterns-legend {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .patterns-legend__item {
    font-size: 0.9rem;
  }
}

@media (max-width: 820px) {
  .patterns-hero {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .patterns-legend {
    display: flex;
    overflow-x: auto;
    scrollbar-width: thin;
  }

  .patterns-legend__item {
    flex: 0 0 10.5rem;
  }

  .patterns-hero__copy {
    padding: 1.25rem;
  }

  .patterns-hero__copy h1 {
    font-size: clamp(2rem, 10vw, 2.55rem);
    line-height: 1.02;
  }

  .patterns-node-list {
    grid-template-columns: 1fr;
  }

  .patterns-node {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .patterns-node__count {
    grid-column: 2;
    justify-self: start;
  }
}
</style>
