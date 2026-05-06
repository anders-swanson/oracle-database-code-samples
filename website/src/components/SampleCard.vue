<script setup lang="ts">
import type { SampleSummary } from '../types';

defineProps<{
  sample: SampleSummary;
}>();
</script>

<template>
  <article class="sample-card">
    <div class="sample-card__header">
      <div class="sample-card__meta">
        <span class="sample-card__language">{{ sample.language }}</span>
      </div>
      <RouterLink class="sample-card__title" :to="{ name: 'sample-detail', params: { id: sample.id } }">
        {{ sample.title }}
      </RouterLink>
      <p class="sample-card__description">{{ sample.description }}</p>
    </div>
    <div class="sample-card__tags">
      <span v-for="tag in sample.tags.slice(0, 5)" :key="tag" class="sample-card__tag">
        #{{ tag }}
      </span>
    </div>

    <div class="sample-card__footer">
      <code>{{ sample.path }}</code>
      <div class="sample-card__actions">
        <RouterLink class="button button--ghost" :to="{ name: 'sample-detail', params: { id: sample.id } }">
          Read More
        </RouterLink>
        <a class="button button--primary" :href="sample.githubCodeUrl" target="_blank" rel="noreferrer">
          View Code
        </a>
      </div>
    </div>
  </article>
</template>
