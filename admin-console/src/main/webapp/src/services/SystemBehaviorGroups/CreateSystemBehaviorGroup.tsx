import { useMutation } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';
import { UUID } from '../../types/Notifications';

export type CreateSystemBehaviorGroup = {
    bundleId: UUID;
    displayName: string;
    id?: UUID | undefined | null;
}

const actionCreator =  (params: CreateSystemBehaviorGroup) => {
    if (params.id === undefined) {
        return Operations.InternalResourceCreateDefaultBehaviorGroup.actionCreator({
            body: {
                bundle_id: params.bundleId,
                display_name: params.displayName,
                id: params.id
            }
        });
    }

    return Operations.InternalResourceUpdateDefaultBehaviorGroup.actionCreator({
        id: params.id ?? '',
        body: {
            bundle_id: params.bundleId,
            display_name: params.displayName,
            id: params.id
        }
    });
};

export const useCreateSystemBehaviorGroup = () => {
    return useMutation(actionCreator);
};
