<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import AppShell from '../components/AppShell.vue';
import { buildSubfeatureGraph, findFeaturePageByName, samples } from '../lib/catalog';
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
  const featurePage = findFeaturePageByName(node.name);

  return featurePage
    ? {
        name: 'feature-detail',
        params: { slug: featurePage.slug }
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
        <p>Drag the canvas to pan. Select any topic to open the matching filtered catalog view.</p>
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
          Click on a topic to view all related samples.
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
