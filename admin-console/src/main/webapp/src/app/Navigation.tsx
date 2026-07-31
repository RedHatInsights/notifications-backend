import { Button, Nav, NavExpandable, NavItem, NavList } from '@patternfly/react-core';
import { PlusCircleIcon } from '@patternfly/react-icons';
import * as React from 'react';
import { Link, useMatch } from 'react-router-dom';

import { linkTo } from '../Routes';
import { Bundle } from '../types/Notifications';

type EnhancedNavItemProps = {
    to: string;
    children?: React.ReactNode;
}

const EnhancedNavItem: React.FunctionComponent<EnhancedNavItemProps> = props => {
    const match = useMatch(props.to);

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
    isAdmin?: boolean;
    onCreateBundle?: () => void;
}

export const Navigation: React.FunctionComponent<NavigationProps> = props => {
    return (
        <Nav>
            { props.isAdmin && props.onCreateBundle && (
                <div style={ { padding: '8px 16px' } }>
                    <Button
                        variant="primary"
                        icon={ <PlusCircleIcon /> }
                        onClick={ props.onCreateBundle }
                        isBlock
                        style={ { justifyContent: 'flex-start' } }
                    >
                        Create Bundle
                    </Button>
                </div>
            ) }
            <NavList>
                <NavExpandable title="Bundles">
                    { props.bundles.map(b => (
                        <EnhancedNavItem key={ b.id } to={ linkTo.bundle(b.id) }>
                            { b.displayName }
                        </EnhancedNavItem>
                    )) }
                </NavExpandable>
                <NavExpandable title="Utils" isExpanded>
                    <EnhancedNavItem to={ linkTo.messageValidator() }>
                        Notification validator
                    </EnhancedNavItem>
                    <EnhancedNavItem to={ linkTo.email() }>
                        Email templates
                    </EnhancedNavItem>
                </NavExpandable>
            </NavList>
        </Nav>
    );
};
