import { BreadcrumbItem, BreadcrumbItemProps } from '@patternfly/react-core';
import * as React from 'react';

import { OuiaComponentProps, withoutOuiaProps } from '../../utils/Ouia';
import { LinkAdapter } from './LinkAdapter';

type BreadcrumbLinkItemProps = Omit<BreadcrumbItemProps, 'component'> & OuiaComponentProps;

export const BreadcrumbLinkItem: React.FunctionComponent<BreadcrumbLinkItemProps> = (props) => {
    return (
        <BreadcrumbItem
            { ...withoutOuiaProps(props) }
            component={ LinkAdapter }
        >
            { props.children }
        </BreadcrumbItem>
    );
};
