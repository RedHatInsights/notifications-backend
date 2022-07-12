import { ActionGroup, Button, Form, Modal, ModalVariant } from '@patternfly/react-core';
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
                title={ ` Email Template for ${ props.applicationName }` }
                isOpen={ props.showModal }
                onClose={ props.onClose }
            ><Form isHorizontal>
                    Viewing modal for existing templates
                    <ActionGroup>
                        <Button variant='primary' type='reset'
                            onClick={ props.onClose }>Close</Button>
                    </ActionGroup>
                </Form>
            </Modal>
        </React.Fragment>
    );
};

