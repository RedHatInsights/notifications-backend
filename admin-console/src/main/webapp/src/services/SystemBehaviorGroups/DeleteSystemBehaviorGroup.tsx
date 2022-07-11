import { useMutation } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';
import { UUID } from '../../types/Notifications';

export const deleteBehaviorGroupActionCreator = (id: UUID) => {
    return Operations.InternalResourceDeleteDefaultBehaviorGroup.actionCreator({
        id
    });
};

export const useDeleteBehaviorGroup = () => useMutation<boolean>(deleteBehaviorGroupActionCreator);
