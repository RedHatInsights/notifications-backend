import {useQuery} from 'react-fetching-library';
import {Operations} from '../../generated/OpenapiInternal';

export const useGetTemplate = (templateId?: string) => {
    return useQuery(Operations.TemplateResourceGetTemplate.actionCreator({
        templateId: templateId ?? ''
    }), !!templateId);
};
