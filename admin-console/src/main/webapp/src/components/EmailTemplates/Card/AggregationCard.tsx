import { Button, Spinner } from '@patternfly/react-core';
import * as React from 'react';
import { AggregationCardRow } from '../../../types/Notifications';


interface AggregationEmailCellProps {
    application: AggregationCardRow
    onClick: React.MouseEventHandler<HTMLButtonElement> | undefined
}

export const AggregationTemplateCardField: React.FunctionComponent<AggregationEmailCellProps> = props => {

    if (props.application.aggregationEmail.isLoading) {
        return <Spinner />;
    }

    return <>
        { props.application.aggregationEmail.id ?
            <Button
                variant='link'
                type='button'
                onClick={ props.onClick }
            >
             Edit aggregation template
            </Button>
            :
            <Button
                variant='primary'
                type='button'
                onClick={ props.onClick }
            >
                    Create aggregation template
            </Button>
        }
    </>;
};
