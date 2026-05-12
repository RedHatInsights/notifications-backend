/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// When adding new internal API endpoints, add the path prefix here so the dev server proxies it.
const apiPaths = [
    '/access',
    '/admin',
    '/applications',
    '/behaviorGroups',
    '/bundles',
    '/daily-digest',
    '/duplicate-name-migration',
    '/eventTypes',
    '/serverInfo',
    '/severities',
    '/sources-migration',
    '/status',
    '/templates',
    '/validation',
    '/version',
];

const apiProxy = Object.fromEntries(
    apiPaths.map((path) => [path, 'http://localhost:8085/internal'])
);

export default defineConfig({
    plugins: [react()],
    base: './',
    build: {
        outDir: 'build',
    },
    server: {
        port: 3000,
        proxy: apiProxy,
    },
    test: {
        globals: true,
        environment: 'jsdom',
        setupFiles: './config/setupTests.ts',
        passWithNoTests: true,
    },
});
