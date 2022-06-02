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

type InstantEmailTemplateRow = {
    isLoading: true;
} | {
    isLoading: false;
    id: string | undefined;
}

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
    body_template: Schemas.Template;
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

export type AggregationTemplate = {
    application?: Schemas.Application1 | undefined | null;
    application_id?: UUID | undefined | null;
    body_template?: Schemas.Template | undefined | null;
    body_template_id: UUID;
    id?: UUID | undefined | null;
    subject_template?: Schemas.Template | undefined | null;
    subject_template_id: UUID;

}
