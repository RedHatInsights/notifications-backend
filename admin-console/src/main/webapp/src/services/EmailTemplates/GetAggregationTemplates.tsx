import { useQuery } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';

export const useAggregatedEmailTemplates = () => {
    return useQuery(Operations.TemplateResourceGetAllAggregationEmailTemplates.actionCreator());
};
