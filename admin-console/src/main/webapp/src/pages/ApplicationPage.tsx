import { Breadcrumb, BreadcrumbItem, PageSection, Spinner, Title } from '@patternfly/react-core';
import * as React from 'react';
import { useMemo } from 'react';
import { useParameterizedQuery } from 'react-fetching-library';
import { useParams } from 'react-router';

import { useUserPermissions } from '../app/PermissionContext';
import { EmailTemplateTable } from '../components/EmailTemplates/EmailTemplateTable';
import { InstantTemplateModal } from '../components/EmailTemplates/InstantEmailTemplateModal';
import { CreateEditModal } from '../components/EventTypes/CreateEditModal';
import { DeleteModal } from '../components/EventTypes/DeleteModal';
import { BreadcrumbLinkItem } from '../components/Wrappers/BreadCrumbLinkItem';
import { linkTo } from '../Routes';
import { useCreateInstantEmailTemplate } from '../services/EmailTemplates/CreateInstantTemplates';
import { useAggregationTemplates } from '../services/EmailTemplates/GetAggregationTemplates';
import { useGetTemplates } from '../services/EmailTemplates/GetTemplates';
import { useCreateEventType } from '../services/EventTypes/CreateEventTypes';
import { useDeleteEventType } from '../services/EventTypes/DeleteEventType';
import { useApplicationTypes } from '../services/EventTypes/GetApplication';
import { getBundleAction  } from '../services/EventTypes/GetBundleAction';
import { EventType, InstantTemplate } from '../types/Notifications';
import { useEventTypes } from './ApplicationPage/useEventTypes';
import { EventTypeTable } from '../components/EventTypes/EventTypeTable';
import { useSaveModal } from '../hooks/useSaveModal';

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

    const aggregationTemplates = useAggregationTemplates(applicationId);
    const getAllTemplates = useGetTemplates();
    const newInstantTemplate = useCreateInstantEmailTemplate();

    const [ eventTypes, setEventTypes ] = React.useState<Partial<EventType>>({});

    const [ instantTemplates, setInstantTemplates ] = React.useState<Partial<InstantTemplate>>({});
    const [ showModal, setShowModal ] = React.useState(false);
    const [ isEdit, setIsEdit ] = React.useState(false);
    const [ showDeleteModal, setShowDeleteModal ] = React.useState(false);

    const templateSaveModal = useSaveModal<InstantTemplate>();

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

    const aggregationEmailTemplates = useMemo(() => {
        if (aggregationTemplates.payload?.status === 200) {
            return aggregationTemplates.payload.value;
        }

        return undefined;
    }, [ aggregationTemplates.payload?.status, aggregationTemplates.payload?.value ]);

    const templates = useMemo(() => {
        if (getAllTemplates.payload?.status === 200) {
            return getAllTemplates.payload.value;
        }

        return undefined;
    }, [ getAllTemplates.payload?.status, getAllTemplates.payload?.value ]);

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

    const handleInstantTemplateSubmit = React.useCallback((instantTemplate: InstantTemplate) => {
        const close = templateSaveModal.close;
        close();
        const mutate = newInstantTemplate.mutate;
        mutate(instantTemplate);
    }, [ newInstantTemplate.mutate, templateSaveModal.close ]);

    const editEventType = (e: EventType) => {
        setShowModal(true);
        setIsEdit(true);
        setEventTypes(e);
    };

    const openInstantTemplateModal = (instantTemplate?: InstantTemplate) => {
        templateSaveModal.open(instantTemplate, !!instantTemplate);
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
                <EventTypeTable
                    hasPermissions={ hasPermission(applicationId) }
                    eventTypes={ eventTypesQuery.data }
                    onCreateEventType={ createEventType }
                    onEditEventType={ editEventType }
                    onDeleteEventTypeModal={ deleteEventTypeModal }
                    onCreateEditInstantTemplate={ openInstantTemplateModal }
                />
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
            <InstantTemplateModal
                isEdit={ templateSaveModal.isEdit }
                showModal={ templateSaveModal.isOpen }
                onClose={ templateSaveModal.close }
                templates={ templates }
                onSubmit={ handleInstantTemplateSubmit }
                initialInstantTemplate={ templateSaveModal.template }
            />
        </React.Fragment>
    );
};
