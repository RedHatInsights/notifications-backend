import { useMutation } from 'react-fetching-library';

import { Operations, Schemas } from '../../generated/OpenapiInternal';
import { Template } from '../../types/Notifications';

export type CreateInstantTemplate = {
    body_template: Template;
    body_template_id: string;
    event_type: Schemas.EventType | null | undefined;
    event_type_id: string;
    id: string;
    subject_template: Template;
    subject_template_id: string;

}

const actionCreator =  (params: CreateInstantTemplate) => {
    if (params.id === undefined) {
        return Operations.TemplateResourceCreateInstantEmailTemplate.actionCreator({
            body: {
                event_type_id: params.event_type_id,
                subject_template_id: params.subject_template_id,
                body_template_id: params.body_template_id
            }
        });
    }

    return Operations.TemplateResourceUpdateInstantEmailTemplate.actionCreator({
        templateId: params.id,
        body: {
            event_type_id: params.event_type_id,
            subject_template_id: params.subject_template_id,
            body_template_id: params.body_template_id
        }
    });
};

export const useCreateInstantEmailTemplate = () => {
    return useMutation(actionCreator);
};
