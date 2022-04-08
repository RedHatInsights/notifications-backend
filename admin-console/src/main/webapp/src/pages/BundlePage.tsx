import { Breadcrumb, BreadcrumbItem, Button, PageSection, Spinner,
    Title, Toolbar, ToolbarContent, ToolbarItem } from '@patternfly/react-core';
import { PencilAltIcon, TrashIcon } from '@patternfly/react-icons';
import { TableComposable, Tbody, Td, Th, Thead, Tr } from '@patternfly/react-table';
import * as React from 'react';
import { Link, useParams } from 'react-router-dom';

import { useUserPermissions } from '../app/PermissionContext';
import { CreateEditApplicationModal } from '../components/CreateEditApplicationModal';
import { DeleteApplicationModal } from '../components/DeleteApplicationModal';
import { ListEventTypes } from '../components/ListEventTypes';
import { BehaviorGroupsTable } from '../components/SystemBehaviorGroups/BehaviorGroupTable';

import { linkTo } from '../Routes';
import { useCreateApplication } from '../services/Applications/CreateApplication';
import { useDeleteApplication } from '../services/Applications/DeleteApplication';
import { useApplications } from '../services/Applications/GetApplicationById';
import { useBundleTypes } from '../services/Applications/GetBundleById';
import { Application, RoleOwnedApplication } from '../types/Notifications';

type BundlePageParams = {
    bundleId: string;
}

export const BundlePage: React.FunctionComponent = () => {
    const { hasPermission, refresh, isAdmin } = useUserPermissions();
    const { bundleId } = useParams<BundlePageParams>();
    const getBundles = useBundleTypes(bundleId);
    const getApplications = useApplications(bundleId);
    const newApplication = useCreateApplication();
    const deleteApplicationMutation = useDeleteApplication();

    const columns = [ 'Application', 'Name', 'Event Types', 'Application Id' ];

    const [ application, setApplication ] = React.useState<Partial<Application>>({});
    const [ showModal, setShowModal ] = React.useState(false);
    const [ showDeleteModal, setShowDeleteModal ] = React.useState(false);
    const [ isEdit, setIsEdit ] = React.useState(false);

    const bundle = React.useMemo(() => {
        if (getBundles.payload?.status === 200) {
            return getBundles.payload.value;
        }

        return undefined;
    }, [ getBundles.payload?.status, getBundles.payload?.value ]);

    const createApplication = () => {
        setShowModal(true);
        setIsEdit(false);
        setApplication({});
    };

    const editApplication = (a: Application) => {
        setShowModal(true);
        setIsEdit(true);
        setApplication(a);

    };

    const handleSubmit = React.useCallback((application: Partial<RoleOwnedApplication>) => {
        setShowModal(false);
        const mutate = newApplication.mutate;
        mutate({
            id: application.id,
            displayName: application.displayName ?? '',
            name: application.name ?? '',
            bundleId,
            ownerRole: application.ownerRole
        })
        .then(r => {
            if (r.payload?.status === 200 && !isAdmin) {
                refresh();
            }

            return r;
        })
        .then(getApplications.query);

    }, [ bundleId, getApplications.query, newApplication.mutate, isAdmin, refresh ]);

    const onClose = () => {
        setShowModal(false);
        setApplication({});
        getApplications.query();
    };

    const handleDelete = React.useCallback(async () => {
        setShowDeleteModal(false);
        const deleteApplication = deleteApplicationMutation.mutate;
        const response = await deleteApplication(application.id);
        if (response.error) {
            return false;
        }

        return true;
    }, [ application.id, deleteApplicationMutation.mutate ]);

    const deleteApplicationModal = (a: Application) => {
        setShowDeleteModal(true);
        setApplication(a);
    };

    const onDeleteClose = () => {
        setShowDeleteModal(false);
        getApplications.query();
    };

    if (getApplications.loading) {
        return <Spinner />;
    }

    if (getApplications.payload?.status !== 200) {
        return <span>Error while loading applications: {getApplications.errorObject.toString()}</span>;
    }

    return (
        <React.Fragment>
            <PageSection>
                <Title headingLevel='h1'>
                    <Breadcrumb>
                        <BreadcrumbItem target='#'> Bundles </BreadcrumbItem>
                        <BreadcrumbItem target='#' >{ (getBundles.loading || getBundles.payload?.status !== 200)
                            ? <Spinner /> : getBundles.payload.value.displayName }
                        </BreadcrumbItem>
                    </Breadcrumb>
                </Title>
                <TableComposable aria-label="Applications table">
                    <Thead>
                        <Toolbar>
                            <ToolbarContent>
                                <ToolbarItem>
                                    <Button variant='primary' type='button' onClick={ createApplication }> Create Application </Button>
                                    {showModal && <CreateEditApplicationModal
                                        isEdit={ isEdit }
                                        bundleName={ bundle?.displayName }
                                        initialApplication={ application }
                                        showModal={ showModal }
                                        applicationName={ application.displayName }
                                        onClose={ onClose }
                                        onSubmit={ handleSubmit }
                                        isLoading={ getApplications.loading }

                                    />
                                    }
                                    <React.Fragment>
                                        <DeleteApplicationModal
                                            onDelete={ handleDelete }
                                            isOpen={ showDeleteModal }
                                            onClose={ onDeleteClose }
                                            applicationName={ application.displayName }
                                            bundleName={ bundle?.displayName }

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
                    <Tbody>
                        { getApplications.payload.value.map(a =>
                            <Tr key={ a.id }>
                                <Td>
                                    <Button variant="link" component={ (props: any) =>
                                        <Link { ...props } to={ linkTo.application(a.id) } /> }>{ a.displayName }</Button>
                                </Td>
                                <Td>{ a.name}</Td>
                                <Td>
                                    <ListEventTypes
                                        appId={ a.id }
                                    />
                                </Td>
                                <Td>{ a.id }</Td>
                                <Td>
                                    <Button className='edit' type='button' variant='plain'
                                        isDisabled={ !hasPermission(a.id) }
                                        onClick={ () => editApplication(a) }
                                    > { <PencilAltIcon /> } </Button></Td>
                                <Td>
                                    <Button className='delete' type='button' variant='plain'
                                        isDisabled={ !isAdmin }
                                        onClick={ () => deleteApplicationModal(a) }

                                    >{ <TrashIcon /> } </Button></Td>
                            </Tr>
                        )}
                    </Tbody>
                </TableComposable>
            </PageSection>
            <BehaviorGroupsTable />
        </React.Fragment>

    );
};

