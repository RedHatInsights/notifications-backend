import { describe, expect, it } from 'vitest';

import { linkActionCreator } from './LinkDefaultBehaviorToEventType';

describe('linkActionCreator', () => {
    it('creates a PUT action with correct path params', () => {
        const action = linkActionCreator({
            behaviorGroupId: 'bg-123',
            eventTypeId: 'et-456'
        });

        expect(action.method).toBe('PUT');
        expect(action.endpoint).toContain('bg-123');
        expect(action.endpoint).toContain('et-456');
        expect(action.endpoint).toContain('behaviorGroups/default');
    });

    it('includes both IDs in the endpoint path', () => {
        const bgId = '00000000-0000-0000-0000-000000000001';
        const etId = '00000000-0000-0000-0000-000000000002';
        const action = linkActionCreator({
            behaviorGroupId: bgId,
            eventTypeId: etId
        });

        expect(action.endpoint).toContain(bgId);
        expect(action.endpoint).toContain(etId);
    });
});
