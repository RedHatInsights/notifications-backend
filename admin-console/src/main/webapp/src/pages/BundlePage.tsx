import { Breadcrumb, BreadcrumbItem, Button, PageSection, Spinner,
    Title, Toolbar, ToolbarContent, ToolbarItem } from '@patternfly/react-core';
import { TableComposable, Tbody, Td, Th, Thead, Tr } from '@patternfly/react-table';
import * as React from 'react';
import { Link, useParams } from 'react-router-dom';

import { CreateEditApplicationModal } from '../components/CreateEditApplicationModal';
import { DeleteApplicationModal } from '../components/DeleteApplicationModal';
import { ListEventTypes } from '../components/ListEventTypes';
import { linkTo } from '../Routes';
import { useCreateApplication } from '../services/Applications/CreateApplication';
import { useDeleteApplication } from '../services/Applications/DeleteApplication';
import { useApplications } from '../services/Applications/GetApplicationById';
import { useBundleTypes } from '../services/Applications/GetBundleById';
import { Application } from '../types/Notifications';

type BundlePageParams = {
    bundleId: string;
}

export const BundlePage: React.FunctionComponent = () => {
    const { bundleId } = useParams<BundlePageParams>();
    const getBundles = useBundleTypes(bundleId);
    const getApplications = useApplications(bundleId);
    const newApplication = useCreateApplication();
    const deleteApplicationMutation = useDeleteApplication();

    const columns = [ 'Application', 'Name', 'Event Types', 'Application Id' ];

    const [ applications, setApplications ] = React.useState<Partial<Application>>({});
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
        setApplications({});
    };

    const editApplication = (rowId: number) => {
        if (getApplications.payload?.status === 200) {
            setShowModal(true);
            setIsEdit(true);
            setApplications(getApplications.payload.value[rowId]);

        }
    };

    const handleSubmit = React.useCallback((eventType) => {
        setShowModal(false);
        const mutate = newApplication.mutate;
        mutate({
            id: eventType.id,
            displayName: eventType.displayName ?? '',
            name: eventType.name ?? '',
            bundleId

        })
        .then (getApplications.query);

    }, [ bundleId, getApplications.query, newApplication.mutate ]);

    const onClose = () => {
        setShowModal(false);
        getApplications.query();
    };

    const handleDelete = React.useCallback(async () => {
        setShowDeleteModal(false);
        const deleteApplication = deleteApplicationMutation.mutate;
        const response = await deleteApplication(applications.id);
        if (response.error) {
            return false;
        }

        return true;
    }, [ applications.id, deleteApplicationMutation.mutate ]);

    const deleteApplicationModal = (rowId: number) => {
        if (getApplications.payload?.status === 200) {
            setShowModal(true);
            setIsEdit(true);
            setApplications(getApplications.payload.value[rowId]);

        }
    };

    const onDeleteClose = () => {
        setShowDeleteModal(false);
        getApplications.query();
    };

    const actions = [
        {
            title: 'Edit',
            onClick: editApplication
        },
        {
            isSeperator: true
        },
        {
            title: 'Delete',
            onClick: deleteApplicationModal
        }
    ];

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
                                    <CreateEditApplicationModal
                                        isEdit={ isEdit }
                                        bundleName={ bundle?.displayName }
                                        initialApplication = { applications }
                                        showModal={ showModal }
                                        applicationName={ applications.displayName }
                                        onClose={ onClose }
                                        onSubmit={ handleSubmit }
                                        isLoading={ getApplications.loading }

                                    />
                                    <React.Fragment>
                                        <DeleteApplicationModal
                                            onDelete={ handleDelete }
                                            isOpen={ showDeleteModal }
                                            onClose={ onDeleteClose }
                                            applicationName={ applications.displayName }
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
                                <Td key={ a.id }
                                    actions={ {
                                        items: actions,
                                        isSeperator: false
                                    } }>

                                </Td>
                            </Tr>
                        )}
                    </Tbody>
                </TableComposable>
            </PageSection>
        </React.Fragment>

    );
};

