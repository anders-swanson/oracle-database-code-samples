<script setup lang="ts">
import { computed } from 'vue';
import { parseInlineMarkdown } from '../lib/inlineMarkdown';

const props = defineProps<{
  text: string;
  baseUrl: string;
}>();

const segments = computed(() => parseInlineMarkdown(props.text, props.baseUrl));
</script>

<template>
  <span>
    <template v-for="(segment, index) in segments" :key="`${index}-${segment.text}`">
      <a
        v-if="segment.kind === 'link' && segment.href"
        class="inline-markdown-link"
        :href="segment.href"
        target="_blank"
        rel="noreferrer"
      >
        {{ segment.text }}
      </a>
      <template v-else>{{ segment.text }}</template>
    </template>
  </span>
</template>

<style scoped>
.inline-markdown-link {
  color: var(--accent);
  text-decoration: underline;
  text-decoration-thickness: 0.08em;
  text-underline-offset: 0.16em;
  transition: color var(--transition-fast), text-decoration-color var(--transition-fast);
}

.inline-markdown-link:hover {
  color: #bfefff;
  text-decoration-color: currentColor;
}

.inline-markdown-link:focus-visible {
  outline: none;
  border-radius: 0.2rem;
  box-shadow: var(--focus-ring);
}
</style>
