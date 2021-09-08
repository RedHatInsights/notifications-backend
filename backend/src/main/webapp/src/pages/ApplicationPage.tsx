import { Breadcrumb, BreadcrumbItem, Button, Modal, ModalVariant, PageSection, Spinner, Title, Toolbar,
    ToolbarContent, ToolbarItem } from '@patternfly/react-core';
import {
    ActionGroup,
    Form,
    FormGroup,
    TextArea,
    TextInput  } from '@patternfly/react-core';
import { TableComposable, Tbody, Td, Th, Thead, Tr } from '@patternfly/react-table';
import * as React from 'react';
import { useState } from 'react';
import { useParams } from 'react-router';

import { useCreateEventType } from '../services/CreateEventTypes';
import { useApplicationTypes } from '../services/GetApplication';
import { useEventTypes } from '../services/GetEventTypes';

type ApplicationPageParams = {
    applicationId: string;
}

export const ApplicationPage: React.FunctionComponent = () => {
    const { applicationId } = useParams<ApplicationPageParams>();
    const eventTypesQuery = useEventTypes(applicationId);
    const applicationTypesQuery = useApplicationTypes(applicationId);
    const columns = [ 'Event Type', 'Event Type Id', 'Description' ];

    const newEvent = useCreateEventType();
    const [ id ] = React.useState<string | undefined>();
    const [ displayName, setDisplayName ] = React.useState<string | undefined>();
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const [ name, setName ] = React.useState<string | undefined>();
    const [ description, setDescription ] = React.useState<string | undefined>();

    const [ isOpen, setIsOpen ] = useState(false);
    const toggle = () => setIsOpen(!isOpen);

    const onSubmit = React.useCallback(() => {
        const mutate = newEvent.mutate;
        mutate({
            id: id ?? '',
            applicationId,
            displayName: displayName ?? '',
            name: displayName ?? '',
            description: description ?? ''
        });
    }, [ newEvent.mutate, id, applicationId, displayName, description ]);

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
                    <BreadcrumbItem to='#'> </BreadcrumbItem>
                    <BreadcrumbItem to='#' isActive> { (applicationTypesQuery.loading || applicationTypesQuery.payload?.status !== 200) ?
                        <Spinner /> : applicationTypesQuery.payload.value.displayName } </BreadcrumbItem>
                </Breadcrumb></Title>
                <TableComposable
                    aria-label="Event types table"
                >
                    <Thead>
                        <Toolbar>
                            <ToolbarContent>
                                <ToolbarItem>
                                    <Button variant='primary' type='submit' onClick={ toggle }> Create Event Type </Button>
                                    <Modal
                                        variant={ ModalVariant.medium }
                                        title='Create Event Type'
                                        isOpen={ isOpen }
                                        onClose={ toggle }
                                    ><Form isHorizontal><FormGroup label='Application Id' fieldId='application-id'
                                            helperText='This is the id of the application'>
                                            <TextInput
                                                type='text'
                                                value= { applicationId }
                                                id='application-id' /></FormGroup>
                                        <FormGroup label='Name' fieldId='name' isRequired
                                            helperText='This is a short name, only composed of a-z 0-9 and - characters.'>
                                            <TextInput
                                                type='text'
                                                onChange={ setName }
                                                id='name' /></FormGroup>
                                        <FormGroup label='Display name' fieldId='display-name' isRequired
                                            helperText='This is the name you want to display on the UI'>
                                            <TextInput
                                                type='text'
                                                onChange={ setDisplayName }
                                                id='display-name' /></FormGroup>
                                        <FormGroup label='Description' fieldId='description'
                                            helperText='Optional short description that appears in the UI to help admin descide how to notify users.'>
                                            <TextArea
                                                type='text'
                                                onChange={ setDescription }
                                                id='description' /></FormGroup>
                                        <ActionGroup>
                                            <Button variant='primary' type='submit' onClick={ onSubmit }>Submit</Button>
                                            <Button variant='link' onClick={ toggle }>Cancel</Button>
                                        </ActionGroup>
                                        </Form><></>
                                    </Modal>
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
                        { eventTypesQuery.payload.value.map(e => (
                            <Tr key={ e.id }>
                                <Td>{ e.displayName }</Td>
                                <Td>{ e.id }</Td>
                                <Td>{ e.description }</Td>
                            </Tr>
                        ))}
                    </Tbody>
                </TableComposable>
            </PageSection>
        </React.Fragment>

    );
};
