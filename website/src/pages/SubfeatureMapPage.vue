<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import AppShell from '../components/AppShell.vue';
import { buildSubfeatureGraph, findPatternMappingByTopic, samples } from '../lib/catalog';
import type { SubfeatureGraphNode } from '../types';

const graph = computed(() => buildSubfeatureGraph(samples));
const mapWindow = ref<HTMLElement | null>(null);
const viewport = ref<HTMLElement | null>(null);
const isDragging = ref(false);
const isFullscreen = ref(false);
const isFullscreenAvailable = ref(false);
const zoom = ref(1);
const panPointerId = ref<number | null>(null);
const dragState = ref({ x: 0, y: 0, left: 0, top: 0 });
const activeTooltip = ref<{
  node: SubfeatureGraphNode;
  left: number;
  top: number;
  placement: 'above' | 'below';
} | null>(null);

const tooltipWidth = 288;
const tooltipHeight = 150;
const tooltipGap = 14;
const tooltipMargin = 16;
const minZoom = 0.65;
const maxZoom = 1.6;
const zoomStep = 0.15;

const canZoomIn = computed(() => zoom.value < maxZoom);
const canZoomOut = computed(() => zoom.value > minZoom);
const catalogResultsTarget = '#catalog-results';

function filteredCatalogTarget(node: SubfeatureGraphNode) {
  return {
    name: 'catalog',
    query: { tags: node.name },
    hash: catalogResultsTarget
  };
}

function topicTarget(node: SubfeatureGraphNode) {
  const pattern = findPatternMappingByTopic(node.name);

  return pattern
    ? {
        name: 'pattern-detail',
        params: { slug: pattern.id }
      }
    : filteredCatalogTarget(node);
}

function centerViewport() {
  const element = viewport.value;
  if (!element) {
    return;
  }

  element.scrollLeft = Math.max(0, graph.value.centerX * zoom.value - element.clientWidth / 2);
  element.scrollTop = Math.max(0, graph.value.centerY * zoom.value - element.clientHeight / 2);
}

function clampZoom(value: number) {
  return Math.min(maxZoom, Math.max(minZoom, Number(value.toFixed(2))));
}

function setZoom(nextZoom: number) {
  const element = viewport.value;
  const previousZoom = zoom.value;
  const clampedZoom = clampZoom(nextZoom);

  if (clampedZoom === previousZoom) {
    return;
  }

  const viewportCenterX = element ? (element.scrollLeft + element.clientWidth / 2) / previousZoom : graph.value.centerX;
  const viewportCenterY = element ? (element.scrollTop + element.clientHeight / 2) / previousZoom : graph.value.centerY;

  zoom.value = clampedZoom;
  hideNodeTooltip();

  if (!element) {
    return;
  }

  nextTick(() => {
    element.scrollLeft = Math.max(0, viewportCenterX * clampedZoom - element.clientWidth / 2);
    element.scrollTop = Math.max(0, viewportCenterY * clampedZoom - element.clientHeight / 2);
  });
}

function setZoomAtPoint(nextZoom: number, clientX: number, clientY: number) {
  const element = viewport.value;
  if (!element) {
    return;
  }

  const previousZoom = zoom.value;
  const clampedZoom = clampZoom(nextZoom);
  if (clampedZoom === previousZoom) {
    return;
  }

  const viewportRect = element.getBoundingClientRect();
  const viewportX = clientX - viewportRect.left;
  const viewportY = clientY - viewportRect.top;
  const mapX = (element.scrollLeft + viewportX) / previousZoom;
  const mapY = (element.scrollTop + viewportY) / previousZoom;

  zoom.value = clampedZoom;
  hideNodeTooltip();

  nextTick(() => {
    element.scrollLeft = Math.max(0, mapX * clampedZoom - viewportX);
    element.scrollTop = Math.max(0, mapY * clampedZoom - viewportY);
  });
}

function zoomIn() {
  setZoom(zoom.value + zoomStep);
}

function zoomOut() {
  setZoom(zoom.value - zoomStep);
}

function updateFullscreenState() {
  if (typeof document === 'undefined') {
    return;
  }

  isFullscreen.value = document.fullscreenElement === mapWindow.value;
}

async function toggleFullscreen() {
  const element = mapWindow.value;
  if (!element || !isFullscreenAvailable.value) {
    return;
  }

  try {
    if (document.fullscreenElement === element) {
      await document.exitFullscreen();
    } else {
      await element.requestFullscreen();
    }
  } catch {
    // Fullscreen can be rejected by browser policy; keep the map usable.
  }
}

function worldStyle() {
  return {
    width: `${graph.value.width * zoom.value}px`,
    height: `${graph.value.height * zoom.value}px`
  };
}

function surfaceStyle() {
  return {
    width: `${graph.value.width}px`,
    height: `${graph.value.height}px`,
    transform: `scale(${zoom.value})`
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

function showNodeTooltip(event: PointerEvent | MouseEvent | FocusEvent, node: SubfeatureGraphNode) {
  if (!node.description || !node.useWhen) {
    activeTooltip.value = null;
    return;
  }

  const element = viewport.value;
  const target = event.currentTarget as HTMLElement | null;
  if (!element || !target) {
    return;
  }

  const viewportRect = element.getBoundingClientRect();
  const nodeRect = target.getBoundingClientRect();
  const nodeCenterX = nodeRect.left + nodeRect.width / 2 - viewportRect.left;
  const aboveTop = nodeRect.top - viewportRect.top - tooltipHeight - tooltipGap;
  const belowTop = nodeRect.bottom - viewportRect.top + tooltipGap;
  const placement = aboveTop >= tooltipMargin ? 'above' : 'below';
  const rawTop = placement === 'above' ? aboveTop : belowTop;
  const minLeft = tooltipWidth / 2 + tooltipMargin;
  const maxLeft = Math.max(minLeft, element.clientWidth - tooltipWidth / 2 - tooltipMargin);
  const maxTop = Math.max(tooltipMargin, element.clientHeight - tooltipHeight - tooltipMargin);

  activeTooltip.value = {
    node,
    left: element.scrollLeft + Math.min(Math.max(nodeCenterX, minLeft), maxLeft),
    top: element.scrollTop + Math.min(Math.max(rawTop, tooltipMargin), maxTop),
    placement
  };
}

function hideNodeTooltip() {
  activeTooltip.value = null;
}

function tooltipStyle() {
  if (!activeTooltip.value) {
    return {};
  }

  return {
    left: `${activeTooltip.value.left}px`,
    top: `${activeTooltip.value.top}px`
  };
}

function isMapInteractionTarget(target: EventTarget | null) {
  return target instanceof HTMLElement && target.closest('.tag-map-node, .tag-map-center, .tag-map-control');
}

function handleWheelZoom(event: WheelEvent) {
  if (isMapInteractionTarget(event.target) || event.deltaY === 0) {
    return;
  }

  setZoomAtPoint(zoom.value + (event.deltaY < 0 ? zoomStep : -zoomStep), event.clientX, event.clientY);
}

function handleDoubleClickZoom(event: MouseEvent) {
  if (isMapInteractionTarget(event.target)) {
    return;
  }

  setZoomAtPoint(zoom.value + zoomStep, event.clientX, event.clientY);
}

function beginPan(event: PointerEvent) {
  if (isMapInteractionTarget(event.target)) {
    return;
  }

  const element = viewport.value;
  if (!element) {
    return;
  }

  isDragging.value = true;
  hideNodeTooltip();
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
  isFullscreenAvailable.value = typeof mapWindow.value?.requestFullscreen === 'function';

  if (typeof document !== 'undefined') {
    document.addEventListener('fullscreenchange', updateFullscreenState);
    updateFullscreenState();
  }

  nextTick(() => centerViewport());
});

onBeforeUnmount(() => {
  if (typeof document !== 'undefined') {
    document.removeEventListener('fullscreenchange', updateFullscreenState);
  }
});
</script>

<template>
  <AppShell>
    <section class="map-hero">
      <div class="map-hero__copy">
        <span class="hero__eyebrow">Sample Topic Map</span>
        <h1>Explore Oracle AI Database samples like a navigable topic map</h1>
        <p>
          Drag across the map to explore the strongest topic clusters. Each node is spaced on a larger canvas so
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
          <span>Mapped topics</span>
        </div>
        <div class="stat-card stat-card--map">
          <strong>{{ graph.totalTags }}</strong>
          <span>Total catalog tags</span>
        </div>
      </div>
    </section>

    <section class="tag-map-panel">
      <div class="tag-map-panel__toolbar">
        <p>Drag the canvas to pan. Select any topic to open the matching engineering pattern.</p>
      </div>

      <div ref="mapWindow" class="tag-map-window" :class="{ 'is-fullscreen': isFullscreen }">
        <div class="tag-map-controls" aria-label="Map controls">
          <button
            v-if="isFullscreenAvailable"
            type="button"
            class="tag-map-control"
            :aria-label="isFullscreen ? 'Exit fullscreen map' : 'View map fullscreen'"
            :title="isFullscreen ? 'Exit fullscreen map' : 'View map fullscreen'"
            @click="toggleFullscreen"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M8 3H3v5" />
              <path d="M16 3h5v5" />
              <path d="M21 16v5h-5" />
              <path d="M8 21H3v-5" />
              <path d="M3 3l6 6" />
              <path d="M21 3l-6 6" />
              <path d="M21 21l-6-6" />
              <path d="M3 21l6-6" />
            </svg>
          </button>
          <button
            type="button"
            class="tag-map-control"
            aria-label="Zoom in"
            title="Zoom in"
            :disabled="!canZoomIn"
            @click="zoomIn"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 5v14" />
              <path d="M5 12h14" />
            </svg>
          </button>
          <button
            type="button"
            class="tag-map-control"
            aria-label="Zoom out"
            title="Zoom out"
            :disabled="!canZoomOut"
            @click="zoomOut"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M5 12h14" />
            </svg>
          </button>
          <button
            type="button"
            class="tag-map-control"
            aria-label="Recenter map"
            title="Recenter map"
            @click="centerViewport"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 2v4" />
              <path d="M12 18v4" />
              <path d="M2 12h4" />
              <path d="M18 12h4" />
              <circle cx="12" cy="12" r="5" />
              <circle cx="12" cy="12" r="1.5" />
            </svg>
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
          @wheel.prevent="handleWheelZoom"
          @dblclick.prevent="handleDoubleClickZoom"
          @scroll="hideNodeTooltip"
        >
          <div class="tag-map-world" :style="worldStyle()">
            <div class="tag-map-surface" :style="surfaceStyle()">
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
                :to="topicTarget(node)"
                @pointerover="showNodeTooltip($event, node)"
                @pointerenter="showNodeTooltip($event, node)"
                @mouseover="showNodeTooltip($event, node)"
                @mouseenter="showNodeTooltip($event, node)"
                @mousemove="showNodeTooltip($event, node)"
                @focus="showNodeTooltip($event, node)"
                @pointerleave="hideNodeTooltip"
                @mouseleave="hideNodeTooltip"
                @blur="hideNodeTooltip"
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
          <div
            v-if="activeTooltip"
            class="tag-map-tooltip"
            :class="`tag-map-tooltip--${activeTooltip.placement}`"
            :style="tooltipStyle()"
            role="tooltip"
          >
            <strong>{{ activeTooltip.node.name }}</strong>
            <span>{{ activeTooltip.node.description }}</span>
            <em>{{ activeTooltip.node.useWhen }}</em>
          </div>
        </div>
      </div>

      <div class="tag-map-panel__footer">
        <p>
          Click on a topic to view the related engineering pattern.
        </p>
        <RouterLink class="button button--ghost" to="/">Browse Full Catalog</RouterLink>
      </div>
    </section>

    <section class="orbit-list">
      <div class="orbit-list__header">
        <span class="catalog-results__eyebrow">Top Orbits</span>
        <h2>Browse topics by sample count</h2>
      </div>

      <div class="orbit-list__grid">
        <RouterLink
          v-for="node in graph.nodes"
          :key="`${node.name}-chip`"
          class="orbit-list__item"
          :to="topicTarget(node)"
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

<style scoped>
.map-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(280px, 0.75fr);
  gap: 1.5rem;
  align-items: stretch;
  padding: 2rem 0 1rem;
}

.map-hero__copy {
  padding: clamp(1.5rem, 3vw, 2.75rem);
}

.map-hero__copy h1 {
  font-size: clamp(2.3rem, 5vw, 4.2rem);
  line-height: 0.96;
}

.map-hero__stats {
  display: grid;
  gap: 1rem;
}

.tag-map-panel {
  padding: 1.25rem;
}

.tag-map-panel__toolbar,
.tag-map-panel__footer,
.orbit-list__header {
  align-items: center;
}

.tag-map-panel__toolbar {
  padding-bottom: 1rem;
}

.tag-map-panel__toolbar p {
  margin: 0;
  color: var(--text-muted);
}

.tag-map-window {
  position: relative;
  border-radius: calc(var(--radius-lg) - 0.3rem);
  background:
    radial-gradient(circle at center, rgba(89, 212, 255, 0.16), transparent 26%),
    radial-gradient(circle at 20% 20%, rgba(93, 224, 167, 0.12), transparent 20%),
    linear-gradient(180deg, rgba(8, 20, 33, 0.88), rgba(5, 11, 20, 0.98));
}

.tag-map-window:fullscreen,
.tag-map-window.is-fullscreen {
  width: 100vw;
  height: 100vh;
  padding: 1rem;
  border-radius: 0;
  background:
    radial-gradient(circle at center, rgba(89, 212, 255, 0.18), transparent 28%),
    linear-gradient(180deg, rgba(8, 20, 33, 0.96), rgba(5, 11, 20, 1));
}

.tag-map-window:fullscreen .tag-map-viewport,
.tag-map-window.is-fullscreen .tag-map-viewport {
  height: 100%;
}

.tag-map-controls {
  position: absolute;
  top: 1rem;
  right: 1rem;
  z-index: 30;
  display: grid;
  gap: 0.45rem;
  padding: 0.35rem;
  border: 1px solid rgba(160, 197, 255, 0.24);
  border-radius: 0.95rem;
  background: rgba(3, 10, 18, 0.82);
  box-shadow:
    0 18px 42px rgba(1, 8, 18, 0.36),
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(14px);
}

.tag-map-control {
  display: grid;
  place-items: center;
  width: 2.45rem;
  height: 2.45rem;
  padding: 0;
  border: 1px solid rgba(160, 197, 255, 0.18);
  border-radius: 0.72rem;
  background: rgba(8, 18, 30, 0.9);
  color: #f7fbff;
  cursor: pointer;
  transition: background var(--transition-fast), border-color var(--transition-fast), color var(--transition-fast), transform var(--transition-fast);
}

.tag-map-control:hover:not(:disabled),
.tag-map-control:focus-visible {
  transform: translateY(-1px);
  border-color: rgba(89, 212, 255, 0.48);
  background: rgba(16, 44, 68, 0.96);
  color: #ffffff;
}

.tag-map-control:focus-visible {
  outline: 2px solid rgba(89, 212, 255, 0.55);
  outline-offset: 2px;
}

.tag-map-control:disabled {
  color: rgba(247, 251, 255, 0.34);
  cursor: not-allowed;
  opacity: 0.58;
}

.tag-map-control svg {
  width: 1.25rem;
  height: 1.25rem;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.9;
}

.tag-map-viewport {
  position: relative;
  height: 48rem;
  overflow: auto;
  border-radius: calc(var(--radius-lg) - 0.3rem);
  cursor: grab;
  scrollbar-color: rgba(89, 212, 255, 0.3) rgba(255, 255, 255, 0.04);
  scrollbar-width: thin;
}

.tag-map-viewport.is-dragging {
  cursor: grabbing;
}

.tag-map-world,
.tag-map-surface {
  position: relative;
}

.tag-map-surface {
  transform-origin: 0 0;
}

.tag-map-world::before {
  content: "";
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(circle at center, black 32%, transparent 92%);
  pointer-events: none;
}

.tag-map-stage__svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.tag-map-stage__halo {
  fill: url(#tagMapGlow);
}

.tag-map-stage__ring {
  fill: none;
  stroke: rgba(160, 197, 255, 0.12);
  stroke-width: 1.3;
}

.tag-map-stage__link {
  stroke: rgba(89, 212, 255, 0.26);
  stroke-width: 1.5;
}

.tag-map-center,
.tag-map-node {
  position: absolute;
  display: grid;
  place-items: center;
  text-align: center;
  transform: translate(-50%, -50%);
  transition: transform var(--transition-card), box-shadow var(--transition-card), border-color var(--transition-card), background var(--transition-card);
}

.tag-map-center {
  width: 23rem;
  aspect-ratio: 1;
  gap: 0.7rem;
  padding: 2rem;
  border: 1px solid rgba(89, 212, 255, 0.38);
  border-radius: 50%;
  background:
    radial-gradient(circle at top, rgba(255, 255, 255, 0.14), transparent 48%),
    linear-gradient(180deg, rgba(13, 46, 71, 0.9), rgba(6, 19, 34, 0.98));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.07),
    0 32px 84px rgba(1, 10, 22, 0.42);
}

.tag-map-center strong {
  font-size: clamp(1.5rem, 2vw, 2.25rem);
  letter-spacing: -0.04em;
}

.tag-map-center span:last-child {
  color: var(--text-muted);
}

.tag-map-center__eyebrow {
  color: var(--accent-warm);
  font-size: 0.76rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.tag-map-node {
  z-index: 2;
  width: var(--node-size);
  grid-template-rows: minmax(4.8rem, 1fr) auto;
  align-content: center;
  gap: 0.5rem;
  padding: 0.8rem 0.8rem 0.85rem;
  border: 1px solid rgba(160, 197, 255, 0.24);
  border-radius: 1.6rem;
  background: rgba(8, 18, 30, 0.9);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.06),
    0 16px 44px rgba(1, 9, 20, 0.28);
  animation: tag-node-drift 560ms ease forwards;
  animation-delay: var(--node-delay);
  opacity: 0;
}

.tag-map-node__icon {
  z-index: 1;
  display: grid;
  place-items: center;
  justify-self: center;
  width: min(100%, 6.6rem);
  aspect-ratio: 1;
}

.tag-map-node__icon img {
  display: block;
  width: min(100%, 5.7rem);
  height: min(100%, 5.7rem);
  object-fit: contain;
  filter: brightness(0) invert(1) drop-shadow(0 0 16px rgba(89, 212, 255, 0.24));
  opacity: 0.92;
}

.tag-map-node:hover,
.tag-map-center:hover {
  transform: translate(-50%, calc(-50% - 0.2rem));
  border-color: rgba(89, 212, 255, 0.4);
  box-shadow: 0 24px 52px rgba(1, 10, 22, 0.36);
}

.tag-map-node:hover,
.tag-map-node:focus-visible {
  z-index: 8;
}

.tag-map-node__meta {
  z-index: 3;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 0.42rem;
  width: 100%;
  min-height: 2.75rem;
  padding: 0.36rem 0.48rem;
  border: 1px solid rgba(89, 212, 255, 0.3);
  border-radius: 0.95rem;
  background: rgba(3, 10, 18, 0.76);
  box-shadow:
    0 0 22px rgba(89, 212, 255, 0.18),
    inset 0 1px 0 rgba(255, 255, 255, 0.07);
  backdrop-filter: blur(10px);
}

.tag-map-node__count {
  display: inline-grid;
  place-items: center;
  min-width: 1.9rem;
  height: 1.9rem;
  padding: 0 0.42rem;
  border-radius: 999px;
  background: rgba(89, 212, 255, 0.18);
  color: #f7fbff;
  box-shadow: 0 0 18px rgba(89, 212, 255, 0.18);
  font-size: 0.78rem;
  font-weight: 800;
  line-height: 1;
}

.tag-map-node__label {
  color: #f7fbff;
  font-size: 0.9rem;
  font-weight: 800;
  line-height: 1.12;
  text-shadow:
    0 1px 10px rgba(1, 6, 14, 0.82),
    0 0 16px rgba(89, 212, 255, 0.18);
  text-wrap: balance;
}

.tag-map-tooltip {
  position: absolute;
  z-index: 20;
  display: grid;
  gap: 0.38rem;
  width: min(18rem, calc(100% - 2rem));
  min-height: 8.5rem;
  padding: 0.85rem 0.95rem;
  border: 1px solid rgba(89, 212, 255, 0.36);
  border-radius: 1rem;
  background: rgba(3, 10, 18, 0.94);
  box-shadow:
    0 20px 48px rgba(1, 8, 18, 0.44),
    0 0 26px rgba(89, 212, 255, 0.2),
    inset 0 1px 0 rgba(255, 255, 255, 0.07);
  color: var(--text);
  pointer-events: none;
  text-align: left;
  transform: translateX(-50%);
}

.tag-map-tooltip::after {
  content: "";
  position: absolute;
  left: 50%;
  width: 0.72rem;
  height: 0.72rem;
  background: rgba(3, 10, 18, 0.94);
  transform: translateX(-50%) rotate(45deg);
}

.tag-map-tooltip--above::after {
  bottom: -0.42rem;
  border-right: 1px solid rgba(89, 212, 255, 0.3);
  border-bottom: 1px solid rgba(89, 212, 255, 0.3);
}

.tag-map-tooltip--below::after {
  top: -0.42rem;
  border-top: 1px solid rgba(89, 212, 255, 0.3);
  border-left: 1px solid rgba(89, 212, 255, 0.3);
}

.tag-map-tooltip strong {
  font-size: 0.86rem;
  line-height: 1.2;
}

.tag-map-tooltip span,
.tag-map-tooltip em {
  color: var(--text-muted);
  font-size: 0.78rem;
  line-height: 1.42;
}

.tag-map-tooltip em {
  color: #dff9ff;
  font-style: normal;
}

.tag-map-panel__footer {
  padding: 1rem 0 0;
}

.tag-map-panel__footer p {
  max-width: 48rem;
  margin: 0;
}

.orbit-list {
  margin-top: 1.5rem;
  padding: 1.5rem;
}

.orbit-list__header h2 {
  margin: 0.35rem 0 0;
}

.orbit-list__grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1rem;
  margin-top: 1.25rem;
}

.orbit-list__item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 0.35rem;
  padding: 1rem 1.1rem;
  border: 1px solid rgba(160, 197, 255, 0.18);
  border-radius: 1.2rem;
  background: rgba(255, 255, 255, 0.04);
  transition: transform var(--transition-card), border-color var(--transition-card), background var(--transition-card);
}

.orbit-list__item strong,
.orbit-list__item span:not(.orbit-list__icon) {
  grid-column: 2;
}

.orbit-list__item:hover {
  transform: translateY(-0.2rem);
  border-color: rgba(89, 212, 255, 0.4);
  background: rgba(255, 255, 255, 0.08);
}

.orbit-list__item span {
  color: var(--text-muted);
}

.orbit-list__icon {
  display: inline-grid;
  grid-row: 1 / span 2;
  place-items: center;
  width: 3.2rem;
  height: 3.2rem;
  border-radius: 1rem;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.45);
}

.orbit-list__icon img {
  width: 2.45rem;
  height: 2.45rem;
  object-fit: contain;
}

.orbit-list__note {
  margin: 1rem 0 0;
}

@keyframes tag-node-drift {
  from {
    opacity: 0;
    transform: translate(-50%, calc(-50% + 1rem)) scale(0.94);
  }

  to {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1);
  }
}

@media (max-width: 1080px) {
  .map-hero,
  .orbit-list__grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .map-hero__stats {
    grid-template-columns: 1fr;
  }

  .tag-map-viewport {
    height: 40rem;
  }

  .tag-map-window:fullscreen,
  .tag-map-window.is-fullscreen {
    padding: 0.55rem;
  }

  .tag-map-controls {
    top: 0.75rem;
    right: 0.75rem;
  }

  .tag-map-control {
    width: 2.25rem;
    height: 2.25rem;
  }

  .tag-map-center {
    width: 18rem;
  }
}
</style>
