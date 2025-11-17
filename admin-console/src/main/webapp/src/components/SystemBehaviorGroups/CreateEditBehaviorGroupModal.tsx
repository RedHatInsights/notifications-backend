import { Button, Form, FormGroup,
    FormSelect,
    FormSelectOption,
    HelperText,
    HelperTextItem,
    Modal, ModalBody, ModalFooter, ModalHeader, ModalVariant, TextInput } from '@patternfly/react-core';
import React from 'react';

import { BehaviorGroup } from '../../types/Notifications';

interface CreateEditModalProps {
    isEdit: boolean;
    showModal: boolean;
    initialSystemBehaviorGroup?: Partial<BehaviorGroup>;
    isLoading: boolean;
    onClose: () => void;
    onSubmit: (systemBehaviorGroup: Partial<BehaviorGroup>) => void;
}

export const CreateEditBehaviorGroupModal: React.FunctionComponent<CreateEditModalProps> = props => {

    const [ systemBehaviorGroup, setSystemBehaviorGroup ] = React.useState<Partial<BehaviorGroup>>(props.initialSystemBehaviorGroup ?? {});

    const actionOption = [
        <FormSelectOption key="choose action" isPlaceholder label="Choose an action" />,
        <FormSelectOption key="drawer-all" label="Send a drawer notification to Users: All" value="drawer-all" />,
        <FormSelectOption key="drawer-admin" label="Send a drawer notification to Users: Admins" value="drawer-admin" />,
        <FormSelectOption key="email-all" label="Send an email to Users: All" value="email-all" />,
        <FormSelectOption key="email-admin" label="Send an email to Users: Admins" value="email-admin" />
    ];

    const handleChange = (event: React.FormEvent<HTMLInputElement> | React.FormEvent<HTMLTextAreaElement>
        | React.FormEvent<HTMLSelectElement>, _value: string) => {
        const target = event.target as HTMLInputElement | HTMLSelectElement;
        setSystemBehaviorGroup(prev => ({ ...prev, [target.name]: target.value }));
    };

    const onSubmitLocal = React.useCallback(() => {
        props.onSubmit(systemBehaviorGroup);
    }, [ systemBehaviorGroup, props ]);

    React.useEffect(() => {
        setSystemBehaviorGroup(props.initialSystemBehaviorGroup ?? {});
    }, [ props.initialSystemBehaviorGroup ]);

    return (
        <Modal
            variant={ ModalVariant.medium }
            isOpen={ props.showModal }
            onClose={ props.onClose }
        >
            <ModalHeader title={ `${props.isEdit ? 'Update' : 'Create'} your System Behavior Group` } />
            <ModalBody>
                <Form isHorizontal>
                    <FormGroup label="Group Name" fieldId="displayName" isRequired>
                        <TextInput
                            type="text"
                            value={ systemBehaviorGroup.displayName }
                            onChange={ handleChange }
                            id="displayName"
                            name="displayName"
                        />
                        <HelperText>
                            <HelperTextItem>
                                Enter a name for your group
                            </HelperTextItem>
                        </HelperText>
                    </FormGroup>
                    <FormGroup label="Action" fieldId="actions" isRequired>
                        <FormSelect
                            id="actions"
                            name="actions"
                            value={ systemBehaviorGroup.actions }
                            open={ props.showModal }
                            onChange={ handleChange }
                        >
                            { actionOption }
                        </FormSelect>
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

