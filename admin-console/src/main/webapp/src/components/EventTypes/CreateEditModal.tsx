import { ActionGroup, Button, Form, FormGroup, FormSelect, FormSelectOption,
    HelperText, HelperTextItem, Modal, ModalVariant, TextInput } from '@patternfly/react-core';
import React from 'react';

import { BehaviorGroup, EventType } from '../../types/Notifications';

interface CreateEditModalProps {
    isEdit: boolean;
    showModal: boolean;
    applicationName?: string;
    initialEventType?: Partial<EventType>;
    systemBehaviorGroup?: string;
    isLoading: boolean;
    onClose: () => void;
    onSubmit: (eventType: Partial<EventType>) => void;
}

export const CreateEditModal: React.FunctionComponent<CreateEditModalProps> = (props) => {

    const [ eventType, setEventType ] = React.useState<Partial<EventType>>(props.initialEventType ?? {});
    const [ systemBehaviorGroup, setSystemBehaviorGroup ] = React.useState<Partial<BehaviorGroup>>();

    const handleChange = (value: string, event: React.FormEvent<HTMLInputElement> | React.FormEvent<HTMLTextAreaElement>) => {
        const target = event.target as HTMLInputElement;
        setEventType(prev => ({ ...prev, [target.name]: target.value }));
    };

    const handleSelect = (value: string, event: React.FormEvent<HTMLSelectElement>) => {
        const target = event.target as HTMLSelectElement;
        setSystemBehaviorGroup(prev => ({ ...prev, [target.name]: target.value }));
    };

    const onSubmitLocal = React.useCallback(() => {
        props.onSubmit(eventType);
    }, [ eventType, props ]);

    React.useEffect(() => {
        setEventType(props.initialEventType ?? {});
    }, [ props.initialEventType ]);

    return (
        <React.Fragment>
            <Modal
                variant={ ModalVariant.medium }
                title={ `${ props.isEdit ? 'Update' : 'Create'} Event Type for ${ props.applicationName }` }
                isOpen={ props.showModal }
                onClose={ props.onClose }
            ><Form isHorizontal>
                    <FormGroup label='Name' fieldId='name' isRequired
                        helperText={ props.isEdit ? <HelperText><HelperTextItem variant="warning" hasIcon>
                                                    If this field is modified it may affect exisiting behavior.
                        </HelperTextItem></HelperText> : 'This is a short name, only composed of a-z 0-9 and - characters.' }>
                        <TextInput
                            type='text'
                            value={ eventType.name }
                            onChange={ handleChange }
                            id='name'
                            name="name"
                        /></FormGroup>
                    <FormGroup label='Display name' fieldId='display-name' isRequired
                        helperText='This is the name you want to display on the UI'>
                        <TextInput
                            type='text'
                            value={ eventType.displayName }
                            onChange={ handleChange }
                            id='display-name'
                            name="displayName"
                        /></FormGroup>
                    <FormGroup label='System Behavior Group' fieldId='system behavior group'
                        helperText='Behavior groups are made up of action/recipient pairings that allow you to configure which
                        notification actions different users will be able to receive. Once you have created
                        a behavior group, you can assign it to an event type.'>
                        <FormSelect
                            value={ systemBehaviorGroup?.displayName }
                            onChange={ handleSelect }
                            id='system behavior group'
                            name='system behavior group'
                        >
                            <FormSelectOption
                                key={ systemBehaviorGroup?.id }
                                value={ systemBehaviorGroup?.displayName }
                                label={ systemBehaviorGroup?.displayName  ?? '' } />
                        </FormSelect></FormGroup>
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

