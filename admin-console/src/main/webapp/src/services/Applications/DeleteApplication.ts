import { useMutation } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';

export const deleteApplicationActionCreator = (appId: string) => {
    return Operations.InternalServiceDeleteApplication.actionCreator({
        appId
    });
};

export const useDeleteApplication = () => useMutation<boolean>(deleteApplicationActionCreator);
