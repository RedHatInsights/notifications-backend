import { useQuery } from 'react-fetching-library';

import { Operations } from '../generated/OpenapiInternal';

export const usePermissions = () => {
    return useQuery(Operations.InternalPermissionServiceGetPermissions.actionCreator());
};
