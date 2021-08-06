export interface Bundle {
    id: string;
    displayName: string;
    applications: ReadonlyArray<Application>;
}

export interface Application {
    id: string;
    displayName: string;
}

export interface EventType {
    id: string;
    displayName: string;
    description: string;
    applicationId: string;
}
