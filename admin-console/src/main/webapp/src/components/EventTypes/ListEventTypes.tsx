import { Label, LabelGroup, Spinner } from '@patternfly/react-core';
import * as React from 'react';

import { useEventTypes } from '../../services/EventTypes/GetEventTypes';

type EventTypeListProps = {
    appId: string;
}

export const ListEventTypes: React.FunctionComponent<EventTypeListProps> = props => {
    const getEventTypes = useEventTypes(props.appId);

    if (getEventTypes.loading) {
        return <Spinner />;
    }

    if (getEventTypes.payload?.status !== 200) {
        return <span>
            Error while loading eventtypes:
            { getEventTypes.errorObject.toString() }
        </span>;
    }

    return (
        <>
            <LabelGroup>
                { getEventTypes.payload.value.length === 0 ? 'No event types' : '' }
            </LabelGroup>
            <LabelGroup>
                { getEventTypes.payload.value.map(e => (
                    <Label isCompact key={ e.id }>{ e.displayName }</Label>
                )) }
            </LabelGroup>
        </>
    );
};
