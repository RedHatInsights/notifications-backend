import {
    DescriptionList,
    DescriptionListDescription,
    DescriptionListGroup,
    DescriptionListTerm
} from '@patternfly/react-core';
import * as React from 'react';

import { EventType } from '../../../types/Notifications';

interface EventTypeExpandableRowProps {
    eventType: EventType;
}

export const EventTypeExpandableRow: React.FunctionComponent<EventTypeExpandableRowProps> = props => {
    return <DescriptionList isHorizontal>
        <DescriptionListGroup>
            <DescriptionListTerm>Event type id</DescriptionListTerm>
            <DescriptionListDescription>{ props.eventType.id }</DescriptionListDescription>
        </DescriptionListGroup>
        <DescriptionListGroup>
            <DescriptionListTerm>Description</DescriptionListTerm>
            <DescriptionListDescription>{ props.eventType.description }</DescriptionListDescription>
        </DescriptionListGroup>
    </DescriptionList>;
};
