import { List, ListItem, ListVariant, Spinner } from '@patternfly/react-core';
import * as React from 'react';

import { useEventTypes } from '../services/EventTypes/GetEventTypes';

type EventTypeListProps = {
    appId: string;
}

export const ListEventTypes: React.FunctionComponent<EventTypeListProps> = (props) => {
    const getEventTypes = useEventTypes(props.appId);

    if (getEventTypes.loading) {
        return <Spinner />;
    }

    if (getEventTypes.payload?.status !== 200) {
        return <span>Error while loading eventtypes: {getEventTypes.errorObject.toString()}</span>;
    }

    return (
        <React.Fragment>
            <List variant={ ListVariant.inline }>
                <ListItem>{ getEventTypes }</ListItem>
            </List>
        </React.Fragment>
    );
};
