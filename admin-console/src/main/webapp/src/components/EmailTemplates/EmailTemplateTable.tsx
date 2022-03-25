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

import { useUserPermissions } from '../app/PermissionContext';

type ApplicationPageParams = {
    applicationId: string;
}

export const ApplicationPage: React.FunctionComponent = () => {
    const { hasPermission } = useUserPermissions();

    const columns = [ 'Email Template'];

    return (
        <React.Fragment>
            <PageSection>
                <Title headingLevel="h1">
                    <Breadcrumb>
                        <BreadcrumbItem target='#'> Email Templates for { (applicationTypesQuery.loading || applicationTypesQuery.payload?.status !== 200) ?
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
                                        isDisabled={ !application || !hasPermission(application?.id) }
                                        onClick={ createEventType }> Create Event Type </Button>
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
        </React.Fragment>

    );
};

