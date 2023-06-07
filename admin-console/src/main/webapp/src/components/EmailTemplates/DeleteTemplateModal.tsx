import { ActionGroup, Button, Modal, ModalVariant } from '@patternfly/react-core';
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
            <Modal variant={ ModalVariant.small } titleIconVariant="warning" isOpen={ props.isOpen }
                onClose={ props.onClose }
                title={ `Permanently delete ${ props.templateName }` }>
                { <b>{ props.templateName }</b> } {' template will be deleted.'}
                <br />
                <br />
                <ActionGroup>
                    <Button variant='danger' type='button'
                        onClick={ onDelete }>Delete</Button>
                    <Button variant='link' type='button' onClick={ props.onClose }>Cancel</Button>
                </ActionGroup>
            </Modal>
        </React.Fragment>
    );
};
