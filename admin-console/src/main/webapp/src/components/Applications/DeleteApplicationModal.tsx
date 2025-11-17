import { Button, Modal, ModalBody, ModalFooter, ModalHeader, ModalVariant, Spinner, TextInput } from '@patternfly/react-core';
import React from 'react';

import { EventType } from '../../types/Notifications';

interface DeleteApplicationModalProps {
    onDelete: (eventType?: EventType) => Promise<boolean>;
    bundleName?: string;
    applicationName?: string;
    isOpen: boolean;
    onClose: () => void;

}
export const DeleteApplicationModal: React.FunctionComponent<DeleteApplicationModalProps> = (props) => {
    const [ errors, setErrors ] = React.useState(true);

    const onDelete = React.useCallback(async () => {
        const onDeleteImpl = props.onDelete;
        const response = await onDeleteImpl();
        if (response) {
            props.onClose();
        } else {
            alert('Could not delete application, please try again.');
        }
    }, [ props ]);

    const handleDeleteChange = (event: React.FormEvent<HTMLInputElement>, _value: string) => {
        const target = event.target as HTMLInputElement;
        if (target.value !== props.applicationName) {
            return setErrors(true);
        } else if (target.value === props.applicationName) {
            return setErrors(false);
        }
    };

    return (
        <React.Fragment>
            <Modal variant={ ModalVariant.small } isOpen={ props.isOpen }
                onClose={ props.onClose }>
                <ModalHeader title={ `Permanently delete ${ props.applicationName }` } />
                <ModalBody>
                    { <b>{ props.applicationName }</b> } {`from ${ props.bundleName ? props.bundleName :
                        <Spinner /> } will be deleted.
                            By deleting this application all associated event types will be deleted and no longer processed. `}
                    <br />
                    <br />
                            Type <b>{ props.applicationName }</b> to confirm:
                    <br />
                    <TextInput type='text' onChange={ handleDeleteChange } id='name' name="name" isRequired />
                </ModalBody>
                <ModalFooter>
                    <Button variant='danger' type='button' isDisabled = { errors }
                        onClick={ onDelete }>Delete</Button>
                    <Button variant='link' type='button' onClick={ props.onClose }>Cancel</Button>
                </ModalFooter>
            </Modal>
        </React.Fragment>
    );
};
