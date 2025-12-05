import { Card, CardBody, CardHeader, PageSection, Title } from '@patternfly/react-core';
import * as React from 'react';

interface AggregationEmailCardProps {
    applicationName?: string;
    bundleName?: string;
    templateName: (string | undefined)[] | undefined;
}

export const AggregationTemplateCard: React.FunctionComponent<AggregationEmailCardProps> = (props) => {

    return (
        <PageSection>
            <Title headingLevel="h3">
                Aggregation Email Template for { props.applicationName }
            </Title>
            <Card>
                <CardHeader>
                    {`Application: ${ props.applicationName } `}
                </CardHeader>
                <CardBody>
                    {`Bundle: ${ props.bundleName }`}
                </CardBody>
                <CardBody>
                    {`Aggregation Template: ${ props.templateName } `}
                </CardBody>
            </Card>
        </PageSection>
    );
};
