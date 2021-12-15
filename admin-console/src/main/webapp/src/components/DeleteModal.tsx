import { ActionGroup, Button, Modal, ModalVariant, Spinner, TextInput } from '@patternfly/react-core';
import React from 'react';

import { EventType } from '../types/Notifications';

interface DeleteModalProps {
    eventTypeName?: string;
    onDelete: (eventType: EventType) => Promise<boolean>;
    bundleName?: string;
    applicationName?: string;
    isOpen: boolean;
    onClose: () => void;

}
export const DeleteModal: React.FunctionComponent<DeleteModalProps> = (props) => {
    const [ errors, setErrors ] = React.useState(true);

    const onDelete = React.useCallback(() => {
        const eventType = props.eventTypeName;
        const onDelete = props.onDelete;
        if (eventType) {
            return onDelete;
        }

        return false;

    }, [ props.eventTypeName, props.onDelete ]);

    const handleDeleteChange = (value: string, event: React.FormEvent<HTMLInputElement>) => {
        const target = event.target as HTMLInputElement;
        if (target.value !== props.eventTypeName) {
            return setErrors(true);
        } else if (target.value === props.eventTypeName) {
            return setErrors(false);
        }
    };

    return (
        <React.Fragment>
            <Modal variant={ ModalVariant.small } titleIconVariant="warning" isOpen={ props.isOpen }
                onClose={ props.onClose }
                title={ `Permanently delete ${ props.eventTypeName }` }>
                { <b>{ props.eventTypeName }</b> } {`from  ${ props.bundleName ? props.bundleName :
                    <Spinner /> }/${ props.applicationName } will be deleted. 
                        If an application is currently sending this event, it will no longer be processed.`}
                <br />
                <br />
                        Type <b>{ props.eventTypeName }</b> to confirm:
                <br />
                <TextInput type='text' onChange={ handleDeleteChange } id='name' name="name" isRequired />
                <br />
                <br />
                <ActionGroup>
                    <Button variant='danger' type='button' isDisabled = { errors }
                        onClick={ onDelete }>Delete</Button>
                    <Button variant='link' type='button' onClick={ props.onClose }>Cancel</Button>
                </ActionGroup>
            </Modal>
        </React.Fragment>
    );
};
