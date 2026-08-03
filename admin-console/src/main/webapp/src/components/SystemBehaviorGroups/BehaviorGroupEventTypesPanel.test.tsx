import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import * as React from 'react';
import { ClientContextProvider, createClient } from 'react-fetching-library';
import { describe, expect, it, vi } from 'vitest';

import { Application, BehaviorGroup } from '../../types/Notifications';
import { BehaviorGroupEventTypesPanel } from './BehaviorGroupEventTypesPanel';

const mockApplications: Application[] = [
    { id: 'app-1', displayName: 'Policies', bundleId: 'bundle-1', name: 'policies' },
    { id: 'app-2', displayName: 'Advisor', bundleId: 'bundle-1', name: 'advisor' }
];

const makeBehaviorGroup = (linkedEventTypeIds: string[]): BehaviorGroup => ({
    id: 'bg-1',
    displayName: 'Email All',
    bundleId: 'bundle-1',
    behaviors: linkedEventTypeIds.map(etId => ({
        id: { behaviorGroupId: 'bg-1', eventTypeId: etId }
    }))
});

const makeEventTypeResponse = (appId: string, eventTypes: Array<{ id: string; name: string; displayName: string }>) => {
    return eventTypes.map(et => ({
        id: et.id,
        name: et.name,
        display_name: et.displayName,
        description: '',
        application_id: appId,
        subscribed_by_default: false,
        subscription_locked: false,
        visible: true,
        included_in_drawer: false
    }));
};

const createMockClient = (responses: Record<string, any>) => {
    return createClient({
        fetch: async (input: string | URL) => {
            const url = typeof input === 'string' ? input : input.toString();
            for (const [ pattern, data ] of Object.entries(responses)) {
                if (url.includes(pattern)) {
                    return new Response(JSON.stringify(data), {
                        status: 200,
                        headers: { 'Content-Type': 'application/json' }
                    });
                }
            }

            return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } });
        }
    });
};

const renderWithClient = (ui: React.ReactElement, responses: Record<string, any>) => {
    const client = createMockClient(responses);
    return render(
        <ClientContextProvider client={ client }>
            { ui }
        </ClientContextProvider>
    );
};

describe('BehaviorGroupEventTypesPanel', () => {
    it('shows application selector with placeholder', () => {
        renderWithClient(
            <BehaviorGroupEventTypesPanel
                behaviorGroup={ makeBehaviorGroup([]) }
                applications={ mockApplications }
                onLinkEventType={ vi.fn() }
                onUnlinkEventType={ vi.fn() }
            />,
            {}
        );

        expect(screen.getByLabelText('Select application')).toBeInTheDocument();
        expect(screen.getByText('Select an application')).toBeInTheDocument();
    });

    it('shows empty message when no applications', () => {
        renderWithClient(
            <BehaviorGroupEventTypesPanel
                behaviorGroup={ makeBehaviorGroup([]) }
                applications={ [] }
                onLinkEventType={ vi.fn() }
                onUnlinkEventType={ vi.fn() }
            />,
            {}
        );

        expect(screen.getByText('No applications available in this bundle.')).toBeInTheDocument();
    });

    it('renders event types with checkboxes when application selected', async () => {
        const responses = {
            'app-1': makeEventTypeResponse('app-1', [
                { id: 'et-1', name: 'policy-triggered', displayName: 'Policy Triggered' },
                { id: 'et-2', name: 'policy-created', displayName: 'Policy Created' }
            ])
        };

        renderWithClient(
            <BehaviorGroupEventTypesPanel
                behaviorGroup={ makeBehaviorGroup([]) }
                applications={ mockApplications }
                onLinkEventType={ vi.fn() }
                onUnlinkEventType={ vi.fn() }
            />,
            responses
        );

        const select = screen.getByLabelText('Select application');
        await userEvent.selectOptions(select, 'app-1');

        await waitFor(() => {
            expect(screen.getByLabelText('Policy Triggered')).toBeInTheDocument();
        });
        expect(screen.getByLabelText('Policy Created')).toBeInTheDocument();
    });

    it('pre-checks linked event types', async () => {
        const responses = {
            'app-1': makeEventTypeResponse('app-1', [
                { id: 'et-1', name: 'policy-triggered', displayName: 'Policy Triggered' },
                { id: 'et-2', name: 'policy-created', displayName: 'Policy Created' }
            ])
        };

        renderWithClient(
            <BehaviorGroupEventTypesPanel
                behaviorGroup={ makeBehaviorGroup([ 'et-1' ]) }
                applications={ mockApplications }
                onLinkEventType={ vi.fn() }
                onUnlinkEventType={ vi.fn() }
            />,
            responses
        );

        const select = screen.getByLabelText('Select application');
        await userEvent.selectOptions(select, 'app-1');

        await waitFor(() => {
            expect(screen.getByLabelText('Policy Triggered')).toBeChecked();
        });
        expect(screen.getByLabelText('Policy Created')).not.toBeChecked();
    });

    it('shows disabled Update button when no changes', async () => {
        const responses = {
            'app-1': makeEventTypeResponse('app-1', [
                { id: 'et-1', name: 'policy-triggered', displayName: 'Policy Triggered' }
            ])
        };

        renderWithClient(
            <BehaviorGroupEventTypesPanel
                behaviorGroup={ makeBehaviorGroup([]) }
                applications={ mockApplications }
                onLinkEventType={ vi.fn() }
                onUnlinkEventType={ vi.fn() }
            />,
            responses
        );

        const select = screen.getByLabelText('Select application');
        await userEvent.selectOptions(select, 'app-1');

        await waitFor(() => {
            expect(screen.getByLabelText('Policy Triggered')).toBeInTheDocument();
        });

        const updateButton = screen.getByRole('button', { name: 'Update' });
        expect(updateButton).toBeDisabled();
    });

    it('enables Update button when checkbox toggled', async () => {
        const responses = {
            'app-1': makeEventTypeResponse('app-1', [
                { id: 'et-1', name: 'policy-triggered', displayName: 'Policy Triggered' }
            ])
        };

        renderWithClient(
            <BehaviorGroupEventTypesPanel
                behaviorGroup={ makeBehaviorGroup([]) }
                applications={ mockApplications }
                onLinkEventType={ vi.fn() }
                onUnlinkEventType={ vi.fn() }
            />,
            responses
        );

        const select = screen.getByLabelText('Select application');
        await userEvent.selectOptions(select, 'app-1');

        await waitFor(() => {
            expect(screen.getByLabelText('Policy Triggered')).toBeInTheDocument();
        });

        await userEvent.click(screen.getByLabelText('Policy Triggered'));

        const updateButton = screen.getByRole('button', { name: 'Update' });
        expect(updateButton).toBeEnabled();
    });

    it('calls onLinkEventType for newly checked items on Update', async () => {
        const onLink = vi.fn().mockResolvedValue(true);
        const onUnlink = vi.fn().mockResolvedValue(true);
        const responses = {
            'app-1': makeEventTypeResponse('app-1', [
                { id: 'et-1', name: 'policy-triggered', displayName: 'Policy Triggered' },
                { id: 'et-2', name: 'policy-created', displayName: 'Policy Created' }
            ])
        };

        renderWithClient(
            <BehaviorGroupEventTypesPanel
                behaviorGroup={ makeBehaviorGroup([]) }
                applications={ mockApplications }
                onLinkEventType={ onLink }
                onUnlinkEventType={ onUnlink }
            />,
            responses
        );

        const select = screen.getByLabelText('Select application');
        await userEvent.selectOptions(select, 'app-1');

        await waitFor(() => {
            expect(screen.getByLabelText('Policy Triggered')).toBeInTheDocument();
        });

        await userEvent.click(screen.getByLabelText('Policy Triggered'));
        await userEvent.click(screen.getByLabelText('Policy Created'));

        await userEvent.click(screen.getByRole('button', { name: 'Update' }));

        await waitFor(() => {
            expect(onLink).toHaveBeenCalledWith('bg-1', 'et-1');
            expect(onLink).toHaveBeenCalledWith('bg-1', 'et-2');
        });
        expect(onUnlink).not.toHaveBeenCalled();
    });

    it('calls onUnlinkEventType for unchecked linked items on Update', async () => {
        const onLink = vi.fn().mockResolvedValue(true);
        const onUnlink = vi.fn().mockResolvedValue(true);
        const responses = {
            'app-1': makeEventTypeResponse('app-1', [
                { id: 'et-1', name: 'policy-triggered', displayName: 'Policy Triggered' }
            ])
        };

        renderWithClient(
            <BehaviorGroupEventTypesPanel
                behaviorGroup={ makeBehaviorGroup([ 'et-1' ]) }
                applications={ mockApplications }
                onLinkEventType={ onLink }
                onUnlinkEventType={ onUnlink }
            />,
            responses
        );

        const select = screen.getByLabelText('Select application');
        await userEvent.selectOptions(select, 'app-1');

        await waitFor(() => {
            expect(screen.getByLabelText('Policy Triggered')).toBeChecked();
        });

        await userEvent.click(screen.getByLabelText('Policy Triggered'));

        await userEvent.click(screen.getByRole('button', { name: 'Update' }));

        await waitFor(() => {
            expect(onUnlink).toHaveBeenCalledWith('bg-1', 'et-1');
        });
        expect(onLink).not.toHaveBeenCalled();
    });

    it('shows success alert after successful update', async () => {
        const onLink = vi.fn().mockResolvedValue(true);
        const responses = {
            'app-1': makeEventTypeResponse('app-1', [
                { id: 'et-1', name: 'policy-triggered', displayName: 'Policy Triggered' }
            ])
        };

        renderWithClient(
            <BehaviorGroupEventTypesPanel
                behaviorGroup={ makeBehaviorGroup([]) }
                applications={ mockApplications }
                onLinkEventType={ onLink }
                onUnlinkEventType={ vi.fn() }
            />,
            responses
        );

        const select = screen.getByLabelText('Select application');
        await userEvent.selectOptions(select, 'app-1');

        await waitFor(() => {
            expect(screen.getByLabelText('Policy Triggered')).toBeInTheDocument();
        });

        await userEvent.click(screen.getByLabelText('Policy Triggered'));
        await userEvent.click(screen.getByRole('button', { name: 'Update' }));

        await waitFor(() => {
            expect(screen.getByText('Event type links updated successfully.')).toBeInTheDocument();
        });
    });

    it('shows error alert on failed update', async () => {
        const onLink = vi.fn().mockResolvedValue(false);
        const responses = {
            'app-1': makeEventTypeResponse('app-1', [
                { id: 'et-1', name: 'policy-triggered', displayName: 'Policy Triggered' }
            ])
        };

        renderWithClient(
            <BehaviorGroupEventTypesPanel
                behaviorGroup={ makeBehaviorGroup([]) }
                applications={ mockApplications }
                onLinkEventType={ onLink }
                onUnlinkEventType={ vi.fn() }
            />,
            responses
        );

        const select = screen.getByLabelText('Select application');
        await userEvent.selectOptions(select, 'app-1');

        await waitFor(() => {
            expect(screen.getByLabelText('Policy Triggered')).toBeInTheDocument();
        });

        await userEvent.click(screen.getByLabelText('Policy Triggered'));
        await userEvent.click(screen.getByRole('button', { name: 'Update' }));

        await waitFor(() => {
            expect(screen.getByText(/Failed to update/)).toBeInTheDocument();
        });
    });

    it('confirms before switching application with unsaved changes', async () => {
        const responses = {
            'app-1': makeEventTypeResponse('app-1', [
                { id: 'et-1', name: 'policy-triggered', displayName: 'Policy Triggered' }
            ]),
            'app-2': makeEventTypeResponse('app-2', [
                { id: 'et-3', name: 'advisor-new', displayName: 'New Recommendation' }
            ])
        };

        renderWithClient(
            <BehaviorGroupEventTypesPanel
                behaviorGroup={ makeBehaviorGroup([]) }
                applications={ mockApplications }
                onLinkEventType={ vi.fn() }
                onUnlinkEventType={ vi.fn() }
            />,
            responses
        );

        const select = screen.getByLabelText('Select application');
        await userEvent.selectOptions(select, 'app-1');

        await waitFor(() => {
            expect(screen.getByLabelText('Policy Triggered')).toBeInTheDocument();
        });

        // Make unsaved change
        await userEvent.click(screen.getByLabelText('Policy Triggered'));

        // Decline the confirmation — should stay on app-1
        vi.spyOn(window, 'confirm').mockReturnValueOnce(false);
        await userEvent.selectOptions(select, 'app-2');

        // Still showing app-1 event types
        expect(screen.getByLabelText('Policy Triggered')).toBeInTheDocument();
        expect(screen.queryByLabelText('New Recommendation')).not.toBeInTheDocument();

        // Accept the confirmation — should switch to app-2
        vi.spyOn(window, 'confirm').mockReturnValueOnce(true);
        await userEvent.selectOptions(select, 'app-2');

        await waitFor(() => {
            expect(screen.getByLabelText('New Recommendation')).toBeInTheDocument();
        });
        expect(screen.queryByLabelText('Policy Triggered')).not.toBeInTheDocument();

        vi.restoreAllMocks();
    });

    it('shows empty message when selected app has no event types', async () => {
        renderWithClient(
            <BehaviorGroupEventTypesPanel
                behaviorGroup={ makeBehaviorGroup([]) }
                applications={ mockApplications }
                onLinkEventType={ vi.fn() }
                onUnlinkEventType={ vi.fn() }
            />,
            {}
        );

        const select = screen.getByLabelText('Select application');
        await userEvent.selectOptions(select, 'app-1');

        await waitFor(() => {
            expect(screen.getByText('No event types found for this application.')).toBeInTheDocument();
        });
    });
});
