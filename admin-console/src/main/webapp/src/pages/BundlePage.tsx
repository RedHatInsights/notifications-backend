import {
    Breadcrumb, BreadcrumbItem, Button, PageSection, PageSectionTypes, Skeleton, Spinner,
    Title, Toolbar, ToolbarContent, ToolbarItem
} from '@patternfly/react-core';
import { PencilAltIcon, TrashIcon } from '@patternfly/react-icons';
import { Table, Tbody, Td, Th, Thead, Tr } from '@patternfly/react-table';
import * as React from 'react';
import { Link, useParams } from 'react-router-dom';

import { useUserPermissions } from '../app/PermissionContext';
import { CreateEditApplicationModal } from '../components/Applications/CreateEditApplicationModal';
import { CreateEditBundleModal } from '../components/Bundles/CreateEditBundleModal';
import { DeleteApplicationModal } from '../components/Applications/DeleteApplicationModal';
import { ListEventTypes } from '../components/EventTypes/ListEventTypes';
import { linkTo } from '../Routes';
import { useCreateApplication } from '../services/Applications/CreateApplication';
import { useCreateBundle } from '../services/Bundles/CreateBundle';
import { useDeleteApplication } from '../services/Applications/DeleteApplication';
import { useApplications } from '../services/Applications/GetApplicationById';
import { useBundleTypes } from '../services/Applications/GetBundleById';
import { Application, RoleOwnedApplication } from '../types/Notifications';
import { BehaviorGroupsTable } from './BundlePage/BehaviorGroupTable';

type BundlePageParams = {
    bundleId: string;
}

export const BundlePage: React.FunctionComponent = () => {
    const { hasPermission, refresh, isAdmin } = useUserPermissions();
    const { bundleId } = useParams<BundlePageParams>();
    const getBundles = useBundleTypes(bundleId!);
    const getApplications = useApplications(bundleId!);
    const newApplication = useCreateApplication();
    const newBundle = useCreateBundle();
    const deleteApplicationMutation = useDeleteApplication();

    const columns = [ 'Application', 'Name', 'Event Types', 'Application Id' ];

    const [ application, setApplication ] = React.useState<Partial<Application>>({});
    const [ showModal, setShowModal ] = React.useState(false);
    const [ showDeleteModal, setShowDeleteModal ] = React.useState(false);
    const [ isEdit, setIsEdit ] = React.useState(false);
    const [ applicationEditError, setApplicationEditError ] = React.useState<string | undefined>(undefined);
    const [ applicationEditLoading, setApplicationEditLoading ] = React.useState(false);

    const [ showBundleEditModal, setShowBundleEditModal ] = React.useState(false);
    const [ bundleEditLoading, setBundleEditLoading ] = React.useState(false);
    const [ bundleEditError, setBundleEditError ] = React.useState<string | undefined>(undefined);

    const bundle = React.useMemo(() => {
        if (getBundles.payload?.status === 200) {
            return getBundles.payload.value;
        }

        return undefined;
    }, [ getBundles.payload?.status, getBundles.payload?.value ]);

    const editBundle = React.useCallback(() => {
        setBundleEditError(undefined);
        setShowBundleEditModal(true);
    }, []);

    const onBundleEditClose = React.useCallback(() => {
        setBundleEditError(undefined);
        setShowBundleEditModal(false);
    }, []);

    const handleBundleEditSubmit = React.useCallback((bundleForm: { id?: string; name?: string; displayName?: string }) => {
        setBundleEditLoading(true);
        setBundleEditError(undefined);
        newBundle.mutate({
            id: bundleId!,
            displayName: bundleForm.displayName ?? '',
            name: bundleForm.name ?? ''
        }).then((response) => {
            if (response.error) {
                setBundleEditError('Failed to update bundle. Please check the values and try again.');
            } else {
                setShowBundleEditModal(false);
                getBundles.query();
            }
        }).catch(() => {
            setBundleEditError('An unexpected error occurred while updating the bundle.');
        }).finally(() => {
            setBundleEditLoading(false);
        });
    }, [ bundleId, newBundle, getBundles ]);

    const createApplication = () => {
        setShowModal(true);
        setIsEdit(false);
        setApplication({});
        setApplicationEditError(undefined);
    };

    const editApplication = (a: Application) => {
        setShowModal(true);
        setIsEdit(true);
        setApplication(a);
        setApplicationEditError(undefined);
    };

    const handleSubmit = React.useCallback((application: Partial<RoleOwnedApplication>) => {
        setApplicationEditError(undefined);
        setApplicationEditLoading(true);
        const mutate = newApplication.mutate;
        mutate({
            id: application.id,
            displayName: application.displayName ?? '',
            name: application.name ?? '',
            bundleId: bundleId!,
            ownerRole: application.ownerRole
        })
            .then(r => {
                if (r.error) {
                    setApplicationEditError('Failed to save application. Please check the values and try again.');
                    return r;
                }

                setShowModal(false);
                if (r.payload?.status === 200 && !isAdmin) {
                    refresh();
                }

                getApplications.query();
                return r;
            })
            .catch(() => {
                setApplicationEditError('An unexpected error occurred while saving the application.');
            })
            .finally(() => {
                setApplicationEditLoading(false);
            });

    }, [ bundleId, getApplications.query, newApplication.mutate, isAdmin, refresh ]);

    const onClose = () => {
        setShowModal(false);
        setApplication({});
        setApplicationEditError(undefined);
        getApplications.query();
    };

    const handleDelete = React.useCallback(async() => {
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
        return <span>
            Error while loading applications:
            { getApplications.errorObject.toString() }
        </span>;
    }

    return (
        <>
            <PageSection type={ PageSectionTypes.breadcrumb }>
                <Breadcrumb>
                    <BreadcrumbItem> Bundles </BreadcrumbItem>
                    <BreadcrumbItem>
                        { (getBundles.loading || getBundles.payload?.status !== 200)
                            ? <Skeleton width="60px" /> : <>
                                { getBundles.payload.value.displayName }
                                { isAdmin && <Button
                                    className="edit"
                                    variant="plain"
                                    onClick={ editBundle }
                                    aria-label="Edit bundle"
                                    style={ { marginLeft: '4px' } }
                                >
                                    <PencilAltIcon />
                                </Button> }
                            </> }
                    </BreadcrumbItem>
                </Breadcrumb>
            </PageSection>
            <PageSection>
                <Title headingLevel="h3">
                    Applications
                </Title>
                <Table aria-label="Applications table">
                    <Thead>
                        <Toolbar>
                            <ToolbarContent>
                                <ToolbarItem>
                                    <Button variant="primary" type="button" onClick={ createApplication }> Create Application </Button>
                                    { showModal && <CreateEditApplicationModal
                                        isEdit={ isEdit }
                                        bundleName={ bundle?.displayName }
                                        initialApplication={ application }
                                        showModal={ showModal }
                                        applicationName={ application.displayName }
                                        onClose={ onClose }
                                        onSubmit={ handleSubmit }
                                        isLoading={ applicationEditLoading }
                                        error={ applicationEditError }
                                    /> }
                                    <>
                                        <DeleteApplicationModal
                                            onDelete={ handleDelete }
                                            isOpen={ showDeleteModal }
                                            onClose={ onDeleteClose }
                                            applicationName={ application.displayName }
                                            bundleName={ bundle?.displayName }

                                        />
                                    </>
                                </ToolbarItem>
                            </ToolbarContent>
                        </Toolbar>
                        <Tr>
                            { columns.map((column, columnIndex) => (
                                <Th key={ columnIndex }>{ column }</Th>
                            )) }
                        </Tr>
                    </Thead>
                    <Tbody>
                        { getApplications.payload.value.map(a => <Tr key={ a.id }>
                            <Td>
                                <Button
                                    variant="link"
                                    component={ (props: any) => <Link { ...props } to={ linkTo.application(a.id) } /> }
                                >
                                    { a.displayName }
                                </Button>
                            </Td>
                            <Td>{ a.name }</Td>
                            <Td>
                                <ListEventTypes
                                    appId={ a.id }
                                />
                            </Td>
                            <Td>{ a.id }</Td>
                            <Td>
                                <Button
                                    className="edit"
                                    type="button"
                                    variant="plain"
                                    isDisabled={ !hasPermission(a.id) }
                                    onClick={ () => editApplication(a) }
                                >
                                    { ' ' }
                                    <PencilAltIcon />
                                    { ' ' }
                                </Button>
                            </Td>
                            <Td>
                                <Button
                                    className="delete"
                                    type="button"
                                    variant="plain"
                                    isDisabled={ !isAdmin }
                                    onClick={ () => deleteApplicationModal(a) }

                                >
                                    <TrashIcon />
                                    { ' ' }
                                </Button>
                            </Td>
                        </Tr>) }
                    </Tbody>
                </Table>
            </PageSection>
            <BehaviorGroupsTable
                bundleId={ bundleId! }
                bundle={ bundle?.displayName }
            />
            { showBundleEditModal && <CreateEditBundleModal
                isEdit
                showModal={ showBundleEditModal }
                bundleName={ bundle?.displayName }
                initialBundle={ {
                    id: bundleId,
                    name: bundle?.name,
                    displayName: bundle?.displayName
                } }
                isLoading={ bundleEditLoading }
                error={ bundleEditError }
                onClose={ onBundleEditClose }
                onSubmit={ handleBundleEditSubmit }
            /> }
        </>

    );
};

