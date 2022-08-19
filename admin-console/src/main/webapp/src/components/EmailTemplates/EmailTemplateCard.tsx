import { Card, CardBody, CardHeader, PageSection, Title } from '@patternfly/react-core';
import * as React from 'react';
import { AggregationCardRow, AggregationTemplate } from '../../types/Notifications';
import { AggregationTemplateCardField } from './Card/AggregationCard';

interface AggregationEmailCardProps {
    application: AggregationCardRow;
    applicationName?: string;
    bundleName?: string;
    onUpdateAggregationTemplate: ( aggregationTemplate: Partial<AggregationTemplate> ) => void;

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
                    { `Aggregation Template: ${
                        <AggregationTemplateCardField
                            application={ props.application }
                            onClick={ () =>
                                !props.application.aggregationEmail.isLoading && props.onUpdateAggregationTemplate(props.application.aggregationEmail) }
                        />}
                        `}
                </CardBody>
            </Card>
        </PageSection>
    );
};
