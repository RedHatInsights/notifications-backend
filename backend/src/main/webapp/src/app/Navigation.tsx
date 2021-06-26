import * as React from 'react';
import {Nav, NavGroup, NavItem} from "@patternfly/react-core";
import {linkTo} from "../Routes";
import {Link, useRouteMatch} from 'react-router-dom';

type EnhancedNavItemProps = {
    to: string;
}

const EnhancedNavItem: React.FunctionComponent<EnhancedNavItemProps> = props => {
    const match = useRouteMatch({
        path: props.to,
        exact: true
    });

    return (
        <NavItem preventDefault isActive={ !!match } component="span">
            <Link to={ props.to }>
                { props.children }
            </Link>
        </NavItem>
    );
};

export const Navigation: React.FunctionComponent<unknown> = () => {
    return (
        <Nav>
            <EnhancedNavItem to={ linkTo.aggregation() }>
                Aggregation
            </EnhancedNavItem>
        </Nav>
    );
};
