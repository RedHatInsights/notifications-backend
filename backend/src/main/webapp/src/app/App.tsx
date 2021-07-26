import '@patternfly/react-core/dist/styles/base.css';
import './app.css';

import { Brand, Page, PageHeader, PageSection, PageSidebar, Spinner } from '@patternfly/react-core';
import React from 'react';

import { Routes } from '../Routes';
import { useBundles } from '../services/GetBundles';
import { Navigation } from './Navigation';
import logo from './redhat-logo.svg';

export const App: React.FunctionComponent<unknown> = () => {

    const [ isNavOpen, setNavOpen ] = React.useState(true);

    const onNavToggle = React.useCallback(() => setNavOpen(prev => !prev), [ setNavOpen ]);

    const bundles = useBundles();

    const appHeader = <PageHeader
        showNavToggle
        logo={ <Brand width="150px" src={ logo } alt="Red Hat" /> }
        logoProps={ { href: '/internal' } }
        isNavOpen={ isNavOpen }
        onNavToggle={ onNavToggle }
    />;

    if (bundles.isLoading) {
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
        <Page
            sidebar={ appSidebar }
            header={ appHeader }>
            <Routes />
        </Page>
    );
};
