import { ActionGroup, Button, Form, FormGroup,
    FormSelect,
    FormSelectOption,
    Modal, ModalVariant, TextInput } from '@patternfly/react-core';
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

export const CreateEditBehaviorGroupModal: React.FunctionComponent<CreateEditModalProps> = (props) => {

    const [ systemBehaviorGroup, setSystemBehaviorGroup ] = React.useState<Partial<BehaviorGroup>>(props.initialSystemBehaviorGroup ?? {});

    const actionOption = [
        <FormSelectOption key='choose action' isPlaceholder label='Choose an action' />,
        <FormSelectOption key='email-all' label='Send an email to Users: All' value='email-all' />,
        <FormSelectOption key='email-admin' label='Send an email to Users: Admins' value='email-admin' />,
        <FormSelectOption key='drawer-all' label='Send a drawer notification to Users: All' value='drawer-all' />,
        <FormSelectOption key='drawer-admin' label='Send a drawer notification to Users: Admins' value='drawer-admin' />
    ];

    const handleChange = (value: string, event: React.FormEvent<HTMLInputElement> | React.FormEvent<HTMLTextAreaElement>
        | React.FormEvent<HTMLSelectElement>) => {
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
        <React.Fragment>
            <Modal
                variant={ ModalVariant.medium }
                title={ `${ props.isEdit ? 'Update' : 'Create'} your System Behavior Group` }
                isOpen={ props.showModal }
                onClose={ props.onClose }
            ><Form isHorizontal>
                    <FormGroup label='Group Name' fieldId='displayName' isRequired
                        helperText='Enter a name for your group'>
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
                            value={ systemBehaviorGroup.actions }
                            open={ props.showModal }
                            onChange={ handleChange }
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

