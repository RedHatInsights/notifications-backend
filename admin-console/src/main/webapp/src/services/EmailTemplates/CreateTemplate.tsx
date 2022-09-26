import { useMutation } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';

export type CreateTemplate = {
    id?: string;
    data: string;
    name: string;
    description: string;
}

const actionCreator =  (params: CreateTemplate) => {
    if (params.id === undefined) {
        return Operations.TemplateResourceCreateTemplate.actionCreator({
            body: {
                id: params.id,
                data: params.data,
                name: params.name,
                description: params.description
            }
        });
    }

    return Operations.TemplateResourceUpdateTemplate.actionCreator({
        templateId: params.id,
        body: {
            id: params.id,
            data: params.data,
            name: params.name,
            description: params.description
        }
    });
};

export const useCreateTemplate = () => {
    return useMutation(actionCreator);
};
