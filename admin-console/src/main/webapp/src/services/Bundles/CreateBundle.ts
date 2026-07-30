import { useMutation } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';

export type CreateBundle = {
    id?: string;
    displayName: string;
    name: string;
}

export const actionCreator = (params: CreateBundle) => {
    if (params.id === undefined) {
        return Operations.InternalResourceCreateBundle.actionCreator({
            body: {
                display_name: params.displayName,
                name: params.name
            }
        });
    }

    return Operations.InternalResourceUpdateBundle.actionCreator({
        bundleId: params.id,
        body: {
            display_name: params.displayName,
            name: params.name
        }
    });
};

export const useCreateBundle = () => {
    return useMutation(actionCreator);
};
