import * as React from 'react';
import { Navigate, Route, Routes as RouterRoutes } from 'react-router-dom';

import { ApplicationPage } from './pages/ApplicationPage';
import { BundlePage } from './pages/BundlePage';
import { MessageValidatorPage } from './pages/MessageValidatorPage';
import { RenderEmailPage } from './pages/RenderEmailPage';

interface Path {
    readonly path: string;
    readonly component: React.ComponentType;
}

export const linkTo = {
    bundle: (bundleId: string) => `/bundle/${bundleId}`,
    application: (applicationId: string) => `/application/${applicationId}`,
    email: () => '/email',
    messageValidator: () => '/utils/message-validator'
};

const pathRoutes: Path[] = [
    {
        path: linkTo.application(':applicationId'),
        component: ApplicationPage
    },
    {
        path: linkTo.bundle(':bundleId'),
        component: BundlePage
    },
    {
        path: linkTo.email(),
        component: RenderEmailPage
    },
    {
        path: linkTo.messageValidator(),
        component: MessageValidatorPage
    }
];

export const Routes: React.FunctionComponent<unknown> = _props => {
    return (
        <RouterRoutes>
            { pathRoutes.map(pathRoute => (
                <Route
                    key={ pathRoute.path }
                    element={ <pathRoute.component /> }
                    path={ pathRoute.path }
                />
            )) }
            <Route path="*" element={ <Navigate to={ linkTo.messageValidator() } replace /> } />
        </RouterRoutes>
    );
};
