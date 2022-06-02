import { Schemas } from '../generated/OpenapiInternal';

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

export type BehaviorGroupAction = {
    created?: string | undefined | null;
    id?: BehaviorGroupActionId | undefined | null;
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
