import {
    ActionGroup,
    Button,
    Form,
    FormGroup,
    FormSelect, FormSelectOption,
    HelperText,
    HelperTextItem,
    Modal,
    ModalVariant,
    TextInput
} from '@patternfly/react-core';
import React from 'react';

import { useUserPermissions } from '../../app/PermissionContext';
import { Application, RoleOwnedApplication } from '../../types/Notifications';

interface CreateEditApplicationModalProps {
    isEdit: boolean;
    showModal: boolean;
    applicationName?: string;
    bundleName?: string;
    initialApplication?: Partial<Application>;
    isLoading: boolean;
    onClose: () => void;
    onSubmit: (application: Partial<RoleOwnedApplication>) => void;
}

export const CreateEditApplicationModal: React.FunctionComponent<CreateEditApplicationModalProps> = (props) => {

    const permissions = useUserPermissions();
    const [ application, setApplication ] = React.useState<Partial<RoleOwnedApplication>>({
        ...props.initialApplication
    });

    const handleChange = (
        event: React.FormEvent<HTMLInputElement> | React.FormEvent<HTMLTextAreaElement> | React.FormEvent<HTMLSelectElement>,
        _value: string) => {
        const target = event.target as HTMLInputElement;
        setApplication(prev => ({ ...prev, [target.name]: target.value }));
    };

    const onSubmitLocal = React.useCallback(() => {
        props.onSubmit(application);
    }, [ application, props ]);

    React.useEffect(() => {
        if (application.ownerRole === undefined && permissions.roles.length > 0) {
            setApplication(prev => ({
                ...prev,
                ownerRole: permissions.roles[0]
            }));
        }
    }, [ permissions.roles, application.ownerRole ]);

    return (
        <React.Fragment>
            <Modal
                variant={ ModalVariant.medium }
                title={ `${ props.isEdit ? `Update ${ props.applicationName }` : 'Create Application'} for ${ props.bundleName }` }
                isOpen={ props.showModal }
                onClose={ props.onClose }
            ><Form isHorizontal>
                    <FormGroup label='Name' fieldId='name' isRequired>
                        <TextInput
                            type='text'
                            value={ application.name }
                            onChange={ handleChange }
                            id='name'
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
                        )}
                    </FormGroup>
                    <FormGroup label='Display name' fieldId='display-name' isRequired>
                        <TextInput
                            type='text'
                            value={ application.displayName }
                            onChange={ handleChange }
                            id='display-name'
                            name="displayName"
                        />
                        <HelperText>
                            <HelperTextItem>
                                This is the name you want to display on the UI
                            </HelperTextItem>
                        </HelperText>
                    </FormGroup>
                    { !props.isEdit && <FormGroup
                        fieldId="role-name"
                        label="Role admin"
                    >
                        { permissions.isAdmin ? (
                            <TextInput
                                type='text'
                                onChange={ handleChange }
                                value={ application.ownerRole }
                                id='owner-role'
                                name="ownerRole"
                            />
                        ) : (
                            <FormSelect
                                isRequired
                                value={ application.ownerRole }
                                onChange={ handleChange }
                                id='owner-role'
                                name="ownerRole"
                            >
                                { permissions.roles.map(r => <FormSelectOption key={ r } label={ r } value={ r } />) }
                            </FormSelect>
                        )}
                        <HelperText>
                            <HelperTextItem>
                                Rover group of users who will manage the application
                            </HelperTextItem>
                        </HelperText>
                    </FormGroup>
                    }
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

