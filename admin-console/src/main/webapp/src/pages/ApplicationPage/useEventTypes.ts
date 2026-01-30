import { useEffect, useState } from 'react';
import { useClient } from 'react-fetching-library';

import { useEventTypes as useGetEventTypes } from '../../services/EventTypes/GetEventTypes';
import { EventTypeRow } from '../../types/Notifications';

interface EventTypesController {
    payload: any;
    data: ReadonlyArray<EventTypeRow> | undefined;
    loading: boolean;
    reload: () => void;
    error: any;
}

export const useEventTypes = (applicationId: string): EventTypesController => {
    const [ eventTypeRows, setEventTypeRows ] = useState<ReadonlyArray<EventTypeRow>>();
    const eventTypesQuery = useGetEventTypes(applicationId);

    const { query } = useClient();

    useEffect(() => {
        setEventTypeRows(undefined);
    }, [ applicationId ]);

    useEffect(() => {
        if (eventTypesQuery.payload?.status === 200) {
            const eventTypes = eventTypesQuery.payload.value.map<EventTypeRow>(e => ({
                id: e.id,
                name: e.name,
                displayName: e.displayName,
                description: e.description,
                applicationId: e.applicationId,
                fullyQualifiedName: e.fullyQualifiedName,
                subscribedByDefault: e.subscribedByDefault,
                subscriptionLocked: e.subscriptionLocked,
                visible: e.visible,
                defaultSeverity: e.defaultSeverity,
                availableSeverities: e.availableSeverities,
                instantEmail: {
                    isLoading: true
                }
            }));

            setEventTypeRows(eventTypes);
        }
    }, [ eventTypesQuery.payload, eventTypesQuery.loading, query ]);

    return {
        payload: eventTypeRows,
        data: eventTypeRows,
        loading: eventTypesQuery.loading,
        reload: eventTypesQuery.query,
        error: eventTypesQuery.errorObject
    };
};
