import { Button, Form, Modal, ModalBody, ModalFooter, ModalHeader, ModalVariant } from '@patternfly/react-core';
import React from 'react';

interface ViewTemplateModalProps {
    showModal: boolean;
    applicationName?: string | undefined;
    onClose: () => void;
}

export const ViewTemplateModal: React.FunctionComponent<ViewTemplateModalProps> = (props) => {

    return (
        <React.Fragment>
            <Modal
                variant={ ModalVariant.medium }
                isOpen={ props.showModal }
                onClose={ props.onClose }
            >
                <ModalHeader title={ ` Email Template for ${ props.applicationName }` } />
                <ModalBody>
                    <Form isHorizontal>
                        Viewing modal for existing templates
                    </Form>
                </ModalBody>
                <ModalFooter>
                    <Button variant='primary' type='reset'
                        onClick={ props.onClose }>Close</Button>
                </ModalFooter>
            </Modal>
        </React.Fragment>
    );
};

