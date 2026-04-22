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
