import { Button, Modal, ModalBody, ModalFooter, ModalHeader, ModalVariant } from '@patternfly/react-core';
import React from 'react';

import { Template } from '../../types/Notifications';

interface DeleteTemplateModalProps {
    onDelete: (template?: Template) => Promise<boolean>;
    templateName?: string;
    isOpen: boolean;
    onClose: () => void;

}
export const DeleteTemplateModal: React.FunctionComponent<DeleteTemplateModalProps> = (props) => {

    const onDelete = React.useCallback(async () => {
        const onDeleteImpl = props.onDelete;
        const response = await onDeleteImpl();
        if (response) {
            props.onClose();
        } else {
            alert('Could not delete template, please try again.');
        }
    }, [ props ]);

    return (
        <React.Fragment>
            <Modal variant={ ModalVariant.small } isOpen={ props.isOpen }
                onClose={ props.onClose }>
                <ModalHeader title={ `Permanently delete ${ props.templateName }` } />
                <ModalBody>
                    { <b>{ props.templateName }</b> } {' template will be deleted.'}
                </ModalBody>
                <ModalFooter>
                    <Button variant='danger' type='button'
                        onClick={ onDelete }>Delete</Button>
                    <Button variant='link' type='button' onClick={ props.onClose }>Cancel</Button>
                </ModalFooter>
            </Modal>
        </React.Fragment>
    );
};
