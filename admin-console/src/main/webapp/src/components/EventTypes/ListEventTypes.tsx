import { Chip, ChipGroup, Spinner } from '@patternfly/react-core';
import * as React from 'react';

import { useEventTypes } from '../../services/EventTypes/GetEventTypes';

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
            <ChipGroup>
                { getEventTypes.payload.value.length === 0 ? 'No event types' : '' }
            </ChipGroup>
            <ChipGroup>
                { getEventTypes.payload.value.map(e => (
                    <Chip isReadOnly key={ e.id }>{ e.displayName}</Chip>
                ))}
            </ChipGroup>
        </React.Fragment>
    );
};
