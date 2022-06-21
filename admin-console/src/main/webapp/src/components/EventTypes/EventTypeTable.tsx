import {
    ActionList, ActionListItem,
    Button,
    ButtonVariant,
    EmptyState, EmptyStateBody,
    Skeleton,
    Toolbar,
    ToolbarContent,
    ToolbarItem
} from '@patternfly/react-core';
import { PencilAltIcon, TrashIcon } from '@patternfly/react-icons';
import { ExpandableRowContent, OnCollapse, TableComposable, Tbody, Td, Th, Thead, Tr } from '@patternfly/react-table';
import produce from 'immer';
import * as React from 'react';
import { useEffect } from 'react';

import { EventType, EventTypeRow } from '../../types/Notifications';
import { EventTypeExpandableRow } from './Table/EventTypeExpandableRow';
import { InstantEmailCell } from './Table/InstantEmailCell';

interface EventTypeTableBaseProps {
    eventTypes: ReadonlyArray<EventTypeRow>;
    hasPermissions: boolean;
    onCreateEventType: () => void;
    onEditEventType: (eventType: EventType) => void;
    onDeleteEventTypeModal: (eventType: EventType) => void;
}

type CreateEventTypeButtonProp = {
    createEventTypeButton: React.ReactNode;
}

type EventTypeTableImplProps = EventTypeTableBaseProps & CreateEventTypeButtonProp & {
    tableData: Record<string, TableData | undefined>;
    onExpandToggle: OnCollapse;
}

interface TableData {
    isExpanded: boolean;
}

type EventTypeTableLayoutProps = Pick<EventTypeTableBaseProps, 'hasPermissions' | 'onCreateEventType'> & CreateEventTypeButtonProp;

export type EventTypeTableProps = Omit<EventTypeTableBaseProps, 'eventTypes'> & Partial<Pick<EventTypeTableBaseProps, 'eventTypes'>>;

const numberOfColumns = 5;
const skeletonRows = 5;

/**
 * Implements the outer look and feel of a EventType table - Wraps the actual implementation and the skeleton component
 * It connects the events that are outside of the rows (i.e. toolbar)
 */
const EventTypeTableLayout: React.FunctionComponent<EventTypeTableLayoutProps> = props => {
    return <>
        <Toolbar>
            <ToolbarContent>
                <ToolbarItem>
                    { props.createEventTypeButton }
                </ToolbarItem>
            </ToolbarContent>
        </Toolbar>
        <TableComposable aria-label="Event types table">
            <Thead>
                <Th />
                <Th>Event Type</Th>
                <Th>Name</Th>
                <Th>Instant email</Th>
                <Th />
            </Thead>
            <Tbody>
                { props.children }
            </Tbody>
        </TableComposable>
    </>;
};

/**
 * Implements the inner workings of the EventType - shows the rows and connects the function to the rows.
 */
const EventTypeTableImpl: React.FunctionComponent<EventTypeTableImplProps> = props => {
    return <>
        { props.eventTypes.length === 0 && <Tr>
            <Td colSpan={ numberOfColumns }>
                <EmptyState>
                    <EmptyStateBody>
                        There are no event types found for this application
                    </EmptyStateBody>
                    { props.createEventTypeButton }
                </EmptyState>
            </Td>
        </Tr> }
        { props.eventTypes.map((eventType, rowIndex) => (
            <React.Fragment key={ eventType.id }>
                <Tr>
                    <Td
                        expand={ {
                            rowIndex,
                            isExpanded: !!props.tableData[eventType.id]?.isExpanded,
                            onToggle: props.onExpandToggle
                        } }
                    />
                    <Td>{ eventType.displayName }</Td>
                    <Td>{ eventType.name }</Td>
                    <Td><InstantEmailCell eventType={ eventType } /></Td>
                    <Td>
                        <ActionList isIconList>
                            <ActionListItem>
                                <Button
                                    className='edit'
                                    variant={ ButtonVariant.plain }
                                    isDisabled={ !props.hasPermissions }
                                    onClick={ () => props.onEditEventType(eventType) }
                                >
                                    <PencilAltIcon />
                                </Button>
                            </ActionListItem>
                            <ActionListItem>
                                <Button
                                    className='delete'
                                    variant={ ButtonVariant.plain }
                                    isDisabled={ !props.hasPermissions }
                                    onClick={ () => props.onDeleteEventTypeModal(eventType) }
                                >
                                    <TrashIcon />
                                </Button>
                            </ActionListItem>
                        </ActionList>
                    </Td>
                </Tr>
                <Tr key={ `${eventType.id}-expanded-row` } isExpanded={ props.tableData[eventType.id]?.isExpanded }>
                    <Td />
                    <Td colSpan={ 4 }>
                        <ExpandableRowContent>
                            <EventTypeExpandableRow eventType={ eventType } />
                        </ExpandableRowContent>
                    </Td>
                </Tr>
            </React.Fragment>
        )) }
    </>;
};

/**
 * Provides skeleton rows
 */
const EventTypeTableSkeleton: React.FunctionComponent<EventTypeTableProps> = () => {
    return <>
        { Array.from(new Array(skeletonRows)).map((_, rowIndex) => <Tr key={ `skeleton-row-${rowIndex}` }>
            { Array.from(new Array(numberOfColumns)).map((_, colIndex) => <Td key={ `skeleton-cell-${rowIndex}-${colIndex}` }><Skeleton /></Td>) }
        </Tr>) }
    </>;
};

/**
 * Public component of the EventTable, in charge of deciding if showing the skeleton or the full view according to the eventTypes sent
 */
export const EventTypeTable: React.FunctionComponent<EventTypeTableProps> = props => {
    const { eventTypes } = props;
    const [ tableData, setTableData ] = React.useState<Record<string, TableData | undefined>>({});

    const createEventTypeButton = <Button
        variant={ ButtonVariant.primary }
        isDisabled={ !props.hasPermissions }
        onClick={ props.onCreateEventType }
    >
        Create Event Type
    </Button>;

    useEffect(() => {
        setTableData(prev => {
            if (!eventTypes) {
                return prev;
            }

            return Object.fromEntries(
                eventTypes.map(e => {
                    const data: TableData = {
                        isExpanded: false,
                        ...prev[e.id]
                    };

                    return [ e.id, data ];
                })
            );
        });
    }, [ eventTypes ]);

    const onExpandedToggle = React.useCallback<OnCollapse>((_event, rowIndex, isOpen) => {
        setTableData(produce(draft => {
            const eventType = eventTypes ? eventTypes[rowIndex] : undefined;
            if (eventType) {
                const data = draft[eventType.id];
                if (data) {
                    data.isExpanded = isOpen;
                }
            }
        }));
    }, [ eventTypes ]);

    return <EventTypeTableLayout { ...props } createEventTypeButton={ createEventTypeButton }>
        { eventTypes ? <EventTypeTableImpl
            { ...props }
            eventTypes={ eventTypes }
            onExpandToggle={ onExpandedToggle }
            tableData={ tableData }
            createEventTypeButton={ createEventTypeButton }
        /> : <EventTypeTableSkeleton { ...props } />}
    </EventTypeTableLayout>;
};
