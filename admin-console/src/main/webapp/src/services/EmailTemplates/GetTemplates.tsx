import { validatedResponse, validationResponseTransformer } from 'openapi2typescript';
import { useMemo } from 'react';
import { useQuery } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';
import { Template } from '../../types/Notifications';

const validateResponse = validationResponseTransformer(
    (payload: Operations.TemplateResourceGetAllTemplates.Payload) => {
        if (payload.status === 200) {
            const emailTemplates: ReadonlyArray<Template> = payload.value.map(value => ({
                id: value.id,
                name: value.name,
                description: value.description,
                data: value.data
            }));

            return validatedResponse(
                'emailTemplates',
                200,
                emailTemplates,
                payload.errors
            );
        }

        return payload;
    }
);

export const useGetTemplates = () => {
    const query = useQuery(Operations.TemplateResourceGetAllTemplates.actionCreator());

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
