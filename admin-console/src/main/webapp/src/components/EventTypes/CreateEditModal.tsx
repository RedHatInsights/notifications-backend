import { ActionGroup, Button, Form, FormGroup, FormSelect, FormSelectOption,
    HelperText, HelperTextItem, Modal, ModalVariant, TextArea, TextInput } from '@patternfly/react-core';
import React, { useMemo } from 'react';

import { useSystemBehaviorGroups } from '../../services/SystemBehaviorGroups/GetBehaviorGroups';
import { BehaviorGroup, EventType } from '../../types/Notifications';

interface CreateEditModalProps {
    isEdit: boolean;
    showModal: boolean;
    applicationName?: string;
    initialEventType?: Partial<EventType>;
    systemBehaviorGroup?: readonly BehaviorGroup[]
    isLoading: boolean;
    onClose: () => void;
    onSubmit: (eventType: Partial<EventType>) => void;
}

export const CreateEditModal: React.FunctionComponent<CreateEditModalProps> = (props) => {

    const [ eventType, setEventType ] = React.useState<Partial<EventType>>(props.initialEventType ?? {});
    const [ systemBehaviorGroup, setSystemBehaviorGroup ] = React.useState<Partial<BehaviorGroup>>();
    const getBehaviorGroups = useSystemBehaviorGroups();

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

    const listSystemBehaviorGroups = useMemo(() => {
        if (getBehaviorGroups.payload?.status === 200) {
            return getBehaviorGroups.payload.value;
        }

        return undefined;
    }, [ getBehaviorGroups.payload?.status, getBehaviorGroups.payload?.value ]);

    const options = React.useMemo(() => {
        return listSystemBehaviorGroups?.map(b => (<FormSelectOption key={ b.id } label={ b.displayName } value={ b.id } />));
    }, [ listSystemBehaviorGroups ]);

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
                    <FormGroup label='System Behavior Group' fieldId='system behavior group'>
                        <FormSelect
                            value={ systemBehaviorGroup?.id }
                            onChange={ handleSelect }
                            id='system behavior group'
                            name='system behavior group'
                        >
                            { options }
                        </FormSelect></FormGroup>
                    <FormGroup label='Description' fieldId='description'
                        helperText='Optional short description that appears in the UI
                                                to help admin decide how to notify users.'>
                        <TextArea
                            type='text'
                            value={ eventType.description }
                            onChange={ handleChange }
                            id='description'
                            name="description"
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

