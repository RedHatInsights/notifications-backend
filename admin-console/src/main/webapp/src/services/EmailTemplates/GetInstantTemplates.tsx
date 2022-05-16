import { useQuery } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';

export const useInstantEmailTemplates = () => {
    return useQuery(Operations.TemplateResourceGetAllInstantEmailTemplates.actionCreator());
};
