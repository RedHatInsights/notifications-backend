import { Chip, Spinner } from '@patternfly/react-core';
import * as React from 'react';

import { useInstantEmailTemplates } from '../../services/EmailTemplates/GetInstantTemplates';

export const ListInstantTemplates: React.FunctionComponent = () => {
    const getInstantTemplates = useInstantEmailTemplates();

    if (getInstantTemplates.loading) {
        return <Spinner />;
    }

    if (getInstantTemplates.payload?.status !== 200) {
        return <span>Error while loading instant templates: {getInstantTemplates.errorObject.toString()}</span>;
    }

    return (
        <React.Fragment>
            <div>
                { getInstantTemplates.payload.value.length === 0 ? 'No instant templates found' : '' }
            </div>
            <div>
                { getInstantTemplates.payload.value.map(e => (
                    <div key={ e.id }> {[ e.subject_template?.name, e.body_template?.name ]} </div>
                ))}
            </div>
        </React.Fragment>
    );
};
