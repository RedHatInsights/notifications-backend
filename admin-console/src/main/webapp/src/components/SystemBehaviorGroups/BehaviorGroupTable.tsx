import { Breadcrumb, BreadcrumbItem, Button, PageSection, Spinner,
    Title, Toolbar, ToolbarContent, ToolbarItem } from '@patternfly/react-core';
import { PencilAltIcon, TrashIcon } from '@patternfly/react-icons';
import { TableComposable, Tbody, Td, Th, Thead, Tr } from '@patternfly/react-table';
import * as React from 'react';
import { useParams } from 'react-router';

import { useBundleTypes } from '../../services/Applications/GetBundleById';
import { useSystemBehaviorGroups } from '../../services/SystemBehaviorGroups/GetBehaviorGroups';

type BundlePageParams = {
    bundleId: string;
}

export const BehaviorGroupsTable: React.FunctionComponent = () => {
    const getBehaviorGroups = useSystemBehaviorGroups();
    const { bundleId } = useParams<BundlePageParams>();
    const getBundles = useBundleTypes(bundleId);

    const columns = [ 'System Behavior Group', 'Action', 'Recipient' ];

    if (getBehaviorGroups.loading) {
        return <Spinner />;
    }

    if (getBehaviorGroups.payload?.status !== 200) {
        return <span>Error while loading eventtypes: {getBehaviorGroups.errorObject.toString()}</span>;
    }

    return (
        <React.Fragment>
            <PageSection>
                <Title headingLevel='h1'>
                    <Breadcrumb>
                        <BreadcrumbItem target='#' >{ (getBundles.loading || getBundles.payload?.status !== 200)
                            ? <Spinner /> : getBundles.payload.value.displayName }
                        </BreadcrumbItem>
                        <BreadcrumbItem target='#'> System Behavior Groups </BreadcrumbItem>
                    </Breadcrumb>
                </Title>
                <TableComposable aria-label="System behavior groups table">
                    <Thead>
                        <Toolbar>
                            <ToolbarContent>
                                <ToolbarItem>
                                    <Button variant='primary' type='button'> Create new group </Button>
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
                        { getBehaviorGroups.payload.value.map(b =>
                            <Tr key={ b.id }>
                                <Td>{b.displayName}</Td>
                                <Td>{b.actions}</Td>
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

