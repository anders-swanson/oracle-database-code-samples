<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import AppShell from '../components/AppShell.vue';
import InlineMarkdown from '../components/InlineMarkdown.vue';
import SampleCard from '../components/SampleCard.vue';
import { findRelatedSamples, findSampleById, samples } from '../lib/catalog';

const route = useRoute();
const sample = computed(() => findSampleById(String(route.params.id)));
const related = computed(() => (sample.value ? findRelatedSamples(sample.value, samples) : []));
</script>

<template>
  <AppShell compact>
    <div v-if="sample" class="detail-page">
      <section class="detail-hero">
        <RouterLink class="detail-hero__back" to="/">← Back to catalog</RouterLink>
        <div class="detail-hero__frame">
          <div class="detail-hero__topline">
            <span>{{ sample.language }}</span>
            <span>{{ sample.parentCollection }}</span>
          </div>
          <h1>{{ sample.title }}</h1>
          <p>{{ sample.description }}</p>

          <div class="detail-hero__actions">
            <a class="button button--primary" :href="sample.githubCodeUrl" target="_blank" rel="noreferrer">
              View Code
            </a>
            <a class="button button--ghost" :href="sample.githubReadmeUrl" target="_blank" rel="noreferrer">
              View README
            </a>
            <a
              v-if="sample.blogPost"
              class="button button--ghost"
              :href="sample.blogPost"
              target="_blank"
              rel="noreferrer"
            >
              Blog Post
            </a>
          </div>

          <div class="detail-hero__feature-list">
            <span v-for="tag in sample.tags" :key="tag" class="sample-card__tag">
              #{{ tag }}
            </span>
          </div>
        </div>
      </section>

      <section class="detail-layout">
        <article class="detail-panel">
          <div class="detail-panel__header">
            <span class="catalog-results__eyebrow">What this sample helps you learn</span>
          </div>
          <p class="detail-panel__excerpt">{{ sample.readmeExcerpt }}</p>
          <div v-if="sample.highlights.length > 0" class="detail-panel__block">
            <h3>Highlights</h3>
            <ul>
              <li v-for="highlight in sample.highlights" :key="highlight">
                <InlineMarkdown :text="highlight" :base-url="sample.githubReadmeUrl" />
              </li>
            </ul>
          </div>
        </article>

        <aside class="detail-sidebar">
          <div class="detail-panel">
            <div class="detail-panel__header">
              <span class="catalog-results__eyebrow">Context</span>
            </div>
            <dl class="metadata-list">
              <div>
                <dt>Repo path</dt>
                <dd><code>{{ sample.path }}</code></dd>
              </div>
              <div>
                <dt>Collection</dt>
                <dd>{{ sample.parentCollection }}</dd>
              </div>
              <div>
                <dt>Tags</dt>
                <dd>{{ sample.tags.length > 0 ? sample.tags.join(', ') : 'No tags declared' }}</dd>
              </div>
            </dl>
          </div>
        </aside>
      </section>

      <section v-if="related.length > 0" class="related-section">
        <div class="detail-panel__header">
          <span class="catalog-results__eyebrow">Keep Exploring</span>
        </div>
        <div class="sample-grid sample-grid--compact">
          <SampleCard v-for="entry in related" :key="entry.id" :sample="entry" />
        </div>
      </section>
    </div>

    <section v-else class="empty-state empty-state--full">
      <h1>Sample not found</h1>
      <p>The requested sample id does not exist in the generated catalog.</p>
      <RouterLink class="button button--primary" to="/">Return to catalog</RouterLink>
    </section>
  </AppShell>
</template>
