import { Nav, NavExpandable, NavItem, NavList } from '@patternfly/react-core';
import * as React from 'react';
import { Link, useRouteMatch } from 'react-router-dom';

import { linkTo } from '../Routes';
import { Bundle } from '../types/Notifications';

type EnhancedNavItemProps = {
    to: string;
}

const EnhancedNavItem: React.FunctionComponent<EnhancedNavItemProps> = props => {
    const match = useRouteMatch({
        path: props.to,
        exact: true
    });

    return (
        <NavItem isActive={ !!match }>
            <Link to={ props.to }>
                { props.children }
            </Link>
        </NavItem>
    );
};

export interface NavigationProps {
    bundles: ReadonlyArray<Bundle>;
}

export const Navigation: React.FunctionComponent<NavigationProps> = props => {
    return (
        <Nav>
            <NavList>
                <NavExpandable title='Bundles'>
                    { props.bundles.map(b => (
                        <EnhancedNavItem key={ b.id } to={ linkTo.bundle(b.id) }>{ b.displayName }
                        </EnhancedNavItem>
                    )) }
                </NavExpandable>
                <NavExpandable title="Utils" isExpanded>
                    <EnhancedNavItem to={ linkTo.consoleCloudEventValidator() }>
                        Console cloud event validator
                    </EnhancedNavItem>
                    <EnhancedNavItem to={ linkTo.messageValidator() }>
                        Old Notification validator
                    </EnhancedNavItem>
                    <EnhancedNavItem to={ linkTo.email() }>
                        Email templates
                    </EnhancedNavItem>
                </NavExpandable>
            </NavList>
        </Nav>
    );
};
