<script setup lang="ts">
import { useHead } from '@unhead/vue';
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { buildRouteHead, resolveRouteMetadata } from '../lib/seo';

const route = useRoute();

useHead(computed(() => buildRouteHead(resolveRouteMetadata(route))));
</script>

<template></template>
