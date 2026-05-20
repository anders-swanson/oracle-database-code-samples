<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import AppShell from '../components/AppShell.vue';
import SampleCard from '../components/SampleCard.vue';
import { getPatternVisual } from '../data/patternVisuals';
import { findPatternMappingBySlug, findRelatedPatternMappings } from '../lib/catalog';
import type { PatternMapping } from '../types';

const route = useRoute();
const patternPage = computed(() => findPatternMappingBySlug(String(route.params.slug ?? '')));
const patternSamples = computed(() => patternPage.value?.samples ?? []);
const relatedPatterns = computed(() => (patternPage.value ? findRelatedPatternMappings(patternPage.value) : []));
const patternVisual = computed(() =>
  getPatternVisual(patternPage.value ? visualTopicForPattern(patternPage.value) : 'Oracle AI Database')
);
const relatedPatternCards = computed(() =>
  relatedPatterns.value.map((pattern) => ({
    pattern,
    visual: getPatternVisual(visualTopicForPattern(pattern))
  }))
);

function visualTopicForPattern(pattern: Pick<PatternMapping, 'title' | 'features' | 'topics'>) {
  return pattern.topics[0] ?? pattern.features[0] ?? pattern.title;
}

function patternInitials(name: string) {
  return name
    .split(/\s+/)
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
}
</script>

<template>
  <AppShell compact>
    <div v-if="patternPage" class="landing-page pattern-landing-page" :style="patternVisual.styleVars">
      <section class="detail-hero landing-hero pattern-hero">
        <div class="detail-hero__frame pattern-hero__frame">
          <div class="pattern-hero__grid">
            <div class="pattern-hero__copy">
              <nav class="detail-breadcrumbs" aria-label="Breadcrumb">
                <RouterLink to="/">Catalog</RouterLink>
                <span>/</span>
                <span>Patterns</span>
                <span>/</span>
                <span>{{ patternPage.title }}</span>
              </nav>
              <div class="detail-hero__topline">
                <span>Pattern Samples</span>
                <span>{{ patternSamples.length }} runnable examples</span>
              </div>
              <h1>{{ patternPage.title }} Pattern</h1>
              <p>{{ patternPage.summary }}</p>
              <div class="detail-hero__actions">
                <RouterLink
                  class="button button--primary pattern-button"
                  :to="{ name: 'catalog', query: { q: patternPage.title }, hash: '#catalog-results' }"
                >
                  Search Catalog
                </RouterLink>
                <RouterLink class="button button--ghost" :to="{ name: 'feature-map' }">
                  Topic Map
                </RouterLink>
              </div>
            </div>

            <aside class="pattern-hero__visual" aria-label="Pattern summary">
              <img
                v-if="patternVisual.iconPath"
                class="pattern-hero__watermark"
                :src="patternVisual.iconPath"
                alt=""
                aria-hidden="true"
              />
              <div class="pattern-hero__icon-shell">
                <img
                  v-if="patternVisual.iconPath"
                  class="pattern-hero__icon"
                  :src="patternVisual.iconPath"
                  :alt="`${patternPage.title} pattern icon`"
                />
                <span v-else class="pattern-hero__initials">{{ patternInitials(patternPage.title) }}</span>
              </div>
              <div class="pattern-hero__visual-copy">
                <span>Pattern focus</span>
                <strong>{{ patternPage.title }}</strong>
              </div>
              <div class="pattern-hero__stat">
                <strong>{{ patternSamples.length }}</strong>
                <span>runnable examples</span>
              </div>
            </aside>
          </div>
        </div>
      </section>

      <section class="landing-layout">
        <article class="detail-panel">
          <div class="detail-panel__header">
            <span class="catalog-results__eyebrow">When to use this pattern</span>
          </div>
          <p class="detail-panel__excerpt">{{ patternPage.useWhen }}</p>
          <div class="detail-panel__block">
            <h2>What these samples show</h2>
            <p>
              These Oracle AI Database samples map the engineering pattern to runnable source code, README
              context, and related implementation paths in the catalog.
            </p>
          </div>
        </article>

        <aside v-if="relatedPatterns.length > 0" class="detail-panel">
          <div class="detail-panel__header">
            <span class="catalog-results__eyebrow">Related patterns</span>
          </div>
          <div class="pattern-link-list">
            <RouterLink
              v-for="{ pattern, visual } in relatedPatternCards"
              :key="pattern.id"
              class="pattern-link-card"
              :to="{ name: 'pattern-detail', params: { slug: pattern.id } }"
              :style="visual.styleVars"
            >
              <span class="pattern-link-card__icon">
                <img v-if="visual.iconPath" :src="visual.iconPath" alt="" aria-hidden="true" />
                <span v-else>{{ patternInitials(pattern.title) }}</span>
              </span>
              <span class="pattern-link-card__copy">
                <strong>{{ pattern.title }}</strong>
                <span>{{ pattern.samples.length }} samples</span>
              </span>
            </RouterLink>
          </div>
        </aside>
      </section>

      <section class="related-section">
        <div class="detail-panel__header">
          <span class="catalog-results__eyebrow">Pattern sample set</span>
        </div>
        <div class="sample-grid sample-grid--compact">
          <SampleCard v-for="sample in patternSamples" :key="sample.id" :sample="sample" />
        </div>
      </section>
    </div>

    <section v-else class="empty-state empty-state--full">
      <h1>Pattern not found</h1>
      <p>The requested pattern page does not exist in the generated catalog.</p>
      <RouterLink class="button button--primary" to="/">Return to catalog</RouterLink>
    </section>
  </AppShell>
</template>

<style scoped>
.pattern-landing-page {
  --pattern-accent: var(--accent);
  --pattern-accent-rgb: 89, 212, 255;
  --pattern-accent-2: var(--accent-green);
  --pattern-accent-2-rgb: 93, 224, 167;
  --pattern-panel-rgb: 18, 43, 57;
}

.pattern-hero__frame {
  position: relative;
  overflow: hidden;
  border-color: rgba(var(--pattern-accent-rgb), 0.34);
  background:
    radial-gradient(circle at 88% 18%, rgba(var(--pattern-accent-rgb), 0.22), transparent 30%),
    radial-gradient(circle at 12% 100%, rgba(var(--pattern-accent-2-rgb), 0.14), transparent 32%),
    linear-gradient(135deg, rgba(var(--pattern-panel-rgb), 0.94), rgba(7, 16, 29, 0.94) 64%);
}

.pattern-hero__frame::before {
  content: "";
  position: absolute;
  inset: 0;
  border-top: 0.3rem solid rgba(var(--pattern-accent-rgb), 0.72);
  pointer-events: none;
}

.pattern-hero__grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(18rem, 0.38fr);
  gap: clamp(1.25rem, 4vw, 3rem);
  align-items: stretch;
}

.pattern-hero__copy {
  display: grid;
  align-content: start;
}

.pattern-hero__copy .detail-hero__topline span:first-child,
.pattern-hero__copy .detail-breadcrumbs a {
  color: var(--pattern-accent);
}

.pattern-button {
  border-color: rgba(var(--pattern-accent-rgb), 0.46);
  background: linear-gradient(180deg, rgba(var(--pattern-accent-rgb), 0.28), rgba(var(--pattern-panel-rgb), 0.8));
}

.pattern-button:hover {
  box-shadow: 0 12px 30px rgba(var(--pattern-accent-rgb), 0.2);
}

.pattern-hero__visual {
  position: relative;
  display: grid;
  min-height: 100%;
  align-content: end;
  gap: 1rem;
  padding: clamp(1.1rem, 2.4vw, 1.5rem);
  overflow: hidden;
  border: 1px solid rgba(var(--pattern-accent-rgb), 0.32);
  border-radius: 24px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.08), transparent 42%),
    linear-gradient(180deg, rgba(var(--pattern-accent-rgb), 0.12), rgba(255, 255, 255, 0.03));
}

.pattern-hero__watermark {
  position: absolute;
  top: -1.7rem;
  right: -1.4rem;
  width: min(72%, 14rem);
  opacity: 0.12;
  filter: saturate(1.2);
  pointer-events: none;
}

.pattern-hero__icon-shell {
  position: relative;
  display: grid;
  place-items: center;
  width: clamp(6rem, 12vw, 8rem);
  aspect-ratio: 1;
  border: 1px solid rgba(var(--pattern-accent-rgb), 0.4);
  border-radius: 26px;
  background:
    radial-gradient(circle at 30% 22%, rgba(255, 255, 255, 0.2), transparent 32%),
    linear-gradient(145deg, rgba(var(--pattern-accent-rgb), 0.2), rgba(var(--pattern-accent-2-rgb), 0.1));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.12),
    0 20px 60px rgba(var(--pattern-accent-rgb), 0.13);
}

.pattern-hero__icon {
  width: 68%;
  max-height: 68%;
  object-fit: contain;
}

.pattern-hero__initials {
  color: var(--pattern-accent);
  font-size: 2rem;
  font-weight: 800;
}

.pattern-hero__visual-copy,
.pattern-hero__stat {
  position: relative;
  z-index: 1;
}

.pattern-hero__visual-copy {
  display: grid;
  gap: 0.2rem;
}

.pattern-hero__visual-copy span,
.pattern-hero__stat span {
  color: var(--text-muted);
  font-size: 0.78rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.pattern-hero__visual-copy strong {
  font-size: clamp(1.4rem, 3vw, 2rem);
  line-height: 1.02;
}

.pattern-hero__stat {
  display: flex;
  align-items: baseline;
  gap: 0.7rem;
  padding-top: 0.9rem;
  border-top: 1px solid rgba(var(--pattern-accent-rgb), 0.22);
}

.pattern-hero__stat strong {
  color: var(--pattern-accent);
  font-size: clamp(2.4rem, 5vw, 3.8rem);
  line-height: 0.9;
}

.pattern-link-list {
  display: grid;
  gap: 0.8rem;
}

.pattern-link-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 0.8rem;
  align-items: center;
  padding: 0.85rem;
  border: 1px solid rgba(var(--pattern-accent-rgb), 0.24);
  border-left: 0.28rem solid rgba(var(--pattern-accent-rgb), 0.78);
  border-radius: var(--radius-md);
  background:
    linear-gradient(90deg, rgba(var(--pattern-accent-rgb), 0.13), transparent 52%),
    rgba(255, 255, 255, 0.03);
  transition: transform var(--transition-fast), border-color var(--transition-fast), background var(--transition-fast), box-shadow var(--transition-fast);
}

.pattern-link-card:hover {
  transform: translateY(-2px);
  border-color: rgba(var(--pattern-accent-rgb), 0.48);
  background:
    linear-gradient(90deg, rgba(var(--pattern-accent-rgb), 0.18), transparent 56%),
    var(--bg-soft-hover);
}

.pattern-link-card:focus-visible {
  outline: none;
  border-color: rgba(var(--pattern-accent-rgb), 0.58);
  box-shadow: 0 0 0 4px rgba(var(--pattern-accent-rgb), 0.16);
}

.pattern-link-card__icon {
  display: grid;
  place-items: center;
  width: 2.65rem;
  aspect-ratio: 1;
  border: 1px solid rgba(var(--pattern-accent-rgb), 0.28);
  border-radius: 14px;
  background: rgba(var(--pattern-accent-rgb), 0.1);
  color: var(--pattern-accent);
  font-size: 0.8rem;
  font-weight: 800;
}

.pattern-link-card__icon img {
  width: 64%;
  max-height: 64%;
  object-fit: contain;
}

.pattern-link-card__copy {
  display: grid;
  min-width: 0;
  gap: 0.15rem;
}

.pattern-link-card__copy strong {
  overflow-wrap: anywhere;
}

.pattern-link-card__copy span {
  color: var(--text-muted);
  font-size: 0.9rem;
}

@media (max-width: 860px) {
  .pattern-hero__grid {
    grid-template-columns: 1fr;
  }

  .pattern-hero__visual {
    min-height: 16rem;
  }
}

@media (max-width: 560px) {
  .pattern-hero__visual {
    min-height: auto;
  }

  .pattern-hero__stat {
    align-items: flex-start;
    flex-direction: column;
    gap: 0.35rem;
  }
}
</style>
