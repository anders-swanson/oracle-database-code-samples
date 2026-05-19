<script setup lang="ts">
import { useRoute } from 'vue-router';

defineProps<{
  compact?: boolean;
}>();

const navigationLinks = [
  {
    label: 'Catalog',
    routeName: 'catalog',
    variant: 'catalog',
    activeRoutes: ['catalog', 'sample-detail']
  },
  {
    label: 'Patterns',
    routeName: 'patterns',
    variant: 'patterns',
    activeRoutes: ['patterns']
  },
  {
    label: 'Topic Map',
    routeName: 'feature-map',
    variant: 'map',
    activeRoutes: ['feature-map', 'feature-detail']
  }
] as const;

const route = useRoute();

function isNavigationLinkActive(activeRoutes: readonly string[]) {
  return typeof route.name === 'string' && activeRoutes.includes(route.name);
}
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
          v-for="link in navigationLinks"
          :key="link.routeName"
          class="site-header__nav-link"
          :class="[
            `site-header__nav-link--${link.variant}`,
            { 'is-active': isNavigationLinkActive(link.activeRoutes) }
          ]"
          :to="{ name: link.routeName }"
        >
          <span class="site-header__nav-icon" aria-hidden="true"></span>
          <span>{{ link.label }}</span>
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

<style scoped>
.app-shell {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
}

.app-shell__glow {
  position: fixed;
  inset: auto;
  width: 42rem;
  height: 42rem;
  border-radius: 50%;
  filter: blur(30px);
  opacity: 0.35;
  pointer-events: none;
}

.app-shell__glow--one {
  top: -12rem;
  left: -8rem;
  background: rgba(89, 212, 255, 0.18);
}

.app-shell__glow--two {
  top: 10rem;
  right: -10rem;
  background: rgba(255, 176, 102, 0.15);
}

.app-shell__grid {
  position: fixed;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.55), transparent 92%);
  pointer-events: none;
}

.site-header,
.site-main {
  position: relative;
  z-index: 1;
}

.site-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding: 1.35rem clamp(1.2rem, 3vw, 2.5rem);
}

.site-header__brand {
  display: inline-flex;
  flex-direction: column;
  gap: 0.25rem;
  transition: transform var(--transition-fast), opacity var(--transition-fast);
}

.site-header__brand:hover {
  transform: translateY(-2px);
  opacity: 0.92;
}

.site-header__eyebrow {
  color: var(--accent);
  font-size: 0.74rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.site-header__title {
  font-size: 1.2rem;
  font-weight: 700;
}

.site-header__nav {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.75rem;
}

.site-header__nav a {
  padding: 0.8rem 1rem;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.03);
  transition: background var(--transition-fast), border-color var(--transition-fast), transform var(--transition-fast), box-shadow var(--transition-fast);
}

.site-header__nav a:hover {
  transform: translateY(-1px);
  border-color: var(--line-strong);
  background: rgba(255, 255, 255, 0.08);
}

.site-header__nav a:focus-visible {
  outline: none;
  border-color: rgba(89, 212, 255, 0.52);
  box-shadow: var(--focus-ring);
}

.site-header__nav-link {
  --nav-accent: var(--accent);
  --nav-accent-soft: rgba(89, 212, 255, 0.16);
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 0.58rem;
  min-height: 2.95rem;
  overflow: hidden;
  border-color: color-mix(in srgb, var(--nav-accent) 44%, rgba(160, 197, 255, 0.18));
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.1), transparent 36%),
    linear-gradient(180deg, var(--nav-accent-soft), rgba(255, 255, 255, 0.035));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    0 10px 26px rgba(1, 10, 22, 0.2);
  color: #f4f9ff;
  font-weight: 800;
  white-space: nowrap;
}

.site-header__nav-link::after {
  content: "";
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: radial-gradient(circle at top left, color-mix(in srgb, var(--nav-accent) 22%, transparent), transparent 56%);
  opacity: 0.8;
  pointer-events: none;
}

.site-header__nav-link:hover {
  border-color: color-mix(in srgb, var(--nav-accent) 66%, rgba(244, 247, 255, 0.2));
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.13), transparent 36%),
    linear-gradient(180deg, color-mix(in srgb, var(--nav-accent) 24%, rgba(255, 255, 255, 0.05)), rgba(255, 255, 255, 0.05));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.1),
    0 16px 34px color-mix(in srgb, var(--nav-accent) 18%, rgba(1, 10, 22, 0.28));
}

.site-header__nav-link span {
  position: relative;
  z-index: 1;
}

.site-header__nav-link--catalog {
  --nav-accent: #59d4ff;
  --nav-accent-soft: rgba(89, 212, 255, 0.18);
}

.site-header__nav-link--patterns {
  --nav-accent: #5de0a7;
  --nav-accent-soft: rgba(93, 224, 167, 0.17);
}

.site-header__nav-link--map {
  --nav-accent: #ffb066;
  --nav-accent-soft: rgba(255, 176, 102, 0.17);
}

.site-header__nav-link.is-active {
  border-color: color-mix(in srgb, var(--nav-accent, #59d4ff) 74%, rgba(244, 247, 255, 0.22));
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.16), transparent 36%),
    linear-gradient(180deg, color-mix(in srgb, var(--nav-accent, #59d4ff) 28%, rgba(255, 255, 255, 0.06)), rgba(255, 255, 255, 0.045));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.12),
    0 0 0 1px color-mix(in srgb, var(--nav-accent, #59d4ff) 20%, transparent),
    0 18px 38px color-mix(in srgb, var(--nav-accent, #59d4ff) 17%, rgba(1, 10, 22, 0.3));
  color: #ffffff;
}

.site-header__nav-icon {
  position: relative;
  display: inline-block;
  width: 1.05rem;
  height: 1.05rem;
  flex: 0 0 auto;
  color: var(--nav-accent);
  filter: drop-shadow(0 0 10px color-mix(in srgb, var(--nav-accent) 52%, transparent));
}

.site-header__nav-link--catalog .site-header__nav-icon {
  background:
    linear-gradient(currentColor 0 0) 0 0 / 0.42rem 0.42rem no-repeat,
    linear-gradient(currentColor 0 0) 100% 0 / 0.42rem 0.42rem no-repeat,
    linear-gradient(currentColor 0 0) 0 100% / 0.42rem 0.42rem no-repeat,
    linear-gradient(currentColor 0 0) 100% 100% / 0.42rem 0.42rem no-repeat;
}

.site-header__nav-link--patterns .site-header__nav-icon {
  border-top: 0.18rem solid currentColor;
  border-bottom: 0.18rem solid currentColor;
}

.site-header__nav-link--patterns .site-header__nav-icon::before {
  content: "";
  display: block;
  width: 100%;
  height: 0.18rem;
  margin-top: 0.34rem;
  background: currentColor;
}

.site-header__nav-link--map .site-header__nav-icon {
  border: 0.16rem solid currentColor;
  border-radius: 50%;
}

.site-header__nav-link--map .site-header__nav-icon::before {
  content: "";
  position: absolute;
  inset: 0.24rem;
  border: 0.12rem solid currentColor;
  border-radius: 50%;
}

.site-header__github {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
  overflow: hidden;
  border-color: rgba(255, 176, 102, 0.62);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.16), transparent 30%),
    linear-gradient(180deg, rgba(255, 176, 102, 0.28), rgba(149, 79, 28, 0.74));
  box-shadow:
    0 0 0 1px rgba(255, 176, 102, 0.12),
    0 14px 34px rgba(255, 176, 102, 0.16);
  color: #fff3dc;
  font-weight: 800;
  white-space: nowrap;
}

.site-header__github::before {
  content: "";
  position: absolute;
  inset: -80% auto -80% -42%;
  width: 2.5rem;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.42), transparent);
  transform: rotate(18deg);
  transition: left 320ms ease;
}

.site-header__github:hover {
  border-color: rgba(255, 213, 135, 0.78);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.2), transparent 30%),
    linear-gradient(180deg, rgba(255, 190, 112, 0.36), rgba(154, 82, 28, 0.82));
  box-shadow:
    0 0 0 1px rgba(255, 213, 135, 0.2),
    0 18px 42px rgba(255, 176, 102, 0.24);
}

.site-header__github:hover::before {
  left: 118%;
}

.site-header__github-icon {
  position: relative;
  width: 1.45rem;
  height: 1.45rem;
  flex: 0 0 auto;
  filter: drop-shadow(0 0 12px rgba(255, 176, 102, 0.46));
}

.site-header__github-star-glow {
  fill: rgba(255, 210, 111, 0.2);
  stroke: rgba(255, 247, 213, 0.34);
  stroke-linejoin: round;
  stroke-width: 1.2;
}

.site-header__github-star {
  fill: #ffd26f;
  stroke: #fff7d5;
  stroke-linejoin: round;
  stroke-width: 1.25;
}

.site-header__github-spark {
  fill: none;
  stroke: #fff7d5;
  stroke-linecap: round;
  stroke-width: 1.45;
}

.site-main {
  width: min(1380px, calc(100% - 2rem));
  margin: 0 auto;
  padding: 0 0 4rem;
}

.site-main--compact {
  width: min(1220px, calc(100% - 2rem));
}

@media (max-width: 720px) {
  .site-main {
    width: min(100% - 1rem, 100%);
  }

  .site-header {
    align-items: flex-start;
  }

  .site-header__nav {
    justify-content: flex-start;
  }
}
</style>
