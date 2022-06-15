import { Chip, ChipGroup, Spinner } from '@patternfly/react-core';
import * as React from 'react';
import { useParams } from 'react-router-dom';

import { useInstantEmailTemplates } from '../../services/EmailTemplates/GetInstantTemplates';

interface EmailTemplateParams {
    applicationId: string;
}

export const ListAssociatedEventTypes: React.FunctionComponent = () => {
    const { applicationId } = useParams<EmailTemplateParams>();
    const getAssociatedEventTypes = useInstantEmailTemplates(applicationId);

    if (getAssociatedEventTypes.loading) {
        return <Spinner />;
    }

    if (getAssociatedEventTypes.payload?.status !== 200) {
        return <span>Error while loading eventtypes: {getAssociatedEventTypes.errorObject.toString()}</span>;
    }

    return (
        <React.Fragment>
            <ChipGroup>
                { getAssociatedEventTypes.payload.value.length === 0 ? 'No event types' : '' }
            </ChipGroup>
            <ChipGroup>
                { getAssociatedEventTypes.payload.value.map(e => (
                    <Chip isReadOnly key={ e.id }>{ e.event_type?.display_name }</Chip>
                ))}
            </ChipGroup>
        </React.Fragment>
    );
};
