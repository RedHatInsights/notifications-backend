import { Chip, ChipGroup, Spinner } from '@patternfly/react-core';
import * as React from 'react';

import { useSystemBehaviorGroups } from '../../services/SystemBehaviorGroups/GetBehaviorGroups';

export const ListSystemBehaviorGroups: React.FunctionComponent = () => {
    const getSystemBehaviorGroups = useSystemBehaviorGroups();

    if (getSystemBehaviorGroups.loading) {
        return <Spinner />;
    }

    if (getSystemBehaviorGroups.payload?.status !== 200) {
        return <span>Error while loading system behavior groups: {getSystemBehaviorGroups.errorObject.toString()}</span>;
    }

    return (
        <React.Fragment>
            <ChipGroup>
                { getSystemBehaviorGroups.payload.value.length === 0 ? 'No system behavior groups' : '' }
            </ChipGroup>
            <ChipGroup>
                { getSystemBehaviorGroups.payload.value.map(g => (
                    <Chip isReadOnly key={ g.id }>{ g.displayName}</Chip>
                ))}
            </ChipGroup>
        </React.Fragment>
    );
};
