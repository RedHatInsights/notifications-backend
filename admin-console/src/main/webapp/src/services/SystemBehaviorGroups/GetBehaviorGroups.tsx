import { validatedResponse, validationResponseTransformer } from 'openapi2typescript';
import { useMemo } from 'react';
import { useQuery } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';
import { BehaviorGroup, EventTypeBehavior } from '../../types/Notifications';

const validateResponse = validationResponseTransformer(
    (payload: Operations.InternalResourceGetDefaultBehaviorGroups.Payload) => {
        if (payload.status === 200) {
            const systemBehaviorGroups: ReadonlyArray<BehaviorGroup> = payload.value.map(value => ({
                bundleId: value.bundle_id,
                displayName: value.display_name,
                actions: value.actions,
                id: value.id,
                behaviors: value.behaviors?.map(b => ({
                    created: b.created,
                    eventType: b.event_type ? {
                        id: b.event_type.id ?? '',
                        displayName: b.event_type.display_name ?? '',
                        name: b.event_type.name ?? '',
                        description: b.event_type.description ?? '',
                        applicationId: b.event_type.application_id ?? '',
                        subscribedByDefault: b.event_type.subscribed_by_default ?? false,
                        subscriptionLocked: b.event_type.subscription_locked ?? false,
                        visible: b.event_type.visible ?? true,
                        includedInDrawer: b.event_type.included_in_drawer ?? false
                    } : undefined,
                    id: b.id
                } as EventTypeBehavior)) ?? null
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

export const useSystemBehaviorGroups = (bundleId: string) => {
    const query = useQuery(Operations.InternalResourceGetDefaultBehaviorGroups.actionCreator());

    const queryPayload = useMemo(() => {
        const payload = query.payload;
        if (payload) {
            const response = validateResponse(payload);
            if (response.status === 200) {
                response.value = response.value.filter(bg => bg.bundleId === bundleId);
            }

            return response;
        }

        return undefined;
    }, [ query.payload, bundleId ]);

    return useMemo(() => ({
        ...query,
        payload: queryPayload
    }), [ query, queryPayload ]);
};

