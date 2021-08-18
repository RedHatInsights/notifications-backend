import { CodeEditor, Language } from '@patternfly/react-code-editor';
import {
    HelperText,
    HelperTextItem,
    PageSection,
    Spinner,
    Split,
    SplitItem,
    Stack,
    StackItem,
    Title
} from '@patternfly/react-core';
import { Button } from '@patternfly/react-core/';
import * as React from 'react';

import { useRenderEmailRequest } from '../services/RenderEmailRequest';

const defaultSubjectTemplate = `
Important email to {user.firstName} from MyCoolApp!
`.trimLeft();

const defaultBodyTemplate = `
<div>Hello {user.firstName} {user.lastName},</div>
<div>We have some important news for you, MyApp has a notification for you</div>
<div>As a reminder, current user: {user.username}: is active? {user.isActive}; is admin? {user.isAdmin}</div>
<div>
    System with name <strong>{action.context.display_name}</strong> (<strong>{action.context.inventory_id}</strong>) 
    did a check in at {action.context.system_check_in.toUtcFormat()}. 
    It was about {action.context.system_check_in.toTimeAgo()}
</div>
<div>This is a loop:</div>
{#if action.events.size() > 0}
<ul>
    {#each action.events}
        <li>
            <a href="http://google.com?q={it.payload.my_id}" target="_blank">{it.payload.my_name}</a>
        </li>
    {/each}
</ul>
<div>Have a nice day!</div>
{/if}
`.trimLeft();

const defaultPayload = JSON.stringify({
    bundle: 'rhel',
    application: 'policies',
    event_type: 'policy-triggered',
    timestamp: '2021-08-05T16:21:14.243',
    account_id: '5758117',
    // eslint-disable-next-line max-len
    context: '{"inventory_id":"80f7e57d-a16a-4189-82af-1d68a747c8b3","system_check_in":"2021-08-05T16:21:12.953036","display_name":"cool display name"}',
    events: [
        {
            metadata: {},
            payload: '{"my_id":"3df53241-3e09-481b-a322-4892caaaaadc","my_name":"Red color"}'
        },
        {
            metadata: {},
            payload: '{"my_id":"6c5e8451-a40a-4bb7-ab9a-0cb10a4c577d","my_name":"Green color"}'
        },
        {
            metadata: {},
            payload: '{"my_id":"b4c6378a-c1fb-4d3e-8e9b-7e5bdfc09dd3","my_name":"Blue color"}'
        }
    ]
}, null, 2);

type RenderedTemplateProps = {
    isLoading: true;
} | {
   isLoading: false;
   succeeded: true;
   subject: string;
   body: string;
} | {
    isLoading: false;
    succeeded: false;
    error: string;
};

const RenderedTemplate: React.FunctionComponent<RenderedTemplateProps> = props => {
    if (props.isLoading) {
        return <Spinner />;
    }

    if (props.succeeded) {
        return (
            <>
                <StackItem>
                    <span><strong>Subject:</strong> { props.subject }</span>
                </StackItem>
                <StackItem>
                    <strong>Body:</strong>
                </StackItem>
                <StackItem>
                    <iframe width="100%" srcDoc={ props.body } />
                </StackItem>
            </>
        );
    }

    return (
        <StackItem>
            <HelperText>
                <HelperTextItem variant="error">{ props.error }</HelperTextItem>
            </HelperText>
        </StackItem>
    );
};

export const RenderEmailPage: React.FunctionComponent = () => {
    const emailTemplate = useRenderEmailRequest();
    const [ subjectTemplate, setSubjectTemplate ] = React.useState<string | undefined>(defaultSubjectTemplate);
    const [ bodyTemplate, setBodyTemplate ] = React.useState<string | undefined>(defaultBodyTemplate);
    const [ payload, setPayload ] = React.useState<string | undefined>(defaultPayload);

    React.useEffect(() => {
        const mutate = emailTemplate.mutate;
        mutate({
            subject: subjectTemplate ?? '',
            body: bodyTemplate ?? '',
            payload: payload ?? ''
        });
        // We only want to activate this once
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [ ]);

    let renderedProps: RenderedTemplateProps;

    console.log(emailTemplate);

    if (emailTemplate.loading) {
        renderedProps = {
            isLoading: true
        };
    } else if (emailTemplate.payload?.status === 200) {
        renderedProps = {
            isLoading: false,
            succeeded: true,
            subject: emailTemplate.payload.value.subject ?? '',
            body: emailTemplate.payload.value.body ?? ''
        };
    } else if (emailTemplate.payload?.status === 400) {
        renderedProps = {
            isLoading: false,
            succeeded: false,
            error: emailTemplate.payload.value.message ?? 'Unknown error'
        };
    } else {
        renderedProps = {
            isLoading: false,
            succeeded: false,
            error: 'Unknown error'
        };
    }

    const onRender = React.useCallback(() => {
        const mutate = emailTemplate.mutate;
        mutate({
            subject: subjectTemplate ?? '',
            body: bodyTemplate ?? '',
            payload: payload ?? ''
        });
    }, [ emailTemplate.mutate, subjectTemplate, bodyTemplate, payload ]);

    return (
        <>
            <PageSection>
                <Split>
                    <SplitItem isFilled>
                        <Title headingLevel="h1" >Email templates</Title>
                    </SplitItem>
                    <SplitItem>
                        <Button onClick={ onRender }>Render</Button>
                    </SplitItem>
                </Split>
            </PageSection>
            <PageSection>
                <Stack>
                    <StackItem>
                        <Title headingLevel="h2">Result</Title>
                    </StackItem>
                    <RenderedTemplate { ...renderedProps }  />
                </Stack>
            </PageSection>
            <PageSection>
                <Title headingLevel="h2">Subject template</Title>
                <CodeEditor
                    isLineNumbersVisible
                    isMinimapVisible={ false }
                    code={ defaultSubjectTemplate }
                    height="50px"
                    onChange={ setSubjectTemplate }
                />
            </PageSection>
            <PageSection>
                <Title headingLevel="h2">Body template</Title>
                <CodeEditor
                    isLineNumbersVisible
                    isMinimapVisible={ false }
                    code={ defaultBodyTemplate }
                    height="300px"
                    onChange={ setBodyTemplate }
                />
            </PageSection>
            <PageSection>
                <Title headingLevel="h2">Payload</Title>
                <CodeEditor
                    isLineNumbersVisible
                    isMinimapVisible={ false }
                    code={ defaultPayload }
                    height="300px"
                    isLanguageLabelVisible
                    language={ Language.json }
                    onChange={ setPayload }
                />
            </PageSection>
        </>
    );
};
