import { useQuery } from 'react-fetching-library';

import { Operations } from '../generated/OpenapiInternal';

export const useServerInfo = () => {
    return useQuery(Operations.InternalResourceGetServerInfo.actionCreator());
};
