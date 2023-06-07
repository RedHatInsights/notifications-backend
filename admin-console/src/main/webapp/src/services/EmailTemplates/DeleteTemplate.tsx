import { useMutation } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';

export const deleteTemplateActionCreator = (templateId: string) => {
    return Operations.TemplateResourceDeleteTemplate.actionCreator({
        templateId
    });
};

export const useDeleteTemplate = () => useMutation<boolean>(deleteTemplateActionCreator);