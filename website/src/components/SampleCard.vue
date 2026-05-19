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

<style scoped>
.sample-card {
  display: grid;
  min-width: 0;
  gap: 1rem;
  padding: 1.2rem;
  border: 1px solid var(--line);
  border-radius: 24px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.02), transparent 45%),
    linear-gradient(180deg, rgba(18, 32, 49, 0.92), rgba(8, 17, 29, 0.94));
  box-shadow: 0 16px 34px rgba(1, 9, 20, 0.22);
  transition: transform var(--transition-card), border-color var(--transition-card), box-shadow var(--transition-card);
}

.sample-card:hover {
  transform: translateY(-6px);
  border-color: rgba(89, 212, 255, 0.32);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.28);
}

.sample-card__header {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.sample-card__meta,
.sample-card__tags,
.sample-card__actions,
.sample-card__footer {
  display: flex;
  flex-wrap: wrap;
}

.sample-card__meta {
  gap: 0.55rem;
}

.sample-card__tags {
  gap: 0.6rem;
}

.sample-card__title {
  font-size: 1.35rem;
  font-weight: 700;
  line-height: 1.14;
  overflow-wrap: anywhere;
  transition: color var(--transition-fast), transform var(--transition-fast);
}

.sample-card__title:hover {
  color: var(--accent);
}

.sample-card__description {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.65;
}

.sample-card__footer {
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.sample-card__footer code {
  color: var(--text-muted);
  font-size: 0.85rem;
  overflow-wrap: anywhere;
}

.sample-card__actions {
  gap: 0.75rem;
}

@media (max-width: 720px) {
  .sample-card__footer {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
