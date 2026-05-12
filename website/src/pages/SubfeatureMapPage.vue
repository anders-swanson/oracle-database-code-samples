<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import AppShell from '../components/AppShell.vue';
import { buildSubfeatureGraph, samples } from '../lib/catalog';
import type { SubfeatureGraphNode } from '../types';

const graph = computed(() => buildSubfeatureGraph(samples));
const viewport = ref<HTMLElement | null>(null);
const isDragging = ref(false);
const panPointerId = ref<number | null>(null);
const dragState = ref({ x: 0, y: 0, left: 0, top: 0 });

function centerViewport() {
  const element = viewport.value;
  if (!element) {
    return;
  }

  element.scrollLeft = Math.max(0, graph.value.centerX - element.clientWidth / 2);
  element.scrollTop = Math.max(0, graph.value.centerY - element.clientHeight / 2);
}

function worldStyle() {
  return {
    width: `${graph.value.width}px`,
    height: `${graph.value.height}px`
  };
}

function centerStyle() {
  return {
    left: `${graph.value.centerX}px`,
    top: `${graph.value.centerY}px`
  };
}

function nodeStyle(node: SubfeatureGraphNode) {
  return {
    left: `${node.x}px`,
    top: `${node.y}px`,
    width: `${node.width}px`,
    height: `${node.height}px`,
    '--node-size': `${node.size}rem`,
    '--node-delay': `${node.ring * 120}ms`
  };
}

function beginPan(event: PointerEvent) {
  const target = event.target as HTMLElement | null;
  if (target?.closest('.tag-map-node, .tag-map-center, .tag-map-panel__button')) {
    return;
  }

  const element = viewport.value;
  if (!element) {
    return;
  }

  isDragging.value = true;
  panPointerId.value = event.pointerId;
  dragState.value = {
    x: event.clientX,
    y: event.clientY,
    left: element.scrollLeft,
    top: element.scrollTop
  };

  if (typeof element.setPointerCapture === 'function') {
    element.setPointerCapture(event.pointerId);
  }
}

function movePan(event: PointerEvent) {
  const element = viewport.value;
  if (!element || !isDragging.value || panPointerId.value !== event.pointerId) {
    return;
  }

  element.scrollLeft = dragState.value.left - (event.clientX - dragState.value.x);
  element.scrollTop = dragState.value.top - (event.clientY - dragState.value.y);
}

function endPan(event?: PointerEvent) {
  const element = viewport.value;
  if (element && event && typeof element.releasePointerCapture === 'function') {
    try {
      element.releasePointerCapture(event.pointerId);
    } catch {
      // Ignore browsers/tests that do not keep pointer capture state.
    }
  }

  isDragging.value = false;
  panPointerId.value = null;
}

onMounted(() => {
  nextTick(() => centerViewport());
});
</script>

<template>
  <AppShell>
    <section class="map-hero">
      <div class="map-hero__copy">
        <span class="hero__eyebrow">Feature Tag Map</span>
        <h1>Explore Oracle AI Database samples like a navigable feature map</h1>
        <p>
          Drag across the map to explore the strongest feature clusters. Each node is spaced on a larger canvas so
          the tags stay readable without stacking on top of each other.
        </p>
      </div>

      <div class="map-hero__stats">
        <div class="stat-card stat-card--map">
          <strong>{{ graph.totalSamples }}</strong>
          <span>Runnable samples</span>
        </div>
        <div class="stat-card stat-card--map">
          <strong>{{ graph.nodes.length }}</strong>
          <span>Mapped features</span>
        </div>
        <div class="stat-card stat-card--map">
          <strong>{{ graph.totalTags }}</strong>
          <span>Total catalog tags</span>
        </div>
      </div>
    </section>

    <section class="tag-map-panel">
      <div class="tag-map-panel__toolbar">
        <p>Drag the canvas to pan. Select any node to open the matching filtered catalog view.</p>
        <button type="button" class="button button--ghost tag-map-panel__button" @click="centerViewport">
          Recenter Map
        </button>
      </div>

      <div
        ref="viewport"
        class="tag-map-viewport"
        :class="{ 'is-dragging': isDragging }"
        @pointerdown="beginPan"
        @pointermove="movePan"
        @pointerup="endPan"
        @pointercancel="endPan"
        @pointerleave="endPan"
      >
        <div class="tag-map-world" :style="worldStyle()">
          <svg class="tag-map-stage__svg" :viewBox="`0 0 ${graph.width} ${graph.height}`" aria-hidden="true">
            <defs>
              <radialGradient id="tagMapGlow" cx="50%" cy="50%" r="50%">
                <stop offset="0%" stop-color="#59d4ff" stop-opacity="0.34" />
                <stop offset="100%" stop-color="#59d4ff" stop-opacity="0" />
              </radialGradient>
            </defs>

            <circle class="tag-map-stage__halo" :cx="graph.centerX" :cy="graph.centerY" r="220" />
            <circle
              v-for="radius in graph.orbitRadii"
              :key="`orbit-${radius}`"
              class="tag-map-stage__ring"
              :cx="graph.centerX"
              :cy="graph.centerY"
              :r="radius"
            />

            <line
              v-for="node in graph.nodes"
              :key="`${node.name}-line`"
              class="tag-map-stage__link"
              :x1="graph.centerX"
              :y1="graph.centerY"
              :x2="node.x"
              :y2="node.y"
            />
          </svg>

          <RouterLink class="tag-map-center" :style="centerStyle()" to="/">
            <span class="tag-map-center__eyebrow">{{ graph.centerSubtitle }}</span>
            <strong>{{ graph.centerLabel }}</strong>
            <span>{{ graph.totalSamples }} samples across the catalog</span>
          </RouterLink>

          <RouterLink
            v-for="node in graph.nodes"
            :key="node.name"
            class="tag-map-node"
            :style="nodeStyle(node)"
            :to="{ name: 'catalog', query: { tags: node.name } }"
          >
            <span v-if="node.iconPath" class="tag-map-node__icon" aria-hidden="true">
              <img :src="node.iconPath" alt="" loading="lazy" />
            </span>
            <span class="tag-map-node__meta">
              <span class="tag-map-node__count">{{ node.count }}</span>
              <span class="tag-map-node__label">{{ node.name }}</span>
            </span>
          </RouterLink>
        </div>
      </div>

      <div class="tag-map-panel__footer">
        <p>
          Click on a feature to view all related samples.
        </p>
        <RouterLink class="button button--ghost" to="/">Browse Full Catalog</RouterLink>
      </div>
    </section>

    <section class="orbit-list">
      <div class="orbit-list__header">
        <span class="catalog-results__eyebrow">Top Orbits</span>
        <h2>Browse features by sample count</h2>
      </div>

      <div class="orbit-list__grid">
        <RouterLink
          v-for="node in graph.nodes"
          :key="`${node.name}-chip`"
          class="orbit-list__item"
          :to="{ name: 'catalog', query: { tags: node.name } }"
        >
          <span v-if="node.iconPath" class="orbit-list__icon" aria-hidden="true">
            <img :src="node.iconPath" alt="" loading="lazy" />
          </span>
          <strong>{{ node.name }}</strong>
          <span>{{ node.count }} samples</span>
        </RouterLink>
      </div>

      <p v-if="graph.hiddenTags > 0" class="orbit-list__note">
        {{ graph.hiddenTags }} smaller tags are omitted from the map view to keep navigation readable, but they still
        appear in the catalog filters.
      </p>
    </section>
  </AppShell>
</template>
