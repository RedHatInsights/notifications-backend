import { useMutation } from 'react-fetching-library';

import { Operations } from '../generated/OpenapiInternal';

export const deleteEventType = (eventTypeId: string) => {
    return Operations.InternalServiceDeleteEventType.actionCreator({
        eventTypeId
    });
};

export const useDeleteEventTypeMutation = () => useMutation(deleteEventType);
