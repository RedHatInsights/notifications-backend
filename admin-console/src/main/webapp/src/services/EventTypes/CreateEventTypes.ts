import { useMutation } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';

export type CreateEventType = {
    id?: string;
    displayName: string;
    description: string;
    applicationId: string;
    name: string;
    fullyQualifiedName: string;
    subscribedByDefault: boolean;
    subscriptionLocked: boolean;
    visible: boolean;
    defaultSeverity?: string;
    availableSeverities?: string[];
}

const actionCreator =  (params: CreateEventType) => {
    const buildBody = () => {
        const body: any = {
            id: params.id,
            application_id: params.applicationId,
            description: params.description,
            display_name: params.displayName,
            name: params.name,
            fully_qualified_name: params.fullyQualifiedName,
            subscribed_by_default: params.subscribedByDefault,
            subscription_locked: params.subscriptionLocked,
            visible: params.visible
        };

        if (params.defaultSeverity && params.defaultSeverity !== '') {
            body.default_severity = params.defaultSeverity;
        }

        if (params.availableSeverities && params.availableSeverities.length > 0) {
            body.available_severities = params.availableSeverities;
        }

        return body;
    };

    if (params.id === undefined) {
        return Operations.InternalResourceCreateEventType.actionCreator({
            body: buildBody()
        });
    }

    return Operations.InternalResourceUpdateEventType.actionCreator({
        eventTypeId: params.id,
        body: buildBody()
    });
};

export const useCreateEventType = () => {
    return useMutation(actionCreator);
};
