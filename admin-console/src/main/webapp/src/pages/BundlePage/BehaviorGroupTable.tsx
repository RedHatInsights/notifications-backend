import { Button, PageSection, Spinner, Title, Toolbar, ToolbarContent, ToolbarItem } from '@patternfly/react-core';
import { PencilAltIcon, TrashIcon } from '@patternfly/react-icons';
import { ExpandableRowContent, Table, Tbody, Td, Th, Thead, Tr } from '@patternfly/react-table';
import * as React from 'react';

import { BehaviorGroupEventTypesPanel } from '../../components/SystemBehaviorGroups/BehaviorGroupEventTypesPanel';
import { CreateEditBehaviorGroupModal } from '../../components/SystemBehaviorGroups/CreateEditBehaviorGroupModal';
import { DeleteBehaviorGroupModal } from '../../components/SystemBehaviorGroups/DeleteBehaviorGroupModal';
import { Schemas } from '../../generated/OpenapiInternal';
import { useCreateSystemBehaviorGroup } from '../../services/SystemBehaviorGroups/CreateSystemBehaviorGroup';
import { useDeleteBehaviorGroup } from '../../services/SystemBehaviorGroups/DeleteSystemBehaviorGroup';
import { useSystemBehaviorGroups } from '../../services/SystemBehaviorGroups/GetBehaviorGroups';
import { useBulkUpdateEventTypeLinks } from '../../services/SystemBehaviorGroups/BulkUpdateEventTypeLinks';
import { useUpdateBehaviorGroupActionsMutation } from '../../services/SystemBehaviorGroups/UpdateActions';
import { Application, BehaviorGroup, BehaviorGroupAction } from '../../types/Notifications';

export const actionsToDropdownValue = (actions?: BehaviorGroupAction[] | null): string | undefined => {
    if (!actions || actions.length === 0) {
        return undefined;
    }

    const action = actions[0];
    const properties = action.endpoint?.properties as Schemas.SystemSubscriptionProperties;
    const endpointType = action.endpoint?.type;
    if (!properties || !endpointType) {
        return undefined;
    }

    if (endpointType === 'drawer') {
        return properties.only_admins ? 'drawer-admin' : 'drawer-all';
    }

    if (endpointType === 'email_subscription') {
        return properties.only_admins ? 'email-admin' : 'email-all';
    }

    return undefined;
};

export const formatActionLabel = (action: BehaviorGroupAction): string => {
    const properties = action.endpoint?.properties as Schemas.SystemSubscriptionProperties;
    const endpointType = action.endpoint?.type;
    if (!properties || !endpointType) {
        return '';
    }

    if (endpointType !== 'drawer' && endpointType !== 'email_subscription') {
        return '';
    }

    const channel = endpointType === 'drawer' ? 'Drawer' : 'Email';
    const audience = properties.only_admins ? 'Admins' : 'All users';
    return `${channel}: ${audience}`;
};

interface BundlePageProps {
    bundleId: string;
    bundle: string | undefined;
    applications: ReadonlyArray<Application>;
}

export const BehaviorGroupsTable: React.FunctionComponent<BundlePageProps> = props => {
    const getBehaviorGroups = useSystemBehaviorGroups(props.bundleId);
    const newBehaviorGroup = useCreateSystemBehaviorGroup();
    const deleteBehaviorGroupMutation = useDeleteBehaviorGroup();
    const updateBehaviorActions = useUpdateBehaviorGroupActionsMutation();
    const bulkUpdateMutation = useBulkUpdateEventTypeLinks();

    const columns = [ 'System Behavior Group', 'Action' ];

    const [ showModal, setShowModal ] = React.useState(false);
    const [ showDeleteModal, setShowDeleteModal ] = React.useState(false);

    const [ isEdit, setIsEdit ] = React.useState(false);
    const [ expandedRows, setExpandedRows ] = React.useState<Record<string, boolean>>({});

    const [ systemBehaviorGroup, setSystemBehaviorGroup ] = React.useState<Partial<BehaviorGroup>>({});

    const handleBulkUpdateEventTypes = React.useCallback(async (
        behaviorGroupId: string,
        eventTypeIdsToLink: string[],
        eventTypeIdsToUnlink: string[]
    ) => {
        try {
            const response = await bulkUpdateMutation.mutate({
                behaviorGroupId,
                eventTypeIdsToLink,
                eventTypeIdsToUnlink
            });
            if (!response.error) {
                const refreshResult = await getBehaviorGroups.query();
                return !refreshResult.error;
            }
        } catch {
            return false;
        }

        return false;
    }, [ bulkUpdateMutation.mutate, getBehaviorGroups.query ]);

    const toggleExpand = React.useCallback((bgId: string) => {
        setExpandedRows(prev => ({ ...prev, [bgId]: !prev[bgId] }));
    }, []);

    const createBehaviorGroup = () => {
        setShowModal(true);
        setIsEdit(false);
        setSystemBehaviorGroup({});
    };

    const editSystemBehaviorGroup = (b: BehaviorGroup) => {
        setShowModal(true);
        setIsEdit(true);
        setSystemBehaviorGroup({
            ...b,
            actions: actionsToDropdownValue(b.actions)
        } as Partial<BehaviorGroup>);
    };

    const deleteBehaviorGroupModal = (b: BehaviorGroup) => {
        setShowDeleteModal(true);
        setSystemBehaviorGroup(b);
    };

    const handleSubmit = React.useCallback(systemBehaviorGroup => {
        setShowModal(false);
        const mutate = newBehaviorGroup.mutate;
        const updateActionsMutate = updateBehaviorActions.mutate;
        mutate({
            id: systemBehaviorGroup.id,
            displayName: systemBehaviorGroup.displayName ?? '',
            bundleId: props.bundleId
        })
            .then(response => {
                if (response.payload?.status === 200 && (response.payload.value.id || systemBehaviorGroup.id)) {
                    return updateActionsMutate({
                        behaviorGroupId: response.payload.value.id ?? systemBehaviorGroup.id,
                        body: [
                            {
                                ignore_preferences: false,
                                only_admins: systemBehaviorGroup.actions === 'email-admin' || systemBehaviorGroup.actions === 'drawer-admin',
                                endpoint_type: systemBehaviorGroup.actions.startsWith('drawer') ? 'DRAWER' : 'EMAIL_SUBSCRIPTION'
                            }
                        ]
                    });
                }
            })
            .finally(getBehaviorGroups.query);

    }, [ getBehaviorGroups.query, newBehaviorGroup.mutate, props.bundleId, updateBehaviorActions.mutate ]);

    const handleDelete = React.useCallback(async() => {
        setShowDeleteModal(false);
        const deleteBehaviorGroup = deleteBehaviorGroupMutation.mutate;
        const response = await deleteBehaviorGroup(systemBehaviorGroup.id).finally(getBehaviorGroups.query);
        return !response.error;
    }, [ deleteBehaviorGroupMutation.mutate, systemBehaviorGroup.id, getBehaviorGroups.query ]);

    const onClose = () => {
        setShowModal(false);
        getBehaviorGroups.query();
    };

    const onDeleteClose = () => {
        setShowDeleteModal(false);
        getBehaviorGroups.query();
    };

    if (getBehaviorGroups.loading) {
        return <Spinner />;
    }

    if (getBehaviorGroups.payload?.status !== 200) {
        return <span>
            Error while loading sysem behavior groups:
            { getBehaviorGroups.errorObject.toString() }
        </span>;
    }

    return (
        <>
            <PageSection>
                <Title headingLevel="h3">
                    System Behavior Groups
                </Title>
                <Table aria-label="System behavior groups table">
                    <Thead>
                        <Toolbar>
                            <ToolbarContent>
                                <ToolbarItem>
                                    <Button variant="primary" type="button" onClick={ createBehaviorGroup }> Create new group </Button>
                                </ToolbarItem>
                            </ToolbarContent>
                        </Toolbar>
                        <Tr>
                            <Th />
                            { columns.map((column, columnIndex) => (
                                <Th key={ columnIndex }>{ column }</Th>
                            )) }
                            <Th />
                            <Th />
                        </Tr>
                    </Thead>
                    <Tbody>
                        { getBehaviorGroups.payload.value.filter(b => b.id).map((b, rowIndex) => <React.Fragment key={ b.id }>
                            <Tr>
                                <Td
                                    expand={ {
                                        rowIndex,
                                        isExpanded: !!expandedRows[b.id ?? ''],
                                        onToggle: () => toggleExpand(b.id ?? '')
                                    } }
                                />
                                <Td>{ b.displayName }</Td>
                                <Td>
                                    { b.actions?.map((action, index) => (
                                        <span key={ index }>{ formatActionLabel(action) }</span>
                                    )) }
                                </Td>
                                <Td>
                                    <Button
                                        className="edit"
                                        type="button"
                                        variant="plain"
                                        onClick={ () => editSystemBehaviorGroup(b) }
                                    >
                                        { ' ' }
                                        <PencilAltIcon />
                                        { ' ' }
                                    </Button>
                                </Td>
                                <Td>
                                    <Button
                                        className="delete"
                                        type="button"
                                        variant="plain"
                                        onClick={ () => deleteBehaviorGroupModal(b) }
                                    >
                                        <TrashIcon />
                                        { ' ' }
                                    </Button>
                                </Td>
                            </Tr>
                            <Tr isExpanded={ !!expandedRows[b.id ?? ''] }>
                                <Td />
                                <Td colSpan={ 4 }>
                                    <ExpandableRowContent>
                                        { expandedRows[b.id ?? ''] && <BehaviorGroupEventTypesPanel
                                            behaviorGroup={ b }
                                            applications={ props.applications }
                                            onBulkUpdateEventTypes={ handleBulkUpdateEventTypes }
                                        /> }
                                    </ExpandableRowContent>
                                </Td>
                            </Tr>
                        </React.Fragment>) }
                    </Tbody>
                </Table>
            </PageSection>
            <CreateEditBehaviorGroupModal
                isEdit={ isEdit }
                initialSystemBehaviorGroup={ systemBehaviorGroup }
                showModal={ showModal }
                onClose={ onClose }
                onSubmit={ handleSubmit }
                isLoading={ false }
            />
            <DeleteBehaviorGroupModal
                onDelete={ handleDelete }
                bundleName={ props.bundle }
                systemBehaviorGroupName={ systemBehaviorGroup.displayName }
                isOpen={ showDeleteModal }
                onClose={ onDeleteClose }
            />
        </>

    );
};

