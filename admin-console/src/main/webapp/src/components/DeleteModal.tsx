import { ActionGroup, Button, Modal, ModalVariant, Spinner, TextInput } from '@patternfly/react-core';
import React from 'react';

import { Application, Bundle, EventType } from '../types/Notifications';

interface DeleteModalProps {
    eventType?: EventType;
    onDelete: (eventType: EventType) => Promise<boolean>;
    bundle?: Bundle;
    application?: Application;
    isOpen: boolean;
    onClose: (deleted: boolean) => void;

}
export const DeleteModal: React.FunctionComponent<DeleteModalProps> = (props) => {
    const [ errors, setErrors ] = React.useState(true);

    const onDelete = React.useCallback(() => {
        const eventType = props.eventType;
        const onDelete = props.onDelete;
        if (eventType) {
            return onDelete(eventType);
        }

        return false;

    }, [ props.eventType, props.onDelete ]);

    const handleDeleteChange = (value: string, event: React.FormEvent<HTMLInputElement>) => {
        const target = event.target as HTMLInputElement;
        if (target.value !== props.eventType?.name) {
            return setErrors(true);
        } else if (target.value === props.eventType?.name) {
            return setErrors(false);
        }
    };

    return (
        <Modal>
            <React.Fragment>
                <Modal variant={ ModalVariant.small } titleIconVariant="warning" isOpen={ props.isOpen }
                    onClose={ () => props.onClose }
                    title={ `Permanently delete ${ props.eventType?.name }` }>
                    { <b>{ props.eventType?.name }</b> } {`from  ${ props.bundle ? props.bundle.displayName :
                        <Spinner /> }/${ props.application?.displayName } will be deleted. 
                        If an application is currently sending this event, it will no longer be processed.`}
                    <br />
                    <br />
                        Type <b>{ props.eventType?.name }</b> to confirm:
                    <br />
                    <TextInput type='text' onChange={ handleDeleteChange } id='name' name="name" isRequired />
                    <br />
                    <br />
                    <ActionGroup>
                        <Button variant='danger' type='button' isDisabled = { errors }
                            onClick={ onDelete }>Delete</Button>
                        <Button variant='link' type='button' onClick={ () => props.isOpen }>Cancel</Button>
                    </ActionGroup>
                </Modal>
            </React.Fragment>
        </Modal>
    );
};
