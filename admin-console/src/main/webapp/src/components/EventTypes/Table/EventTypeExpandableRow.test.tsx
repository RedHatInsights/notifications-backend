import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { EventType } from '../../../types/Notifications';
import { EventTypeExpandableRow } from './EventTypeExpandableRow';

const mockEventType: EventType = {
    id: 'et-001',
    displayName: 'Test Event',
    name: 'test-event',
    description: 'A test event type',
    applicationId: 'app-001',
    subscribedByDefault: false,
    subscriptionLocked: false,
    visible: true,
    includedInDrawer: false
};

describe('EventTypeExpandableRow', () => {
    it('renders event type id and description', () => {
        render(<EventTypeExpandableRow eventType={ mockEventType } />);

        expect(screen.getByText('et-001')).toBeInTheDocument();
        expect(screen.getByText('A test event type')).toBeInTheDocument();
    });
});
