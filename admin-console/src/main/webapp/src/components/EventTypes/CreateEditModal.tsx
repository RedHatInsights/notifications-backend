import {
    Button, Checkbox, Form, FormGroup,
    HelperText, HelperTextItem, Modal, ModalBody, ModalFooter, ModalHeader, ModalVariant, TextArea, TextInput
} from '@patternfly/react-core';
import React from 'react';

import { EventType } from '../../types/Notifications';

interface CreateEditModalProps {
    isEdit: boolean;
    showModal: boolean;
    applicationName?: string;
    initialEventType?: Partial<EventType>;
    isLoading: boolean;
    onClose: () => void;
    onSubmit: (eventType: Partial<EventType>) => void;
}

export const CreateEditModal: React.FunctionComponent<CreateEditModalProps> = (props) => {

    const [ eventType, setEventType ] = React.useState<Partial<EventType>>(props.initialEventType ?? {});

    const handleChange = (event: React.FormEvent<HTMLInputElement> | React.FormEvent<HTMLTextAreaElement>, _value: string) => {
        const target = event.target as HTMLInputElement;
        setEventType(prev => ({ ...prev, [target.name]: target.value }));
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
                isOpen={ props.showModal }
                onClose={ props.onClose }
            >
                <ModalHeader title={ `${ props.isEdit ? 'Update' : 'Create'} Event Type for ${ props.applicationName }` } />
                <ModalBody>
                    <Form isHorizontal>
                        <FormGroup label='Name' fieldId='name' isRequired>
                            <TextInput
                                type='text'
                                value={ eventType.name }
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
                        <FormGroup
                            label='Event type name'
                            fieldId='fullyQualifiedName'
                            isRequired>
                            <TextInput
                                type='text'
                                value={ eventType.fullyQualifiedName }
                                onChange={ handleChange }
                                id='fullyQualifiedName'
                                name="fullyQualifiedName"
                            />
                            { props.isEdit ? (
                                <HelperText>
                                    <HelperTextItem variant="warning">
                                        If this field is modified it may affect existing behavior.
                                    </HelperTextItem>
                                </HelperText>
                            ) : (
                                <HelperText>
                                    <HelperTextItem>
                                        This is the fully qualified name for the event type. e.g. &apos;com.redhat.console.insights.policies.policy-triggered&apos;
                                    </HelperTextItem>
                                </HelperText>
                            )}
                        </FormGroup>
                        <FormGroup label='Display name' fieldId='display-name' isRequired>
                            <TextInput
                                type='text'
                                value={ eventType.displayName }
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
                        <FormGroup label='Description' fieldId='description'>
                            <TextArea
                                type='text'
                                value={ eventType.description }
                                onChange={ handleChange }
                                id='description'
                                name="description"
                            />
                            <HelperText>
                                <HelperTextItem>
                                    Optional short description that appears in the UI to help admin decide how to notify users.
                                </HelperTextItem>
                            </HelperText>
                        </FormGroup>
                        <FormGroup label='Subscribed by default?' fieldId='subscribedByDefault'>
                            <Checkbox
                                id='subscribedByDefault'
                                name='subscribedByDefault'
                                isChecked={ eventType.subscribedByDefault }
                                onChange={ (_event, isChecked) => setEventType(prev => ({...prev, subscribedByDefault: isChecked})) }
                            />
                            <HelperText>
                                <HelperTextItem>
                                    Should users be subscribed by default?
                                </HelperTextItem>
                            </HelperText>
                        </FormGroup>
                        <FormGroup label='Subscription locked?' fieldId='subscriptionLocked'>
                            <Checkbox
                                id='subscriptionLocked'
                                name='subscriptionLocked'
                                isChecked={ eventType.subscriptionLocked }
                                onChange={ (_event, isChecked) => setEventType(prev => ({...prev, subscriptionLocked: isChecked})) }
                            />
                            <HelperText>
                                <HelperTextItem>
                                    Should the subscription be locked, preventing the users from unsubscribing?
                                </HelperTextItem>
                            </HelperText>
                        </FormGroup>
                        <FormGroup label='Is visible?' fieldId='visible'>
                            <Checkbox
                                id='visible'
                                name='visible'
                                isChecked={ eventType.visible }
                                onChange={ (_event, isChecked) => setEventType(prev => ({...prev, visible: isChecked})) }
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
                    <Button variant='primary' type='submit'
                        isLoading={ props.isLoading } isDisabled={ props.isLoading }
                        onClick={ onSubmitLocal }>{ props.isEdit ? 'Update' : 'Submit' }</Button>
                    <Button variant='link' type='reset'
                        onClick={ props.onClose }>Cancel</Button>
                </ModalFooter>
            </Modal>
        </React.Fragment>
    );
};

