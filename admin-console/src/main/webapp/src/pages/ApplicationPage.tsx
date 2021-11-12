import { Breadcrumb, BreadcrumbItem, Button, Modal, ModalVariant, PageSection, Spinner, Title, Toolbar,
    ToolbarContent, ToolbarItem } from '@patternfly/react-core';
import {
    ActionGroup,
    Form,
    FormGroup, TextArea,
    TextInput  } from '@patternfly/react-core';
import { PencilAltIcon } from '@patternfly/react-icons';
import {
    TableComposable,
    Tbody,
    Td,  Th,   Thead,
    Tr } from '@patternfly/react-table';
import * as React from 'react';
import { useParams } from 'react-router';

import { useCreateEventType } from '../services/CreateEventTypes';
import { useApplicationTypes } from '../services/GetApplication';
import { useBundles } from '../services/GetBundles';
import { useEventTypes } from '../services/GetEventTypes';
import { EventType } from '../types/Notifications';

type ApplicationPageParams = {
    applicationId: string;
}

export const ApplicationPage: React.FunctionComponent = () => {
    const { applicationId } = useParams<ApplicationPageParams>();
    const eventTypesQuery = useEventTypes(applicationId);
    const applicationTypesQuery = useApplicationTypes(applicationId);
    const getBundle = useBundles();
    const columns = [ 'Event Type', 'Name', 'Description', 'Event Type Id' ];

    const newEvent = useCreateEventType();
    const [ eventType, setEventType ] = React.useState<Partial<EventType>>({});

    const [ showModal, setShowModal ] = React.useState(false);
    const [ isEdit, setIsEdit ] = React.useState(false);

    const createEventType = () => {
        setShowModal(true);
        setIsEdit(false);
        setEventType({});
    };

    const handleSubmit = React.useCallback(() => {
        setShowModal(false);
        const mutate = newEvent.mutate;
        mutate({
            id: eventType.id,
            displayName: eventType.displayName ?? '',
            name: eventType.name ?? '',
            description: eventType.description ?? '',
            applicationId: applicationId

        }).then(eventTypesQuery.query);

    }, [ newEvent.mutate, eventType, applicationId, eventTypesQuery.query ]);

    const handleChange = (value: string, event: React.FormEvent<HTMLInputElement>) => {
        const target = event.target as HTMLInputElement;
        setEventType(prev => ({ ...prev, [target.name]: target.value }));
    };

    const editEventType = (e: EventType) => {
        setShowModal(true);
        setIsEdit(true);
        setEventType(e);
    };

    if (eventTypesQuery.loading) {
        return <Spinner />;
    }

    if (eventTypesQuery.payload?.status !== 200) {
        return <span>Error while loading eventtypes: {eventTypesQuery.errorObject.toString()}</span>;
    }

    if (eventTypesQuery.payload.value.length === 0) {
        return <span>No event types found for this application</span>;
    }

    return (
        <React.Fragment>
            <PageSection>
                <Title headingLevel="h1"><Breadcrumb>
                    <BreadcrumbItem to='#'>
                        { getBundle.bundles.map(bundle => bundle.displayName) } </BreadcrumbItem>

                    <BreadcrumbItem to='#' isActive> { (applicationTypesQuery.loading || applicationTypesQuery.payload?.status !== 200) ?
                        <Spinner /> : applicationTypesQuery.payload.value.displayName } </BreadcrumbItem>
                </Breadcrumb></Title>
                <TableComposable
                    aria-label="Event types table"
                >
                    <Thead>
                        <Toolbar>
                            <ToolbarContent>
                                <ToolbarItem>
                                    <Button variant='primary' type='button'
                                        onClick={ createEventType }> Create Event Type </Button>
                                    <Modal
                                        variant={ ModalVariant.medium }
                                        title={ `Create Event Type for ${ (applicationTypesQuery.loading ||
                                            applicationTypesQuery.payload?.status !== 200) ?
                                            <Spinner /> : applicationTypesQuery.payload.value.displayName }` }
                                        isOpen={ showModal }
                                        onClose={ () => setShowModal(false) }
                                    ><Form isHorizontal>
                                            <FormGroup label='Name' fieldId='name' isRequired
                                                helperText='This is a short name, only composed of a-z 0-9 and - characters.'>
                                                <TextInput
                                                    type='text'
                                                    defaultValue={ eventType.name }
                                                    onChange={ handleChange }
                                                    id='name'
                                                    name="name"
                                                /></FormGroup>
                                            <FormGroup label='Display name' fieldId='display-name' isRequired
                                                helperText='This is the name you want to display on the UI'>
                                                <TextInput
                                                    type='text'
                                                    defaultValue={ eventType.displayName }
                                                    name="displayName"
                                                    onChange={ handleChange }
                                                    id='display-name' /></FormGroup>
                                            <FormGroup label='Description' fieldId='description'
                                                helperText='Optional short description that appears in the UI
                                                to help admin descide how to notify users.'>
                                                <TextArea
                                                    type='text'
                                                    defaultValue={ eventType.description }
                                                    onChange={ () => handleChange }
                                                    id='description'
                                                    name="description"/></FormGroup>
                                            <ActionGroup>
                                                <Button variant='primary' type='button'
                                                    isDisabled={ isEdit }
                                                    { ...(newEvent.loading || newEvent.payload?.status !== 200) ?
                                                        <Spinner /> : eventTypesQuery.payload.value }
                                                    onClick={ handleSubmit }>Submit</Button>
                                                <Button variant='primary' type='button' isDisabled={ !isEdit }
                                                    onClick={ handleSubmit }>Update</Button>
                                                <Button variant='link' type='reset'
                                                    onClick={ () => setShowModal(false) }>Cancel</Button>
                                            </ActionGroup>
                                        </Form>
                                        <>
                                        </>
                                    </Modal>
                                </ToolbarItem>
                            </ToolbarContent>
                        </Toolbar>
                        <Tr>
                            {columns.map((column, columnIndex) => (
                                <Th key={ columnIndex }>{column}</Th>
                            ))}
                        </Tr>
                    </Thead>
                    <Tbody>
                        { eventTypesQuery.payload.value.map(e => (
                            <Tr key={ e.id }>
                                <Td>{ e.displayName }</Td>
                                <Td>{ e.name }</Td>
                                <Td>{ e.description }</Td>
                                <Td>{ e.id }</Td>
                                <Td>
                                    <Button className='edit' type='button' variant='plain'
                                        onClick={ () => editEventType(e) }> { <PencilAltIcon /> } </Button></Td>
                            </Tr>
                        ))}
                    </Tbody>
                </TableComposable>
            </PageSection>
        </React.Fragment>

    );
};
