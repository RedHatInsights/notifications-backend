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
    it('shows spinner while loading', () => {
        const client = createClient({
            fetch: () => new Promise(() => {})
        });
        render(
            <ClientContextProvider client={ client }>
                <BehaviorGroupEventTypesPanel
                    behaviorGroup={ makeBehaviorGroup([]) }
                    applications={ mockApplications }
                    onLinkEventType={ vi.fn() }
                    onUnlinkEventType={ vi.fn() }
                />
            </ClientContextProvider>
        );

        expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });

    it('renders event types grouped by application', async () => {
        const responses = {
            'app-1': makeEventTypeResponse('app-1', [
                { id: 'et-1', name: 'policy-triggered', displayName: 'Policy Triggered' }
            ]),
            'app-2': makeEventTypeResponse('app-2', [
                { id: 'et-2', name: 'new-recommendation', displayName: 'New Recommendation' }
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

        await waitFor(() => {
            expect(screen.getByText('Policies')).toBeInTheDocument();
        });
        expect(screen.getByText('Advisor')).toBeInTheDocument();
        expect(screen.getByLabelText('Toggle link for Policy Triggered')).toBeInTheDocument();
        expect(screen.getByLabelText('Toggle link for New Recommendation')).toBeInTheDocument();
    });

    it('shows checked switch for linked event types', async () => {
        const responses = {
            'app-1': makeEventTypeResponse('app-1', [
                { id: 'et-1', name: 'policy-triggered', displayName: 'Policy Triggered' }
            ])
        };

        renderWithClient(
            <BehaviorGroupEventTypesPanel
                behaviorGroup={ makeBehaviorGroup([ 'et-1' ]) }
                applications={ [ mockApplications[0] ] }
                onLinkEventType={ vi.fn() }
                onUnlinkEventType={ vi.fn() }
            />,
            responses
        );

        await waitFor(() => {
            expect(screen.getByLabelText('Toggle link for Policy Triggered')).toBeChecked();
        });
    });

    it('shows unchecked switch for unlinked event types', async () => {
        const responses = {
            'app-1': makeEventTypeResponse('app-1', [
                { id: 'et-1', name: 'policy-triggered', displayName: 'Policy Triggered' }
            ])
        };

        renderWithClient(
            <BehaviorGroupEventTypesPanel
                behaviorGroup={ makeBehaviorGroup([]) }
                applications={ [ mockApplications[0] ] }
                onLinkEventType={ vi.fn() }
                onUnlinkEventType={ vi.fn() }
            />,
            responses
        );

        await waitFor(() => {
            expect(screen.getByLabelText('Toggle link for Policy Triggered')).not.toBeChecked();
        });
    });

    it('calls onLinkEventType when toggling unlinked switch', async () => {
        const onLink = vi.fn().mockResolvedValue(true);
        const responses = {
            'app-1': makeEventTypeResponse('app-1', [
                { id: 'et-1', name: 'policy-triggered', displayName: 'Policy Triggered' }
            ])
        };

        renderWithClient(
            <BehaviorGroupEventTypesPanel
                behaviorGroup={ makeBehaviorGroup([]) }
                applications={ [ mockApplications[0] ] }
                onLinkEventType={ onLink }
                onUnlinkEventType={ vi.fn() }
            />,
            responses
        );

        await waitFor(() => {
            expect(screen.getByLabelText('Toggle link for Policy Triggered')).toBeInTheDocument();
        });

        const toggle = screen.getByLabelText('Toggle link for Policy Triggered');
        await userEvent.click(toggle);

        await waitFor(() => {
            expect(onLink).toHaveBeenCalledWith('bg-1', 'et-1');
        });
    });

    it('calls onUnlinkEventType when toggling linked switch', async () => {
        const onUnlink = vi.fn().mockResolvedValue(true);
        const responses = {
            'app-1': makeEventTypeResponse('app-1', [
                { id: 'et-1', name: 'policy-triggered', displayName: 'Policy Triggered' }
            ])
        };

        renderWithClient(
            <BehaviorGroupEventTypesPanel
                behaviorGroup={ makeBehaviorGroup([ 'et-1' ]) }
                applications={ [ mockApplications[0] ] }
                onLinkEventType={ vi.fn() }
                onUnlinkEventType={ onUnlink }
            />,
            responses
        );

        await waitFor(() => {
            expect(screen.getByLabelText('Toggle link for Policy Triggered')).toBeChecked();
        });

        const toggle = screen.getByLabelText('Toggle link for Policy Triggered');
        await userEvent.click(toggle);

        await waitFor(() => {
            expect(onUnlink).toHaveBeenCalledWith('bg-1', 'et-1');
        });
    });

    it('shows error alert on failed toggle', async () => {
        const onLink = vi.fn().mockResolvedValue(false);
        const responses = {
            'app-1': makeEventTypeResponse('app-1', [
                { id: 'et-1', name: 'policy-triggered', displayName: 'Policy Triggered' }
            ])
        };

        renderWithClient(
            <BehaviorGroupEventTypesPanel
                behaviorGroup={ makeBehaviorGroup([]) }
                applications={ [ mockApplications[0] ] }
                onLinkEventType={ onLink }
                onUnlinkEventType={ vi.fn() }
            />,
            responses
        );

        await waitFor(() => {
            expect(screen.getByLabelText('Toggle link for Policy Triggered')).toBeInTheDocument();
        });

        const toggle = screen.getByLabelText('Toggle link for Policy Triggered');
        await userEvent.click(toggle);

        await waitFor(() => {
            expect(screen.getByText(/Failed to link/)).toBeInTheDocument();
        });
    });

    it('shows empty message when no event types exist', async () => {
        renderWithClient(
            <BehaviorGroupEventTypesPanel
                behaviorGroup={ makeBehaviorGroup([]) }
                applications={ [] }
                onLinkEventType={ vi.fn() }
                onUnlinkEventType={ vi.fn() }
            />,
            {}
        );

        await waitFor(() => {
            expect(screen.getByText('No event types available in this bundle.')).toBeInTheDocument();
        });
    });
});
