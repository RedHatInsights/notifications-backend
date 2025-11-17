const { FlatCompat } = require('@eslint/eslintrc');
const js = require('@eslint/js');
const typescriptEslint = require('@typescript-eslint/eslint-plugin');
const typescriptParser = require('@typescript-eslint/parser');
const reactHooks = require('eslint-plugin-react-hooks');
const path = require('path');

const compat = new FlatCompat({
    baseDirectory: __dirname,
    recommendedConfig: js.configs.recommended,
});

module.exports = [
    {
        ignores: ['build/**', 'node_modules/**', 'dist/**', 'src/generated/**'],
    },
    ...compat.extends('@redhat-cloud-services/eslint-config-redhat-cloud-services'),
    {
        files: ['**/*.js'],
        languageOptions: {
            ecmaVersion: 'latest',
            sourceType: 'commonjs',
            parserOptions: {
                requireConfigFile: false,
            },
            globals: {
                process: 'readonly',
                module: 'readonly',
                require: 'readonly',
                __dirname: 'readonly',
            },
        },
        rules: {
            'prettier/prettier': 'off',
            'no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
        },
    },
    {
        files: ['**/*.ts', '**/*.tsx'],
        languageOptions: {
            parser: typescriptParser,
            parserOptions: {
                ecmaVersion: 'latest',
                sourceType: 'module',
            },
            globals: {
                insights: 'readonly',
                shallow: 'readonly',
                render: 'readonly',
                mount: 'readonly',
            },
        },
        plugins: {
            '@typescript-eslint': typescriptEslint,
            'react-hooks': reactHooks,
        },
        rules: {
            'react/prop-types': 'off',
            'prettier/prettier': 'off',
            'sort-imports': 'off',
            'no-unused-vars': 'off',
            '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
        },
    },
];
