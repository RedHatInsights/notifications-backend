import {
    Button
} from '@patternfly/react-core';
import React from 'react';

interface InstantTemplateButtonProps {
    hasTemplate: boolean;
    createInstantTemplateButton: React.ReactNode

}
export const InstantTemplateButton: React.FunctionComponent<InstantTemplateButtonProps> = (props) => {
    return (
        <React.Fragment>
            <Button
                variant={ props.hasTemplate ? 'link' : 'primary' }
                type='button'
            >
                {props.hasTemplate ? 'View instant template' : 'Create Instant template'}
                { props.createInstantTemplateButton }
            </Button>
        </React.Fragment>
    );
};

