import { BreadcrumbItem, BreadcrumbItemProps } from '@patternfly/react-core';
import * as React from 'react';

import { LinkAdapter } from './LinkAdapter';

type BreadcrumbLinkItemProps = Omit<BreadcrumbItemProps, 'component'>

export const BreadcrumbLinkItem: React.FunctionComponent<BreadcrumbLinkItemProps> = (props) => {
    return (
        <BreadcrumbItem
            { ...props }
            component={ LinkAdapter }
        >
            { props.children }
        </BreadcrumbItem>
    );
};
