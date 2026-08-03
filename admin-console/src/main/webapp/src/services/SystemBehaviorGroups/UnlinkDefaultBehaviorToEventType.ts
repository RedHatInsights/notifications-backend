import { useMutation } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';

export interface UnlinkDefaultBehaviorToEventTypeParams {
    behaviorGroupId: string;
    eventTypeId: string;
}

export const unlinkActionCreator = (params: UnlinkDefaultBehaviorToEventTypeParams) => {
    return Operations.InternalResourceUnlinkDefaultBehaviorToEventType.actionCreator({
        behaviorGroupId: params.behaviorGroupId,
        eventTypeId: params.eventTypeId
    });
};

export const useUnlinkDefaultBehaviorToEventType = () => {
    return useMutation(unlinkActionCreator);
};
