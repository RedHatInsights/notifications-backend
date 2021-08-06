import { Spinner } from '@patternfly/react-core';
import { Breadcrumb, BreadcrumbItem } from '@patternfly/react-core';
import { Caption, TableComposable, Tbody, Td, Th, Thead, Tr } from '@patternfly/react-table';
import * as React from 'react';
import { useParams } from 'react-router';

import { useEventTypes } from '../services/GetEventTypes';

type ApplicationPageParams = {
    applicationId: string;
}

export const ApplicationPage: React.FunctionComponent = () => {
    const { applicationId } = useParams<ApplicationPageParams>();
    const eventTypesQuery = useEventTypes(applicationId);
    const columns = [ 'Event', 'Event Id', 'Description' ];

    if (eventTypesQuery.loading) {
        return <Spinner />;
    }

    if (eventTypesQuery.payload?.status !== 200) {
        return <span>Error while loading eventtypes: {eventTypesQuery.errorObject.toString() }</span>;
    }

    if (eventTypesQuery.payload.value.length === 0) {
        return <span>No event types found for this application</span>;
    }

    return (
        <React.Fragment>
            <TableComposable
                aria-label="Simple table"
            >
                <Caption>
                    <Breadcrumb>
                        <BreadcrumbItem to="#">Red Hat Enterprise Linux</BreadcrumbItem>
                        <BreadcrumbItem to="#" isActive>Policies</BreadcrumbItem>
                    </Breadcrumb></Caption>
                <Thead>
                    <Tr>
                        {columns.map((column, columnIndex) => (
                            <Th key={ columnIndex }>{column}</Th>
                        ))}
                    </Tr>
                </Thead>
                <Tbody>
                    { eventTypesQuery.payload.map((e: any) => (
                        <Td key={ e.id }> { e.display_name }
                        </Td>
                    ))}
                    { eventTypesQuery.payload.map((e: any) => (
                        <Td key={ e.id }> { e.id }
                        </Td>
                    ))}
                    { eventTypesQuery.payload.map((e: any) => (
                        <Td key={ e.id }> { e.description }
                        </Td>
                    ))}
                </Tbody>
            </TableComposable>
        </React.Fragment>

    );};
