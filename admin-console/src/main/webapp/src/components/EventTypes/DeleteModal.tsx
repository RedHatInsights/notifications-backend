import { Button, Modal, ModalBody, ModalFooter, ModalHeader, ModalVariant, Spinner, TextInput } from '@patternfly/react-core';
import React from 'react';

interface DeleteModalProps {
    eventTypeName?: string;
    onDelete: () => Promise<boolean>;
    bundleName?: string;
    applicationName?: string;
    isOpen: boolean;
    onClose: () => void;

}
export const DeleteModal: React.FunctionComponent<DeleteModalProps> = props => {
    const [ errors, setErrors ] = React.useState(true);

    const onDelete = React.useCallback(async() => {
        const response = await props.onDelete();
        if (response) {
            props.onClose();
        } else {
            alert('Could not delete event type, please try again.');
        }
    }, [ props ]);

    const handleDeleteChange = (event: React.FormEvent<HTMLInputElement>, _value: string) => {
        const target = event.target as HTMLInputElement;
        if (target.value !== props.eventTypeName) {
            return setErrors(true);
        } if (target.value === props.eventTypeName) {
            return setErrors(false);
        }
    };

    return (
        <Modal
            variant={ ModalVariant.small }
            isOpen={ props.isOpen }
            onClose={ props.onClose }
        >
            <ModalHeader title={ `Permanently delete ${props.eventTypeName}` } />
            <ModalBody>
                <b>{ props.eventTypeName }</b>
                { ' ' }
                { `from  ${props.applicationName}/${props.bundleName ? props.bundleName
                    : <Spinner />} will be deleted.
                            If an application is currently sending this event, it will no longer be processed.` }
                <br />
                <br />
                Type
                { ' ' }
                <b>{ props.eventTypeName }</b>
                { ' ' }
                to confirm:
                <br />
                <TextInput type="text" onChange={ handleDeleteChange } id="name" name="name" isRequired />
            </ModalBody>
            <ModalFooter>
                <Button
                    variant="danger"
                    type="button"
                    isDisabled={ errors }
                    onClick={ onDelete }
                >
                    Delete
                </Button>
                <Button variant="link" type="button" onClick={ props.onClose }>Cancel</Button>
            </ModalFooter>
        </Modal>
    );
};
