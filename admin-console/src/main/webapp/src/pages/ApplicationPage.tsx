import { Breadcrumb, BreadcrumbItem, Button, Modal, ModalVariant, PageSection, Spinner, Title, Toolbar,
    ToolbarContent, ToolbarItem } from '@patternfly/react-core';
import {
    ActionGroup,
    Form,
    FormGroup, TextArea,
    TextInput  } from '@patternfly/react-core';
import { PencilAltIcon, TrashIcon } from '@patternfly/react-icons';
import {
    TableComposable,
    Tbody,
    Td,  Th,   Thead,
    Tr } from '@patternfly/react-table';
import * as React from 'react';
import { useMemo } from 'react';
import { useParameterizedQuery } from 'react-fetching-library';
import { useParams } from 'react-router';

import { useCreateEventType } from '../services/CreateEventTypes';
import { useDeleteEventType } from '../services/DeleteEventType';
import { useApplicationTypes } from '../services/GetApplication';
import { getBundleAction  } from '../services/GetBundleAction';
import { useEventTypes } from '../services/GetEventTypes';
import { EventType } from '../types/Notifications';

type ApplicationPageParams = {
    applicationId: string;
    eventTypeId: string;
}

export const ApplicationPage: React.FunctionComponent = () => {
    const { applicationId } = useParams<ApplicationPageParams>();
    const eventTypesQuery = useEventTypes(applicationId);
    const applicationTypesQuery = useApplicationTypes(applicationId);
    const deleteEventTypeMutation = useDeleteEventType();
    const columns = [ 'Event Type', 'Name', 'Description', 'Event Type Id' ];

    const newEvent = useCreateEventType();
    const [ event, setEvent ] = React.useState<Partial<EventType>>({});

    const [ showModal, setShowModal ] = React.useState(false);
    const [ isEdit, setIsEdit ] = React.useState(false);

    const [ showDeleteModal, setShowDeleteModal ] = React.useState(false);

    const getBundleId = React.useMemo(() => {
        if (applicationTypesQuery.payload?.type === 'Application') {
            return applicationTypesQuery.payload.value.bundleId;
        }

        return undefined;
    }, [ applicationTypesQuery.payload ]);

    const bundleNameQuery = useParameterizedQuery(getBundleAction);

    React.useEffect(() => {
        const query = bundleNameQuery.query;
        if (getBundleId) {
            query(getBundleId);
        }
    }, [ getBundleId, bundleNameQuery.query ]);

    const bundle = useMemo(() => {
        if (bundleNameQuery.payload?.status === 200) {
            return bundleNameQuery.payload.value;
        }

        return undefined;
    }, [ bundleNameQuery.payload?.status, bundleNameQuery.payload?.value ]);

    const createEventType = () => {
        setShowModal(true);
        setIsEdit(false);
        setEvent({});
    };

    const handleSubmit = () => {
        setShowModal(false);
        const mutate = newEvent.mutate;
        mutate({
            id: event.id ?? '',
            displayName: event.displayName ?? '',
            name: event.name ?? '',
            description: event.description ?? '',
            applicationId

        })
        .then (eventTypesQuery.query);

    };

    const editEventType = (e: EventType) => {
        setShowModal(true);
        setIsEdit(true);
        setEvent(e);
    };

    const handleDelete = React.useCallback(() => {
        setShowDeleteModal(false);
        const deleteEventType = deleteEventTypeMutation.mutate;
        deleteEventType(event.id).then (eventTypesQuery.query);

    }, [ deleteEventTypeMutation.mutate, event.id, eventTypesQuery.query ]);

    const deleteEventTypeModal = (e: EventType) => {
        setShowDeleteModal(true);
        setEvent(e);
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
                <Title headingLevel="h1">
                    <Breadcrumb>
                        <BreadcrumbItem target='#'>{ bundle ? bundle.display_name : <Spinner /> }
                        </BreadcrumbItem>

                        <BreadcrumbItem target='#'> { (applicationTypesQuery.loading || applicationTypesQuery.payload?.status !== 200) ?
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
                                        title={ `${ isEdit ? 'Update' : 'Create'} Event Type for ${ (applicationTypesQuery.loading ||
                                            applicationTypesQuery.payload?.status !== 200) ?
                                            <Spinner /> : applicationTypesQuery.payload.value.displayName }` }
                                        isOpen={ showModal }
                                        onClose={ () => setShowModal(false) }
                                    ><Form isHorizontal>
                                            <FormGroup label='Name' fieldId='name' isRequired
                                                helperText='This is a short name, only composed of a-z 0-9 and - characters.'>
                                                <TextInput
                                                    type='text'
                                                    value={ event.name }
                                                    onChange={ () => setEvent }
                                                    id='name'
                                                /></FormGroup>
                                            <FormGroup label='Display name' fieldId='display-name' isRequired
                                                helperText='This is the name you want to display on the UI'>
                                                <TextInput
                                                    type='text'
                                                    value={ event.displayName }
                                                    onChange={ () => setEvent }
                                                    id='display-name' /></FormGroup>
                                            <FormGroup label='Description' fieldId='description'
                                                helperText='Optional short description that appears in the UI
                                                to help admin descide how to notify users.'>
                                                <TextArea
                                                    type='text'
                                                    value={ event.description }
                                                    onChange={ () => setEvent }
                                                    id='description' /></FormGroup>
                                            <ActionGroup>
                                                <Button variant='primary' type='submit'
                                                    isDisabled={ isEdit }
                                                    { ...(newEvent.loading || newEvent.payload?.status !== 200) ?
                                                        <Spinner /> : eventTypesQuery.payload.value }
                                                    onClick={ handleSubmit }>Submit</Button>
                                                <Button variant='primary' type='submit' isDisabled={ !isEdit }
                                                    onClick={ handleSubmit }>Update</Button>
                                                <Button variant='link' type='reset'
                                                    onClick={ () => setShowModal(false) }>Cancel</Button>
                                            </ActionGroup>
                                        </Form>
                                        <>
                                        </>
                                    </Modal>
                                    <React.Fragment>
                                        <Modal variant={ ModalVariant.small } titleIconVariant="warning" isOpen={ showDeleteModal }
                                            onClose={ () => setShowDeleteModal(false) }
                                            title={ `Permanetly delete ${ event.name }` }>
                                            { <b>{ event.name }</b> } {`from  ${ bundle ? bundle.display_name :
                                                <Spinner /> }/${ (applicationTypesQuery.loading
                                             || applicationTypesQuery.payload?.status !== 200) ?
                                                <Spinner /> : applicationTypesQuery.payload.value.displayName } will be deleted. 
                                                If an application is currently sending this event, it will no longer be processed.`}
                                            <br />
                                            <br />
                                            Type <b>{ event.name }</b> to confirm:
                                            <br />
                                            <TextInput type='text' id='name' isRequired />
                                            <br />
                                            <br />
                                            <ActionGroup>
                                                <Button variant='danger' type='button' onClick={ handleDelete }>Delete</Button>
                                                <Button variant='link' type='button' onClick={ () => setShowDeleteModal(false) }>Cancel</Button>
                                            </ActionGroup>
                                        </Modal>
                                    </React.Fragment>
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
                                <Td>
                                    <Button className='delete' type='button' variant='plain'
                                        onClick={ () => deleteEventTypeModal(e) }>{ <TrashIcon /> } </Button></Td>
                            </Tr>
                        ))}
                    </Tbody>
                </TableComposable>
            </PageSection>
        </React.Fragment>

    );
};

