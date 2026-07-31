import '@patternfly/react-core/dist/styles/base.css';
import './app.css';

import { Alert, AlertVariant, Brand, Button, Masthead, MastheadToggle, MastheadMain, MastheadBrand, Page, PageSection, PageSidebar, Spinner } from '@patternfly/react-core';
import { BarsIcon } from '@patternfly/react-icons';
import React, { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { style } from 'typestyle';

import { CreateEditBundleModal } from '../components/Bundles/CreateEditBundleModal';
import { linkTo, Routes } from '../Routes';
import { useCreateBundle } from '../services/Bundles/CreateBundle';
import { useBundles } from '../services/EventTypes/GetBundles';
import { usePermissions } from '../services/Permissions';
import { useServerInfo } from '../services/ServerInfo';
import { BundlesContext } from './BundlesContext';
import { Navigation } from './Navigation';
import { PermissionContext } from './PermissionContext';
import logo from './redhat-logo.svg';

const brandClassName = style({
    width: 150
});

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
    const newBundle = useCreateBundle();
    const navigate = useNavigate();

    const [ showBundleModal, setShowBundleModal ] = React.useState(false);
    const [ bundleCreateLoading, setBundleCreateLoading ] = React.useState(false);
    const [ bundleCreateError, setBundleCreateError ] = React.useState<string | undefined>(undefined);

    const onCreateBundle = React.useCallback(() => {
        setBundleCreateError(undefined);
        setShowBundleModal(true);
    }, []);

    const onBundleModalClose = React.useCallback(() => {
        setBundleCreateError(undefined);
        setShowBundleModal(false);
    }, []);

    const handleBundleSubmit = React.useCallback((bundle: { name?: string; displayName?: string }) => {
        setBundleCreateLoading(true);
        setBundleCreateError(undefined);
        newBundle.mutate({
            displayName: bundle.displayName ?? '',
            name: bundle.name ?? ''
        }).then((response) => {
            if (response.error) {
                setBundleCreateError('Failed to create bundle. Please check the values and try again.');
            } else {
                setShowBundleModal(false);
                bundles.query();
                const createdBundle = response.payload?.type === 'Bundle' ? response.payload.value : undefined;
                if (createdBundle?.id) {
                    navigate(linkTo.bundle(createdBundle.id));
                }
            }
        }).catch(() => {
            setBundleCreateError('An unexpected error occurred while creating the bundle.');
        }).finally(() => {
            setBundleCreateLoading(false);
        });
    }, [ newBundle, bundles, navigate ]);

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

    const appHeader = <Masthead>
        <MastheadMain>
            <MastheadToggle>
                <Button
                    variant="plain"
                    onClick={ onNavToggle }
                    aria-label="Global navigation"
                    aria-expanded={ isNavOpen }
                >
                    <BarsIcon />
                </Button>
            </MastheadToggle>
            <MastheadBrand href="/internal">
                <Brand className={ brandClassName } src={ logo } alt="Red Hat" />
            </MastheadBrand>
        </MastheadMain>
    </Masthead>;

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

    const bundlesContextValue = React.useMemo<BundlesContext>(() => ({
        refreshBundles: bundles.query
    }), [ bundles.query ]);

    if (bundles.isLoading || serverInfo.loading || permissionQuery.loading) {
        return (
            <Page
                masthead={ appHeader }
            >
                <PageSection>
                    <Spinner />
                </PageSection>
            </Page>
        );
    }

    const appSidebar = <PageSidebar isSidebarOpen={ isNavOpen }>
        <Navigation
            bundles={ bundles.bundles }
            isAdmin={ permission.isAdmin }
            onCreateBundle={ onCreateBundle }
        />
    </PageSidebar>;

    return (
        <BundlesContext.Provider value={ bundlesContextValue }>
            <PermissionContext.Provider value={ permission }>
                <Page
                    sidebar={ appSidebar }
                    masthead={ appHeader }
                >
                    { message.show && (
                        <PageSection>
                            <Alert variant={ AlertVariant.warning } title={ message.content } />
                        </PageSection>
                    ) }
                    <Routes />
                </Page>
                { showBundleModal && <CreateEditBundleModal
                    isEdit={ false }
                    showModal={ showBundleModal }
                    isLoading={ bundleCreateLoading }
                    error={ bundleCreateError }
                    onClose={ onBundleModalClose }
                    onSubmit={ handleBundleSubmit }
                /> }
            </PermissionContext.Provider>
        </BundlesContext.Provider>
    );
};
