import { validatedResponse, validationResponseTransformer } from 'openapi2typescript';
import { useMemo } from 'react';
import { useQuery } from 'react-fetching-library';

import { Operations } from '../generated/OpenapiInternal';
import { Application } from '../types/Notifications';

const validateResponse = validationResponseTransformer(
    (payload: Operations.InternalServiceGetApplication.Payload) => {
        if (payload.status === 200) {
            const applicationType: Application = { id: payload.value.id ?? '', displayName: payload.value.display_name };

            return validatedResponse(
                'Application',
                200,
                applicationType,
                payload.errors
            );
        }

        return payload;
    }
);

export const useApplicationTypes = (applicationId: string) => {
    const query = useQuery(Operations.InternalServiceGetApplication.actionCreator({
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
