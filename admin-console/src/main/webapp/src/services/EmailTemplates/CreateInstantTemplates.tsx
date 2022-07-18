import { useMutation } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';

export type CreateInstantTemplate = {
    id?: string;
    eventTypeId: string;
    subjectTemplateId: string;
    bodyTemplateId: string;
}

const actionCreator =  (params: CreateInstantTemplate) => {
    if (params.id === undefined) {
        return Operations.TemplateResourceCreateInstantEmailTemplate.actionCreator({
            body: {
                event_type_id: params.eventTypeId,
                subject_template_id: params.subjectTemplateId,
                body_template_id: params.bodyTemplateId
            }
        });
    }

    return Operations.TemplateResourceUpdateInstantEmailTemplate.actionCreator({
        templateId: params.id,
        body: {
            event_type_id: params.eventTypeId,
            subject_template_id: params.subjectTemplateId,
            body_template_id: params.bodyTemplateId
        }
    });
};

export const useCreateInstantEmailTemplate = () => {
    return useMutation(actionCreator);
};
