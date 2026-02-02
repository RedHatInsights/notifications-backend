import {
    Button, Checkbox, Form, FormGroup, FormSelect, FormSelectOption,
    HelperText, HelperTextItem, Modal, ModalBody, ModalFooter, ModalHeader, ModalVariant, TextArea, TextInput
} from '@patternfly/react-core';
import React from 'react';

import { EventType } from '../../types/Notifications';
import { useGetSeverities } from '../../services/Notifications/GetSeverities';

interface CreateEditModalProps {
    isEdit: boolean;
    showModal: boolean;
    applicationName?: string;
    initialEventType?: Partial<EventType>;
    isLoading: boolean;
    onClose: () => void;
    onSubmit: (eventType: Partial<EventType>) => void;
}

export const CreateEditModal: React.FunctionComponent<CreateEditModalProps> = props => {

    const [ eventType, setEventType ] = React.useState<Partial<EventType>>(props.initialEventType ?? {});
    const { severities, loading: severitiesLoading } = useGetSeverities();

    const handleChange = (event: React.FormEvent<HTMLInputElement> | React.FormEvent<HTMLTextAreaElement>, _value: string) => {
        const target = event.target as HTMLInputElement;
        setEventType(prev => ({ ...prev, [target.name]: target.value }));
    };

    const handleAvailableSeveritiesChange = (event: React.FormEvent<HTMLSelectElement>, _value: string) => {
        const options = event.currentTarget.options;
        const selectedValues: string[] = [];
        for (let i = 0; i < options.length; i++) {
            if (options[i].selected) {
                selectedValues.push(options[i].value);
            }
        }
        setEventType(prev => ({ ...prev, availableSeverities: selectedValues }));
    };

    const onSubmitLocal = React.useCallback(() => {
        props.onSubmit(eventType);
    }, [ eventType, props ]);

    React.useEffect(() => {
        setEventType(props.initialEventType ?? {});
    }, [ props.initialEventType ]);

    return (
        <Modal
            variant={ ModalVariant.medium }
            isOpen={ props.showModal }
            onClose={ props.onClose }
        >
            <ModalHeader title={ `${props.isEdit ? 'Update' : 'Create'} Event Type for ${props.applicationName}` } />
            <ModalBody>
                <Form isHorizontal>
                    <FormGroup label="Name" fieldId="name" isRequired>
                        <TextInput
                            type="text"
                            value={ eventType.name }
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
                            value={ eventType.displayName }
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
                    <FormGroup label="Description" fieldId="description">
                        <TextArea
                            type="text"
                            value={ eventType.description }
                            onChange={ handleChange }
                            id="description"
                            name="description"
                        />
                        <HelperText>
                            <HelperTextItem>
                                Optional short description that appears in the UI to help admin decide how to notify users.
                            </HelperTextItem>
                        </HelperText>
                    </FormGroup>
                    <FormGroup label="Default Severity" fieldId="defaultSeverity">
                        <FormSelect
                            value={ eventType.defaultSeverity ?? '' }
                            onChange={ (event, value) => setEventType(prev => ({ ...prev, defaultSeverity: value })) }
                            id="defaultSeverity"
                            name="defaultSeverity"
                            isDisabled={ severitiesLoading }
                        >
                            <FormSelectOption key="empty" value="" label="Select a severity" />
                            { severities.map(severity => (
                                <FormSelectOption key={ severity } value={ severity } label={ severity } />
                            )) }
                        </FormSelect>
                        <HelperText>
                            <HelperTextItem>
                                The default severity level for this event type.
                            </HelperTextItem>
                        </HelperText>
                    </FormGroup>
                    <FormGroup label="Available Severities" fieldId="availableSeverities">
                        <FormSelect
                            value={ eventType.availableSeverities ?? [] }
                            onChange={ handleAvailableSeveritiesChange }
                            id="availableSeverities"
                            name="availableSeverities"
                            isDisabled={ severitiesLoading }
                            multiple
                        >
                            { severities.map(severity => (
                                <FormSelectOption
                                    key={ severity }
                                    value={ severity }
                                    label={ severity }
                                    selected={ eventType.availableSeverities?.includes(severity) ?? false }
                                />
                            )) }
                        </FormSelect>
                        <HelperText>
                            <HelperTextItem>
                                The available severity levels that can be used for this event type. Hold Ctrl/Cmd to select multiple.
                            </HelperTextItem>
                        </HelperText>
                    </FormGroup>
                    <FormGroup label="Subscribed by default?" fieldId="subscribedByDefault">
                        <Checkbox
                            id="subscribedByDefault"
                            name="subscribedByDefault"
                            isChecked={ eventType.subscribedByDefault }
                            onChange={ (_event, isChecked) => setEventType(prev => ({ ...prev, subscribedByDefault: isChecked })) }
                        />
                        <HelperText>
                            <HelperTextItem>
                                Should users be subscribed by default?
                            </HelperTextItem>
                        </HelperText>
                    </FormGroup>
                    <FormGroup label="Subscription locked?" fieldId="subscriptionLocked">
                        <Checkbox
                            id="subscriptionLocked"
                            name="subscriptionLocked"
                            isChecked={ eventType.subscriptionLocked }
                            onChange={ (_event, isChecked) => setEventType(prev => ({ ...prev, subscriptionLocked: isChecked })) }
                        />
                        <HelperText>
                            <HelperTextItem>
                                Should the subscription be locked, preventing the users from unsubscribing?
                            </HelperTextItem>
                        </HelperText>
                    </FormGroup>
                    <FormGroup label="Is visible?" fieldId="visible">
                        <Checkbox
                            id="visible"
                            name="visible"
                            isChecked={ eventType.visible }
                            onChange={ (_event, isChecked) => setEventType(prev => ({ ...prev, visible: isChecked })) }
                        />
                        <HelperText>
                            <HelperTextItem>
                                Should the event type be visible in the public UI?
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

