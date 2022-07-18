import { Button, Spinner } from '@patternfly/react-core';
import * as React from 'react';

import { EventTypeRow } from '../../../types/Notifications';

interface InstantEmailCellProps {
    eventType: EventTypeRow;
    onClick: React.MouseEventHandler<HTMLButtonElement> | undefined
}

export const InstantEmailCell: React.FunctionComponent<InstantEmailCellProps> = props => {

    if (props.eventType.instantEmail.isLoading) {
        return <Spinner />;
    }

    return <>
        { props.eventType.instantEmail.id ?
            <Button
                variant='link'
                type='button'
                onClick={ props.onClick }
            >
             Edit instant template
            </Button>
            :
            <Button
                variant='primary'
                type='button'
                onClick={ props.onClick }
            >
                    Create instant template
            </Button>
        }
    </>;
};
