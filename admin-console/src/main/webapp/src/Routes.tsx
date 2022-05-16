import * as React from 'react';
import { Redirect, Route, Switch } from 'react-router';

import { AggregationPage } from './pages/AggregationPage';
import { ApplicationPage } from './pages/ApplicationPage';
import { BundlePage } from './pages/BundlePage';
import { EmailTemplatePage } from './pages/EmailTemplatePage';
import { MessageValidatorPage } from './pages/MessageValidatorPage';
import { RenderEmailPage } from './pages/RenderEmailPage';

interface Path {
    readonly path: string;
    readonly component: React.ComponentType;
}

export const linkTo = {
    bundle: (bundleId: string) => `/bundle/${bundleId}`,
    application: (applicationId: string) => `/application/${applicationId}`,
    aggregation: () => '/aggregation',
    email: () => '/email',
    emailTemplates: () => `/emailTemplates`,
    messageValidator: () => '/utils/message-validator'
};

const pathRoutes: Path[] = [
    {
        path: linkTo.aggregation(),
        component: AggregationPage
    },
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
        path: linkTo.emailTemplates(),
        component: EmailTemplatePage
    },
    {
        path: linkTo.messageValidator(),
        component: MessageValidatorPage
    }
];

export const Routes: React.FunctionComponent<unknown> = _props => {
    return (
        <Switch>
            { pathRoutes.map(pathRoute => (
                <Route
                    key={ pathRoute.path }
                    component={ pathRoute.component }
                    path={ pathRoute.path }
                />
            )) }
            <Redirect to={ linkTo.aggregation() } />
        </Switch>
    );
};
