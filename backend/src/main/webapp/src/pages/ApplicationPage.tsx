import { Spinner } from '@patternfly/react-core';
import { Breadcrumb, BreadcrumbItem, PageSection, Title } from '@patternfly/react-core';
import { TableComposable, Tbody, Td, Th, Thead, Tr } from '@patternfly/react-table';
import * as React from 'react';
import { useParams } from 'react-router';

import { useApplicationTypes }  from '../services/GetApplication';
import { useEventTypes } from '../services/GetEventTypes';

type ApplicationPageParams = {
    applicationId: string;
}

export const ApplicationPage: React.FunctionComponent = () => {
    const { applicationId } = useParams<ApplicationPageParams>();
    const eventTypesQuery = useEventTypes(applicationId);
    const applicationTypesQuery = useApplicationTypes(applicationId);
    const columns = [ 'Event Type', 'Event Id', 'Description' ];

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
            <PageSection>
                <Title headingLevel="h1"><Breadcrumb>
                    <BreadcrumbItem to='#'> Red Hat Enterprise Linux </BreadcrumbItem>
                    <BreadcrumbItem to='#'isActive> { applicationTypesQuery.payload.displayName } </BreadcrumbItem>
                </Breadcrumb></Title>
            </PageSection>
            <TableComposable
                aria-label="Simple table"
            >
                <Thead>
                    <Tr>
                        {columns.map((column, columnIndex) => (
                            <Th key={ columnIndex }>{column}</Th>
                        ))}
                    </Tr>
                </Thead>
                <Tbody>
                    { eventTypesQuery.payload.value.map(e => (
                        <Tr key={ e.id }>
                            <Td>{ e.displayName }</Td>
                            <Td>{ e.id }</Td>
                            <Td>{ e.description }</Td>
                        </Tr>
                    ))}
                </Tbody>
            </TableComposable>
        </React.Fragment>

    );};
