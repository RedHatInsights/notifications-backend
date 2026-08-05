import { useMutation } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';

export interface BulkUpdateEventTypeLinksParams {
    behaviorGroupId: string;
    eventTypeIdsToLink: string[];
    eventTypeIdsToUnlink: string[];
}

export const bulkUpdateActionCreator = (params: BulkUpdateEventTypeLinksParams) => {
    return Operations.InternalResourceBulkUpdateDefaultBehaviorEventTypes.actionCreator({
        behaviorGroupId: params.behaviorGroupId,
        body: {
            event_type_ids_to_link: params.eventTypeIdsToLink,
            event_type_ids_to_unlink: params.eventTypeIdsToUnlink
        }
    });
};

export const useBulkUpdateEventTypeLinks = () => {
    return useMutation(bulkUpdateActionCreator);
};
