import { CodeEditor, Language } from '@patternfly/react-code-editor';
import { ActionGroup, Button, PageSection, Split, SplitItem, Title } from '@patternfly/react-core';
import React from 'react';
import { Link } from 'react-router-dom';

import { linkTo } from '../Routes';

export const CreateEmailTemplatePage: React.FunctionComponent = () => {
    return (
        <>

            <PageSection>
                <Split>
                    <SplitItem isFilled>
                        <Title headingLevel="h1">Create/Edit Email Template for $applicationDisplayName</Title>
                    </SplitItem>
                    <SplitItem>
                        <Button variant="primary" component={ (props: any) =>
                            <Link { ...props } to={ linkTo.application } /> } isDisabled={ true }>Go back</Button>
                    </SplitItem>
                </Split>
            </PageSection>
            <PageSection>
                <Title headingLevel="h2">Title template</Title>
                <CodeEditor
                    isLineNumbersVisible
                    height="50px"
                />
            </PageSection>
            <PageSection>
                <Title headingLevel="h2">Body template</Title>
                <CodeEditor
                    isLineNumbersVisible
                    isMinimapVisible={ false }
                    height="300px"
                />
            </PageSection>
            <PageSection>
                <Title headingLevel="h2">Payload</Title>
                <CodeEditor
                    isLineNumbersVisible
                    height="100px"
                    isLanguageLabelVisible
                    language={ Language.json }
                />
            </PageSection>
            <PageSection>
                <ActionGroup>
                    <Button variant='primary' type='submit' isDisabled={ true }>Save</Button>
                    <Button variant='link' type='reset' isDisabled={ true }>Cancel</Button>
                </ActionGroup>
            </PageSection>
        </>

    );

};
