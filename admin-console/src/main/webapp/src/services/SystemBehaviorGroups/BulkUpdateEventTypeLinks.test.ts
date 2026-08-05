import { describe, expect, it } from 'vitest';

import { bulkUpdateActionCreator } from './BulkUpdateEventTypeLinks';

describe('bulkUpdateActionCreator', () => {
    it('creates a PUT action with correct path', () => {
        const action = bulkUpdateActionCreator({
            behaviorGroupId: 'bg-123',
            eventTypeIdsToLink: [ 'et-1' ],
            eventTypeIdsToUnlink: [ 'et-2' ]
        });

        expect(action.method).toBe('PUT');
        expect(action.endpoint).toContain('behaviorGroups/default');
        expect(action.endpoint).toContain('bg-123');
        expect(action.endpoint).toContain('eventTypes');
    });

    it('includes link and unlink IDs in the body', () => {
        const action = bulkUpdateActionCreator({
            behaviorGroupId: 'bg-123',
            eventTypeIdsToLink: [ 'et-1', 'et-2' ],
            eventTypeIdsToUnlink: [ 'et-3' ]
        });

        expect(action.body).toEqual({
            event_type_ids_to_link: [ 'et-1', 'et-2' ],
            event_type_ids_to_unlink: [ 'et-3' ]
        });
    });

    it('handles empty link and unlink arrays', () => {
        const action = bulkUpdateActionCreator({
            behaviorGroupId: 'bg-123',
            eventTypeIdsToLink: [],
            eventTypeIdsToUnlink: []
        });

        expect(action.body).toEqual({
            event_type_ids_to_link: [],
            event_type_ids_to_unlink: []
        });
    });
});
