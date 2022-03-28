import { Breadcrumb, BreadcrumbItem, Button, PageSection, Spinner, Title, Toolbar,
    ToolbarContent, ToolbarItem } from '@patternfly/react-core';
import { EyeIcon, PencilAltIcon, TrashIcon } from '@patternfly/react-icons';
import {
    TableComposable,
    Tbody,
    Td,  Th,   Thead,
    Tr } from '@patternfly/react-table';
import * as React from 'react';
import { useParams } from 'react-router';
import { Link } from 'react-router-dom';

import { linkTo } from '../../Routes';
import { useApplicationTypes } from '../../services/EventTypes/GetApplication';
import { ViewTemplateModal } from './VIewTemplateModal';

type ApplicationPageParams = {
    applicationId: string;
}

export const EmailTemplateTable: React.FunctionComponent = () => {
    const { applicationId } = useParams<ApplicationPageParams>();
    const applicationTypesQuery = useApplicationTypes(applicationId);

    const [ showViewModal, setShowViewModal ] = React.useState(false);
    const viewModal = () => {
        setShowViewModal(true);
    };

    const onClose = () => {
        setShowViewModal(false);
    };

    const application = React.useMemo(() => {
        if (applicationTypesQuery.payload?.status === 200) {
            return applicationTypesQuery.payload.value;
        }

        return undefined;
    }, [ applicationTypesQuery.payload?.status, applicationTypesQuery.payload?.value ]);

    const columns = [ 'Email Template', 'Id' ];
    const rows = [
        {
            displayName: 'i',
            id: 1
        },
        {
            displayName: 'am',
            id: 2
        },
        {
            displayName: 'a',
            id: 3
        },
        {
            displayName: 'list',
            id: 4
        }
    ];

    return (
        <React.Fragment>
            <PageSection>
                <Title headingLevel="h1">
                    <Breadcrumb>
                        <BreadcrumbItem target='#'> Email Template for { application?.displayName ?? '' }</BreadcrumbItem>
                    </Breadcrumb></Title>
                <TableComposable aria-label="Email Template table">
                    <Thead>
                        <Toolbar>
                            <ToolbarContent>
                                <ToolbarItem>
                                    <Button variant="primary" component={ (props: any) =>
                                        <Link { ...props } to={ linkTo.emailTemplates } /> }>Create Email Template</Button>
                                    <ViewTemplateModal
                                        showModal={ showViewModal }
                                        applicationName={ application?.displayName ?? '' }
                                        onClose={ onClose }
                                    />
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
                        { rows.map(r => (
                            <Tr key={ r.id }>
                                <Td>{r.displayName}</Td>
                                <Td>{r.id}</Td>
                                <Td>
                                    <Button className='view' type='button' variant='plain' onClick={ viewModal }
                                    > { <EyeIcon /> } </Button></Td>
                                <Td>
                                    <Button className='edit' type='button' variant='plain'
                                        isDisabled> { <PencilAltIcon /> } </Button></Td>
                                <Td>
                                    <Button className='delete' type='button' variant='plain'
                                        isDisabled>{ <TrashIcon /> } </Button></Td>
                            </Tr>

                        ))}
                    </Tbody>
                </TableComposable>
            </PageSection>
        </React.Fragment>

    );
};

