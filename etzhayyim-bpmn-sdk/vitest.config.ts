/// <reference types="vitest" />
import { defineConfig } from 'vitest/config';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'node:url';

const rootDir = dirname(fileURLToPath(import.meta.url));
const r = (p: string) => resolve(rootDir, p);

export default defineConfig({
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: [r('./vitest.setup.ts')],
    include: ['packages/**/src/**/*.test.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      include: ['packages/**/src/**/*.{ts,tsx}'],
      exclude: ['**/node_modules/**', 'examples/**'],
      all: true,
      thresholds: {
        global: {
          branches: 100,
          functions: 100,
          lines: 100,
          statements: 100,
        },
      },
    },
  },
  resolve: {
    alias: {
      '@etzhayyim/bpmn-sdk-core': resolve('./packages/core/src'),
      '@etzhayyim/bpmn-sdk-dsl': resolve('./packages/dsl/src'),
      '@etzhayyim/bpmn-sdk-compiler': resolve('./packages/compiler/src'),
      '@etzhayyim/bpmn-sdk-runtime': resolve('./packages/runtime/src'),
      '@etzhayyim/bpmn-sdk-importer': resolve('./packages/importer/src'),
      '@etzhayyim/bpmn-sdk-human': resolve('./packages/human/src'),
      '@etzhayyim/bpmn-sdk-validation': resolve('./packages/validation/src'),
      '@etzhayyim/bpmn-sdk-testing': resolve('./packages/testing/src'),
      '@etzhayyim/bpmn-sdk-ops': resolve('./packages/ops/src'),
      '@etzhayyim/bpmn-sdk-form': resolve('./packages/form/src'),
      '@etzhayyim/bpmn-sdk-dmn': resolve('./packages/dmn/src'),
      '@etzhayyim/bpmn-sdk/testkit': resolve('./packages/internal/testkit/src'),
    },
  },
});

