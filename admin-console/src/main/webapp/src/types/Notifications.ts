import { Schemas } from '../generated/OpenapiInternal';
import Endpoint = Schemas.Endpoint;

export interface Bundle {
    id: string;
    displayName: string;
    applications: ReadonlyArray<Application>;
}

export interface Application {
    id: string;
    displayName: string;
    bundleId: string;
    name: string;
}

export interface RoleOwnedApplication extends Application {
    ownerRole?: string;
}

export type UUID = Schemas.UUID;

// Consider using the generated types instead of writing the same again
export type BehaviorGroupAction = {
    created?: string | undefined | null;
    id?: BehaviorGroupActionId | undefined | null;
    endpoint?: Endpoint | undefined | null;
};

export type BehaviorGroupActionId = {
    behaviorGroupId: UUID;
    endpointId: UUID;
  };

export type BehaviorGroup = {
    actions?: Array<BehaviorGroupAction> | undefined | null;
    bundle?: Bundle | undefined | null;
    bundleId: UUID;
    isDefault?: boolean | undefined | null;
    displayName: string;
    id?: UUID | undefined | null;
  };

export interface EventType {
    id: string;
    displayName: string;
    name: string;
    description: string;
    applicationId: string;
}

type InstantEmailTemplateRow = {
    isLoading: true;
} | ({
    isLoading: false;
} & Partial<InstantTemplate>);

export interface EventTypeRow extends EventType {
    instantEmail: InstantEmailTemplateRow;
}

export interface Template {
    data: string;
    description: string;
    id?: UUID | undefined | null;
    name: string;
}

export type InstantTemplate = {
    id: string;
    eventTypeId: string;
    subjectTemplateId: string;
    bodyTemplateId: string;
}

export type Application1 = {
    bundle_id: UUID;
    display_name: string;
    id?: UUID | undefined | null;
    name: string;
  };

export type AggregationTemplate = {
    id: string;
    applicationId: string;
    subjectTemplateId: string;
    bodyTemplateId: string;

}

type AggregationEmailTemplateRow = {
    isLoading: true;
} | ({
    isLoading: false;
} & Partial<AggregationTemplate>);

export interface AggregationCardRow extends Application {
    aggregationEmail: AggregationEmailTemplateRow;
}
