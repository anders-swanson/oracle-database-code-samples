<script setup lang="ts">
defineProps<{
  compact?: boolean;
}>();
</script>

<template>
  <div class="app-shell">
    <div class="app-shell__glow app-shell__glow--one"></div>
    <div class="app-shell__glow app-shell__glow--two"></div>
    <div class="app-shell__grid"></div>
    <header class="site-header">
      <RouterLink class="site-header__brand" to="/">
        <span class="site-header__eyebrow">Oracle AI Database</span>
        <span class="site-header__title">Code Samples</span>
      </RouterLink>
      <nav class="site-header__nav">
        <a href="https://github.com/anders-swanson/oracle-database-code-samples" target="_blank" rel="noreferrer">
          GitHub
        </a>
      </nav>
    </header>
    <main class="site-main" :class="{ 'site-main--compact': compact }">
      <slot />
    </main>
  </div>
</template>
