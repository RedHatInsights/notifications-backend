import * as React from 'react';
import { useCallback, useContext } from 'react';

import { Schemas } from '../generated/OpenapiInternal';

export interface PermissionContext {
    isAdmin: boolean;
    applications: ReadonlyArray<{
        id: string;
        displayName: string;
    }>;
    roles: ReadonlyArray<string>;
    refresh: () => void;
}

export const PermissionContext = React.createContext<PermissionContext>({
    isAdmin: false,
    applications: [],
    roles: [],
    refresh: () => {
        throw new Error('Invalid use of refresh');
    }
});

export const useUserPermissions = () => {
    const permission = useContext(PermissionContext);

    const hasPermission = useCallback((appId: Schemas.UUID) => {
        if (permission.isAdmin) {
            return true;
        }

        return permission.applications.find(a => a.id === appId) !== undefined;
    }, [ permission ]);

    return {
        ...permission,
        hasPermission
    };
};
