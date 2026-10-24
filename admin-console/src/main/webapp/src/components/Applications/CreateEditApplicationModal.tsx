import {
    Alert,
    AlertVariant,
    Button,
    Form,
    FormGroup,
    HelperText,
    HelperTextItem,
    Modal,
    ModalBody,
    ModalFooter,
    ModalHeader,
    ModalVariant,
    TextInput
} from '@patternfly/react-core';
import React from 'react';

import { Application } from '../../types/Notifications';

interface CreateEditApplicationModalProps {
    isEdit: boolean;
    showModal: boolean;
    applicationName?: string;
    bundleName?: string;
    initialApplication?: Partial<Application>;
    isLoading: boolean;
    error?: string;
    onClose: () => void;
    onSubmit: (application: Partial<Application>) => void;
}

export const CreateEditApplicationModal: React.FunctionComponent<CreateEditApplicationModalProps> = props => {

    const [ application, setApplication ] = React.useState<Partial<Application>>({
        ...props.initialApplication
    });

    const handleChange = (
        event: React.FormEvent<HTMLInputElement> | React.FormEvent<HTMLTextAreaElement> | React.FormEvent<HTMLSelectElement>,
        _value: string
    ) => {
        const target = event.target as HTMLInputElement;
        setApplication(prev => ({ ...prev, [target.name]: target.value }));
    };

    const onSubmitLocal = React.useCallback(() => {
        props.onSubmit(application);
    }, [ application, props ]);

    return (
        <Modal
            variant={ ModalVariant.medium }
            isOpen={ props.showModal }
            onClose={ props.onClose }
        >
            <ModalHeader title={ `${props.isEdit ? `Update ${props.applicationName}` : 'Create Application'} for ${props.bundleName}` } />
            <ModalBody>
                { props.error && (
                    <Alert variant={ AlertVariant.danger } title={ props.error } isInline />
                ) }
                <Form isHorizontal>
                    <FormGroup label="Name" fieldId="name" isRequired>
                        <TextInput
                            type="text"
                            value={ application.name }
                            onChange={ handleChange }
                            id="name"
                            name="name"
                        />
                        { props.isEdit ? (
                            <HelperText>
                                <HelperTextItem variant="warning">
                                    If this field is modified it may affect exisiting behavior.
                                </HelperTextItem>
                            </HelperText>
                        ) : (
                            <HelperText>
                                <HelperTextItem>
                                    This is a short name, only composed of a-z 0-9 and - characters.
                                </HelperTextItem>
                            </HelperText>
                        ) }
                    </FormGroup>
                    <FormGroup label="Display name" fieldId="display-name" isRequired>
                        <TextInput
                            type="text"
                            value={ application.displayName }
                            onChange={ handleChange }
                            id="display-name"
                            name="displayName"
                        />
                        <HelperText>
                            <HelperTextItem>
                                This is the name you want to display on the UI
                            </HelperTextItem>
                        </HelperText>
                    </FormGroup>
                </Form>
            </ModalBody>
            <ModalFooter>
                <Button
                    variant="primary"
                    type="submit"
                    isLoading={ props.isLoading }
                    isDisabled={ props.isLoading }
                    onClick={ onSubmitLocal }
                >
                    { props.isEdit ? 'Update' : 'Submit' }
                </Button>
                <Button
                    variant="link"
                    type="reset"
                    onClick={ props.onClose }
                >
                    Cancel
                </Button>
            </ModalFooter>
        </Modal>
    );
};

