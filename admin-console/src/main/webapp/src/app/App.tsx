import '@patternfly/react-core/dist/styles/base.css';
import './app.css';

import { Alert, AlertVariant, Brand, Page, PageHeader, PageSection, PageSidebar, Spinner } from '@patternfly/react-core';
import React, { useMemo } from 'react';

import { Routes } from '../Routes';
import { useBundles } from '../services/EventTypes/GetBundles';
import { usePermissions } from '../services/Permissions';
import { useServerInfo } from '../services/ServerInfo';
import { Navigation } from './Navigation';
import { PermissionContext } from './PermissionContext';
import logo from './redhat-logo.svg';

type Message = {
    show: false;
} | {
    show: true;
    content: string;
}

export const App: React.FunctionComponent<unknown> = () => {

    const [ isNavOpen, setNavOpen ] = React.useState(true);

    const onNavToggle = React.useCallback(() => setNavOpen(prev => !prev), [ setNavOpen ]);

    const bundles = useBundles();
    const serverInfo = useServerInfo();

    const message = useMemo<Message>(() => {
        const payload = serverInfo.payload;
        if (payload?.status === 200) {
            if (payload.value.environment === 'PROD') {
                return {
                    show: true,
                    content: 'You are viewing the production environment - '
                    + 'Any change you make here will be applied immediately and could disrupt the service.'
                };
            }

            return {
                show: false
            };
        }

        return {
            show: true,
            content: 'Could not load the current environment. Please verify the URL before making any change.'
        };
    }, [ serverInfo.payload ]);
    const permissionQuery = usePermissions();

    const appHeader = <PageHeader
        showNavToggle
        logo={ <Brand width="150px" src={ logo } alt="Red Hat" /> }
        logoProps={ { href: '/internal' } }
        isNavOpen={ isNavOpen }
        onNavToggle={ onNavToggle }
    />;

    const permission = React.useMemo<PermissionContext>(() => {
        const payload = permissionQuery.payload;
        if (payload?.status === 200) {
            return {
                isAdmin: payload.value.is_admin,
                applications: payload.value.applications.map(a => ({
                    id: a.id,
                    displayName: a.display_name
                })),
                roles: payload.value.roles,
                refresh: permissionQuery.query
            };
        }

        return {
            isAdmin: false,
            applications: [],
            roles: [],
            refresh: permissionQuery.query
        };
    }, [ permissionQuery.payload, permissionQuery.query ]);

    if (bundles.isLoading || serverInfo.loading || permissionQuery.loading) {
        return (
            <Page
                header={ appHeader }
            >
                <PageSection>
                    <Spinner />
                </PageSection>
            </Page>
        );
    }

    const appSidebar = <PageSidebar nav={ <Navigation bundles={ bundles.bundles } /> } isNavOpen={ isNavOpen } />;

    return (
        <PermissionContext.Provider value={ permission }>
            <Page
                sidebar={ appSidebar }
                header={ appHeader }>
                { message.show && (
                    <PageSection>
                        <Alert variant={ AlertVariant.warning } title={ message.content } />
                    </PageSection>
                )}
                <Routes />
            </Page>
        </PermissionContext.Provider>
    );
};
