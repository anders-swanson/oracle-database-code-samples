<script setup lang="ts">
import type { FeatureSummary } from '../types';

defineProps<{
  features: FeatureSummary[];
  activeFeature: string | null;
}>();

const emit = defineEmits<{
  select: [feature: string];
}>();
</script>

<template>
  <section class="feature-spotlights">
    <article
      v-for="feature in features"
      :key="feature.name"
      class="feature-card"
      :class="[`feature-card--${feature.theme}`, { 'feature-card--active': activeFeature === feature.name }]"
    >
      <button type="button" class="feature-card__button" @click="emit('select', feature.name)">
        <span class="feature-card__count">{{ feature.count }} samples</span>
        <h3>{{ feature.name }}</h3>
        <p>{{ feature.description }}</p>
      </button>
    </article>
  </section>
</template>
