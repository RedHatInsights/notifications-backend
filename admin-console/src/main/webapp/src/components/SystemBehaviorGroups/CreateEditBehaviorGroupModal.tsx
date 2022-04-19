import { ActionGroup, Button, Form, FormGroup,
    FormSelect,
    FormSelectOption,
    HelperText, HelperTextItem, Modal, ModalVariant, TextInput } from '@patternfly/react-core';
import React from 'react';

import { BehaviorGroup } from '../../types/Notifications';

interface CreateEditModalProps {
    isEdit: boolean;
    showModal: boolean;
    isOpen: boolean;
    initialSystemBehaviorGroup?: Partial<BehaviorGroup>;
    isLoading: boolean;
    onClose: () => void;
    onSubmit: (systemBehaviorGroup: Partial<BehaviorGroup>) => void;
}

export const CreateEditBehaviorGroupModal: React.FunctionComponent<CreateEditModalProps> = (props) => {

    const [ systemBehaviorGroup, setSystemBehaviorGroup ] = React.useState<Partial<BehaviorGroup>>(props.initialSystemBehaviorGroup ?? {});

    const handleSelect = (value: string, event: React.FormEvent<HTMLSelectElement>) => {
        const target = event.target as HTMLSelectElement;
        setSystemBehaviorGroup(prev => ({ ...prev, [target.name]: target.value }));
    };

    const actionOption = [
        <FormSelectOption key={ systemBehaviorGroup?.id } isPlaceholder label='Choose an action' />,
        <FormSelectOption key={ systemBehaviorGroup?.id } label='Send an email to Users: All' value={ systemBehaviorGroup?.id } />,
        <FormSelectOption key={ systemBehaviorGroup?.id } label='Send an email to Users: Admins' value={ systemBehaviorGroup?.id } />
    ];

    const handleChange = (value: string, event: React.FormEvent<HTMLInputElement> | React.FormEvent<HTMLTextAreaElement>) => {
        const target = event.target as HTMLInputElement;
        setSystemBehaviorGroup(prev => ({ ...prev, [target.name]: target.value }));
    };

    const onSubmitLocal = React.useCallback(() => {
        props.onSubmit(systemBehaviorGroup);
    }, [ systemBehaviorGroup, props ]);

    React.useEffect(() => {
        setSystemBehaviorGroup(props.initialSystemBehaviorGroup ?? {});
    }, [ props.initialSystemBehaviorGroup ]);

    return (
        <React.Fragment>
            <Modal
                variant={ ModalVariant.medium }
                title={ `${ props.isEdit ? 'Update' : 'Create'} your System Behavior Group` }
                isOpen={ props.showModal }
                onClose={ props.onClose }
            ><Form isHorizontal>
                    <FormGroup label='Group Name' fieldId='displayName' isRequired
                        helperText={ props.isEdit ? <HelperText><HelperTextItem variant="warning" hasIcon>
                                                    If this field is modified it may affect exisiting behavior.
                        </HelperTextItem></HelperText> : 'Enter a name for your group' }>
                        <TextInput
                            type='text'
                            value={ systemBehaviorGroup.displayName }
                            onChange={ handleChange }
                            id='displayName'
                            name="displayName"
                        /></FormGroup>
                    <FormGroup label='Action' fieldId='actions' isRequired>
                        <FormSelect
                            id='actions'
                            name='actions'
                            value={ systemBehaviorGroup?.id }
                            open={ props.isOpen }
                            onChange={ handleSelect }
                        >
                            { actionOption }
                        </FormSelect>
                    </FormGroup>
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

