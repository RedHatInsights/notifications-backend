import { describe, expect, it } from 'vitest';

import { unlinkActionCreator } from './UnlinkDefaultBehaviorToEventType';

describe('unlinkActionCreator', () => {
    it('creates a DELETE action with correct path params', () => {
        const action = unlinkActionCreator({
            behaviorGroupId: 'bg-123',
            eventTypeId: 'et-456'
        });

        expect(action.method).toBe('DELETE');
        expect(action.endpoint).toContain('bg-123');
        expect(action.endpoint).toContain('et-456');
        expect(action.endpoint).toContain('behaviorGroups/default');
    });

    it('includes both IDs in the endpoint path', () => {
        const bgId = '00000000-0000-0000-0000-000000000001';
        const etId = '00000000-0000-0000-0000-000000000002';
        const action = unlinkActionCreator({
            behaviorGroupId: bgId,
            eventTypeId: etId
        });

        expect(action.endpoint).toContain(bgId);
        expect(action.endpoint).toContain(etId);
    });
});
