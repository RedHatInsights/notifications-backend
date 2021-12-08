import { ActionGroup, Button, Modal, ModalVariant, Spinner, TextInput } from '@patternfly/react-core';
import React from 'react';

import { useDeleteEventType } from '../services/DeleteEventType';
import { EventType } from '../types/Notifications';

interface DeleteModalProps {

}
export const DeleteModal: React.FunctionComponent<DeleteModalProps> = props => {
    const deleteEventTypeMutation = useDeleteEventType();
    const [ showDeleteModal, setShowDeleteModal ] = React.useState(false);
    const [ errors, setErrors ] = React.useState(true);

    const handleDelete = React.useCallback(() => {
        setShowDeleteModal(false);
        const deleteEventType = deleteEventTypeMutation.mutate;
        deleteEventType(eventType.id).then (eventTypesQuery.query);

    }, [ deleteEventTypeMutation.mutate ]);

    const handleDeleteChange = (value: string, event: React.FormEvent<HTMLInputElement>) => {
        const target = event.target as HTMLInputElement;
        if (target.value !== eventType.name) {
            return setErrors(true);
        } else if (target.value === eventType.name) {
            return setErrors(false);
        }
    };

    return (
        <Modal>
            <React.Fragment>
                <Modal variant={ ModalVariant.small } titleIconVariant="warning" isOpen={ showDeleteModal }
                    onClose={ () => setShowDeleteModal(false) }
                    title={ `Permanently delete ${ eventType.name }` }>
                    { <b>{ eventType.name }</b> } {`from  ${ bundle ? bundle.display_name :
                        <Spinner /> }/${ (applicationTypesQuery.loading
                                             || applicationTypesQuery.payload?.status !== 200) ?
                        <Spinner /> : applicationTypesQuery.payload.value.displayName } will be deleted. 
                                                If an application is currently sending this event, it will no longer be processed.`}
                    <br />
                    <br />
                        Type <b>{ eventType.name }</b> to confirm:
                    <br />
                    <TextInput type='text' onChange={ handleDeleteChange } id='name' name="name" isRequired />
                    <br />
                    <br />
                    <ActionGroup>
                        <Button variant='danger' type='button' isDisabled = { errors }
                            onClick={ handleDelete }>Delete</Button>
                        <Button variant='link' type='button' onClick={ () => setShowDeleteModal(false) }>Cancel</Button>
                    </ActionGroup>
                </Modal>
            </React.Fragment>
        </Modal>
    );
};
