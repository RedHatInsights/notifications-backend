import { validatedResponse, validationResponseTransformer } from 'openapi2typescript';
import { useMemo } from 'react';
import { useQuery } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';
import { BehaviorGroup } from '../../types/Notifications';

const validateResponse = validationResponseTransformer(
    (payload: Operations.InternalServiceGetDefaultBehaviorGroups.Payload) => {
        if (payload.status === 200) {
            const systemBehaviorGroups: ReadonlyArray<BehaviorGroup> = payload.value.map(value => ({
                bundleId: value.bundle_id,
                displayName: value.display_name,
                actions: value.actions,
                id: value.id
            }));

            return validatedResponse(
                'BehaviorGroups',
                200,
                systemBehaviorGroups,
                payload.errors
            );
        }

        return payload;
    }
);

export const useSystemBehaviorGroups = () => {
    const query = useQuery(Operations.InternalServiceGetDefaultBehaviorGroups.actionCreator());

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
