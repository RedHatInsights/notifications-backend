import { useMutation } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';

export interface LinkDefaultBehaviorToEventTypeParams {
    behaviorGroupId: string;
    eventTypeId: string;
}

export const linkActionCreator = (params: LinkDefaultBehaviorToEventTypeParams) => {
    return Operations.InternalResourceLinkDefaultBehaviorToEventType.actionCreator({
        behaviorGroupId: params.behaviorGroupId,
        eventTypeId: params.eventTypeId
    });
};

export const useLinkDefaultBehaviorToEventType = () => {
    return useMutation(linkActionCreator);
};
