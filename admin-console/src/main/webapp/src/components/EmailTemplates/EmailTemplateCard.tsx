import { Breadcrumb, BreadcrumbItem, Card, CardBody, CardHeader, PageSection, Title } from '@patternfly/react-core';
import * as React from 'react';
import { Link } from 'react-router-dom';

interface AggregationEmailCardProps {
    applicationName?: string;
    bundleName?: string;
    templateName: (string | undefined)[] | undefined;
}

export const AggregationTemplateCard: React.FunctionComponent<AggregationEmailCardProps> = (props) => {

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
                <CardBody>
                    {`Aggregation Template: ${ <Link to=''>{ props.templateName }</Link> }` }
                </CardBody>
            </Card>
        </PageSection>
    );
};
