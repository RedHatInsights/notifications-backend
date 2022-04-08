import { useMutation } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';
import { BehaviorGroupAction, UUID } from '../../types/Notifications';

export type CreateSystemBehaviorGroup = {
    actions?: Array<BehaviorGroupAction> | undefined | null;
    bundleId: UUID;
    isDefault?: boolean | undefined | null;
    displayName: string;
    id?: UUID | undefined | null;
}

const actionCreator =  (params: CreateSystemBehaviorGroup) => {
    if (params.id === undefined) {
        return Operations.InternalServiceCreateDefaultBehaviorGroup.actionCreator({
            body: {
                actions: params.actions,
                bundle_id: params.bundleId,
                display_name: params.displayName,
                id: params.id
            }
        });
    }

    return Operations.InternalServiceCreateDefaultBehaviorGroup.actionCreator({
        body: {
            actions: params.actions,
            bundle_id: params.bundleId,
            display_name: params.displayName,
            id: params.id
        }
    });
};

export const useCreateSystemBehaviorGroup = () => {
    return useMutation(actionCreator);
};
