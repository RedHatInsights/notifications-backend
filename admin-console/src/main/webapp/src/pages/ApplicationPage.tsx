import { Breadcrumb, BreadcrumbItem, Button, PageSection, Spinner, Title, Toolbar,
    ToolbarContent, ToolbarItem } from '@patternfly/react-core';
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

import { CreateEditModal } from '../components/CreateEditModal';
import { DeleteModal } from '../components/DeleteModal';
import { useCreateEventType } from '../services/CreateEventTypes';
import { useDeleteEventType } from '../services/DeleteEventType';
import { useApplicationTypes } from '../services/GetApplication';
import { getBundleAction  } from '../services/GetBundleAction';
import { useEventTypes } from '../services/GetEventTypes';
import { EventType } from '../types/Notifications';

type ApplicationPageParams = {
    applicationId: string;
}

export const ApplicationPage: React.FunctionComponent = () => {
    const { applicationId } = useParams<ApplicationPageParams>();
    const eventTypesQuery = useEventTypes(applicationId);
    const applicationTypesQuery = useApplicationTypes(applicationId);
    const deleteEventTypeMutation = useDeleteEventType();
    const newEvent = useCreateEventType();

    const columns = [ 'Event Type', 'Name', 'Description', 'Event Type Id' ];

    const [ eventType, setEventType ] = React.useState<Partial<EventType>>({});

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

    const application = useMemo(() => {
        if (applicationTypesQuery.payload?.status === 200) {
            return applicationTypesQuery.payload.value;
        }

        return undefined;
    }, [ applicationTypesQuery.payload?.status, applicationTypesQuery.payload?.value ]);

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
            applicationId

        })
        .then (eventTypesQuery.query);

    }, [ applicationId, eventType, eventTypesQuery.query, newEvent.mutate ]);

    const editEventType = (e: EventType) => {
        setShowModal(true);
        setIsEdit(true);
        setEventType(e);
    };

    const handleDelete = React.useCallback(async () => {
        setShowDeleteModal(false);
        const deleteEventType = deleteEventTypeMutation.mutate;
        const response = await deleteEventType(eventType.id);
        if (response.error) {
            return false;
        }

        return true;
    }, [ deleteEventTypeMutation.mutate, eventType.id ]);

    const deleteEventTypeModal = (e: EventType) => {
        setShowDeleteModal(true);
        setEventType(e);
    };

    const onClose = () => {
        setShowModal(false);
        eventTypesQuery.query();
    };

    const onDeleteClose = () => {
        setShowDeleteModal(false);
        eventTypesQuery.query();
    };

    if (eventTypesQuery.loading) {
        return <Spinner />;
    }

    if (eventTypesQuery.payload?.status !== 200) {
        return <span>Error while loading eventtypes: {eventTypesQuery.errorObject.toString()}</span>;
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
                                    <CreateEditModal
                                        isEdit={ isEdit }
                                        showModal={ showModal }
                                        applicationName={ application?.displayName }
                                        onClose={ onClose }
                                        onSubmit={ handleSubmit }
                                        eventTypeQuery={ eventTypesQuery }

                                    />
                                    <React.Fragment>
                                        <DeleteModal
                                            onDelete={ handleDelete }
                                            isOpen={ showDeleteModal }
                                            onClose={ onDeleteClose }
                                            eventTypeName={ eventType.name }
                                            applicationName={ application?.displayName }
                                            bundleName={ bundle?.display_name }

                                        />
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
                    <Tbody>{ (eventTypesQuery.payload.value.length === 0 ? 'There are no event types found for this application' : '') }</Tbody>
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

