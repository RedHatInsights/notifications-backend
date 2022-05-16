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

import { useUserPermissions } from '../../app/PermissionContext';
import { linkTo } from '../../Routes';
import { useInstantEmailTemplates } from '../../services/EmailTemplates/GetInstantTemplates';
import { useApplicationTypes } from '../../services/EventTypes/GetApplication';
import { ListAssociatedEventTypes } from './ListAssociatedEventTypes';
import { ListInstantTemplates } from './ListInstantTemplates';
import { ViewTemplateModal } from './ViewEmailTemplateModal';

type ApplicationPageParams = {
    applicationId: string;
}

export const InstantEmailTemplateTable: React.FunctionComponent = () => {
    const { hasPermission } = useUserPermissions();
    const templateQuery = useInstantEmailTemplates();

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

    const columns = [ 'Instant Email Templates', 'Event Types' ];

    if (templateQuery.loading) {
        return <Spinner />;
    }

    if (templateQuery.payload?.status !== 200) {
        return <span>Error while loading templates: {templateQuery.errorObject.toString()}</span>;
    }

    return (
        <React.Fragment>
            <PageSection>
                <Title headingLevel="h1">
                    <Breadcrumb>
                        <BreadcrumbItem target='#'>Instant Email Template for { application?.displayName ?? '' }</BreadcrumbItem>
                    </Breadcrumb></Title>
                <TableComposable aria-label="Email Template table">
                    <Thead>
                        <Toolbar>
                            <ToolbarContent>
                                <ToolbarItem>
                                    <Button variant="primary" isDisabled={ !application || !hasPermission(application?.id) }
                                        component={ (props: any) =>
                                            <Link { ...props } to={ linkTo.emailTemplates } /> }>Create Instant Email Template</Button>
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
                        <Tr>
                            <Td>
                                <ListInstantTemplates />
                            </Td>
                            <Td>
                                <ListAssociatedEventTypes />
                            </Td>
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
                    </Tbody>
                </TableComposable>
            </PageSection>
        </React.Fragment>

    );
};

