import { Nav, NavItem, NavList } from '@patternfly/react-core';
import * as React from 'react';
import { Link, useRouteMatch } from 'react-router-dom';

import { linkTo } from '../Routes';

type EnhancedNavItemProps = {
    to: string;
}

const EnhancedNavItem: React.FunctionComponent<EnhancedNavItemProps> = props => {
    const match = useRouteMatch({
        path: props.to,
        exact: true
    });

    return (
        <NavItem preventDefault isActive={ !!match }>
            <Link to={ props.to }>
                { props.children }
            </Link>
        </NavItem>
    );
};

export const Navigation: React.FunctionComponent<unknown> = () => {
    return (
        <Nav>
            <NavList>
                <EnhancedNavItem to={ linkTo.aggregation() }>
                    Aggregation
                </EnhancedNavItem>
            </NavList>
        </Nav>
    );
};
