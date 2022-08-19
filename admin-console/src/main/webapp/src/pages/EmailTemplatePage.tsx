import { CodeEditor, Language } from '@patternfly/react-code-editor';
import {
    ActionGroup,
    Button,
    Form,
    FormGroup,
    PageSection,
    Split,
    SplitItem,
    TextInput,
    Title
} from '@patternfly/react-core';
import * as React from 'react';
import { useUserPermissions } from '../app/PermissionContext';
import { useCreateTemplate } from '../services/EmailTemplates/CreateTemplate';
import { Template } from '../types/Notifications';

const defaultContentTemplate = `
Important email to {user.firstName} from MyCoolApp!

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

export const EmailTemplatePage: React.FunctionComponent = () => {
    const { isAdmin } = useUserPermissions();
    const handleBackClick = React.useCallback(() => {
        history.back();
    }, []);

    const newTemplate = useCreateTemplate();
    const [ template, setTemplate ] = React.useState<Partial<Template>>({
        data: defaultContentTemplate
    });

    const handleChange = (value: string, event: React.FormEvent<HTMLInputElement>) => {
        const target = event.target as HTMLInputElement;
        setTemplate(prev => ({ ...prev, [target.name]: target.value }));
    };

    const handleCodeChange = (value: string) => {
        setTemplate(prev => ({ ...prev, data: value }));
    };

    const handleSubmit = React.useCallback(() => {
        if (template && template.data && template.name && template?.description) {
            const mutate = newTemplate.mutate;
            mutate({
                id: template.id ?? undefined,
                data: template.data,
                name: template.name,
                description: template.description
            }).then(() => {
                handleBackClick();
            });
        }

    }, [ handleBackClick, newTemplate.mutate, template ]);

    return (
        <>{ isAdmin &&
            <><PageSection>
                <Split>
                    <SplitItem isFilled>
                        <Title headingLevel="h1">Create an Email Template</Title>
                    </SplitItem>
                </Split>
            </PageSection><PageSection>
                <Form>
                    <FormGroup label='Name' fieldId='name' isRequired
                        helperText='Enter a name for your template'>
                        <TextInput
                            type='text'
                            id='name'
                            name="name"
                            value={ template?.name }
                            onChange={ handleChange }
                        /></FormGroup>
                    <FormGroup label='Description' fieldId='description' isRequired
                        helperText='Enter a brief description for your template'>
                        <TextInput
                            type='text'
                            id='description'
                            name="description"
                            value={ template?.description }
                            onChange={ handleChange }
                        /></FormGroup>
                    <FormGroup>
                        <Title headingLevel="h2">Data</Title>
                        <CodeEditor
                            isLineNumbersVisible
                            code={ template?.data }
                            isMinimapVisible={ false }
                            onChange={ handleCodeChange }
                            height="300px" />
                    </FormGroup>
                    <FormGroup>
                        <Title headingLevel="h2">Payload</Title>
                        <CodeEditor
                            isLineNumbersVisible
                            isMinimapVisible={ false }
                            onChange={ handleCodeChange }
                            code={ defaultPayload }
                            value={ template?.data }
                            height="300px"
                            isLanguageLabelVisible
                            language={ Language.json } />
                    </FormGroup>
                </Form>
            </PageSection>
            <PageSection>
                <ActionGroup>
                    <Split hasGutter>
                        <SplitItem>
                            <Button variant='primary' onClick={ handleSubmit } type='submit'>Submit</Button>
                        </SplitItem>
                        <SplitItem>
                            <Button variant='secondary' type='reset' onClick={ handleBackClick }>Back</Button>
                        </SplitItem>
                    </Split>
                </ActionGroup>
            </PageSection>
            </>
        }
        </>
    );
};
