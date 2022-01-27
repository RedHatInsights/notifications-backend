import { Breadcrumb, BreadcrumbItem, Button, PageSection, Title, Toolbar, ToolbarContent, ToolbarItem } from '@patternfly/react-core';
import { PencilAltIcon, TrashIcon } from '@patternfly/react-icons';
import { TableComposable, Tbody, Td, Th, Thead, Tr } from '@patternfly/react-table';
import * as React from 'react';

export const BundlePage: React.FunctionComponent = () => {
    const columns = [ 'Application', 'Application Id', 'Event Types' ];

    return (
        <React.Fragment>
            <PageSection>
                <Title headingLevel='h1'>
                    <Breadcrumb>
                        <BreadcrumbItem target='#'> Bundles </BreadcrumbItem>
                        <BreadcrumbItem target='#'> Bundle Display Name </BreadcrumbItem>
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
                        <Tr>
                            <Td>
                                <Button className='edit' type='button' variant='plain'
                                > { <PencilAltIcon /> } </Button></Td>
                            <Td>
                                <Button className='delete' type='button' variant='plain'
                                >{ <TrashIcon /> } </Button></Td>
                        </Tr>
                    </Tbody>
                </TableComposable>
            </PageSection>
        </React.Fragment>

    );
};

