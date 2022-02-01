import { validatedResponse, validationResponseTransformer } from 'openapi2typescript';
import { useMemo } from 'react';
import { useQuery } from 'react-fetching-library';

import { Operations } from '../generated/OpenapiInternal';
import { Bundle } from '../types/Notifications';

const validateResponse = validationResponseTransformer(
    (payload: Operations.InternalServiceGetBundle.Payload) => {
        if (payload.status === 200) {
            const bundleTypes: Bundle = {
                id: payload.value.id ?? '',
                displayName: payload.value.display_name,
                applications: []
            };

            return validatedResponse(
                'Bundle',
                200,
                bundleTypes,
                payload.errors
            );
        }

        return payload;
    }
);

export const useBundleTypes = (bundleId: string) => {
    const query = useQuery(Operations.InternalServiceGetBundle.actionCreator({
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
