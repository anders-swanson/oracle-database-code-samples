import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig(({ command }) => ({
  base: command === 'serve' ? '/' : '/oracle-database-code-samples/',
  plugins: [vue()],
  test: {
    environment: 'jsdom'
  }
}));
