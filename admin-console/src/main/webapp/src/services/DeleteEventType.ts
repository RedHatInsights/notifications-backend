import { useMutation } from 'react-fetching-library';

import { Operations } from '../generated/OpenapiInternal';

export const deleteEventTypeActionCreator = (eventTypeId: string) => {
    return Operations.InternalServiceDeleteEventType.actionCreator({
        eventTypeId
    });
};

export const useDeleteEventType = () => useMutation<boolean>(deleteEventTypeActionCreator);
