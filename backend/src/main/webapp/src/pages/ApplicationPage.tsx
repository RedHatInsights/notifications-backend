import { List, ListItem, Spinner } from '@patternfly/react-core';
import * as React from 'react';
import { useParams } from 'react-router';

import { useEventTypes } from '../services/GetEventTypes';

type ApplicationPageParams = {
    applicationId: string;
}

export const ApplicationPage: React.FunctionComponent = () => {
    const { applicationId } = useParams<ApplicationPageParams>();
    const eventTypesQuery = useEventTypes(applicationId);

    if (eventTypesQuery.loading) {
        return <Spinner />;
    }

    if (eventTypesQuery.payload?.status !== 200) {
        return <span>Error while loading eventtypes: {eventTypesQuery.errorObject.toString() }</span>;
    }

    if (eventTypesQuery.payload.value.length === 0) {
        return <span>No event types found for this application</span>;
    }

    return (
        <List>
            { eventTypesQuery.payload.value.map(e => (
                <ListItem key={ e.id }>{ e.displayName }</ListItem>
            )) }
        </List>
    );
};
