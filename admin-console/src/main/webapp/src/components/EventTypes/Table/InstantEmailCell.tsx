import { Spinner } from '@patternfly/react-core';
import * as React from 'react';

import { EventTypeRow } from '../../../types/Notifications';

interface InstantEmailCellProps {
    eventType: EventTypeRow;
}

export const InstantEmailCell: React.FunctionComponent<InstantEmailCellProps> = props => {
    const { eventType } = props;

    if (eventType.instantEmail.isLoading) {
        return <Spinner />;
    }

    return <>
        { eventType.instantEmail.id ? 'Template found' : 'No template' }
    </>;
};
