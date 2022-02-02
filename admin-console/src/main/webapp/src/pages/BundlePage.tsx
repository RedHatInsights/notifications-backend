import { Breadcrumb, BreadcrumbItem, Button, PageSection, Spinner,
    Title, Toolbar, ToolbarContent, ToolbarItem } from '@patternfly/react-core';
import { PencilAltIcon, TrashIcon } from '@patternfly/react-icons';
import { TableComposable, Tbody, Td, Th, Thead, Tr } from '@patternfly/react-table';
import * as React from 'react';
import { Link, useParams } from 'react-router-dom';

import { ListEventTypes } from '../components/ListEventTypes';
import { linkTo } from '../Routes';
import { useApplications } from '../services/Applications/GetApplicationById';
import { useBundleTypes } from '../services/Applications/GetBundleById';

type BundlePageParams = {
    bundleId: string;
}

export const BundlePage: React.FunctionComponent = () => {
    const { bundleId } = useParams<BundlePageParams>();
    const getBundles = useBundleTypes(bundleId);
    const getApplications = useApplications(bundleId);

    const columns = [ 'Application', 'Application Id', 'Event Types' ];

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
                                    <Button variant='primary' type='button'> Create Application </Button>
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
                                <Link to={ linkTo.application(a.id) }>{ a.displayName }</Link>
                                <Td>{ a.id }</Td>
                                <Td>
                                    <ListEventTypes
                                        appId={ a.id }
                                    />
                                </Td>
                                <Td>
                                    <Button className='edit' type='button' variant='plain'
                                    > { <PencilAltIcon /> } </Button></Td>
                                <Td>
                                    <Button className='delete' type='button' variant='plain'
                                    >{ <TrashIcon /> } </Button></Td>
                            </Tr>
                        )}
                    </Tbody>
                </TableComposable>
            </PageSection>
        </React.Fragment>

    );
};

