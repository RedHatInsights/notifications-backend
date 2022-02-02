import { validatedResponse, validationResponseTransformer } from 'openapi2typescript';
import { useMemo } from 'react';
import { useQuery } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';
import { EventType } from '../../types/Notifications';

const validateResponse = validationResponseTransformer(
    (payload: Operations.InternalServiceGetEventTypes.Payload) => {
        if (payload.status === 200) {
            const eventTypes: ReadonlyArray<EventType> = payload.value.map(value => ({
                id: value.id ?? '',
                name: value.name,
                displayName: value.display_name,
                description: value.description ?? '',
                applicationId: value.application_id
            }));

            return validatedResponse(
                'EventTypes',
                200,
                eventTypes,
                payload.errors
            );
        }

        return payload;
    }
);

export const useEventTypes = (applicationId: string) => {
    const query = useQuery(Operations.InternalServiceGetEventTypes.actionCreator({
        appId: applicationId
    }));

    const queryPayload = useMemo(() => {
        const payload = query.payload;
        if (payload) {
            return validateResponse(payload);
        }

        return undefined;
    }, [ query.payload ]);

    return useMemo(() => ({
        ...query,
        payload: queryPayload
    }), [ query, queryPayload ]);
};
