import { useMutation } from 'react-fetching-library';

import { Operations, Schemas } from '../../generated/OpenapiInternal';
import { UUID } from '../../types/Notifications';

type UpdateBehaviorGroupActionsParams = {
    behaviorGroupId: UUID;
    body: Array<Schemas.RequestDefaultBehaviorGroupPropertyList>
}

const updateBehaviorGroupActionsActionCreator =  (params: UpdateBehaviorGroupActionsParams) => {
    return Operations.InternalResourceUpdateDefaultBehaviorGroupActions.actionCreator({
        behaviorGroupId: params.behaviorGroupId,
        body: params.body
    });
};

export const useUpdateBehaviorGroupActionsMutation = () => useMutation(updateBehaviorGroupActionsActionCreator);

