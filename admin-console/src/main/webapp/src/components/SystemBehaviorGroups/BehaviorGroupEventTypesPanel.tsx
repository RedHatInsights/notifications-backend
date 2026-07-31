import {
    Alert,
    AlertVariant,
    Button,
    Checkbox,
    FormGroup,
    FormSelect,
    FormSelectOption,
    Spinner,
    Title
} from '@patternfly/react-core';
import * as React from 'react';
import { useClient } from 'react-fetching-library';

import { Operations } from '../../generated/OpenapiInternal';
import { Application, BehaviorGroup, EventType } from '../../types/Notifications';

interface BehaviorGroupEventTypesPanelProps {
    behaviorGroup: BehaviorGroup;
    applications: ReadonlyArray<Application>;
    onLinkEventType: (behaviorGroupId: string, eventTypeId: string) => Promise<boolean>;
    onUnlinkEventType: (behaviorGroupId: string, eventTypeId: string) => Promise<boolean>;
}

const isEventTypeLinked = (behaviorGroup: BehaviorGroup, eventTypeId: string): boolean => {
    return behaviorGroup.behaviors?.some(
        b => b.id?.eventTypeId === eventTypeId
    ) ?? false;
};

export const BehaviorGroupEventTypesPanel: React.FunctionComponent<BehaviorGroupEventTypesPanelProps> = props => {
    const { query } = useClient();
    const [ selectedAppId, setSelectedAppId ] = React.useState<string>('');
    const [ eventTypes, setEventTypes ] = React.useState<ReadonlyArray<EventType>>([]);
    const [ loading, setLoading ] = React.useState(false);
    const [ fetchError, setFetchError ] = React.useState<string | null>(null);
    const [ checkedIds, setCheckedIds ] = React.useState<Set<string>>(new Set());
    const [ saving, setSaving ] = React.useState(false);
    const [ saveError, setSaveError ] = React.useState<string | null>(null);
    const [ saveSuccess, setSaveSuccess ] = React.useState(false);

    React.useEffect(() => {
        if (!selectedAppId) {
            setEventTypes([]);
            setCheckedIds(new Set());
            return;
        }

        let cancelled = false;

        const fetchEventTypes = async () => {
            setLoading(true);
            setFetchError(null);
            setSaveError(null);
            setSaveSuccess(false);

            try {
                const action = Operations.InternalResourceGetEventTypes.actionCreator({ appId: selectedAppId });
                const response = await query(action);
                if (cancelled) {
                    return;
                }

                if (response.error || !response.payload) {
                    setFetchError('Failed to load event types.');
                    setEventTypes([]);
                    return;
                }

                const rawData = response.payload as any;
                const value = rawData.status === 200 ? rawData.value : rawData;
                const ets: EventType[] = (Array.isArray(value) ? value : []).map((v: any) => ({
                    id: v.id ?? '',
                    name: v.name ?? '',
                    displayName: v.display_name ?? v.displayName ?? '',
                    description: v.description ?? '',
                    applicationId: v.application_id ?? v.applicationId ?? selectedAppId,
                    subscribedByDefault: !!v.subscribed_by_default,
                    subscriptionLocked: !!v.subscription_locked,
                    visible: v.visible ?? true,
                    includedInDrawer: !!v.included_in_drawer
                }));

                if (!cancelled) {
                    setEventTypes(ets);
                    const initialChecked = new Set<string>();
                    ets.forEach(et => {
                        if (isEventTypeLinked(props.behaviorGroup, et.id)) {
                            initialChecked.add(et.id);
                        }
                    });
                    setCheckedIds(initialChecked);
                }
            } catch {
                if (!cancelled) {
                    setFetchError('Failed to load event types.');
                }
            } finally {
                if (!cancelled) {
                    setLoading(false);
                }
            }
        };

        fetchEventTypes();
        return () => {
            cancelled = true;
        };
    }, [ selectedAppId, query, props.behaviorGroup ]);

    const handleAppChange = React.useCallback((_event: React.FormEvent<HTMLSelectElement>, value: string) => {
        setSelectedAppId(value);
    }, []);

    const handleCheckboxChange = React.useCallback((etId: string, checked: boolean) => {
        setCheckedIds(prev => {
            const next = new Set(prev);
            if (checked) {
                next.add(etId);
            } else {
                next.delete(etId);
            }

            return next;
        });
    }, []);

    const handleUpdate = React.useCallback(async () => {
        const bgId = props.behaviorGroup.id;
        if (!bgId) {
            return;
        }

        setSaving(true);
        setSaveError(null);
        setSaveSuccess(false);

        const errors: string[] = [];

        for (const et of eventTypes) {
            const wasLinked = isEventTypeLinked(props.behaviorGroup, et.id);
            const isChecked = checkedIds.has(et.id);

            if (isChecked && !wasLinked) {
                const success = await props.onLinkEventType(bgId, et.id);
                if (!success) {
                    errors.push(et.displayName);
                }
            } else if (!isChecked && wasLinked) {
                const success = await props.onUnlinkEventType(bgId, et.id);
                if (!success) {
                    errors.push(et.displayName);
                }
            }
        }

        setSaving(false);
        if (errors.length > 0) {
            setSaveError(`Failed to update: ${errors.join(', ')}`);
        } else {
            setSaveSuccess(true);
        }
    }, [ props, eventTypes, checkedIds ]);

    const hasChanges = React.useMemo(() => {
        return eventTypes.some(et => {
            const wasLinked = isEventTypeLinked(props.behaviorGroup, et.id);
            const isChecked = checkedIds.has(et.id);
            return wasLinked !== isChecked;
        });
    }, [ eventTypes, checkedIds, props.behaviorGroup ]);

    if (props.applications.length === 0) {
        return <span>No applications available in this bundle.</span>;
    }

    return <>
        <Title headingLevel="h5" className="pf-v6-u-mb-sm">
            Linked Event Types
        </Title>
        <FormGroup label="Application" fieldId={ `app-select-${props.behaviorGroup.id}` }>
            <FormSelect
                id={ `app-select-${props.behaviorGroup.id}` }
                value={ selectedAppId }
                onChange={ handleAppChange }
                aria-label="Select application"
            >
                <FormSelectOption key="" isPlaceholder label="Select an application" value="" />
                { props.applications.map(app => (
                    <FormSelectOption key={ app.id } label={ app.displayName } value={ app.id } />
                )) }
            </FormSelect>
        </FormGroup>
        { loading && <Spinner size="lg" className="pf-v6-u-mt-sm" /> }
        { fetchError && <Alert variant={ AlertVariant.danger } title={ fetchError } isInline className="pf-v6-u-mt-sm" /> }
        { saveError && <Alert variant={ AlertVariant.danger } title={ saveError } isInline className="pf-v6-u-mt-sm" /> }
        { saveSuccess && <Alert variant={ AlertVariant.success } title="Event type links updated successfully." isInline className="pf-v6-u-mt-sm" /> }
        { !loading && !fetchError && selectedAppId && eventTypes.length === 0 && (
            <span className="pf-v6-u-mt-sm">No event types found for this application.</span>
        ) }
        { !loading && eventTypes.length > 0 && (
            <div className="pf-v6-u-mt-sm">
                { eventTypes.map(et => (
                    <Checkbox
                        key={ et.id }
                        id={ `et-check-${props.behaviorGroup.id}-${et.id}` }
                        label={ et.displayName }
                        isChecked={ checkedIds.has(et.id) }
                        onChange={ (_event, checked) => handleCheckboxChange(et.id, checked) }
                        className="pf-v6-u-mb-xs"
                    />
                )) }
                <Button
                    variant="primary"
                    onClick={ handleUpdate }
                    isLoading={ saving }
                    isDisabled={ saving || !hasChanges }
                    className="pf-v6-u-mt-sm"
                >
                    Update
                </Button>
            </div>
        ) }
    </>;
};
