import { Breadcrumb, BreadcrumbItem, Button, PageSection, Spinner,
    Title, Toolbar, ToolbarContent, ToolbarItem } from '@patternfly/react-core';
import { PencilAltIcon, TrashIcon } from '@patternfly/react-icons';
import { TableComposable, Tbody, Td, Th, Thead, Tr } from '@patternfly/react-table';
import * as React from 'react';
import { useParams } from 'react-router';

import { useBundleTypes } from '../../services/Applications/GetBundleById';
import { useCreateSystemBehaviorGroup } from '../../services/SystemBehaviorGroups/CreateSystemBehaviorGroup';
import { useSystemBehaviorGroups } from '../../services/SystemBehaviorGroups/GetBehaviorGroups';
import { BehaviorGroup } from '../../types/Notifications';
import { CreateEditBehaviorGroupModal } from './CreateEditBehaviorGroupModal';

type BundlePageParams = {
    bundleId: string;
}

export const BehaviorGroupsTable: React.FunctionComponent = () => {
    const getBehaviorGroups = useSystemBehaviorGroups();
    const { bundleId } = useParams<BundlePageParams>();
    const getBundles = useBundleTypes(bundleId);
    const newBehaviorGroup = useCreateSystemBehaviorGroup();

    const columns = [ 'System Behavior Group', 'Action', 'Recipient' ];
    const [ showModal, setShowModal ] = React.useState(false);
    const [ isEdit, setIsEdit ] = React.useState(false);
    const [ systemBehaviorGroup, setSystemBehaviorGroup ] = React.useState<Partial<BehaviorGroup>>({});

    const createBehaviorGroup = () => {
        setShowModal(true);
        setIsEdit(false);
        setSystemBehaviorGroup({});
    };

    const handleSubmit = React.useCallback((systemBehaviorGroup) => {
        setShowModal(false);
        const mutate = newBehaviorGroup.mutate;
        mutate({
            id: systemBehaviorGroup.id,
            displayName: systemBehaviorGroup.displayName ?? '',
            bundleId
        });

    }, [ bundleId, newBehaviorGroup.mutate ]);

    const onClose = () => {
        setShowModal(false);
        setSystemBehaviorGroup({});
    };

    if (getBehaviorGroups.loading) {
        return <Spinner />;
    }

    if (getBehaviorGroups.payload?.status !== 200) {
        return <span>Error while loading sysem behavior groups: {getBehaviorGroups.errorObject.toString()}</span>;
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
                                    <Button variant='primary' type='button' onClick={ createBehaviorGroup }> Create new group </Button>
                                    <CreateEditBehaviorGroupModal
                                        isEdit={ isEdit }
                                        initialSystemBehaviorGroup={ systemBehaviorGroup }
                                        showModal={ showModal }
                                        onClose={ onClose }
                                        onSubmit={ handleSubmit }
                                        isLoading={ false }
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
                        { getBehaviorGroups.payload.value.map(b =>
                            <Tr key={ b.id }>
                                <Td>{b.displayName}</Td>
                                <Td>{b.actions}</Td>
                                <Td>recipient</Td>
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

