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

    const toggle = React.useCallback(() => setOpen(prev => !prev), [ setOpen ]);

    const recipientOption = [
        { value: 'Users: All' },
        { value: 'Users: Admin' }
    ];

    const actionOption = [
        { value: 'Send an email' }
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
                            menuAppendTo={ document.body }>
                            {actionOption.map((option, index) => (
                                <SelectOption
                                    selected={ selected }
                                    key={ index }
                                    value={ option.value }
                                />
                            ))}
                        </Select>
                    </FormGroup>
                    <FormGroup label='Recipient' fieldId='actions' isRequired>
                        <Select
                            variant={ SelectVariant.single }
                            aria-label="Select recipiect"
                            placeholderText="Select a recipient"
                            onToggle={ toggle }
                            isOpen={ isOpen }
                            onSelect={ onSelect }
                            menuAppendTo={ document.body }>
                            {recipientOption.map((option, index) => (
                                <SelectOption
                                    selected={ selected }
                                    key={ index }
                                    value={ option.value }
                                />
                            ))}
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

