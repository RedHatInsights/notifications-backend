import * as React from 'react';
import { Redirect, Route, Switch } from 'react-router';
import {AggregationPage} from "./pages/AggregationPage";

interface Path {
    readonly path: string;
    readonly component: React.ComponentType;
}

export const linkTo = {
    aggregation: () => '/aggregation'
};

const pathRoutes: Path[] = [
    {
        path: linkTo.aggregation(),
        component: AggregationPage
    }
];

export const Routes: React.FunctionComponent<unknown> = _props => {
    return (
        <Switch>
            { pathRoutes.map(pathRoute => (
                <Route
                    key={ pathRoute.path}
                    component={ pathRoute.component }
                    path={ pathRoute.path }
                />
            )) }
            <Redirect to={ linkTo.aggregation() } />
        </Switch>
    )
};
