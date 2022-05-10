import { validatedResponse, validationResponseTransformer } from 'openapi2typescript';
import { useMemo } from 'react';
import { useQuery } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';
import { InstantTemplate } from '../../types/Notifications';

const validateResponse = validationResponseTransformer(
    (payload: Operations.TemplateResourceGetAllInstantEmailTemplates.Payload) => {
        if (payload.status === 200) {
            const instantEmailTemplates: ReadonlyArray<InstantTemplate> = payload.value.map(value => ({
                event_type: value.event_type,
                event_type_id: value.event_type_id,
                id: value.id,
                subject_template: value.subject_template,
                body_template: value.body_template!
            }));

            return validatedResponse(
                'instantEmailTemplates',
                200,
                instantEmailTemplates,
                payload.errors
            );
        }

        return payload;
    }
);

export const useGetInstantTemplates = () => {
    const query = useQuery(Operations.TemplateResourceGetAllInstantEmailTemplates.actionCreator());

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
