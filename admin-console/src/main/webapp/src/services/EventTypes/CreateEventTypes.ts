import { useMutation } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';

export type CreateEventType = {
    id?: string;
    displayName: string;
    description: string;
    applicationId: string;
    name: string;
    fullyQualifiedName: string;

}

const actionCreator =  (params: CreateEventType) => {
    if (params.id === undefined) {
        return Operations.InternalResourceCreateEventType.actionCreator({
            body: {
                id: params.id,
                application_id: params.applicationId,
                description: params.description,
                display_name: params.displayName,
                name: params.name,
                fully_qualified_name: params.fullyQualifiedName

            }
        });
    }

    return Operations.InternalResourceUpdateEventType.actionCreator({
        eventTypeId: params.id,
        body: {
            id: params.id,
            application_id: params.applicationId,
            description: params.description,
            display_name: params.displayName,
            name: params.name,
            fully_qualified_name: params.fullyQualifiedName

        }
    });
};

export const useCreateEventType = () => {
    return useMutation(actionCreator);
};
