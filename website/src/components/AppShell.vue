<script setup lang="ts">
import { useRoute } from 'vue-router';

defineProps<{
  compact?: boolean;
}>();

const route = useRoute();
</script>

<template>
  <div class="app-shell">
    <div class="app-shell__glow app-shell__glow--one"></div>
    <div class="app-shell__glow app-shell__glow--two"></div>
    <div class="app-shell__grid"></div>
    <header class="site-header">
      <RouterLink class="site-header__brand" :to="{ name: 'catalog' }">
        <span class="site-header__eyebrow">Oracle AI Database</span>
        <span class="site-header__title">Code Samples</span>
      </RouterLink>
      <nav class="site-header__nav">
        <RouterLink
          class="site-header__nav-link site-header__nav-link--catalog"
          :class="{ 'is-active': route.name === 'catalog' || route.name === 'sample-detail' }"
          :to="{ name: 'catalog' }"
        >
          <span class="site-header__nav-icon" aria-hidden="true"></span>
          <span>Catalog</span>
        </RouterLink>
        <RouterLink
          class="site-header__nav-link site-header__nav-link--patterns"
          :class="{ 'is-active': route.name === 'patterns' }"
          :to="{ name: 'patterns' }"
        >
          <span class="site-header__nav-icon" aria-hidden="true"></span>
          <span>Patterns</span>
        </RouterLink>
        <RouterLink
          class="site-header__nav-link site-header__nav-link--map"
          :class="{ 'is-active': route.name === 'feature-map' || route.name === 'feature-detail' }"
          :to="{ name: 'feature-map' }"
        >
          <span class="site-header__nav-icon" aria-hidden="true"></span>
          <span>Topic Map</span>
        </RouterLink>
        <a
          class="site-header__github"
          href="https://github.com/anders-swanson/oracle-database-code-samples"
          target="_blank"
          rel="noreferrer"
          aria-label="Star this project on GitHub"
        >
          <svg class="site-header__github-icon" viewBox="0 0 32 32" aria-hidden="true">
            <path
              class="site-header__github-star-glow"
              d="m16 3.1 3.9 7.9 8.7 1.26-6.3 6.14 1.49 8.66L16 22.96l-7.79 4.1 1.49-8.66-6.3-6.14L12.1 11 16 3.1Z"
            />
            <path
              class="site-header__github-star"
              d="m16 5.36 3.08 6.24 6.89 1-4.98 4.85 1.17 6.86L16 21.08l-6.16 3.23 1.17-6.86-4.98-4.85 6.89-1L16 5.36Z"
            />
            <path
              class="site-header__github-spark"
              d="M24.9 4.25v3.1m1.55-1.55h-3.1M8.1 4.95v2.2M9.2 6.05H7"
            />
          </svg>
          <span>Star on GitHub</span>
        </a>
      </nav>
    </header>
    <main class="site-main" :class="{ 'site-main--compact': compact }">
      <slot />
    </main>
  </div>
</template>
