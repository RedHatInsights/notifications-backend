import { useQuery } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';

export const useInstantEmailTemplates = (applicationId: string) => {
    return useQuery(Operations.TemplateResourceGetAllInstantEmailTemplates.actionCreator({
        applicationId
    }));
};

