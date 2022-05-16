import { Breadcrumb, BreadcrumbItem, Card, CardBody, CardHeader, PageSection, Spinner, Title } from '@patternfly/react-core';
import * as React from 'react';
import { Link } from 'react-router-dom';

import { useAggregatedEmailTemplates } from '../../services/EmailTemplates/GetAggregationTemplates';

interface AggregationEmailCardProps {
    applicationName?: string;
    bundleName?: string;
}

export const AggregationTemplateCard: React.FunctionComponent<AggregationEmailCardProps> = (props) => {

    const getAggregatedTemplates = useAggregatedEmailTemplates();

    if (getAggregatedTemplates.loading) {
        return <Spinner />;
    }

    if (getAggregatedTemplates.payload?.status !== 200) {
        return <span>Error while loading eventtypes: {getAggregatedTemplates.errorObject.toString()}</span>;
    }

    return (
        <PageSection>
            <Title headingLevel="h1">
                <Breadcrumb>
                    <BreadcrumbItem target='#'>Aggregation Email Template for { props.applicationName }</BreadcrumbItem>
                </Breadcrumb></Title>
            <Card>
                <CardHeader>
                    {`Application: ${ props.applicationName } `}
                </CardHeader>
                <CardBody>
                    {`Bundle: ${ props.bundleName }`}
                </CardBody>
                { getAggregatedTemplates.payload.value.map(a => (
                    <CardBody key={ a.id }>
                        {`Aggregation Template: ${ <Link to=''>{a.body_template?.name }</Link> }` }
                    </CardBody>
                ))}
            </Card>
        </PageSection>
    );
};
