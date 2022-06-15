import { validatedResponse, validationResponseTransformer } from 'openapi2typescript';
import { useMemo } from 'react';
import { useQuery } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';
import { AggregationTemplate } from '../../types/Notifications';

const validateResponse = validationResponseTransformer(
    (payload: Operations.TemplateResourceGetAggregationEmailTemplatesByApplication.Payload) => {
        if (payload.status === 200) {
            const aggregationTemplates: ReadonlyArray<AggregationTemplate> = payload.value.map(value => ({
                id: value.id ?? '',
                application: value.application,
                applicationId: value.application_id,
                body_template: value.body_template,
                body_template_id: value.body_template_id,
                subject_template: value.subject_template,
                subject_template_id: value.subject_template_id
            }));

            return validatedResponse(
                'AggregationEmailTemplates',
                200,
                aggregationTemplates,
                payload.errors
            );
        }

        return payload;
    }
);

export const useAggregationTemplates = (applicationId: string) => {
    const query = useQuery(Operations.TemplateResourceGetAggregationEmailTemplatesByApplication.actionCreator({
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

