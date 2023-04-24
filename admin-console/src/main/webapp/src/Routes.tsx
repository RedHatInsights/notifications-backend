import * as React from 'react';
import { Redirect, Route, Switch } from 'react-router';

import { ApplicationPage } from './pages/ApplicationPage';
import { BundlePage } from './pages/BundlePage';
import { EmailTemplatePage } from './pages/EmailTemplatePage';
import { MessageValidatorPage } from './pages/MessageValidatorPage';
import { RenderEmailPage } from './pages/RenderEmailPage';
import { ConsoleCloudEventValidatorPage } from './pages/ConsoleCloudEventValidatorPage';

interface Path {
    readonly path: string;
    readonly component: React.ComponentType;
}

export const linkTo = {
    bundle: (bundleId: string) => `/bundle/${bundleId}`,
    application: (applicationId: string) => `/application/${applicationId}`,
    email: () => '/email',
    newEmailTemplate: () => '/email-templates',
    emailTemplates: (templateId: string) => `/email-templates/${templateId}`,
    messageValidator: () => '/utils/message-validator',
    consoleCloudEventValidator: () => '/utils/console-cloud-event-validator'
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
        path: linkTo.emailTemplates(':templateId'),
        component: EmailTemplatePage
    },
    {
        path: linkTo.newEmailTemplate(),
        component: EmailTemplatePage
    },
    {
        path: linkTo.messageValidator(),
        component: MessageValidatorPage
    },
    {
        path: linkTo.consoleCloudEventValidator(),
        component: ConsoleCloudEventValidatorPage
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
            <Redirect to={ linkTo.consoleCloudEventValidator() } />
        </Switch>
    );
};
