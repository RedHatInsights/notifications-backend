import { useMemo } from 'react';
import { useQuery } from 'react-fetching-library';

import { EventType } from '../../types/Notifications';

const getEventTypesAction = (applicationId: string) => ({
    method: 'GET' as const,
    endpoint: `./applications/${applicationId}/eventTypes`
});

export const useEventTypes = (applicationId: string) => {
    const query = useQuery(getEventTypesAction(applicationId));

    const queryPayload = useMemo(() => {
        if (query.payload && !query.error) {
            const rawData = query.payload as any[];
            const eventTypes: ReadonlyArray<EventType> = rawData.map(value => ({
                id: value.id ?? '',
                name: value.name,
                displayName: value.display_name,
                description: value.description ?? '',
                applicationId: value.application_id,
                fullyQualifiedName: value.fully_qualified_name ?? '',
                subscribedByDefault: !!value.subscribed_by_default,
                subscriptionLocked: !!value.subscription_locked,
                visible: !!value.visible,
                defaultSeverity: value.default_severity,
                availableSeverities: value.available_severities
            }));

            return {
                status: 200 as const,
                value: eventTypes
            };
        }

        return undefined;
    }, [ query.payload, query.error ]);

    return useMemo(() => ({
        ...query,
        payload: queryPayload
    }), [ query, queryPayload ]);
};
