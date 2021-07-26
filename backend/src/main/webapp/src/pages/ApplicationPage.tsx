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

    if (eventTypesQuery.payload.length === 0) {
        return <span>No event types found for this application</span>;
    }

    return (
        <List>
            { eventTypesQuery.payload.map((e: any) => (
                <ListItem key={ e.id }>{ e.display_name }</ListItem>
            )) }
        </List>
    );
};
