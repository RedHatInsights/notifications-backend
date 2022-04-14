import { ActionGroup, Button, Form, FormGroup,
    HelperText, HelperTextItem, Modal, ModalVariant, Select, SelectOption, SelectVariant, TextInput } from '@patternfly/react-core';
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
    const [ isOpen, setOpen ] = React.useState(false);
    const [ selected, setSelected ] = React.useState(false);

    const onSelect = () => {
        setSelected;
    };

    const toggle = React.useCallback(() => {
        setOpen(isOpen);
    }, [ isOpen ]);

    const actionOption = [
        <SelectOption key={ 0 } value='Send an email to Users: All' />,
        <SelectOption key={ 1 } value='Send an email to Users: Admins' />
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
                        <Select
                            variant={ SelectVariant.single }
                            aria-label="Select action"
                            placeholderText="Select an action"
                            onToggle={ toggle }
                            isOpen={ isOpen }
                            onSelect={ onSelect }
                            selections={ selected }
                            menuAppendTo={ document.body }>
                            { actionOption }
                        </Select>
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

