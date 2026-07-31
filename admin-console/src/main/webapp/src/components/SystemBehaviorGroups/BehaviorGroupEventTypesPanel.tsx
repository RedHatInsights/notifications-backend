import {
    Alert,
    AlertVariant,
    Spinner,
    Switch,
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

interface ApplicationEventTypes {
    application: Application;
    eventTypes: ReadonlyArray<EventType>;
}

const isEventTypeLinked = (behaviorGroup: BehaviorGroup, eventTypeId: string): boolean => {
    return behaviorGroup.behaviors?.some(
        b => b.id?.eventTypeId === eventTypeId
    ) ?? false;
};

export const BehaviorGroupEventTypesPanel: React.FunctionComponent<BehaviorGroupEventTypesPanelProps> = props => {
    const { query } = useClient();
    const [ appEventTypes, setAppEventTypes ] = React.useState<ReadonlyArray<ApplicationEventTypes>>([]);
    const [ loading, setLoading ] = React.useState(true);
    const [ fetchError, setFetchError ] = React.useState<string | null>(null);
    const [ pendingToggle, setPendingToggle ] = React.useState<string | null>(null);
    const [ toggleError, setToggleError ] = React.useState<string | null>(null);

    React.useEffect(() => {
        let cancelled = false;

        const fetchAllEventTypes = async () => {
            setLoading(true);
            setFetchError(null);

            try {
                const results: ApplicationEventTypes[] = [];
                for (const app of props.applications) {
                    const action = Operations.InternalResourceGetEventTypes.actionCreator({ appId: app.id });
                    const response = await query(action);
                    if (!response.error && response.payload) {
                        const rawData = response.payload as any;
                        const value = rawData.status === 200 ? rawData.value : rawData;
                        const eventTypes: EventType[] = (Array.isArray(value) ? value : []).map((v: any) => ({
                            id: v.id ?? '',
                            name: v.name ?? '',
                            displayName: v.display_name ?? v.displayName ?? '',
                            description: v.description ?? '',
                            applicationId: v.application_id ?? v.applicationId ?? app.id,
                            subscribedByDefault: !!v.subscribed_by_default,
                            subscriptionLocked: !!v.subscription_locked,
                            visible: v.visible ?? true,
                            includedInDrawer: !!v.included_in_drawer
                        }));

                        if (eventTypes.length > 0) {
                            results.push({ application: app, eventTypes });
                        }
                    }
                }

                if (!cancelled) {
                    setAppEventTypes(results);
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

        fetchAllEventTypes();
        return () => {
            cancelled = true;
        };
    }, [ props.applications, query ]);

    const handleToggle = React.useCallback(async (eventType: EventType, isLinked: boolean) => {
        const bgId = props.behaviorGroup.id;
        if (!bgId) {
            return;
        }

        setPendingToggle(eventType.id);
        setToggleError(null);

        try {
            const success = isLinked
                ? await props.onUnlinkEventType(bgId, eventType.id)
                : await props.onLinkEventType(bgId, eventType.id);

            if (!success) {
                setToggleError(`Failed to ${isLinked ? 'unlink' : 'link'} event type "${eventType.displayName}".`);
            }
        } catch {
            setToggleError(`Failed to ${isLinked ? 'unlink' : 'link'} event type "${eventType.displayName}".`);
        } finally {
            setPendingToggle(null);
        }
    }, [ props ]);

    if (loading) {
        return <Spinner size="lg" />;
    }

    if (fetchError) {
        return <Alert variant={ AlertVariant.danger } title={ fetchError } isInline />;
    }

    if (appEventTypes.length === 0) {
        return <span>No event types available in this bundle.</span>;
    }

    return <>
        <Title headingLevel="h5" className="pf-v6-u-mb-sm">
            Linked Event Types
        </Title>
        { toggleError && <Alert variant={ AlertVariant.danger } title={ toggleError } isInline className="pf-v6-u-mb-sm" /> }
        { appEventTypes.map(({ application, eventTypes }) => (
            <div key={ application.id } className="pf-v6-u-mb-md">
                <Title headingLevel="h6" className="pf-v6-u-mb-xs">
                    { application.displayName }
                </Title>
                { eventTypes.map(et => {
                    const linked = isEventTypeLinked(props.behaviorGroup, et.id);
                    const isPending = pendingToggle === et.id;
                    return <div key={ et.id } className="pf-v6-u-mb-xs">
                        { isPending ? <Spinner size="md" /> : <Switch
                            id={ `et-link-${props.behaviorGroup.id}-${et.id}` }
                            label={ et.displayName }
                            isChecked={ linked }
                            onChange={ () => handleToggle(et, linked) }
                            aria-label={ `Toggle link for ${et.displayName}` }
                        /> }
                    </div>;
                }) }
            </div>
        )) }
    </>;
};
