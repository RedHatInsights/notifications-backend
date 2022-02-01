import { Breadcrumb, BreadcrumbItem, Button, PageSection, Spinner, Title, Toolbar, ToolbarContent, ToolbarItem } from '@patternfly/react-core';
import { PencilAltIcon, TrashIcon } from '@patternfly/react-icons';
import { TableComposable, Tbody, Td, Th, Thead, Tr } from '@patternfly/react-table';
import * as React from 'react';
import { useMemo } from 'react';
import { Link, useParams } from 'react-router-dom';

import { linkTo } from '../Routes';
import { useBundles } from '../services/GetBundles';

type BundlePageParams = {
    applicationId: string;
}

export const BundlePage: React.FunctionComponent = () => {
    const getBundles = useBundles();
    const { applicationId } = useParams<BundlePageParams>();
    const eventTypePageUrl = React.useMemo(() => linkTo.application(applicationId), [ applicationId ]);

    const bundle = useMemo(() => {
        if (getBundles.isLoading || getBundles.bundles.map(b => b.id)) {
            return getBundles.bundles.map(b =>b.displayName);
        }

        return undefined;
    }, [ getBundles.bundles, getBundles.isLoading ]);

    const columns = [ 'Application', 'Application Id', 'Event Types' ];

    return (
        <React.Fragment>
            <PageSection>
                <Title headingLevel='h1'>
                    <Breadcrumb>
                        <BreadcrumbItem target='#'> Bundles </BreadcrumbItem>
                        <BreadcrumbItem target='#'> { bundle }
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
                        { getBundles.bundles.map(b => b.applications.map(a => (
                            <Tr key={ a.id }>
                                <Link to={ eventTypePageUrl }>{ a.displayName }</Link>
                                <Td>{ a.id }</Td>
                                <Td>eventType</Td>
                                <Td>
                                    <Button className='edit' type='button' variant='plain'
                                    > { <PencilAltIcon /> } </Button></Td>
                                <Td>
                                    <Button className='delete' type='button' variant='plain'
                                    >{ <TrashIcon /> } </Button></Td>
                            </Tr>
                        )))}
                    </Tbody>
                </TableComposable>
            </PageSection>
        </React.Fragment>

    );
};

