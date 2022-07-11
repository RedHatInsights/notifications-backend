import { Breadcrumb, BreadcrumbItem, Button, PageSection, Spinner, Title, Toolbar, ToolbarContent, ToolbarItem } from '@patternfly/react-core';
import { PencilAltIcon, TrashIcon } from '@patternfly/react-icons';
import { TableComposable, Tbody, Td, Th, Thead, Tr } from '@patternfly/react-table';
import * as React from 'react';
import { useMemo } from 'react';
import { useParameterizedQuery } from 'react-fetching-library';
import { useParams } from 'react-router';

import { useUserPermissions } from '../app/PermissionContext';
import { EmailTemplateTable } from '../components/EmailTemplates/EmailTemplateTable';
import { CreateEditModal } from '../components/EventTypes/CreateEditModal';
import { DeleteModal } from '../components/EventTypes/DeleteModal';
import { BreadcrumbLinkItem } from '../components/Wrappers/BreadCrumbLinkItem';
import { linkTo } from '../Routes';
import { useCreateEventType } from '../services/EventTypes/CreateEventTypes';
import { useDeleteEventType } from '../services/EventTypes/DeleteEventType';
import { useApplicationTypes } from '../services/EventTypes/GetApplication';
import { getBundleAction  } from '../services/EventTypes/GetBundleAction';
import { EventType } from '../types/Notifications';
import { useEventTypes } from './ApplicationPage/useEventTypes';

type ApplicationPageParams = {
    applicationId: string;
}

export const ApplicationPage: React.FunctionComponent = () => {
    const { hasPermission, isAdmin } = useUserPermissions();
    const { applicationId } = useParams<ApplicationPageParams>();
    const eventTypesQuery = useEventTypes(applicationId);
    const applicationTypesQuery = useApplicationTypes(applicationId);
    const deleteEventTypeMutation = useDeleteEventType();
    const newEvent = useCreateEventType();

    const columns = [ 'Event Type', 'Name', 'Event Type Id' ];

    const [ eventTypes, setEventTypes ] = React.useState<Partial<EventType>>({});

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

    const bundle = React.useMemo(() => {
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
        setEventTypes({});
    };

    const handleSubmit = React.useCallback((eventType) => {
        setShowModal(false);
        const mutate = newEvent.mutate;
        mutate({
            id: eventType.id,
            displayName: eventType.displayName ?? '',
            name: eventType.name ?? '',
            description: eventType.description ?? '',
            applicationId

        })
        .then (eventTypesQuery.reload);

    }, [ applicationId, eventTypesQuery.reload, newEvent.mutate ]);

    const editEventType = (e: EventType) => {
        setShowModal(true);
        setIsEdit(true);
        setEventTypes(e);
    };

    const handleDelete = React.useCallback(async () => {
        setShowDeleteModal(false);
        const deleteEventType = deleteEventTypeMutation.mutate;
        const response = await deleteEventType(eventTypes.id);
        if (response.error) {
            return false;
        }

        return true;
    }, [ deleteEventTypeMutation.mutate, eventTypes.id ]);

    const deleteEventTypeModal = (e: EventType) => {
        setShowDeleteModal(true);
        setEventTypes(e);
    };

    const onClose = () => {
        setShowModal(false);
        eventTypesQuery.reload();
    };

    const onDeleteClose = () => {
        setShowDeleteModal(false);
        eventTypesQuery.reload();
    };

    if (eventTypesQuery.error) {
        return <span>Error while loading eventtypes: {eventTypesQuery.error.toString()}</span>;
    }

    return (
        <React.Fragment>
            <PageSection>
                <Title headingLevel="h1">
                    <Breadcrumb>
                        <BreadcrumbItem target='#'> Bundles </BreadcrumbItem>
                        <BreadcrumbLinkItem to={ linkTo.bundle(getBundleId ?? '') }>
                            { bundle ? bundle.display_name : <Spinner /> }
                        </BreadcrumbLinkItem>
                        <BreadcrumbItem to='#' isActive> { (applicationTypesQuery.loading
                        || applicationTypesQuery.payload?.status !== 200) ? <Spinner /> : applicationTypesQuery.payload.value.displayName }
                        </BreadcrumbItem>
                    </Breadcrumb></Title>
                <TableComposable
                    aria-label="Event types table"
                >
                    <Thead>
                        <Toolbar>
                            <ToolbarContent>
                                <ToolbarItem>
                                    <Button variant='primary' type='button'
                                        isDisabled={ !application || !hasPermission(application?.id) }
                                        onClick={ createEventType }> Create Event Type </Button>
                                </ToolbarItem>
                            </ToolbarContent>
                        </Toolbar>
                        <Tr>
                            {columns.map((column, columnIndex) => (
                                <Th key={ columnIndex }>{column}</Th>
                            ))}
                        </Tr>
                    </Thead>
                    <Tbody>{ (eventTypesQuery.data?.length === 0 ? 'There are no event types found for this application' : '') }</Tbody>
                    <Tbody>
                        { eventTypesQuery.data?.map((e: EventType) => (
                            <Tr key={ e.id }>
                                <Td>{ e.displayName }</Td>
                                <Td>{ e.name }</Td>
                                <Td>{ e.id }</Td>
                                <Td>
                                    <Button className='edit' type='button' variant='plain'
                                        isDisabled={ !application || !hasPermission(application?.id) }
                                        onClick={ () => editEventType(e) }> { <PencilAltIcon /> } </Button></Td>
                                <Td>
                                    <Button className='delete' type='button' variant='plain'
                                        isDisabled={ !application || !hasPermission(application?.id) }
                                        onClick={ () => deleteEventTypeModal(e) }>{ <TrashIcon /> } </Button></Td>
                            </Tr>
                        ))}
                    </Tbody>
                </TableComposable>
            </PageSection>
            { isAdmin && <EmailTemplateTable
                application={ application?.displayName ?? '' }
            /> }
            <CreateEditModal
                isEdit={ isEdit }
                initialEventType={ eventTypes }
                showModal={ showModal }
                applicationName={ application?.displayName }
                onClose={ onClose }
                onSubmit={ handleSubmit }
                isLoading={ eventTypesQuery.loading }
            />
            <DeleteModal
                onDelete={ handleDelete }
                isOpen={ showDeleteModal }
                onClose={ onDeleteClose }
                eventTypeName={ eventTypes.name }
                applicationName={ application?.displayName }
                bundleName={ bundle?.display_name }
            />
        </React.Fragment>

    );
};
