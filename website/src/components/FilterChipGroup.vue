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
