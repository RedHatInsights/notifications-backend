import { validatedResponse, validationResponseTransformer } from 'openapi2typescript';
import { useMemo } from 'react';
import { useQuery } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';
import { AggregationTemplate } from '../../types/Notifications';

const validateResponse = validationResponseTransformer(
    (payload: Operations.TemplateResourceGetAggregationEmailTemplatesByApplication.Payload) => {
        if (payload.status === 200) {
            const aggregationEmailTemplates: ReadonlyArray<AggregationTemplate> = payload.value.map(value => ({
                body_template: value.body_template,
                id: value.id,
                applicationId: value.application_id ?? '',
                subject_template: value.subject_template,
                application: value.application

            }));

            return validatedResponse(
                'AggregationEmailTemplates',
                200,
                aggregationEmailTemplates,
                payload.errors
            );
        }

        return payload;
    }
);

export const useGetAggregationsTemplates = (appId: string) => {
    const query = useQuery(Operations.TemplateResourceGetAggregationEmailTemplatesByApplication.actionCreator({
        appId
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
