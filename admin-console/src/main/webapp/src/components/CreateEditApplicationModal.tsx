import { ActionGroup, Button, Form, FormGroup, HelperText, HelperTextItem, Modal, ModalVariant, TextInput } from '@patternfly/react-core';
import React from 'react';

import { Application } from '../types/Notifications';

interface CreateEditApplicationModalProps {
    isEdit: boolean;
    showModal: boolean;
    applicationName?: string;
    bundleName?: string;
    initialApplication?: Partial<Application>;
    isLoading: boolean;
    onClose: () => void;
    onSubmit: (application: Partial<Application>) => void;
}

export const CreateEditApplicationModal: React.FunctionComponent<CreateEditApplicationModalProps> = (props) => {

    const [ application, setApplication ] = React.useState<Partial<Application>>(props.initialApplication ?? {});

    const handleChange = (value: string, event: React.FormEvent<HTMLInputElement> | React.FormEvent<HTMLTextAreaElement>) => {
        const target = event.target as HTMLInputElement;
        setApplication(prev => ({ ...prev, [target.name]: target.value }));
    };

    const onSubmitLocal = React.useCallback(() => {
        props.onSubmit(application);
    }, [ application, props ]);

    React.useEffect(() => {
        setApplication(props.initialApplication ?? {});
    }, [ props.initialApplication ]);

    return (
        <React.Fragment>
            <Modal
                variant={ ModalVariant.medium }
                title={ `${ props.isEdit ? `Update ${ props.applicationName }` : 'Create'} Application for ${ props.bundleName }` }
                isOpen={ props.showModal }
                onClose={ props.onClose }
            ><Form isHorizontal>
                    <FormGroup label='Name' fieldId='name' isRequired
                        helperText={ props.isEdit ? <HelperText><HelperTextItem variant="warning" hasIcon>
                                                    If this field is modified it may affect exisiting behavior.
                        </HelperTextItem></HelperText> : 'This is a short name, only composed of a-z 0-9 and - characters.' }>
                        <TextInput
                            type='text'
                            value={ application.name }
                            onChange={ handleChange }
                            id='name'
                            name="name"
                        /></FormGroup>
                    <FormGroup label='Display name' fieldId='display-name' isRequired
                        helperText='This is the name you want to display on the UI'>
                        <TextInput
                            type='text'
                            value={ application.displayName }
                            onChange={ handleChange }
                            id='display-name'
                            name="displayName"
                        /></FormGroup>
                    <ActionGroup>
                        <Button variant='primary' type='submit'
                            isLoading={ props.isLoading } isDisabled={ props.isLoading }
                            onClick={ onSubmitLocal }>{ props.isEdit ? 'Update' : 'Submit' }</Button>
                        <Button variant='link' type='reset'
                            onClick={ props.onClose }>Cancel</Button>
                    </ActionGroup>
                </Form>
            </Modal>
        </React.Fragment>
    );
};

