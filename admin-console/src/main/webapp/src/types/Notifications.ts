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

export interface EventType {
    id: string;
    displayName: string;
    name: string;
    description: string;
    applicationId: string;
}

export interface Template {
    id: string | null | undefined;
    name: string;
    description: string;
    data: string;
}

export interface InstantTemplate {
    body_template: Template;
    event_type: Schemas.EventType;
    event_type_id: UUID;
    id: UUID;
    subject_template: Template;

}

export type UUID = string;

export type Application1 = {
    bundle_id: UUID;
    display_name: string;
    id?: UUID | undefined | null;
    name: string;
  };

export interface AggregationTemplate {
    application: Application1 | null | undefined;
    applicationId: UUID;
    body_template: Template;
    id?: UUID;
    subject_template: Template;

}
