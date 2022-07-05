import {
    ActionGroup,
    Button,
    Form,
    FormGroup,
    FormSelect,
    Modal,
    ModalVariant
} from '@patternfly/react-core';
import React from 'react';

interface InstantTemplateModalProps {
    isEdit: boolean;
    showModal: boolean;
    onClose: () => void;
    templates: string[] | undefined;
}

export const InstantTemplateModal: React.FunctionComponent<InstantTemplateModalProps> = (props) => {
    return (
        <React.Fragment>
            <Modal
                variant={ ModalVariant.medium }
                title={ `${ props.isEdit ? `Update` : 'Select'} your instant email templates` }
                isOpen={ props.showModal }
                onClose={ props.onClose }
            ><Form isHorizontal>
                    <FormGroup label='Subject template' fieldId='subject-template'>
                        <FormSelect
                            id="subject-template"
                            name="subject-template"
                            aria-label="Subject template"
                            isRequired
                        >
                            { props.templates }
                        </FormSelect>
                    </FormGroup>
                    <FormGroup label='Body template' fieldId='body-template'>
                        <FormSelect
                            id="body-template"
                            name="body-template"
                            aria-label="Body template"
                            isRequired
                        >
                            { props.templates }
                        </FormSelect>
                    </FormGroup>
                    <ActionGroup>
                        <Button variant='primary' type='submit'
                        >{ props.isEdit ? 'Update' : 'Submit' }</Button>
                        <Button variant='link' type='reset'
                            onClick={ props.onClose }>Cancel</Button>
                    </ActionGroup>
                </Form>
            </Modal>
        </React.Fragment>
    );
};

