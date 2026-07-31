import { describe, expect, it } from 'vitest';

import { actionCreator, CreateBundle } from './CreateBundle';

describe('CreateBundle actionCreator', () => {
    it('creates a POST action when id is undefined', () => {
        const params: CreateBundle = {
            displayName: 'My Bundle',
            name: 'my-bundle'
        };

        const action = actionCreator(params);

        expect(action.method).toBe('POST');
        expect(action.endpoint).toContain('/bundles');
        expect(action.body).toEqual({
            display_name: 'My Bundle',
            name: 'my-bundle'
        });
    });

    it('creates a PUT action when id is provided', () => {
        const params: CreateBundle = {
            id: '123e4567-e89b-12d3-a456-426614174000',
            displayName: 'Updated Bundle',
            name: 'updated-bundle'
        };

        const action = actionCreator(params);

        expect(action.method).toBe('PUT');
        expect(action.endpoint).toContain('/bundles/123e4567-e89b-12d3-a456-426614174000');
        expect(action.body).toEqual({
            display_name: 'Updated Bundle',
            name: 'updated-bundle'
        });
    });

    it('uses empty strings for create when fields are empty', () => {
        const params: CreateBundle = {
            displayName: '',
            name: ''
        };

        const action = actionCreator(params);

        expect(action.method).toBe('POST');
        expect(action.body).toEqual({
            display_name: '',
            name: ''
        });
    });
});
