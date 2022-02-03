import { useMutation } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';

export type CreateApplication = {
    id?: string;
    displayName: string;
    bundleId: string;
    name: string;

}

const actionCreator =  (params: CreateApplication) => {
    if (params.id === undefined) {
        return Operations.InternalServiceCreateApplication.actionCreator({
            body: {
                id: params.id,
                bundle_id: params.bundleId,
                display_name: params.displayName,
                name: params.name

            }
        });
    }

    return Operations.InternalServiceUpdateApplication.actionCreator({
        appId: params.id,
        body: {
            id: params.id,
            bundle_id: params.bundleId,
            display_name: params.displayName,
            name: params.name

        }
    });
};

export const useCreateApplication = () => {
    return useMutation(actionCreator);
};
