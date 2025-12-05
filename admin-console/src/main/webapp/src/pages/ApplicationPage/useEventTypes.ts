import produce, { original } from 'immer';
import { useEffect, useState } from 'react';
import { useClient } from 'react-fetching-library';

import {
    Operations
} from '../../generated/OpenapiInternal';
import { useEventTypes as useGetEventTypes } from '../../services/EventTypes/GetEventTypes';
import {EventTypeRow, InstantTemplate} from '../../types/Notifications';

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
                ...e,
                instantEmail: {
                    isLoading: true
                }
            }));

            setEventTypeRows(eventTypes);

            eventTypes.forEach(row => {
                query(Operations.TemplateResourceGetInstantEmailTemplateByEventType.actionCreator({
                    eventTypeId: row.id
                }))
                .then(result => {
                    if (result.payload?.status === 200 || result.payload?.status === 404) {
                        const value: Partial<InstantTemplate> = result.payload.status === 200 ? {
                            id: result.payload.value.id ?? undefined,
                            bodyTemplateId: result.payload.value.body_template_id ?? undefined,
                            subjectTemplateId: result.payload.value.subject_template_id ?? undefined,
                            eventTypeId: result.payload.value.event_type_id ?? row.id
                        } : {
                            eventTypeId: row.id
                        };
                        setEventTypeRows(produce(draft => {
                            const originalValue = original(draft);
                            if (draft && originalValue) {
                                const index = originalValue.findIndex(r => r.id === row.id);
                                if (index !== -1) {
                                    draft[index].instantEmail = {
                                        isLoading: false,
                                        ...value
                                    };
                                }
                            }
                        }));
                    }
                });
            });
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
