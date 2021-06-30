import '@patternfly/react-core/dist/styles/base.css';
import './app.css';

import { Brand, Page, PageHeader, PageSidebar } from '@patternfly/react-core';
import React from 'react';

import { Routes } from '../Routes';
import { Navigation } from './Navigation';
import logo from './redhat-logo.svg';

export const App: React.FunctionComponent<unknown> = () => {

    const [ isNavOpen, setNavOpen ] = React.useState(true);

    const onNavToggle = React.useCallback(() => setNavOpen(prev => !prev), [ setNavOpen ]);

    const appHeader = <PageHeader
        showNavToggle
        logo={ <Brand width="150px" src={ logo } alt="Red Hat" /> }
        logoProps={ { href: '/internal' } }
        isNavOpen={ isNavOpen }
        onNavToggle={ onNavToggle }
    />;

    const appSidebar = <PageSidebar nav={ <Navigation /> } isNavOpen={ isNavOpen } />;

    return (
        <Page
            sidebar={ appSidebar }
            header={ appHeader }>
            <Routes />
        </Page>
    );
};
