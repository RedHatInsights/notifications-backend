import { validatedResponse, validationResponseTransformer } from 'openapi2typescript';
import { useMemo } from 'react';
import { useQuery } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';
import { Application } from '../../types/Notifications';

const validateResponse = validationResponseTransformer(
    (payload: Operations.InternalServiceGetApplications.Payload) => {
        if (payload.status === 200) {
            const applications: ReadonlyArray<Application> = payload.value.map(value => ({
                id: value.id ?? '',
                displayName: value.display_name,
                bundleId: value.bundle_id,
                name: value.name
            }));

            return validatedResponse(
                'Applications',
                200,
                applications,
                payload.errors
            );
        }

        return payload;
    }
);

export const useApplications = (bundleId: string) => {
    const query = useQuery(Operations.InternalServiceGetApplications.actionCreator({
        bundleId
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
