<script setup lang="ts">
import type { FilterOption } from '../types';

defineProps<{
  title: string;
  options: FilterOption[];
  selected: string[];
}>();

const emit = defineEmits<{
  toggle: [value: string];
}>();
</script>

<template>
  <section class="filter-group">
    <div class="filter-group__header">
      <h3>{{ title }}</h3>
      <span>{{ selected.length }} selected</span>
    </div>
    <div class="filter-chip-list">
      <button
        v-for="option in options"
        :key="option.value"
        type="button"
        class="filter-chip"
        :class="{ 'filter-chip--active': selected.includes(option.value) }"
        @click="emit('toggle', option.value)"
      >
        <span>{{ option.value }}</span>
        <strong>{{ option.count }}</strong>
      </button>
    </div>
  </section>
</template>

<style scoped>
.filter-group + .filter-group {
  margin-top: 1rem;
}

.filter-group__header {
  align-items: center;
}

.filter-group__header h3 {
  margin: 0;
  font-size: 0.94rem;
}

.filter-group__header span {
  color: var(--text-muted);
  font-size: 0.85rem;
}

.filter-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

.filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.68rem 0.9rem;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.03);
  color: var(--text);
  cursor: pointer;
  transition: transform var(--transition-fast), border-color var(--transition-fast), background var(--transition-fast), box-shadow var(--transition-fast);
}

.filter-chip:hover {
  transform: translateY(-2px);
  border-color: var(--line-strong);
  background: var(--bg-soft-hover);
}

.filter-chip--active {
  border-color: rgba(89, 212, 255, 0.55);
  background: rgba(89, 212, 255, 0.14);
}

.filter-chip strong {
  color: var(--accent);
  font-size: 0.84rem;
}
</style>
