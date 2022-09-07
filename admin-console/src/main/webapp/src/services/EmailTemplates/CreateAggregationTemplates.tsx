import { useMutation } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';

export type CreateAggregationTemplate = {
    id?: string;
    applicationId: string;
    subjectTemplateId: string;
    bodyTemplateId: string;
}

const actionCreator =  (params: CreateAggregationTemplate) => {
    if (params.id === undefined) {
        return Operations.TemplateResourceCreateAggregationEmailTemplate.actionCreator({
            body: {
                id: params.id,
                application_id: params.applicationId,
                subject_template_id: params.subjectTemplateId,
                body_template_id: params.bodyTemplateId,
            }
        });
    }

    return Operations.TemplateResourceUpdateAggregationEmailTemplate.actionCreator({
        templateId: params.id,
        body: {
            id: params.id,
            application_id: params.applicationId,
            subject_template_id: params.subjectTemplateId,
            body_template_id: params.bodyTemplateId,
        }
    });
};

export const useCreateAggregationEmailTemplate = () => {
    return useMutation(actionCreator);
};
